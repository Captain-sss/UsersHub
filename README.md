# UsersHub

适用于已 Root / Magisk 的 Android 多用户设备的原生空间冻结控制器。目标环境：Redmi K30 Pro / MIUI 14 / Android 12，多用户 ID 10、11、12…；灰色图标机制已在目标设备通过 `pm suspend --user` 实机验证。

## v1.2.3

需要安装两个文件：

1. `UsersHub-v1.2.3.apk`：原生 Android Activity，不使用 WebView，不跳网页，直接请求 Magisk Root。
2. `UsersHub-Core-Magisk-v1.2.3.zip`：提供冻结/解冻、开机策略、Shared 生命周期、紧急救援。

## 最深冻结

v1.2.2 及以前使用“全部第三方包 + Launcher 可见系统包”作为候选。MIUI 在用户已经停止时可能对 Launcher query 返回不完整，因此会出现 UsersHub 显示已冻结但仍有下载管理、小米视频等系统 App 保持彩色，甚至冻结记录为 0 个包。

v1.2.3 改为直接枚举该用户安装的全部 package。除极少数恢复/启动必须保留的核心组件外，对系统包和第三方包都尝试执行：

```sh
pm suspend --user USER PACKAGE
```

然后执行：

```sh
am stop-user -w -f USER
```

Android 自己明确禁止 suspend 的系统包记为 `protected`，不会让整次冻结失败。UsersHub 明确保留的核心包括：当前 HOME/桌面、SystemUI、Android framework、shell、包管理/权限组件、设置/媒体等关键 provider、电话核心、当前输入法和少量 MIUI 启动/安全核心。其余安装包都会尝试 suspend。

冻结记录只保存 UsersHub 自己成功暂停的包。解冻时只恢复这些记录，因此不会解除其他冻结工具原本的 suspend 状态。

## Shared

Shared 继续遵循 v1.2.2 的真实用户生命周期：

- 只有真实运行中的空间才 bind mount Shared。
- 不会为了 Shared 临时启动冻结/停止的空间。
- 不会给从未初始化的用户预先制造假的 Shared。
- 空间停止/冻结后自动卸载 Shared。
- 空间真正启动后，Android 自己准备好 `/data/media/<ID>`，再挂载：

```text
/data/media/0/Download/Shared
        ->
/data/media/<ID>/Download/Shared
```

## 修复已有“0 个包”的冻结空间

升级 v1.2.3 并重启后，后台会重新扫描已经标记冻结的空间。也可以在 APK 里点“全部冻结/重扫”，或者执行：

```sh
usershubctl freeze-all
```

正常情况下冻结记录数量会从 0 变成实际被 UsersHub suspend 的包数量。

## 紧急救援

Magisk → UsersHub Core → Action，或执行：

```sh
usershubctl rescue-all
```

会解冻 UsersHub 管理的全部空间并暂时关闭下次开机自动冻结。

## 常用命令

```sh
usershubctl status --plain
usershubctl freeze 10
usershubctl thaw 10
usershubctl freeze-all
usershubctl thaw-all
usershubctl maintain-frozen
usershubshare status
usershubshare all
usershubctl rescue-all
usershubctl boot-freeze on
usershubctl boot-freeze off
```
