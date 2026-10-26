# 音笺 MusicMeta v1.22 正式版

发布类型：正式版。以 GitHub Actions 成功构建、签名校验和实际 Release APK 附件为准。

- **音源设置改为两级选择**：先选 API 服务入口（GD Studio 或 flac.music.hi.cn），再选音乐平台；新默认 GD Studio + 网易云（netease）。两种服务分别记住平台偏好。
- **支持 GD Studio 多平台参数**：netease、tencent、kuwo、tidal、qobuz、joox、bilibili、apple、ytmusic、spotify。具体平台能否访问由服务端动态决定；flac.music.hi.cn 当前适配网易云和酷我。
- **降低重复 API 请求**：GD Studio 相同 GET URL 增加带 TTL 的进程内响应缓存；搜索、歌词、封面地址与下载地址采用不同缓存时长；重复搜索结果也会短时复用。
- **遵守请求频率约束**：把最近 5 分钟的 GD Studio 请求时间写入应用私有设置，限流预算在进程重启后仍保留，每个安装实例最多 45 次 / 5 分钟，为文档所述 50 次留出余量；HTTP 429 不自动重试。其他设备共享的公网 IP 额度无法由单个客户端完全控制。
- **明确服务入口行为**：选定 GD Studio 就通过 GD Studio 搜索；选定 flac.music.hi.cn 就使用该站适配的平台。失败时提示切换，不会静默触发额外备用搜索。
- **移除未在所提供 GD Studio 文档中列出的歌单 API 调用**：歌单仍通过网易云公开只读接口读取；失败会明确提示，已有快照不会被清空。
- **优化分页缓存**：统一以 100 首歌曲为底层缓存批次，25 / 50 / 100 每页共用同一批数据，避免改变每页数量后重复请求。同一歌单的分页缓存格式升级后会重建，不影响歌单快照、本地曲库或下载歌曲。
- **版本号**：1.22，versionCode 26。

第三方 API 使用提示：GD Studio 文档要求注明出处“GD音乐台 (music.gdstudio.xyz)”，并声明 CC BY-NC 4.0 / 非商业使用限制。服务端当前开放的平台可能变化；请遵守服务条款和适用版权规则。

自动化验收包括 `:app:testDebugUnitTest`、`:app:assembleRelease`、APK 对齐、签名和包名校验。CI 成功不等于真实设备上的多音源检索、实际下载、限流、悬浮窗和播放体验已完成测试。

安装注意：如果正式 APK 的签名证书与旧版不一致，Android 将拒绝覆盖安装。请在 Release 构建日志中核对签名状态；若使用临时签名，可能需要卸载旧版再安装，卸载可能清除应用私有设置。
