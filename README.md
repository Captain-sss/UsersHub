# UsersHub

适用于已 Root / Magisk 的 Android 多用户设备的**原生空间冻结控制器**。

当前目标环境：Redmi K30 Pro / MIUI 14 / Android 12，多用户 ID 10、11、12…；实现基于已经实机验证成功的 `pm suspend --user` 灰色图标机制。

## 需要安装的两个文件

1. `UsersHub-v1.0.0.apk`：原生 Android APK，不使用 WebView、不跳网页。
2. `UsersHub-Core-Magisk-v1.0.0.zip`：Magisk 模块，提供 root helper、开机策略和 Shared 挂载。

GitHub Actions 每次提交会自动编译，并在 `UsersHub-v1.0.0` artifact 中同时产出二者。

## 冻结一个空间

例如冻结用户 10：读取用户 10 当前第三方 App，跳过已经被其他工具 suspend 的 App，对其余第三方 App执行 `pm suspend --user 10 <package>`；只记录 UsersHub 自己成功 suspend 的包名，然后执行 `am stop-user -w -f 10`。因此 App 图标保持灰色，再次切入该用户也不会自动解除 suspend。

## 解冻一个空间

只恢复 UsersHub 自己保存的包名单，已卸载包自动忽略，恢复失败会保留到重试名单；之后执行 `am start-user -w 10` 并自动补 Shared。不会随意解除其他冻结工具原本设置的 suspend 状态。

## Root

APK 第一次打开会直接执行 `su`，因此会出现 Magisk 超级用户授权弹窗。APK 不运行 HTTP Server，不访问 `127.0.0.1`，也不打开浏览器。

## Shared

共享源：`/data/media/0/Download/Shared`，自动 bind mount 到 `/data/media/<用户ID>/Download/Shared`。模块每 15 秒检查一次后续创建的新用户，并补 Shared；挂载使用 Magisk master mount namespace。

## 开机行为

重启后默认冻结所有 10～899 的次级用户空间。新建用户在当前启动周期内只自动补 Shared，不会在初始化过程中被突然冻结；下次重启会进入默认冻结状态。

## 卸载保护

卸载 Magisk 模块前会先解冻 UsersHub 自己记录的 suspend 包，再卸载 Shared 挂载，避免留下无法恢复的灰色 App。

## 手工排障

```sh
usershubctl status --plain
usershubctl freeze 10
usershubctl thaw 10
usershubctl freeze-all
usershubctl thaw-all
usershubctl share-all
```

日志：`/data/adb/usershub/usershub.log`
