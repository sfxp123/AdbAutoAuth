package com.trae.adbautoauth;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 监听系统 ADB 调试授权弹窗，自动勾选“始终允许”并点击“允许”。
 * 与 XRSec/wifi-adb 的做法不同：不做包名白名单、不做“只在自己连接时生效”的时效限制，
 * 只要窗口文本同时具备“调试 / 允许 / 密钥(或始终)”三个特征就执行，因此外部电脑连接同样能触发。
 */
public class AdbAutoAuthService extends AccessibilityService {

    private static final String TAG = "AdbAutoAuth";
    private static final int MAX_NODES = 600;
    private static final int MAX_DEPTH = 40;
    private static final int MAX_ATTEMPTS_PER_DIALOG = 8;

    private static final String[] ALLOW_TEXT = {
            "允许", "允許", "同意", "确定", "確定", "好",
            "always allow", "allow", "ok", "yes"
    };

    private static final String[] DENY_TEXT = {
            "不允许", "不允許", "拒绝", "拒絕", "取消", "稍后", "稍後",
            "deny", "cancel", "later"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastActionAt = 0L;
    private int attempts = 0;
    private String lastDialogKey = "";

    private final Runnable scanner = new Runnable() {
        @Override
        public void run() {
            scan();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "无障碍服务已连接，开始监听 ADB 授权弹窗");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg != null && getPackageName().contentEquals(pkg)) {
            return;
        }
        schedule(60);
    }

    @Override
    public void onInterrupt() {
        // 无操作
    }

    private void schedule(long delay) {
        handler.removeCallbacks(scanner);
        handler.postDelayed(scanner, delay);
    }

    private void scan() {
        // 防止同一弹窗被连续点击导致事件风暴
        if (System.currentTimeMillis() - lastActionAt < 250) {
            return;
        }
        List<AccessibilityNodeInfo> roots = collectRoots();
        for (AccessibilityNodeInfo root : roots) {
            if (root == null) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            collectText(root, sb, new int[]{0}, 0);
            String text = sb.toString().toLowerCase(Locale.ROOT);
            if (!isAdbAuthDialog(text)) {
                continue;
            }
            String key = Integer.toHexString(text.hashCode());
            if (!key.equals(lastDialogKey)) {
                lastDialogKey = key;
                attempts = 0;
            }
            if (attempts >= MAX_ATTEMPTS_PER_DIALOG) {
                continue;
            }
            if (handleDialog(root)) {
                attempts++;
                lastActionAt = System.currentTimeMillis();
                schedule(600);
                return;
            }
        }
    }

    private List<AccessibilityNodeInfo> collectRoots() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    try {
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root != null) {
                            roots.add(root);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (roots.isEmpty()) {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    roots.add(root);
                }
            } catch (Throwable ignored) {
            }
        }
        return roots;
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb, int[] count, int depth) {
        if (node == null || depth > MAX_DEPTH || count[0] > MAX_NODES) {
            return;
        }
        count[0]++;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            sb.append(text).append(' ');
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0) {
            sb.append(desc).append(' ');
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            try {
                collectText(node.getChild(i), sb, count, depth + 1);
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean isAdbAuthDialog(String text) {
        boolean hasDebug = text.contains("调试") || text.contains("debug") || text.contains("adb");
        boolean hasAsk = text.contains("允许") || text.contains("允許") || text.contains("allow");
        boolean hasKey = text.contains("rsa")
                || text.contains("密钥") || text.contains("密鑰")
                || text.contains("指纹") || text.contains("fingerprint")
                || text.contains("授权") || text.contains("authoriz")
                || text.contains("始终") || text.contains("always");
        return hasDebug && hasAsk && hasKey;
    }

    private boolean handleDialog(AccessibilityNodeInfo root) {
        // 第一步：先勾选“始终允许使用这台计算机进行调试”，避免下一轮才生效
        AccessibilityNodeInfo alwaysAllow = findAlwaysAllow(root, new int[]{0}, 0);
        if (alwaysAllow != null && !alwaysAllow.isChecked()) {
            if (click(alwaysAllow)) {
                Log.i(TAG, "已勾选“始终允许”");
                return true;
            }
        }
        // 第二步：点击“允许”
        AccessibilityNodeInfo allow = findAllowButton(root, new int[]{0}, 0);
        if (allow != null && click(allow)) {
            Log.i(TAG, "已自动点击“允许”");
            return true;
        }
        return false;
    }

    private AccessibilityNodeInfo findAlwaysAllow(AccessibilityNodeInfo node, int[] count, int depth) {
        if (node == null || depth > MAX_DEPTH || count[0] > MAX_NODES) {
            return null;
        }
        count[0]++;
        if (node.isCheckable()) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String s = ((text == null ? "" : text.toString()) + " "
                    + (desc == null ? "" : desc.toString())).toLowerCase(Locale.ROOT);
            if (s.contains("始终") || s.contains("always")) {
                return node;
            }
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            try {
                AccessibilityNodeInfo found = findAlwaysAllow(node.getChild(i), count, depth + 1);
                if (found != null) {
                    return found;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findAllowButton(AccessibilityNodeInfo node, int[] count, int depth) {
        if (node == null || depth > MAX_DEPTH || count[0] > MAX_NODES) {
            return null;
        }
        count[0]++;
        if (node.isClickable() && !node.isCheckable()) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String s = ((text == null ? "" : text.toString()) + " "
                    + (desc == null ? "" : desc.toString())).trim();
            if (matchesAllow(s)) {
                return node;
            }
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            try {
                AccessibilityNodeInfo found = findAllowButton(node.getChild(i), count, depth + 1);
                if (found != null) {
                    return found;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private boolean matchesAllow(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        String t = s.toLowerCase(Locale.ROOT);
        for (String deny : DENY_TEXT) {
            if (t.contains(deny)) {
                return false;
            }
        }
        for (String allow : ALLOW_TEXT) {
            if (t.contains(allow)) {
                return true;
            }
        }
        return false;
    }

    private boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        for (int i = 0; i < 3 && target != null; i++) {
            if (target.isClickable()) {
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            }
            try {
                target = target.getParent();
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }
}
