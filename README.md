# 音笺 MusicMeta

一个只围绕“公开歌单 → 选歌 → 下载 → 写入标签 → 保存”的 Android 小工具。

界面参考椒盐音乐 / Salt Player 的视觉方向：封面优先、轻量歌曲列表、清晰层级与统一圆角。这里不打包椒盐音乐的播放器代码、商标或图片素材，只借鉴其简洁的界面组织方式。

## 主要流程

1. 粘贴网易云公开歌单链接或 ID，直接同步，不需要登录网易云账号。
2. 在同一页面筛选歌曲并点“找音源”，选择酷我或网易云候选版本。
3. 通过下载站提供的正常地址下载音频；非 MP3 音频会实际转码为 MP3。
4. 尽可能获取封面、原文歌词和现成的中文翻译歌词，嵌入音频标签。
5. 保存到默认的“音乐/MusicMeta”目录，或在主页面“保存位置”中选择任意系统允许访问的文件夹；选择结果会记住，下次继续使用。

## 下载站验证

默认音源适配器使用 https://flac.music.hi.cn/ 的公开 AJAX 接口。如果网站触发访问验证，可以点击页面右上角“下载站验证”，在弹出的网页中手动完成，然后重新搜索。应用不会绕过验证码或站点访问控制。如果 API 不可用，也可以在该网页中使用网站本身提供的正常下载入口。

## 已知限制

- 只读取公开歌单；私人歌单需要账号的场景不支持。
- 保存目录可以通过 Android 系统文件夹选择器指定；若未指定则使用“音乐/MusicMeta”。系统不允许写入的受保护目录无法选择。
- 歌曲版本仍需在下载前核对；同名歌曲可能存在现场版、翻唱版或不同母带。
- 只有找到原文/翻译时才会写入相应歌词，不会自动编造翻译。
- 下载站或公开 API 可能限流、验证或变更；遇到错误会显示在页面底部。
- 本应用不是音源服务。请仅下载自己有权保存的内容，并遵守来源站点条款和当地法律。

## 参考与组件

- [Salt Player](https://github.com/Moriafly/SaltPlayerSource) / [Salt UI](https://github.com/Moriafly/SaltUI)：参考其简洁的列表与封面布局风格。
- [Hachimi](https://github.com/qingyueyin/Hachimi)：参考只读歌单、原文/翻译歌词字段与标签处理的功能设计；未移植其登录功能。
- [GD Studio Music API](https://music.gdstudio.xyz/)：公开歌单元数据接口，需遵守其服务条款和频率限制。
- [jaudiotagger](https://www.jthink.net/jaudiotagger/index.jsp) 3.0.1（LGPL-2.1-or-later）：音频标签写入。
- [FFmpegKit maintained audio](https://github.com/ffmpegkit-maintained/ffmpeg) 8.1.9（LGPL-3.0）：MP3 转码。
- [LRCLIB](https://lrclib.net/)：歌词补充查询。
- Android Jetpack Compose、OkHttp、Coil。

## 本地构建

需要 JDK 17、Android SDK 35 与 Gradle 8.9：

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 许可与声明

项目源码按根目录 MIT License 发布。第三方依赖许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。本项目与网易云音乐、椒盐音乐或下载站点没有官方关联，相关商标和音频版权归各自所有者。
