# 音笺 MusicMeta

MusicMeta（音笺）是一款以歌单整理和本地音乐管理为核心的 Android 应用：读取可公开访问的网易云歌单，搜索并核对音源，下载音频，并尽可能补齐封面、歌词和音频标签。

> **当前开发版本：1.24** · `versionCode = 28`  
> 应用 ID：`com.wulinlee.musicmeta` · 最低 Android：10（API 29）· 目标 SDK：35  
> 技术栈：Kotlin、Jetpack Compose、Material 3、Coroutines、OkHttp、Coil、jaudiotagger。

- **正式版发布页：** [GitHub Releases](https://github.com/WuLinLee/MusicMeta/releases)
- **v1.24 APK 直链：** [MusicMeta-v1.24.apk](https://github.com/WuLinLee/MusicMeta/releases/download/v1.24/MusicMeta-v1.24.apk)
- **构建记录：** [GitHub Actions](https://github.com/WuLinLee/MusicMeta/actions)

下载链接只有在对应 Release 资产创建成功后才可用。版本号、Git 标签、Actions 构建结果和 Release 附件是不同的事实；请以对应提交的成功构建结果和实际资产为准，不要仅凭本文件中的版本号判断已发布。

## 目录

- [功能](#功能)
- [使用说明](#使用说明)
- [悬浮迷你播放器](#悬浮迷你播放器)
- [技术架构与源码地图](#技术架构与源码地图)
- [数据源与使用边界](#数据源与使用边界)
- [开发环境与构建](#开发环境与构建)
- [测试与发布验收](#测试与发布验收)
- [版本与签名](#版本与签名)
- [当前限制](#当前限制)
- [许可与第三方服务](#许可与第三方服务)

## 功能

- **歌单导入：** 读取网易云公开歌单；可尝试通过用户 ID 定位“我喜欢的音乐”。私密、受限或要求登录授权的歌单不保证可访问。
- **分页与筛选：** 支持每页 25 / 50 / 100 首、跳页、已加载曲目筛选，以及不同歌单的独立状态。
- **直接搜歌与音源候选：** 可按歌名 / 歌手搜索候选音源，支持网易云和酷我来源。候选可能存在同名翻唱、现场版、重制版或不同母带，下载前应核对曲名、歌手、专辑和版本。
- **快速查找并播放：** 每首歌曲“找音源”按钮旁边的纯图标按钮会搜索并尝试播放第一个匹配结果。
- **本地曲库：** 扫描常见音乐目录（包括 `Download/-07-MusicMeta` 和 `Music`），也可以选择有权访问的本地音乐文件夹；点击歌曲可开始试听。
- **歌曲信息与文件整理：** 在来源可提供的范围内补充封面、原文歌词、翻译、罗马音或逐字歌词；可写入音频标签并按设置另存 LRC 文件。
- **音频格式：** 支持选择 MP3 128 kbps、MP3 320 kbps 或 FLAC 来源。应用不把低规格音频转码或伪装成无损；实际质量取决于音源本身。
- **本地信息复制：** 长按本地歌曲可复制歌曲名和艺人名。
- **错误报告：** 页面上的错误报告入口可复制最近捕获的错误信息；生成的报告会隐藏 URL 及可能的敏感字段。
- **站点验证：** 需要时可在应用内 WebView 手动完成来源站要求的验证；应用不绕过验证码、登录限制或其他访问控制。

## 使用说明

1. 从 [GitHub Releases](https://github.com/WuLinLee/MusicMeta/releases) 下载正式 APK。安装更新时如果 Android 提示签名不一致，请先核对本次 Release 的构建日志和签名证书，不要直接卸载以外的方式尝试绕过系统校验。
2. 进入相应分页，输入可访问的歌单链接 / ID；使用红心歌单功能时输入网易云用户 ID。需要直接搜歌时切换到“直接搜歌”入口。
3. 从候选结果中检查歌名、歌手、专辑与版本。确认候选后再下载，避免把同名曲目误认为同一录音。
4. 在本地分页点击“扫描”读取设备允许访问的音乐文件。需要扫描其他目录时，通过应用提供的文件夹选择器授权。
5. 在设置中选择 API 服务（GD Studio 或 flac.music.hi.cn）、次级音乐源、音质、保存目录及悬浮歌词偏好。

首次使用跨应用悬浮播放器时，需要在 Android 系统设置中授予“显示在其他应用上层”权限。撤销权限后，跨应用浮层不可用。

## v1.24 本次改进

- **GD Studio 错误诊断：** HTTP 400 等响应会显示服务端简短原因并隐藏网址；可选平台暂按官方文档当前标注的稳定源显示为网易云、JOOX 和 Bilibili。第三方可用源会动态变化。
- **下载地址失效提示：** 对 flac.music.hi.cn 的 Song not found or invalid response 提示重新搜索、改选候选或切换 API 服务 / 平台。
- **悬浮歌词方向：** 封面位于屏幕左侧时，歌词栏向右展开；封面位于右侧时，歌词栏向左展开。
- **服务偏好校验：** 切换 API 服务时只保留该服务支持的平台选择。
- **版本号：** 1.24，`versionCode = 28`。


## v1.23 本次改进

- **修正请求量放大：** 搜索候选列表不再为每一行自动请求封面地址；缺失封面的候选先显示应用默认封面，开始试听时再解析当前歌曲封面。
- **优化 GD Studio 请求预算体验：** 本地保护预算调整为每安装实例最多 50 次 / 5 分钟，不再额外预留 5 次；达到预算时显示可恢复提示，不作为异常报告上传。
- **减少歌词补全请求：** 网易云已返回原文歌词但没有翻译时，不再为了补一个可能不存在的译文额外请求 GD Studio；只有原文缺失时才尝试 GD Studio 歌词补全。
- **区分服务职责：** 将旧的“默认音源”常量改名为 flac.music.hi.cn 站点地址，GD Studio 请求不再读取或附带该站 Cookie；下载地址解析函数与界面提示不再把 GD Studio 描述成无条件“兜底”。
- **切歌失败保护：** 上一首 / 下一首因 GD Studio 冷却而无法搜索时，保留当前正在播放的音频，并显示预计可重试时间及可切换服务的提示。
- **版本号：** 1.23，versionCode 27。

请求额度是服务端限制，不是播放次数限制；一首歌曲仍可能涉及搜索、播放链接、歌词和封面等独立接口。请求缓存会减少重复访问，但客户端无法知道同一公网 IP 上其他设备已使用的额度。

第三方 API 使用提示：GD Studio 文档要求注明“GD音乐台 (music.gdstudio.xyz)”出处，并遵守其 CC BY-NC 4.0 / 非商业使用限制。

## v1.22 改进记录

- **两级音源配置：** 先选择 API 服务入口（GD Studio / flac.music.hi.cn），再选择音乐平台；默认 GD Studio + 网易云（netease）。两种服务分别记住平台选择。
- **GD Studio 请求控制：** 同一 GET URL 使用带过期时间的响应缓存；把最近 5 分钟的请求时间写入应用私有设置，限流预算在进程重启后仍保留（每个安装实例最多 45 次 / 5 分钟），并避免 HTTP 429 自动重试。共享公网 IP 上的其他设备仍会消耗服务端额度。
- **API 选择透明：** 所选服务决定主要搜索和下载地址解析入口；失败时提示用户切换，不再静默触发额外备用搜索。
- **歌单接口可靠性：** 移除未在已提供 GD Studio 文档中列出的歌单 API 调用；歌单仍使用网易云公开只读接口，失败不会清空已有快照。
- **分页缓存优化：** 使用每 100 首为底层缓存批次，25 / 50 / 100 首页面复用同一批数据。缓存格式升级后分页缓存会重新建立，不影响歌单快照、本地曲库或已下载歌曲。
- **服务条款提示：** GD Studio 要求注明“GD音乐台 (music.gdstudio.xyz)”出处，并遵守 CC BY-NC 4.0 / 非商业使用限制。服务端开放平台可能动态变化。

自动化验收与真实设备验收是两件事：CI 成功不会自动证明所有来源都开放、每首音源匹配正确或所有设备上的播放交互正常。

## 悬浮迷你播放器

### v1.20 的交互与流畅度优化

- 封面与歌词栏仍使用同一个 `ComposeView / WindowManager` 窗口，统一移动与展开尺寸。
- 拖动更新合并到每个屏幕帧，避免每个触摸采样都单独触发 WindowManager 布局；拖动坐标保留小数余量，降低小幅移动被四舍五入吞掉的感觉。
- 悬浮位置只在拖动结束后保存，不再在手指每次移动时写入偏好设置，减少跨应用拖动的额外开销。
- 长按封面或歌词正文均可展开 / 收起歌词栏，并提供触觉反馈；歌词正文短按仍用于切换原文 / 可用译文。
- 画圈打开应用改为累计完成三整圈（约 1080°）且总移动轨迹至少约 360 dp 后触发，以减少误触。实际识别仍受设备触控采样和系统行为影响。

播放进度仍由 Activity 提供唯一时间轴，悬浮服务不独立递增播放位置。上述调整旨在改善手感和降低错位风险，**不代表已经在所有 Android 设备上完成实机验收**。

### 手势

| 操作 | 行为 |
|---|---|
| 单击封面 | 播放 / 暂停当前试听 |
| 双击封面 | 关闭当前迷你播放器 / 试听 |
| 长按封面 | 展开 / 收起歌词栏 |
| 拖动封面或歌词栏 | 移动整组悬浮播放器 |
| 拖动时画圈 | 打开应用，并尝试定位当前歌曲 |
| 点击歌词中间区域 | 在原文 / 可用译文间切换 |
| 点击进度条 | 跳转到对应播放位置 |
| 点击歌词栏两侧按钮 | 尝试播放上一首 / 下一首 |
| 点击播放模式图标 | 在顺序、乱序和单曲循环模式间切换 |

画圈识别需要足够的移动距离和接近完整一周的累计转角；不同触控采样、设备系统和悬浮窗权限行为可能影响识别。画圈定位属于“尝试恢复”，如果曲目不在当前已加载的歌单或队列中，应用可能无法精确定位。

跨应用悬浮层由 `FloatingMiniPlayerService` 管理窗口和命令转发；当前试听的 `MediaPlayer` 仍由 `MainActivity` 管理。这不是完整的后台媒体播放服务，不保证在系统强制回收 Activity / 进程后继续播放。

## 技术架构与源码地图

项目当前为单 Android 应用模块。主要业务状态仍集中在 `MainActivity.kt`，修改时应优先做可验证的小步重构。

| 文件 | 主要职责 |
|---|---|
| `app/src/main/java/com/wulinlee/musicmeta/MainActivity.kt` | Compose 界面、歌单状态与分页、音源搜索、下载编排、试听、歌词和 WebView 验证 |
| `app/src/main/java/com/wulinlee/musicmeta/FloatingMiniPlayerService.kt` | 跨应用悬浮窗、单窗口布局、拖动 / 画圈手势、前台通知与控制命令 |
| `MusicModels.kt` | 歌曲、歌单与媒体元数据模型 |
| `PublicPlaylistProvider.kt` | 网易云公开歌单元数据、曲目列表与批量读取 |
| `FlacMusicProvider.kt` | 候选搜索、下载地址解析、来源封面 / 歌词与备用来源 |
| `MetadataProvider.kt` | 歌词 / 封面补全与响应解析 |
| `AudioPipeline.kt` | 下载器、文件命名、音频标签写入和媒体发布 |
| `LrcMerger.kt` | YRC / LRC 规范化及原文和翻译的时间戳合并 |
| `app/src/test/java/com/wulinlee/musicmeta/LrcMergerTest.kt` | 歌词合并与规范化 JVM 单元测试 |
| `app/src/main/AndroidManifest.xml` | 应用组件、权限与 Android 网络安全策略 |
| `app/build.gradle.kts` | SDK、依赖、版本号及 Kotlin / Java 编译设置 |
| `.github/workflows/android.yml` | CI 测试、Release APK 构建与签名、上传和正式发布 |

### 主要数据流

**歌单：** 输入歌单链接 / ID → Provider 读取歌单和曲目 ID → 按页获取详情并更新缓存 → 用户筛选、跳页或导航。

**下载：** 搜索候选 → 核对版本 → 解析下载 URL → 校验响应与音频内容 → 临时文件下载 → 可选写标签 / 封面 / LRC → 发布到 MediaStore 或用户授权目录。网络或文件操作失败时应清理临时文件。

**播放器：** Activity 持有试听播放器与当前播放状态 → 以轻量进度更新同步悬浮层 → 服务通过广播把播放、暂停、跳转和歌曲导航请求交回 Activity。悬浮层不维护第二套播放时间轴。

### 当前依赖

- Android Gradle Plugin 8.7.3、Kotlin 2.0.21、JDK 17、Gradle 8.9。
- Jetpack Compose、Material 3、AndroidX Activity / Core / Lifecycle / DocumentFile。
- Kotlin Coroutines、OkHttp 4.12.0、Coil 2.7.0、jaudiotagger 3.0.1。
- JUnit 4；当前自动化测试主要覆盖歌词转换和合并。

项目目前没有集成 Room、DI 框架、Media3 / ExoPlayer 或 MediaSession。不要把这些规划中的能力写成现成功能。

## 数据源与使用边界

项目从第三方服务获取可访问的歌单、候选音源、歌词和封面。歌单使用网易云公开只读接口；音源服务可以选择 GD Studio API 或 flac.music.hi.cn。GD Studio 的 source 参数较多，但文档声明当前可用源会动态调整；flac.music.hi.cn 当前适配网易云与酷我。LRCLIB 和 iTunes Search 用于部分歌词 / 封面补全。接口、域名和访问策略可能改变，实际使用以当前 Provider 实现和服务条款为准。

- 不承诺私密歌单、受限资源或需要登录授权的内容可用。
- 不保证第三方音源长期可访问，也不保证每首歌曲都能找到完全匹配的录音版本。
- 不把站点 Cookie 发送给无关域名；不绕过验证码、登录限制或其他访问控制。
- 不生成不存在的封面、歌词或翻译。缺少译文时只展示可用原文或提示无歌词。
- 请遵守相关服务条款、版权法规及所在地法律。下载和使用的权利由用户自行确认。

## 开发环境与构建

### 环境要求

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 35.0.0
- Gradle 8.9（与当前 CI 一致）

在仓库根目录执行：

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleRelease
```

Release 未签名 APK 的默认输出路径：

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

CI 会另外执行 APK 对齐、签名、签名证书验证和包名检查。构建成功只证明对应自动化步骤成功，并不等于网络音源、真实下载、标签写入和所有设备上的播放器交互均已完成端到端验收。

## 测试与发布验收

每次修改至少确认以下事项：

1. 单元测试和 Release 构建：`gradle --no-daemon :app:testDebugUnitTest :app:assembleRelease`。
2. GitHub Actions 对应提交的 `testDebugUnitTest`、`assembleRelease`、APK 签名、包信息检查和发布步骤结论。
3. APK 资产名称、大小、SHA-256、Release 的 tag 指向和 commit SHA。
4. 如改动播放器，在真实设备上测试封面 / 歌词栏拖动、画圈定位、单击 / 双击 / 长按、歌词切换、播放进度、歌曲结束、上一首 / 下一首、展开 / 收起、前后台切换及权限撤销。
5. 如改动下载，验证无效音频响应、HTML 验证页、HTTP 错误、重定向、大小限制和失败清理。

真实设备验收需要设备或用户提供的测试反馈；不能只根据 CI 通过就声称悬浮窗不再闪动。

## 版本与签名

- 当前版本名：`1.24`
- 当前版本号：`versionCode = 28`
- Gradle 版本元数据位于 `app/build.gradle.kts`；用户可见变更记录位于 `RELEASE_NOTES.md`。

GitHub Actions 工作流会设置 JDK 17 和 Gradle 8.9，检查资源后运行 JVM 单元测试、构建 Release、对 APK 执行 zipalign / 签名 / 校验，再上传 APK 并创建 Release。常规 `main` push 会创建预览发布；提交信息以 `release:` 开头时走正式发布逻辑。已有同名 Release 默认不会覆盖，只有明确的 `release: refresh v...` 提交才允许刷新现有版本。

正式发布前应检查：
- `main` 最新 commit 与 `v1.23` 标签指向一致；
- 对应 Actions run 为 `completed / success`；
- Release 不是草稿或预发布；
- `MusicMeta-v1.23.apk` 资产已上传，签名及 package metadata 检查通过。

### APK 签名注意事项

工作流使用以下 GitHub Actions Secrets 配置长期签名密钥：

- `MUSICMETA_KEYSTORE_BASE64`
- `MUSICMETA_KEYSTORE_PASSWORD`
- `MUSICMETA_KEY_ALIAS`
- `MUSICMETA_KEY_PASSWORD`

如果四项密钥未完整配置，工作流会发出警告并生成临时签名密钥。临时密钥不会在运行间持久保存，因此这种 APK 可能无法覆盖安装在使用其他签名证书的旧版应用之上。**请以该次 Actions 日志中的签名分支和证书指纹为准**；不要假定所有 Release 都使用同一把证书，也不要把 keystore 或密码提交到仓库。

## 当前限制

- 播放器使用 Activity 内的 `MediaPlayer`，尚无完整的 Media3 / ExoPlayer、MediaSession、系统媒体通知、锁屏 / 蓝牙控制和独立持久播放队列。
- 自动化测试覆盖有限；网络接口变化、音源匹配、真实下载与设备厂商差异需要额外验证。
- 悬浮窗需要系统权限；系统回收进程或用户撤销权限后，不保证跨应用显示和播放持续。
- `MainActivity.kt` 仍承载大量业务状态，后续应渐进拆分播放器、歌单、搜索和下载职责，并为网络解析、下载校验、导航竞态补充测试。

## 许可与第三方服务

- 本项目根目录采用 MIT License，见 [LICENSE](LICENSE)。
- 第三方库与服务请遵循各自的许可和服务条款；服务可用性不由本项目控制。
- 项目与网易云音乐、GD Studio、LRCLIB、Apple 或其他音源服务均无官方关联。
- 第三方依赖和声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。


### GD Studio API attribution and usage

When the selected service is GD Studio, MusicMeta uses its published search, URL, album-art and lyric endpoints where available. The documentation asks clients to credit **GD音乐台 (music.gdstudio.xyz)** and states CC BY-NC 4.0 / non-commercial study-use restrictions. These API terms do not automatically grant permission to download or redistribute every recording; users remain responsible for permissions and applicable law. The provider's currently supported source list may change.
