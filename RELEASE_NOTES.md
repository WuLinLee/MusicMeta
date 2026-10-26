# 音笺 MusicMeta v1.24 正式版

- **修复 GD Studio 搜索错误诊断：** HTTP 400 等非成功响应会显示服务端返回的简短原因（自动隐藏网址），不再只有一个 HTTP 状态码。当前设置的平台列表与 GD Studio 文档动态标注的稳定来源对齐：网易云、JOOX、Bilibili；来源可用情况仍由服务端决定。
- **修复下载站无效候选提示：** 对 flac.music.hi.cn 返回的 Song not found or invalid response 提供明确提示，引导重新搜索并选择其他候选，或切换 API 服务 / 平台。
- **调整悬浮歌词栏展开方向：** 封面靠屏幕左侧时，歌词栏优先展开到右边；封面靠右侧时，歌词栏展开到左边。窗口位置会按完整展开宽度限制在屏幕范围内。
- **音源偏好迁移：** 按所选 API 服务校验已保存的平台，避免把一个服务不支持的平台带到另一个服务中。
- **版本号：** 1.24，versionCode 28。

GD Studio API 文档要求注明出处“GD音乐台 (music.gdstudio.xyz)”，并声明 CC BY-NC 4.0 / 非商业使用限制。请遵守服务条款与适用版权规则。

自动化验收包括 \`:app:testDebugUnitTest\`、\`:app:assembleRelease\`、APK 对齐、签名与包名检查。CI 成功不代表所有服务端音源均可用，也不代表已经完成真实设备的完整端到端测试。

安装提醒：若本次 APK 的签名与已安装版本不同，Android 会拒绝覆盖安装；以该次 Actions 日志中的证书指纹为准。
