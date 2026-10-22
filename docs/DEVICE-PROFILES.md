# 设备配置与验证（1.9.0）

## 使用随机设备配置

1. 在 LSPosed 中关闭 SpoofMyDevice，并取消其目标应用勾选；保留它的应用和配置文件。已运行的目标需重启以退出旧 Hook。启用 UsersHub 模块，不选择系统框架。
2. 进入 UsersHub 的“设备配置”，点击“随机设备指纹”。确认后为所有现有工作空间分别生成高级 10 项标识、启用自动继承并同步作用域；也可以只重新随机当前空间。
3. 每个空间只保留一套当前配置；同空间所有第三方目标共用，跨空间不同。重新生成会覆盖旧配置，不留旧配置备份。内部仍以 User ID + 系统序列号隔离，不显示实例编号。
4. 只处理 Managed Profile，主空间、系统应用、UsersHub、SpoofMyDevice、UsersManager、LSPosed/Magisk 管理工具不参与。新装应用由后台周期扫描加入作用域，通常约一分钟；“同步配置”可以立即处理。不要把自动继承误认为在首次启动之前必定已经加载。
5. 不启动冻结空间、不自动打开或停止目标应用。停止的空间等待正常启动后部署配置服务；只安装 UsersHub 自己的隐藏服务，不复制主空间其他应用。
6. 已运行的目标请手动重启，正常使用后查看“生效检查”。配置保存、作用域同步和接口返回核对是不同状态。

高级标识生成后稳定保存，不随每次启动或新装应用重新随机。尚未执行重新随机的 1.7.0 配置保留原手动绑定行为。

## 作用域自动适配

只针对已核对的 LSPosed 1.9.2 (7024)，服务协议 API 100，数据库版本 3。Root 适配器核验 UsersHub 已启用、SpoofMyDevice 已关闭；从注册接收器信息中唯一识别受 BRICK 权限保护的作用域批准接收器。新增目标由 LSPosed 自身提交并刷新缓存，再回读数据库确认；常驻同步器不直接写 LSPosed 数据库。接口异常、超时或不同版本均显示待处理，不能宣称已生效。

后台同步与冻结保护分离，60 秒节流并用锁防止重复执行。只自动增加当前已安装且符合规则的第三方作用域。LSPosed 1.9.2 可能保留已卸载应用的无效作用域行；不存在目标包时它不能注入，重新安装同包时本来就应继续继承。若希望界面完全无残留，可在 LSPosed 对应空间取消该已卸载应用的勾选。UsersHub 不直接写 LSPosed 数据库冒充清理成功。关闭模拟时停止供给配置，已有作用域条目可以保留，不等于仍在模拟。

首次迁移的 `tools/SpoofScopeMaintenance.java` 是一次性 root 清理工具：仅在 SpoofMyDevice 已禁用且已知数据库结构时，以事务删除它的目标作用域，保留默认模块自身记录和所有配置数据。该工具不打包进发布 APK，也不参与后台同步。

## 覆盖边界

高级字段：Android ID、IMEI、MEID、IMSI、ICCID、模拟号码、Google 广告 ID、GSF ID 部分接口、Media DRM ID、App Set ID。

可选 Build 字段：品牌、厂商、型号、设备/产品代号、主板、硬件平台、构建指纹、Build ID、增量版本、构建类型、签名标签、序列号。后四项从完整构建指纹拆出，不凭空猜测；对应覆盖 `Build.ID`、`Build.VERSION.INCREMENTAL`、`Build.TYPE`、`Build.TAGS` 以及进程内 `ro.*.build.*` 读取。停用配置时恢复进程原值。

“随机同版本机型”只选择与当前 Android 主版本一致的完整模板，因为 UsersHub 不修改 `Build.VERSION.SDK_INT` 或 `Build.VERSION.RELEASE`。系统版本不同的模板只允许人工确认后填入，并会提示存在明显不一致风险。批量生成所有空间仍维持 10 项基础标识的既有契约，不自动附加机型模板。

本版是 Java API / 进程内 Build 字段与属性读取的模拟，不写基带、SIM 或全机系统属性。不包含 OAID、原生层所有读取途径、SDK、屏幕/CPU/内存、WebView、IP/网络指纹、账号/服务器历史、Play Integrity 或硬件认证修改。GSF/Google SDK Hook 不等于覆盖所有 Google 服务实现，混淆、动态加载或应用缓存也可能影响结果。

诊断只记录字段名、版本、时间和 PID，不记录真实或模拟标识明文；每个进程分别回报，历史回报不代表进程仍在运行。接口返回值核对仅反映本模块 Hook 回调阶段，不是服务器认可或完整设备身份已改变的证明。Build 静态字段读取不产生接口调用回报。

## 保存、停用与恢复

- 主配置：主用户 UsersHub 的私有文件 `identity-store.json`。新版界面不再提供旧配置备份入口，重新随机直接替换当前配置。
- Core 暂存目录：`/data/adb/usershub/identity`，权限700；运行副本仅进入本空间 UsersHub 私有目录，继承该用户正确的 SELinux 标签。
- Provider 校验 Binder 调用 UID、包名和当前真实用户序列号。普通应用不能伪造另一个包名或读取其他空间配置。
- 冻结空间只保存待部署配置，不为了部署或读回报自动启动；同 ID 不同序列号不套用。新增应用读取当前空间共享配置时仍校验调用 UID、包名、非系统应用和管理工具排除项。
- “紧急停用全部”保留配置和应用数据。运行中替换会停止，但应用缓存的内容需要重启目标应用才能保证恢复。
- Magisk Action 同时紧急停用模拟并执行原有救援解冻。正式卸载 Core 会停用管理过的次用户配置服务，避免停止用户之后恢复旧运行配置。仅禁用模块或其他工具直接删除文件不等同于执行正式卸载；应先紧急停用，再取消 LSPosed 作用域。

## 已做的真机验证

2026-09-01，1.9.0：

- 工作空间10–21各一套高级10项标识，逐字段跨空间无重复；12个空间的抖音作用域经LSPosed异步缓存刷新后仍存在。SpoofMyDevice已关闭且11条目标作用域已清除，只保留模块自身默认行与原配置数据。
- 为已运行的空间18临时添加主空间已有Termux但未打开：后台同步识别为第二个第三方应用，加入同一空间共享配置和`com.termux/user18`作用域，异步回读保持。随后从空间18移除Termux，主空间Termux不受影响，并通过LSPosed官方界面清除测试残留作用域。
- Core 1.9.0 覆盖安装并重启后，空间10–17、20–21保持FROZEN/STOPPED/NO_SHARED，各261个暂停包；空间18–19恢复原NORMAL/RUNNING/SHARED。与升级前262项备份逐项比较，唯一清理的是已卸载的`com.spoofmydevice`陈旧记录，没有新增或遗漏其他冻结目标。
- 单元测试、Release APK、Probe、lintVital、Shell语法以及配置/作用域/冻结状态验证通过。本轮未打开抖音，因此没有新增运行时接口读取回报；不能把作用域成功误称为服务端认可或完整设备身份已改变。

2026-08-31，Redmi K30 Pro / MIUI14 / Android12 / LSPosed1.9.2(7024)：

- 临时用户22/serial63及23/serial64，自编写 probe，无 QUERY_ALL_PACKAGES / queries：各19字段匹配配置；高级10字段跨用户不同；重启19字段稳定。
- 关闭、解除绑定、紧急停用恢复19字段基线；错误用户序列号拒绝；冻结时部署不启动用户，解冻后正常部署；伪造包名拒绝。
- GAID/App Set 测试使用同 API 形状的测试类，**未验证真实 Google 服务**。
- 用户指定的u20/serial60抖音40.0.0：加载 UsersHub 配置1；启动期间 Android ID、Media DRM ID 被调用并通过 Hook 返回核对，未记录冲突。其余8项未在本次启动观测到。应用停在个人信息保护指引，未代用户同意，也未操作账号。

原始脱敏结果和升级前恢复备份在本机 `build/device/identity-170-validation`，不加入发布包。

## 开发与来源

Java17、Gradle8.9、Android SDK35：

```powershell
gradle --no-daemon :app:assembleRelease :app:testDebugUnitTest :probe:assembleDebug
./tools/package-release.ps1
```

`tools/validate-identity-device.ps1` 仅用于本次明确核对的临时用户，内有用户名称和序列号保护，不可直接用于日常空间。probe 不随正式 APK 安装。

实现按 Android/Xposed API 独立编写，参考了 [SpoofMyDevice v2.0.1](https://github.com/BuSung-dev/SpoofMyDevice/tree/ca78ffa6f18d44dc3d1e0fff2a48d74ad4884b19) 的功能与接口行为，没有复制其 APK 或反编译类到本项目。该项目许可证还包含用途限制，不能简单视作无限制 MIT 授权。

接口资料：[Xposed API](https://api.xposed.info/reference/de/robv/android/xposed/XposedHelpers.html)、[Android 用户序列号](https://developer.android.com/reference/android/os/UserManager#getSerialNumberForUser(android.os.UserHandle))、[Magisk 开发说明](https://topjohnwu.github.io/Magisk/guides.html)。

作用域适配依据：[LSPosed 1.9.2 服务接收器](https://github.com/LSPosed/LSPosed/blob/v1.9.2/daemon/src/main/java/org/lsposed/lspd/service/LSPosedService.java)、[框架配置与缓存](https://github.com/LSPosed/LSPosed/blob/v1.9.2/daemon/src/main/java/org/lsposed/lspd/service/ConfigManager.java)、[API100 回调](https://github.com/libxposed/service/blob/100/interface/src/main/aidl/io/github/libxposed/service/IXposedScopeCallback.aidl)。它是固定版本兼容适配，不是 LSPosed 承诺稳定的公开管理 API。
