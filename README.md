# 音笺 MusicMeta

精简的中文 Android 工具：免登录同步网易云公开歌单、打开用户指定的下载站点、保存站点正常提供的音频，并尽可能嵌入专辑封面、原文与中文翻译歌词。

## 核心功能

- 输入网易云歌单 ID 或完整链接；仅读取公开歌单，不需要账号。
- 使用歌单完整 trackIds 批量获取歌曲信息，避免只显示接口返回的前几首。
- 搜索筛选歌曲；点“去下载”自动复制歌曲名与歌手，并打开下载站。
- 默认打开 https://flac.music.hi.cn/ ，也可输入其他网站或标准音频直链。
- 内置 flac.music.hi.cn AJAX 适配器：通过站点的 `search` 与 `getUrl` 接口搜索歌曲并解析其正常提供的下载链接；网页验证 Cookie 从内嵌 WebView 读取，不需要音乐账号登录。
- 同时保留网站 WebView 与标准 HTTP/HTTPS 下载回调，站点 API 不可用时仍可尝试网页操作。
- 查询封面、原文歌词与中文翻译；按时间戳合并双语 LRC 并写入可用的音频标签。
- 保存到手机 Music/MusicMeta 目录；可选择保留未修改的原文件。

## 使用方法

1. 从 GitHub Releases 下载 APK 并安装。
2. 在“网易云歌单”页面输入公开歌单 ID（纯数字）或网易云歌单链接，点“同步”。
3. 找到歌曲点“去下载”；应用会预填歌曲名和歌手，并打开下载站。
4. 在“下载网站”页顶部使用“API 搜索”，可选“酷我”或“网易云”音源；选中结果后点“下载并转 MP3”，应用会调用站点正常提供的下载地址。
5. 如果站点返回访问验证页，请先在内嵌网页中完成人工验证，再重新搜索。这里不绕过验证码或站点访问控制。
6. 下载完成后检查歌曲版本与歌词状态，点“嵌入封面 / 双语歌词并保存”。
7. 音频保存在手机的“音乐/MusicMeta”目录。

## 参考与组件

- Hachimi（MIT）：参考只读歌单/歌曲信息与原文/翻译歌词字段，以及写入标签的功能设计。没有搬入登录、云盘、发现页等不需要的模块。
- net.jthink:jaudiotagger 3.0.1（LGPL-2.1-or-later）：处理 MP3/FLAC 等音频标签。
- FFmpegKit maintained audio 8.1.9（LGPL-3.0）：真正的音频转码与 MP3 编码。
- LRCLIB：仅作为歌词补充来源，使用其公开检索 API；服务可用性及歌词覆盖率可能变化。
- Android Jetpack Compose、OkHttp、Coil：复用成熟 UI、网络与封面显示组件。
- Gradle Wrapper 脚本/JAR：Apache License 2.0。

## 限制与隐私

- 不登录网易云或其他音乐账号，不保存网易云账号 Cookie；网易云只读取公开歌单元数据。站点验证 Cookie 仅从内嵌网页的 CookieManager 临时读取，用于向该站发送 API 请求。
- 本应用不是音源服务。下载内容由用户指定的网站提供，只下载你有权保存的内容，并遵守网站条款和当地法律。
- 仅接管标准 HTTP/HTTPS 音频下载；不绕过 DRM、付费墙、验证码或网站限制。blob/data 下载链接无法由当前版本接管。
- 没有公开中文翻译的歌曲只会嵌入能找到的歌词，不会生成虚假的翻译。
- 网易云歌单优先使用 GD Studio 的公开 `types=playlist` 接口，失败或曲目不完整时回退网易云只读接口；公开 API 可能限流或变更。GD Studio API 标注 CC BY-NC 4.0（仅限非商业用途），并要求注明来源；请遵守其条款与访问频率限制。
- flac.music.hi.cn 的 API 可能要求 SafeLine 网页访问验证；若遇到验证页或 HTTP 468，需要在内嵌网页中人工完成验证。应用不绕过挑战，也不自动伪造验证 Cookie。
- 公开 API 可能只返回部分内容；若完整性校验失败，应用会提示，不会静默声称同步完整。
- 非 MP3 音频会通过 FFmpeg 音频模块真正转码为 MP3（VBR 高质量设置），然后再嵌入封面/歌词；原本是 MP3 的文件不重复转码。

## 本地构建

需要 JDK 17、Android SDK 35 与 Gradle 8.9：

    gradle :app:testDebugUnitTest :app:assembleDebug

APK 输出：app/build/outputs/apk/debug/app-debug.apk

## 许可

项目源码按根目录 MIT License 发布。jaudiotagger 使用 LGPL 许可；第三方许可说明见 THIRD_PARTY_NOTICES.md。该应用与网易云音乐、下载站点及音源提供方没有官方关联。
