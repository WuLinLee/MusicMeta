# 音笺 MusicMeta

**MusicMeta（音笺）** 是一个 Android 音乐整理工具：读取网易云公开歌单或可匿名访问的红心歌单，搜索并核对候选音源，下载音频，并尽可能补齐封面、歌词和音频标签。

> **当前版本：1.18**（`versionCode = 22`；正式 APK 请以本次 GitHub Actions 成功结果和 Release 附件为准）  
> 应用 ID：`com.wulinlee.musicmeta` · 最低 Android：10 / API 29 · 目标 SDK：35  
> 技术栈：Kotlin、Jetpack Compose、Material 3、Coroutines、OkHttp、Coil、jaudiotagger。

**交接时先看这里：** 仓库当前版本号、Git tag、Release 页面和 APK 必须视为四件不同的东西。发布前请确认 main 的最新提交、Actions 构建结果、`v1.18` tag 指向和 Release 附件都一致。不要只根据 README 上的版本号就声称 APK 已验证。

## 目录

- [用户功能与边界](#用户功能与边界)
- [源码地图](#源码地图)
- [架构与关键数据流](#架构与关键数据流)
- [播放器现状和改造方向](#播放器现状和改造方向)
- [数据源与接口回退](#数据源与接口回退)
- [开发环境与本地构建](#开发环境与本地构建)
- [测试与验收](#测试与验收)
- [版本、签名与发布](#版本签名与发布)
- [已知限制和待办清单](#已知限制和待办清单)
- [交给下一位 AI 的工作约定](#交给下一位-ai-的工作约定)
- [第三方项目和许可](#第三方项目和许可)

---

## 用户功能与边界

### 当前功能

- **歌单入口**：同步网易云公开歌单；输入用户 ID 尝试读取“我喜欢的音乐”；独立“直接搜歌”入口。
- **分页和筛选**：每页 25 / 50 / 100 首；按需加载歌曲详情；筛选已加载曲目；不同歌单页面保留各自状态。
- **本地**：新增独立分页，默认扫描 `Download/-07-MusicMeta` 与 `Music`；可在设置中改选本地音乐文件夹，点击曲目后继续使用悬浮播放器，不打开全屏播放器。
- **音源候选**：支持网易云、酷我候选搜索；下载前核对歌名、歌手、专辑和版本。
- **下载格式**：MP3 128 kbps、MP3 320 kbps、FLAC。应用不进行格式转码，不应把低规格音频伪装为 FLAC 无损。
- **歌曲整理**：补充可获得的封面、原文 / 翻译 / 罗马音 / 逐字歌词；将标签与封面写入音频；按设置另存 LRC 文件。
- **文件保存**：支持默认 Download 子目录或用户通过 Android 文件夹选择器授权的目录，并按命名规则整理文件。
- **迷你播放器（1.18）**：本地和在线曲目共用悬浮封面、歌词、进度、播放/暂停及顺序 / 乱序 / 单曲循环控制；模式按钮改用标准 Material 播放图标。进度通过轻量更新同步，歌词窗口保持附着并由 Activity 统一控制原文 / 翻译和展开状态，以避免频繁更新导致闪烁。拖动悬浮封面或歌词栏画圈可打开应用，并尝试定位回当前歌曲；封面和歌词栏均可拖动整组窗口。单击播放 / 暂停，双击关闭，长按展开 / 收起歌词；歌词语言切换会复用现有窗口，避免不必要的窗口重排。歌词优先匹配网易云曲目和译文，本地文件按曲名 / 歌手匹配，缺少译文时显示可用原文。开启系统悬浮窗后，可在其他 App 上方使用同一套控件（需授予“显示在其他应用上层”权限）。
- **错误报告**：左上角复制图标复制最近一次捕获的错误报告；没有错误报告时按钮禁用。分页区 Home 图标仍是“回到歌曲列表顶部”，不能改成错误报告或播放器操作。
- **站点验证**：嵌入式 WebView 供用户手动完成来源站要求的验证。不绕过验证码、登录限制或其他访问控制。

### 明确不承诺

- 私密歌单、受限内容或需要账号授权的资源不保证能读取。
- 外部 API 会限流、改版、返回验证页或失效；不能保证某个音源永久可用。
- 不凭空生成缺失的封面、歌词和翻译。候选匹配可能遇到同名翻唱、现场版或不同母带，最终应让用户确认。
- **跨应用悬浮窗已实现**：顶部窗口图标可开启 / 关闭；首次开启会跳转到 Android 的悬浮窗权限设置。启用后使用独立 `WindowManager` 窗口和前台服务在其他 App 上方显示，通知栏会保留悬浮播放器运行通知。
- 当前音频仍由 Activity 内的 `MediaPlayer` 负责，悬浮服务主要负责跨应用窗口与控制转发；尚未提供完整 MediaSession、标准系统媒体会话、锁屏 / 蓝牙控制或可靠的独立播放队列。Android 若彻底回收应用进程，悬浮窗与试听不会作为成熟音乐服务保证持续运行。见[播放器现状和改造方向](#播放器现状和改造方向)。

## 源码地图

这是一个单模块应用，主要业务状态仍集中在 `MainActivity.kt`。表中是修改前应该先读的入口。

| 文件 | 职责 | 修改注意事项 |
|---|---|---|
| [MainActivity.kt](app/src/main/java/com/wulinlee/musicmeta/MainActivity.kt) | Compose UI、歌单状态与分页、搜索、下载编排、试听播放、歌词面板、WebView 验证和错误报告 | 约 3,000 行，状态耦合较多；按职责渐进拆分，不要一次性大改整页。 |
| [FloatingMiniPlayerService.kt](app/src/main/java/com/wulinlee/musicmeta/FloatingMiniPlayerService.kt) | 跨应用悬浮窗、`WindowManager` 双窗口、拖动位置保存、前台通知与控件命令转发 | 需要用户授权 `SYSTEM_ALERT_WINDOW`；该服务目前并不拥有独立音频播放队列。 |
| [MusicModels.kt](app/src/main/java/com/wulinlee/musicmeta/MusicModels.kt) | `MusicTrack`、`PublicPlaylist`、`TrackAssets` 数据模型 | 尽量保持为轻量数据类型；不要在模型里发请求或持有 UI 状态。 |
| [PublicPlaylistProvider.kt](app/src/main/java/com/wulinlee/musicmeta/PublicPlaylistProvider.kt) | 歌单元信息、曲目列表和分页数据，带备用来源 | 检查 offset / limit、总曲目数、分页缓存和异步返回的竞态。 |
| [FlacMusicProvider.kt](app/src/main/java/com/wulinlee/musicmeta/FlacMusicProvider.kt) | 候选搜索、下载 URL 解析、来源封面 / 歌词及备用音源 | Cookie 来自 WebView；不能把 Cookie 转发到无关域名，也不要尝试绕过验证。 |
| [MetadataProvider.kt](app/src/main/java/com/wulinlee/musicmeta/MetadataProvider.kt) | 歌词 / 封面补全、备用查询、网络响应解析 | 同名不同版本容易造成歌词或封面错配；优先核对歌手和版本。封面响应有大小上限。 |
| [AudioPipeline.kt](app/src/main/java/com/wulinlee/musicmeta/AudioPipeline.kt) | `AudioDownloader`、`AudioFileNamer`、`AudioTagWriter`、`MediaPublisher`：下载、校验、命名、标签和发布文件 | 手动处理重定向、流式大小限制、文件头检查和失败清理都集中于此；改动后务必测试失败路径。 |
| [LrcMerger.kt](app/src/main/java/com/wulinlee/musicmeta/LrcMerger.kt) | YRC 转标准 LRC、原文和翻译按时间戳合并 | 相对独立的纯 Kotlin 逻辑，适合增加 JVM 单元测试。 |
| [LrcMergerTest.kt](app/src/test/java/com/wulinlee/musicmeta/LrcMergerTest.kt) | 歌词转换 / 合并的单元测试 | 当前测试覆盖有限，不能代表网络、下载、标签、播放器全链路已测试。 |
| [AndroidManifest.xml](app/src/main/AndroidManifest.xml) | 应用入口、网络权限和 Android 网络安全策略 | `usesCleartextTraffic=false`；不要为迁就某个 HTTP 源而全局放开明文流量。 |
| [app/build.gradle.kts](app/build.gradle.kts) | SDK、版本号、依赖和 Kotlin 编译配置 | 正式版本改动时同步 `versionCode`、`versionName`、README、Release Notes。 |
| [android.yml](.github/workflows/android.yml) | Actions：测试、打包、APK 签名、artifact 和 Release 上传 | 发布逻辑有保护和显式 refresh 条件；先检查 tag 与 Actions，再操作 Release。 |
| [RELEASE_NOTES.md](RELEASE_NOTES.md) | 预览版 / 正式版变更摘要 | 面向用户，不要写未实现、未验证或容易误解的功能。 |

### 当前技术栈

- Android Gradle Plugin 8.7.3、Kotlin 2.0.21、JDK 17、Gradle 8.9。
- Jetpack Compose / Material 3；AndroidX Activity、Core、DocumentFile、Lifecycle。
- Kotlin Coroutines、OkHttp 4.12.0、Coil 2.7.0。
- jaudiotagger 3.0.1 写音频标签。
- JUnit 4 进行 JVM 单元测试。

当前**没有** Room 数据库、DI 框架、PlaybackService、ExoPlayer 或 MediaSession。不要从其他播放器项目的架构反推这些功能已存在。

## 架构与关键数据流

### 歌单读取

1. 用户输入歌单链接 / ID，或红心歌单用户 ID。
2. `PublicPlaylistProvider` 获取歌单元信息和曲目 ID。
3. 按页批量读取曲目详情，UI 更新当前歌单及缓存。
4. 用户筛选、翻页或跳转时，调用对应的状态更新和按需加载逻辑。

分页改动时至少检查：模式切换、歌单 ID 变化、每页数量变化、快速连续跳页、网络响应返回时用户已切换页面，以及缓存是否被旧请求覆盖。

### 搜索与下载

1. UI 将 `MusicTrack` 或手动关键词交给 `FlacMusicProvider` 搜索候选。
2. 用户确认歌名、歌手、专辑、版本后，解析真实下载地址。
3. `AudioDownloader` 进行 HTTP 状态 / 内容类型 / 文件头校验，并流式写入应用缓存目录。
4. 根据设置执行命名和标签处理（`AudioFileNamer` / `AudioTagWriter`）。
5. `MediaPublisher` 将结果写入 MediaStore 或授权目录，并发布媒体条目。
6. 下载、标签或发布任一步骤失败时，需要清理尚未成功发布的缓存文件，并保留便于诊断的错误信息。

**安全不变量：**

- 仅允许规范的 HTTP / HTTPS 地址；不把 `blob:` / `data:` 当作可直接下载的 URL。
- 显式 WebView Cookie 只能发送给符合来源主机约束的 HTTPS 请求。跨域重定向不能继承 Cookie。
- 音频流读取有累计字节数上限（当前 1.5 GB），不能只依赖可缺失或伪造的 `Content-Length`。
- 封面读取有累计字节数上限（当前 12 MB）；避免用不设上限的 `body.bytes()` 处理网络图像。
- 对未知内容类型、HTML 验证页面、JSON 响应和不符合音频文件头的内容，不要保存为“成功下载”的歌曲。
- 失败 / 取消时清理临时文件。修改代码后检查下载成功、下载途中失败、标签写入失败、保存失败和取消等路径。

### 歌词

`LrcMerger` 负责 YRC 到标准行级 LRC 的规范化与带时间戳的原文 / 翻译合并。迷你播放器从歌词行提取时间戳，并根据当前播放位置高亮；无时间戳歌词只会以文本展示，无法准确同步或点击跳转。

若切换到不同候选版本，必须避免继续复用另一版本的翻译歌词。歌名匹配不足以证明原文、翻译、封面属于同一录音版本。

## 播放器现状和改造方向

### 当前迷你播放器交互

音频试听仍由 `MainActivity.kt` 中的 `MediaPlayer` 驱动。顶部的悬浮窗按钮会请求 `SYSTEM_ALERT_WINDOW` 权限；授予后 `FloatingMiniPlayerService` 用两个独立的 `WindowManager` 窗口显示封面和歌词栏，并通过前台服务保持窗口可见。封面或歌词栏均可拖动整组窗口；单击播放 / 暂停、双击关闭播放器、长按展开 / 收起歌词；拖动时画圈可打开应用并尝试定位当前歌曲。整组约 356 dp 宽；封面下方是迷你时间和播放 / 暂停控件；歌词栏约 278 dp 长、64 dp 高，左右按钮在当前歌单中切换上一 / 下一首，每首歌自动搜索并播放首个匹配音源，当前曲目播完后自动进入下一首；中间区域显示歌词与进度条，点击歌词切换原文 / 译文。

它的队列来源只是启动播放时可用的候选音源列表，并非一个独立且可持久化的播放队列。离开页面、Activity 生命周期变化、网络地址变化和播放器释放等情况需要格外小心。

### 还缺少的播放器能力

- 播放引擎与 UI 解耦；生命周期 / 进程处理可靠。
- 后台持续播放、系统媒体通知、锁屏、蓝牙耳机和系统媒体按键。
- 音频焦点、来电 / 通知打断、拔耳机暂停及错误后的恢复策略。
- 正式播放队列、队列增删 / 点击播放、上一首 / 下一首、随机播放、单曲 / 列表循环。
- 稳定的播放状态模型和位置更新机制；防止异步搜索结果覆盖当前曲目状态。
- 跨应用窗口与权限请求已实现，但仍需在不同 Android 厂商、分屏 / 横屏、系统强制停止和权限撤销场景进行实机验收。悬浮窗服务不是 `MediaSessionService`，不要将窗口常驻等同于保证后台音频不被系统终止。

### 推荐参考实现，不要从零硬堆

建议先研究 AndroidX Media3 / ExoPlayer 的播放器与会话模型：

- [AndroidX Media3 官方仓库](https://github.com/androidx/media)
- [Android Media3 文档](https://developer.android.com/media/media3)
- [示例开源播放器：skaftab-in/android-music-player](https://github.com/skaftab-in/android-music-player) —— 仓库里将 `PlaybackService`（ExoPlayer + MediaSession）、`PlayerViewModel`（连接 MediaController 并暴露队列 / 播放状态）和 Compose 播放界面拆开，是可读的架构参考。

当前项目同时支持在线音源整理与本地曲库扫描，但音频播放仍由 Activity 内的 `MediaPlayer` 驱动，尚未拥有完整的独立播放服务 / MediaSession。参考架构时应保留 `FlacMusicProvider` 的地址解析和在线版本核对流程，并让本地 MediaStore 曲目与在线 URL 最终进入统一播放队列；不要把本地文件扫描和网络音源解析混成同一套资源解析逻辑。复制外部项目代码前，先核对其 LICENSE 和依赖版本。

建议的演进顺序：

1. 建立独立播放器层 / 服务，播放 URL 和媒体元数据通过明确接口交接。
2. 用 MediaController 将播放器状态暴露给界面；播放服务拥有队列和实际播放状态。
3. 添加标准媒体通知、音频焦点和耳机拔出处理，验证锁屏与切后台。
4. 将目前迷你封面保留为 UI 组件，让它只消费播放器状态并调用播放控制接口。
5. 当前跨应用悬浮窗已由 `FloatingMiniPlayerService` 负责；后续可把 `MediaPlayer` 迁移到独立播放服务 / Media3，再让悬浮层仅消费播放器状态。

## 数据源与接口回退

这些是源码当前使用的服务，不保证第三方可用性：

| 用途 | 服务 / 接口 | 备注 |
|---|---|---|
| 歌单详情与曲目 ID | 网易云公开 API：`music.163.com/api/v6/playlist/detail`、`music.163.com/api/v3/song/detail` | 首选路径；曲目详情按页批量查询。 |
| 红心歌单定位 | 网易云：`music.163.com/api/user/playlist` | 根据用户 ID 查找“我喜欢的音乐”；私密列表可能无法读取。 |
| 歌单备用读取 | GD Studio：`music-api.gdstudio.xyz/api.php?types=playlist&source=netease&id=…` | 主歌单接口失败时尝试。 |
| 候选搜索 / 地址解析 / 站点歌词 | `flac.music.hi.cn/ajax.php`，包括 `search`、`getUrl`、`getLyric` | 接口可能要求站点验证；不得试图绕过验证。 |
| 备用搜索 / 下载地址 | GD Studio Music API：`music-api.gdstudio.xyz/api.php`，包括 `types=search`、`types=url` | 主地址解析失败后才尝试；应重新核对候选歌名和歌手。 |
| 备用歌词 | LRCLIB：`lrclib.net/api/get`、`lrclib.net/api/search` | 仅在主来源缺失时尝试；返回结果仍需匹配核对。 |
| 备用封面 | GD Studio、Apple iTunes Search API | 仅在现有封面无法取得时尝试。 |

不要将第三方接口写入本地服务器密钥或声称这些服务由 MusicMeta 托管。若要更换接口，应把解析逻辑留在 Provider 中，并考虑超时、状态码、空响应、JSON 格式变化、HTML 验证页和回退终止条件。

## 开发环境与本地构建

### 需要什么

- JDK 17。
- Android SDK Platform 35。
- Android SDK Build Tools 35.0.0。
- Gradle 8.9（CI 使用该版本）。

在仓库根目录执行：

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleRelease
```

Release APK 默认在：

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

本地 release 构建默认未签名。调试版和 release 版本的包名也可能不同（debug 配置带有 `.debug` 后缀）。构建成功不等于安装、音源网络请求、标签写入和播放行为都已经在真实 Android 设备上验收。

## 测试与验收

当前自动化测试重点只有歌词转换 / 合并。计划碰下载器或播放器时，优先新增可脱离网络的单元测试，再补充设备验收清单。

每个改动至少完成：

1. `gradle --no-daemon :app:testDebugUnitTest :app:assembleRelease`。
2. 检查 GitHub Actions 最新提交的 test/build、APK 签名和 package metadata 步骤；不要把排队中或已取消 run 说成成功。
3. 涉及下载时，检查有效音频、HTML 伪装、错误内容类型、无 `Content-Length` 的超大响应、重定向、跨域 Cookie、校验失败清理。
4. 涉及播放器时，检查快速连续选歌、解析失败、暂停后继续、播放结束、拖动进度、歌词时间戳、前后台切换和 Activity 生命周期。

如果 API 需要真实外网或站点 Cookie，说明集成测试条件，不要把单元测试通过描述为完整的端到端验证。

## 版本、签名与发布

### 版本号

版本定义在 [app/build.gradle.kts](app/build.gradle.kts)：

- 当前 `versionName = "1.18"`
- 当前 `versionCode = 22`

下一版应提高 `versionCode`，同时更新 `versionName`、`RELEASE_NOTES.md` 和必要的 README 信息。

### GitHub Actions 工作流

[.github/workflows/android.yml](.github/workflows/android.yml) 的主要步骤：

1. 设置 JDK 17 / Android SDK / Gradle 8.9。
2. 检查封面资源、读取版本号。
3. 运行 `testDebugUnitTest` 并构建 release APK。
4. APK 对齐、签名、校验包名和签名。
5. 上传 Actions artifact。
6. 根据提交信息或手动输入发布预览版 / 正式版。

常规 main push 会创建预览版；提交信息以 `release:` 开头时触发正式版逻辑。已有同名正式版默认不会被覆盖；只有明确写成 `release: refresh v...` 才会刷新已有版本。这会更新现有 Release 的 APK，请确保是有意操作，并核查成功构建日志。

### 签名是一个重要限制

当前工作流支持以下 GitHub Actions Secrets：

- `MUSICMETA_KEYSTORE_BASE64`：长期 keystore 文件的 Base64 内容。
- `MUSICMETA_KEYSTORE_PASSWORD`
- `MUSICMETA_KEY_ALIAS`
- `MUSICMETA_KEY_PASSWORD`

**v1.08 的已发布 APK 使用了本次运行生成的临时签名密钥，密钥没有被保存。** 因此后续版本如果使用新密钥，Android 将不允许覆盖安装在这个 APK 之上。要从 v1.08 迁移到长期签名渠道，用户需要先卸载 v1.08 再安装长期签名版本（卸载可能清除本地设置）。管理员配置一把长期密钥后，未来的版本必须持续使用同一密钥，不能每次构建重新生成。不要把长期私钥提交到公开仓库。

正式发布前还要核对 [Releases](https://github.com/WuLinLee/MusicMeta/releases) 中 tag 指向的 commit、APK 名称 / SHA-256 和最新 Actions 成功状态。不要删除历史版本来掩盖发布失败。

## 已知限制和待办清单

按优先级建议下一位维护者处理：

### P1 — 播放器架构与安全

- [ ] 将 `MediaPlayer` 从巨型 Compose 页面移出；评估 Media3 / ExoPlayer + `MediaSessionService`。
- [ ] 加入真正的播放队列、音频焦点、后台播放、系统媒体通知、锁屏和蓝牙控制。
- [ ] 为 Cookie 约束、重定向和流式上限补充自动化回归测试。
- [ ] 复核所有下载和保存失败路径都能清理临时文件，不留下部分标签文件或媒体条目。
- [ ] 检查不同候选版本切换后歌词 / 翻译 / 封面是否仍来自同一录音版本。

### P2 — 可维护性

- [ ] 将 `MainActivity.kt` 按播放器、下载、歌单、搜索和界面拆分；优先通过小步抽取保持行为不变。
- [ ] 将网络请求状态、错误类型和下载状态定义为明确的数据类型，减少互相覆盖的字符串状态。
- [ ] 为 `PublicPlaylistProvider`、`FlacMusicProvider`、文件命名与下载校验添加可控响应的测试。
- [ ] 为分页请求添加竞态测试（快切页面、快速切换模式、重复请求）。

### P3 — 产品体验

- [ ] 实机测试跨应用悬浮窗的权限撤销、屏幕旋转 / 横屏、厂商后台限制和系统回收；确保权限关闭后能平稳回退到应用内播放器。
- [ ] 为元数据匹配增加候选来源与置信度提示，避免无提示地混合不同版本歌词。
- [ ] 统一 README、Release Notes、版本号和用户可见的版本信息。

## 交给下一位 AI 的工作约定

接手时请遵循以下顺序：

1. **先检查现状**：读取 main 的最新 SHA、对应源码、最新 Actions 和 Release/tag，再动文件。聊天中的旧信息可能已过期。
2. **先研究现成实现**：播放器可从 [AndroidX Media3](https://github.com/androidx/media) 和上面的参考播放器入手；对照其 Service、MediaSession、Controller、队列和状态同步设计，而不是继续在 `MainActivity` 里堆按钮。
3. **先给小方案，再做最小改动**：列出要改文件和回归风险，分阶段实现；除非需求明确，不大规模重写 UI 或改变用户已保存的设置。
4. **把事实与计划分开**：未实现的能力写成 TODO；没有真实构建 / 测试就不要声称已通过；CI 失败应读取日志、修复后重新验证。
5. **保护安全与发布历史**：不提交私钥，不把 Cookie 发给其他域名，不移除文件大小限制，不为了发布成功强删历史 tag / Release。
6. **交付时列清楚**：改了什么、哪些测试通过、哪些设备 / 外部接口还没验证、安装或升级有什么签名限制，以及最终 commit / Release 链接。

## 第三方项目和许可

- [GD Studio Music API](https://music.gdstudio.xyz/)：歌单、歌词、封面和备用音源来源之一；遵守其服务条款。
- [LRCLIB](https://lrclib.net/)：备用歌词查询。
- [jaudiotagger](https://www.jthink.net/jaudiotagger/index.jsp)：音频标签。
- [AndroidX Media3](https://github.com/androidx/media)：后续播放器架构建议参考的官方项目，当前尚未集成。
- [skaftab-in/android-music-player](https://github.com/skaftab-in/android-music-player)：参考 ExoPlayer、MediaSessionService、MediaController 与 Compose 分层的开源示例；不直接移植其本地曲库实现，复制代码前先核对许可。
- Salt Player / Salt UI 与 Hachimi 仅作为此前参考过的 UI / 字段设计资料；本项目没有打包其播放器实现。

本项目根目录采用 MIT License；第三方依赖与许可详情见 [LICENSE](LICENSE) 和 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。本项目与网易云音乐、GD Studio、LRCLIB、Apple、Salt Player、Hachimi 等均无官方关联。
