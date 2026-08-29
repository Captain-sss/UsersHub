# UsersHub

适用于已 Root / Magisk 的 Android 多用户设备的**原生空间冻结控制器**。当前目标环境：Redmi K30 Pro / MIUI 14 / Android 12，多用户 ID 10、11、12…；核心灰色图标机制已在目标设备用 `pm suspend --user` 实机验证。

## 最终需要安装的两个文件

1. `UsersHub-v1.1.0.apk`：真正原生 Android Activity，不使用 WebView、不跳网页；第一次打开直接向 Magisk 请求 Root。
2. `UsersHub-Core-Magisk-v1.1.0.zip`：提供 `usershubctl` root helper、开机冻结策略、Shared 自动挂载、新 App 冻结维护和卸载恢复保护。

## 冻结

冻结某个用户时，只处理该用户的第三方 App：跳过已经被其他工具 suspend 的 App，将 UsersHub 自己成功 suspend 的包记录到 `/data/adb/usershub/frozen/<user>.list`，再 `stop-user`。这样该空间 App 图标为灰色，重新进入空间也不会自动解除 suspend。

如果某个包第一次 suspend 失败，再点一次“冻结”会自动重试；空间处于冻结状态时新安装的第三方 App 会在约 15 秒内自动补 suspend，但后台不会反复 `stop-user`，因此你仍可以切进冻结空间查看灰色图标而不被强制踢出。

## 解冻

只对 UsersHub 自己记录的包执行 `unsuspend`，不会解除其他冻结工具原本拥有的 suspend 状态。已卸载包自动忽略；恢复失败的包会保留在重试名单里，下次再点“解冻”继续恢复。随后启动用户并补 Shared。

## Shared

共享源固定为 `/data/media/0/Download/Shared`，自动 bind mount 到 `/data/media/<用户ID>/Download/Shared`。挂载使用 Magisk master mount namespace（`su -M`），并每 15 秒发现后续新建空间。

## 开机

按照需求，重启后默认冻结所有 10～899 的次级用户。当前启动周期中新建的用户只自动补 Shared，不会在初始化中突然被 stop；下次重启进入默认冻结。

## 冲突处理与卸载保护

安装模块时会自动禁用旧开发版模块：`shared_users_download`、`user_spaces_control`、`usershub_shared_control`、`usershub_space_freezer`。卸载新模块时会先恢复 UsersHub 自己记录的 suspended App，再卸载 Shared，避免留下永久灰色应用。

## APK 签名

仓库使用固定的个人工具签名密钥构建 v1.1.0 及后续版本，避免 GitHub Actions 每次随机 debug key 导致新版 APK 无法覆盖安装旧版。此签名仅用于该私人仓库中的个人工具，不作为商店生产签名。

## 手工排障

```sh
usershubctl status --plain
usershubctl freeze 10
usershubctl thaw 10
usershubctl freeze-all
usershubctl thaw-all
usershubctl maintain-frozen
usershubctl share-all
```

日志：`/data/adb/usershub/usershub.log`
