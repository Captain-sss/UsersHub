# Deep Freeze 说明

UsersHub 1.2 的“冻结”是深度冻结：

1. 对目标 Android 用户的所有第三方 App 执行 `pm suspend --user`。
2. 对该用户所有带 Launcher 图标的系统 App 也执行 `pm suspend --user`，因此设置、文件管理、相册等可见系统 App 在 MIUI 桌面会进入灰色暂停状态（只要系统允许 suspend）。
3. 最后执行 `am stop-user -w -f USER`，停止该用户的后台运行。

不会 suspend 极少数维持 Android 用户可恢复性的核心组件，例如当前 HOME/桌面、SystemUI、Android framework、包管理/权限控制组件，以及 MIUI 的关键安全组件。否则进入冻结空间后可能没有桌面可显示灰色图标，甚至无法正常启动该用户。无图标的系统服务不需要单独 suspend，因为 `stop-user` 已停止该用户的运行；解冻时 UsersHub 只恢复自己记录过的 suspend 包。

如果发生异常，可在 Magisk 模块页面点击 Action：它会执行救援解冻全部，并临时关闭开机自动冻结。也可以在 root shell 执行：

```sh
usershubctl rescue-all
```

恢复开机自动冻结：

```sh
usershubctl boot-freeze on
```
