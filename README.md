# AdbAutoAuth

无需 root 的 ADB 授权自动放行方案：开启无障碍服务后，系统弹出的“允许 USB 调试吗？”对话框会被自动勾选“始终允许使用这台计算机进行调试”并点击“允许”。

## 适用场景

- 手机没有 root，无法写入 `/data/misc/adb/adb_keys`
- 屏幕损坏 / 不方便手动点授权框
- 希望任何电脑（含局域网无线调试）连接时自动放行

## 为什么不是 Shizuku

Shizuku 在没有 root 时只有 shell（uid 2000）权限，而 `/data/misc/adb/adb_keys` 属主是 `system:shell`、SELinux 标签为 `adb_keys_file`，shell 无法写入；`ro.adb.secure` 也只有 root 能改。所以非 root 场景下唯一的自动化手段就是无障碍点击。

## 安装

1. 下载 Actions 产物或 Release 里的 APK 并安装。
2. 打开应用 → “打开无障碍设置” → 找到“ADB 自动授权”并开启。
3. 之后连接调试即自动放行。

## 与 Magisk 模块的区别

| | 本应用 | Magisk 模块 |
|---|---|---|
| 需要 root | 否 | 是 |
| 效果 | 弹窗仍出现，自动点掉 | 完全不弹窗 |
| 依赖 | 无障碍服务常驻 | adbd 层面生效，无进程可杀 |

有 root 的设备推荐使用配套模块（免弹窗、更稳）。

## 注意

开启后同网段任何设备都能拿到调试权限，请在可信网络下使用。
