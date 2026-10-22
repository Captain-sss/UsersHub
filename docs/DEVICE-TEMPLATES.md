# UsersHub 机型模板库

机型模板随 APK 离线打包在 `app/src/main/assets/device_templates.json`，不依赖在线接口。每一项都必须包含来源链接、核验状态和 UsersHub 实际支持的 Build 字段；模板不会修改 SDK、CPU、屏幕、基带、硬件认证或服务器记录。

## 核验状态

- `official`：构建指纹可从设备厂商或 Android 官方页面直接核对。字段缺失时保留为部分模板，不推测补齐，也不参加“随机完整机型”。
- `community`：来自公开社区设备数据。UsersHub 校验必需字段、HTTPS 来源、正式 `user/release-keys` 指纹格式，以及指纹中的品牌、产品代号和设备代号是否与模板一致；这不等于本项目用对应真机证明过。
- `review`：发现可能不一致或不能充分确认的字段。模板仍可供人工检查，但不参加随机。

“随机同版本机型”要求 Android 10 或更高版本、与当前手机 Android 主版本一致，并同时具有品牌、厂商、型号、设备代号、产品代号、硬件平台和构建指纹。`Build.BOARD` 并不是每个可靠来源都会提供；缺失时保持未填写、未勾选，不会猜测补齐。序列号属于每台设备的独立标识，不从机型模板复制。

UsersHub 会按 Android 标准构建指纹的 `brand/product/device:release/id/incremental:type/tags` 结构拆出 Build ID、增量版本、构建类型和签名标签，确保这些伴随字段与指纹一致。不会从模板推测 SDK、安全补丁、CPU、基带或硬件认证信息。

## 当前来源

- Google Pixel 6：Google 官方 [Pixel Binary Transparency 构建指纹示例](https://developers.google.com/android/binary_transparency/pixel_verification)。官方页未同时给出硬件平台，因此只允许手动选择。
- 34 套历史与现代机型：来自 Aurora OSS 的 [DeviceConfigs 公开目录](https://auroraoss.com/files/DeviceConfigs)。原目录 48 份配置；UsersHub 排除了模拟器、测试签名、字段缺失、混用其他设备指纹，以及品牌、产品或设备代号不一致的条目。Android 10 以下的合格历史机型只允许手动选择。
- Pixel 7 Pro、Pixel 8 Pro、Samsung SM-X900、Xiaomi 15 Ultra：从 [SpoofMyDevice Devices](https://github.com/BuSung-dev/SpoofMyDevice_Devices) 的公开设备属性规范化为 UsersHub 自己的最小字段格式，标为社区来源。
- Samsung Galaxy S25 Ultra：采用 [EFF rs-google-play 的公开设备配置](https://github.com/EFForg/rs-google-play/blob/master/gpapi/device.properties)，型号为 `SM-S938B`，其产品、设备代号、硬件平台与构建指纹相互一致。
- Meizu 18、Meizu 18 Pro、OPPO Find X8 Ultra、Xiaomi 8 Explorer Edition：来自 MIT 许可的 [device_faker_config](https://github.com/Seyud/device_faker_config)。这 4 套字段和构建指纹一致，但来源未提供硬件平台，因此只允许手动选择。

模板库共 44 套，其中 25 套可随机。项目只记录本功能需要的公开设备属性事实，并在模板中保留来源名称和链接；不复制上游代码或说明文档。新增模板必须先通过 `DeviceTemplateCatalogTest`，不能只因为“网上找得到”就标成官方或已验证。
