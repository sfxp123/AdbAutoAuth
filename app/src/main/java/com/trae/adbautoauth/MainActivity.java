package com.trae.adbautoauth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("ADB 自动授权");
        title.setTextSize(24);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setPadding(0, pad, 0, pad);
        root.addView(statusView);

        Button open = new Button(this);
        open.setText("打开无障碍设置");
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable ignored) {
            }
        });
        root.addView(open);

        TextView help = new TextView(this);
        help.setTextSize(14);
        help.setPadding(0, pad, 0, 0);
        help.setText("使用说明：\n"
                + "1. 点上面的按钮，在无障碍列表里找到“ADB 自动授权”并开启。\n"
                + "2. 之后任何电脑连接 USB 调试或无线调试，弹窗里的“允许”会被自动点击，"
                + "并自动勾选“始终允许使用这台计算机进行调试”。\n"
                + "3. 无需 root，也无需 Shizuku：非 root 下 Shizuku 只有 shell 权限，"
                + "改不了 /data/misc/adb/adb_keys，无法做到免弹窗，所以本应用走无障碍点击。\n"
                + "4. Android 11 及以上要先用无线调试的配对码配对一次，之后连接会自动放行。\n"
                + "5. 建议在电池设置里把本应用设为“不受限制”，避免被后台清理后失效。\n\n"
                + "注意：开启后同网段任何设备都能拿到调试权限，请只在可信网络下使用。");
        root.addView(help);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean enabled = isServiceEnabled();
        statusView.setText(enabled
                ? "状态：已开启，正在自动同意调试授权"
                : "状态：未开启（请在下方的无障碍设置里启用）");
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        String cls = AdbAutoAuthService.class.getName();
        for (String item : enabled.split(":")) {
            if (item.contains(getPackageName()) && item.contains(cls)) {
                return true;
            }
        }
        return false;
    }
}
