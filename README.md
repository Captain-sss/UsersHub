# UsersHub

适用于已 Root / Magisk 的 Android 多用户设备的**原生空间冻结控制器**。目标环境：Redmi K30 Pro / MIUI 14 / Android 12，多用户 ID 10、11、12…；灰色图标机制已在目标设备通过 `pm suspend --user` 实机验证。

## 需要安装的两个文件

1. `UsersHub-v1.2.0.apk`：真正原生 Android Activity，不使用 WebView、不跳网页；第一次打开直接向 Magisk 请求 Root。
2. `UsersHub-Core-Magisk-v1.2.0.zip`：提供 `usershubctl` root helper、开机深度冻结、Shared 自动挂载、新 App 冻结维护和紧急救援。

GitHub Actions 会自动编译并输出 APK、Magisk ZIP 和 SHA256。

## v1.2 深度冻结

冻结一个空间时：

1. Suspend 该用户全部第三方 App。
2. Suspend 该用户所有带 Launcher 图标的系统 App，因此设置、文件管理、相册、联系人等可见系统 App 在 MIUI 桌面会灰掉（前提是 Android 允许该包被 suspend）。
3. 最后执行 `am stop-user -w -f USER`，停止整个 Android User 的后台，包括没有桌面图标的系统服务。

为了保证冻结空间仍然能启动、显示灰色图标并可恢复，极少数核心组件不会 suspend：当前 HOME/桌面、SystemUI、Android framework、包管理/权限控制组件、当前输入法，以及少量 MIUI 关键安全组件。否则把 HOME 或核心框架一起 suspend 后，进入该空间可能没有桌面甚至无法恢复。无图标核心后台不需要 suspend，因为 `stop-user` 已经停止该用户的运行。

## 解冻

UsersHub 只对自己记录成功 suspend 的包执行 `pm unsuspend --user`，不会解除其他冻结工具原本设置的 suspend。恢复完成后执行 `am start-user -w USER` 并补 Shared。

如果某个包恢复失败，会保留在重试名单，下次点“解冻”继续恢复；已经卸载的包自动忽略。

## 冻结中的新应用

模块后台会维护已经标记为冻结的空间：新安装的第三方 App、后来出现的 Launcher 可见系统 App会自动补 suspend。为了降低后台开销，维护周期约 30 秒。后台不会反复 `stop-user`，因此你切进冻结空间查看灰色图标时不会立刻被踢回主空间。

## Shared

共享源固定为：

```text
/data/media/0/Download/Shared
```

自动 bind mount 到：

```text
/data/media/<用户ID>/Download/Shared
```

使用 Magisk master mount namespace（`su -M`），后续创建的新空间也会自动检查。

## 开机行为

安装后默认启用开机自动深度冻结：重启后会冻结所有 10～899 的次级用户。可手工关闭：

```sh
usershubctl boot-freeze off
```

重新启用：

```sh
usershubctl boot-freeze on
```

## 紧急救援

如果某个系统 App suspend 后导致异常，可以直接到 Magisk → UsersHub Core → Action。Action 会：

1. 解冻 UsersHub 自己管理的全部空间；
2. 恢复 UsersHub 记录的所有 suspended App；
3. 临时关闭下次开机自动冻结。

命令行等价：

```sh
usershubctl rescue-all
```

恢复后重新开启自动冻结：

```sh
usershubctl boot-freeze on
```

## 常用命令

```sh
usershubctl status --plain
usershubctl freeze 10
usershubctl thaw 10
usershubctl freeze-all
usershubctl thaw-all
usershubctl maintain-frozen
usershubctl share-all
usershubctl rescue-all
usershubctl boot-freeze on
usershubctl boot-freeze off
```

## 卸载保护

卸载 Magisk 模块时会先尝试解冻 UsersHub 自己记录的包，再卸载 Shared bind mount，避免留下 UsersHub 自己造成的永久灰色应用。
