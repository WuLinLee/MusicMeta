# 音笺 MusicMeta 系统歌词附属栏预览版

这是一个可与正式版并存安装的实验 APK，不是正式发布。

- **唯一功能实验：** 系统媒体卡片继续显示真实歌曲标题和艺人；把当前歌词写入 `METADATA_KEY_DISPLAY_DESCRIPTION`，观察 Android 下拉栏与锁屏媒体卡片是否显示该附属描述。
- **悬浮窗不变：** 沿用 v1.31 的悬浮窗和歌词面板实现，不修改悬浮窗布局、歌词同步或交互。
- **独立安装：** 包名为 `com.wulinlee.musicmeta.preview`，应用名称为“音笺歌词预览版”，可与正式版共存。
- **实验限制：** Android / OEM 可以忽略 `DISPLAY_DESCRIPTION` 或不展示第三行。CI 只能验证构建和元数据字段，最终显示效果需要在真实手机的下拉栏与锁屏上分别验证。
- **版本：** 1.31-preview，versionCode 36。

本预览版不创建正式 Release，不修改 v1.31 tag 或 main 分支。

GD Studio API 使用须注明来源“GD音乐台 (music.gdstudio.xyz)”并遵守 CC BY-NC 4.0 非商业使用限制。
