# UsersHub

Android 多用户空间冻结/解冻控制器（Root / Magisk）。

- 原生 APK：直接通过 Magisk `su` 获取 Root，不使用网页或 WebView。
- 冻结空间：记录该用户当前第三方 App → `pm suspend --user`（图标灰色）→ `am stop-user`。
- 解冻空间：仅恢复 UsersHub 自己记录的 App → `pm unsuspend --user` → `am start-user`。
- Shared：配套 Magisk 模块把 `/data/media/0/Download/Shared` bind mount 到后续创建的次级用户。
- 当前前台用户不会被直接 stop，避免把正在操作的空间踢掉。

> 目标设备：已解锁 / Root 的 Android 多用户设备；当前方案按 Redmi K30 Pro / MIUI 14 / Android 12 的实测路径设计。
