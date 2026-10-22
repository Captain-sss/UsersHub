# UsersHub

适用于已 Root / Magisk 的 Android 多用户设备的原生空间冻结控制器。目标环境：Redmi K30 Pro / MIUI 14 / Android 12，多用户 ID 10、11、12…；灰色图标机制已在目标设备通过 `pm suspend --user` 实机验证。

## v1.9.1（ReSukiSU/KernelSU 兼容候选版）

基于 v1.9.0 原有空间、冻结、文件共享及设备信息功能，修复在 ReSukiSU/KernelSU **未安装元模块**时，Core 虽已安装但 `/system/bin/usershub*` 不可见、APK 报错的情况。APK 和 Core 服务现在直接解析已启用模块的实际命令目录；身份信息工具可以使用 KernelSU 内置 BusyBox，Shared 挂载兼容全局 Mount Namespace。

- 保留 Magisk 支持；不修改内核、不依赖强制安装 Magic Mount 元模块。
- **新 APK + 新 Core 必须配套使用**。更新前请备份现有 `/data/adb/usershub` 数据与空间状态；使用相同模块 ID 在 ReSukiSU 中升级并重启，不要先卸载旧版 Core（卸载清理脚本会清除自身状态数据）。
- 候选版仍需在 Redmi K30 Pro / MIUI 14 / Android 12 / ReSukiSU 上实机验证后台服务、Shared 实际挂载和 LSPosed 作用域，不保证仅首页可显示就代表完整功能正常。

候选构建：`dist/UsersHub-v1.9.1.apk`、`dist/UsersHub-Core-Magisk-v1.9.1.zip`。

## v1.9.0：空间优先界面与一致设备资料

- 主界面采用“空间优先”的信息层级：创建空间是唯一主操作，批量冻结/解冻收进二级入口；空间卡片只保留状态操作、应用和设备信息，改名与删除进入“管理”。
- 空间管理和设备配置共享同一套颜色、圆角、间距、按钮和状态标签；设备页先显示空间、服务、适用应用和当前资料，高级字段默认收起。
- 机型模板会从完整构建指纹可靠拆出 Build ID、增量版本、构建类型和签名标签，并同时覆盖对应 Java Build 字段与进程内 `ro.*` 读取，停用后恢复原值。
- “随机同版本机型”只从与本机 Android 主版本一致的完整模板中选择；不同系统版本模板仍允许专家手动选择，但会明确提示 SDK/系统版本不会随之改变。
- 仍不修改 SDK、CPU、屏幕、基带、Play Integrity 或硬件认证。批量生成空间配置继续只生成 10 项基础标识，不暗示服务器认可或完整硬件身份改变。

## v1.8.3：扩展机型库与搜索

- 离线机型库从 6 套扩展到 44 套，覆盖 Google、Samsung、Xiaomi/Redmi、OnePlus、OPPO、realme、Meizu、HUAWEI/HONOR、Nokia 和 Fairphone 等品牌；选择窗口支持按品牌、型号、设备代号和 Android 版本搜索。
- 25 套 Android 10–15 的成套配置可参与随机；旧系统机型仍可手动选择。可靠来源没有给出主板时保持未勾选，不用猜测值补齐。
- Aurora 的 48 份公开配置经过正式签名、非模拟器、字段完整性，以及指纹品牌、产品和设备代号一致性校验，最终只收录 34 份；混用其他设备指纹、测试签名或产品代号不一致的条目全部排除。
- 修正 Galaxy S25 Ultra 的型号为 `SM-S938B`，并使用字段相互一致的公开设备配置替换原待核验项。完整规则和来源见 [机型模板库说明](docs/DEVICE-TEMPLATES.md)。

## v1.8.2：可核验的成套机型模板

- 设备信息编辑页新增“选择成套机型”“随机完整机型”和“使用本机真实信息”，选择后整组填入并自动勾选，保存前仍可人工修改。
- APK 内置 6 套带来源和核验状态的离线模板：1 套 Google 官方 Pixel 6 构建指纹示例、4 套通过格式与字段一致性检查的 SpoofMyDevice 社区完整模板，以及 1 套不参加随机的待核验模板。
- 随机池只接受 8 个机型字段齐全的模板；部分官方模板和存在疑点的模板不会被伪装成“完整且已验证”。序列号不会从公共机型模板复制。完整规则与逐项来源见 [机型模板库说明](docs/DEVICE-TEMPLATES.md)。

## v1.8.1：设备信息页面易用性改版

- “同步配置”改为“立即应用到本空间”，明确表示同时保存设备信息、部署到工作空间并自动准备适用应用；LSPosed 不保存设备参数。
- 首页直接显示模拟服务、设备信息、开关和适用应用数量；“生效检查”改为普通用户可读的应用检查结果，作用域、Hook 与进程回报收进“高级诊断”。
- 随机生成、编辑、适用应用和冻结空间提示统一改为操作结果导向的说明，不改变原有生成、自动继承、作用域同步及冻结保护逻辑。

## v1.8.0：工作空间随机设备标识

空间控制继续使用 UsersHub APK + Magisk Core；**应用内设备信息模拟还需要已有的 LSPosed**。不打包 SpoofMyDevice 或 LSPosed。首次迁移须先停用 SpoofMyDevice 并取消目标应用勾选。

- “随机设备指纹”为每个工作空间独立生成 10 项高级标识，同空间第三方应用共用，新装应用自动继承；重新生成覆盖旧配置，不保留旧配置备份。
- 仅处理 Managed Profile；主空间、系统应用、空间/Root/Hook 管理工具不参与。内部仍校验用户序列号，界面不再显示实例编号。
- 自动同步 UsersHub 的 LSPosed 作用域，当前适配 1.9.2 (7024)；运行时只读其数据库，通过框架自身的批准回调提交新增作用域并刷新缓存。不支持的版本明确报错。
- 冻结空间不启动，仅保存配置和作用域，正常启动后部署；不自动打开或停止应用，只提示手动重启。生效检查区分配置、作用域和接口回报，不保证完整设备身份已改变。

v1.9.0 归档安装包：`dist/UsersHub-v1.9.0.apk`、`dist/UsersHub-Core-Magisk-v1.9.0.zip`。最新候选版见上方 v1.9.1；安装或升级 Core 后需要重启。详细使用、边界和验证见 [设备配置说明](docs/DEVICE-PROFILES.md)。

## 原有空间功能（v1.6.0 起）

Android 12 / MIUI 多工作资料上限修复：模块在 system_server 启动前设置 `ro.debuggable=1` 和 `persist.sys.max_profiles=99`，沿用 UsersManager 已在目标设备验证的初始化方式。安装或更新模块后必须重启手机才会生效。

新建空间现在采用最简 Managed Profile：不再从主用户或 10 号等模板空间同步第三方应用；如果系统创建阶段自动继承了第三方安装关系，UsersHub 只在这个刚创建的新 User 中移除它们。Android/MIUI 自己为 Managed Profile 配置的必需系统组件会保留。已有空间及其中的应用不会因升级、刷新或修复流程被清理。

创建时备注名称可以留空。UsersHub 会先让 Android 分配真实 User ID，再自动把显示名称设为同一个数字；例如实际分配 ID 21，就显示为 21。删除空间后，Android 通常要到重启后才会重新使用刚释放的 ID；如果重启后实际分配的是较小空缺 ID 15，默认名称也会自动设为 15。UsersHub 不直接修改系统用户数据库来强行指定 ID。

空间卡片可直接修改备注名称。备注保存在 UsersHub 自己的数据目录中，不调用各 ROM 事务编号可能不同的隐藏 Binder 接口；不会删除或重建空间，真实 User ID、应用和数据均保持不变。卡片与全局操作统一使用矢量图标、等宽按钮和一致的状态布局。

仅创建但尚未运行的空间会显示“启动”按钮。启动只进入 Android 用户运行状态并自动挂载共享文件，不执行冻结扫描；已冻结的空间仍显示“解冻”，用于恢复暂停的应用后再启动。

空间卡片新增“应用”入口：带图标的自适应多选列表可批量选择设备上已有的第三方应用安装到指定空间，也可只从指定空间移除应用。列表显示应用名与包名，并提供选择计数、全选/清空和危险操作二次确认；移除只清除目标 User 内的应用数据，不影响主用户和其他空间。UsersHub、UsersManager、Magisk 与 Termux 默认受保护，不会出现在可操作列表中。

需要安装两个文件：

1. `UsersHub-v1.9.1.apk`：原生 Android Activity，不使用 WebView，不跳网页，直接请求 Root 授权。
2. `UsersHub-Core-Magisk-v1.9.1.zip`：同一个模块支持 Magisk / ReSukiSU，提供空间创建、按空间管理应用、冻结/解冻、开机策略、共享文件生命周期、紧急救援。

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

## 共享文件（Download/Shared）

共享文件目录继续遵循 v1.2.2 的真实用户生命周期：

- 只有真实运行中的空间才 bind mount `Download/Shared`。
- 不会为了共享文件临时启动冻结/停止的空间。
- 不会给从未初始化的用户预先制造假的共享目录。
- 空间停止/冻结后自动卸载共享文件目录。
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
usershubapps list 20
usershubapps install 20 com.example.app
usershubapps remove 20 com.example.app
usershubctl rescue-all
usershubctl boot-freeze on
usershubctl boot-freeze off
```
