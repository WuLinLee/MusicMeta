package com.wulinlee.musicmeta

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val DEFAULT_SOURCE = "https://flac.music.hi.cn/"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MusicMetaApp() } }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicMetaApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlistProvider = remember { PublicPlaylistProvider() }
    val metadataProvider = remember { MetadataProvider() }
    val flacProvider = remember { FlacMusicProvider() }

    var tab by remember { mutableStateOf(0) }
    var playlistInput by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<PublicPlaylist?>(null) }
    var search by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("输入网易云公开歌单 ID 或链接即可同步，不需要登录账号。") }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var flacQuery by remember { mutableStateOf("") }
    var flacPlatform by remember { mutableStateOf("kuwo") }
    var flacResults by remember { mutableStateOf<List<FlacSiteSong>>(emptyList()) }
    var isSearchingFlac by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(DEFAULT_SOURCE) }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var isTagging by remember { mutableStateOf(false) }
    var tagAssets by remember { mutableStateOf<TrackAssets?>(null) }
    var taggedTrack by remember { mutableStateOf<MusicTrack?>(null) }

    val downloadAction: (String, String, String?, String?, String, String?) -> Unit = {
            url, userAgent, disposition, mime, referer, cookie ->
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            status = "这个网站使用了网页内部 blob/data 下载方式；当前版本只接管标准 HTTP/HTTPS 下载链接，不绕过站点限制。"
        } else if (isDownloading) {
            status = "已有下载任务正在运行。"
        } else {
            scope.launch {
                isDownloading = true
                status = "正在下载音频……"
                try {
                    tagAssets = null
                    taggedTrack = null
                    val sourceFile = withContext(Dispatchers.IO) {
                        AudioDownloader.download(context, url, userAgent, cookie, disposition, mime, referer)
                    }
                    downloadedFile = sourceFile
                    if (!sourceFile.extension.equals("mp3", ignoreCase = true)) {
                        status = "音频下载完成，正在转换为 MP3……"
                    }
                    val file = withContext(Dispatchers.IO) { AudioConverter.toMp3(sourceFile) }
                    downloadedFile = file
                    val selected = selectedTrack
                    if (selected == null) {
                        tagAssets = null
                        taggedTrack = null
                        status = "下载完成：${file.name}。请先同步公开歌单并选择对应歌曲后再下载，才能自动匹配标签。"
                    } else {
                        status = "下载完成，正在获取《${selected.title}》的封面与歌词……"
                        try {
                            val assets = metadataProvider.fetchAssets(selected)
                            tagAssets = assets
                            taggedTrack = selected
                            val lyricStatus = when {
                                assets.originalLyrics.isBlank() -> "暂未找到原文歌词"
                                assets.translatedLyrics.isBlank() -> "已找到原文，暂无中文翻译"
                                else -> "已找到原文及中文翻译"
                            }
                            status = "已匹配《${selected.title}》；${if (assets.coverBytes != null) "封面已找到" else "暂无封面"}，${lyricStatus}。保存前请核对歌曲版本。"
                        } catch (error: Exception) {
                            status = "音频已下载，但获取标签信息失败：${error.message ?: "未知错误"}。仍可保存原文件。"
                        }
                    }
                    tab = 1
                } catch (error: Exception) {
                    status = "下载失败：${error.message ?: "未知错误"}"
                } finally {
                    isDownloading = false
                }
            }
        }
    }
    val latestDownloadAction by rememberUpdatedState(downloadAction)

    val searchFlac: () -> Unit = {
        val query = flacQuery.trim()
        if (query.isBlank()) {
            status = "请输入要搜索的歌名或歌手。"
        } else if (isSearchingFlac || isDownloading) {
            status = "当前已有任务正在运行。"
        } else {
            isSearchingFlac = true
            flacResults = emptyList()
            status = "正在通过下载站 API 搜索歌曲……"
            scope.launch {
                try {
                    val cookie = CookieManager.getInstance().getCookie(DEFAULT_SOURCE)
                    val userAgent = browser?.settings?.userAgentString.orEmpty()
                    val results = withContext(Dispatchers.IO) {
                        flacProvider.search(query, flacPlatform, cookie, userAgent)
                    }
                    flacResults = results
                    status = "下载站找到 ${results.size} 首候选歌曲；请核对版本后下载。"
                } catch (error: Exception) {
                    status = "下载站 API 搜索失败：${error.message ?: "未知错误"}"
                } finally {
                    isSearchingFlac = false
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("音笺 MusicMeta") }) },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("网易云歌单") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("下载网站") })
            }

            if (tab == 0) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("公开歌单同步", style = MaterialTheme.typography.titleLarge)
                    Text("只读取公开歌单信息，不需要网易云账号。私人歌单不支持。", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = playlistInput,
                            onValueChange = { playlistInput = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("歌单 ID 或链接") },
                            placeholder = { Text("例如：4051234567") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        )
                        Button(
                            onClick = {
                                isSyncing = true
                                status = "正在读取公开歌单……"
                                scope.launch {
                                    try {
                                        val loaded = playlistProvider.load(playlistInput)
                                        playlist = loaded
                                        status = "同步完成：${loaded.tracks.size} 首歌。点歌曲右侧“去下载”开始获取音频。"
                                    } catch (error: Exception) {
                                        status = "同步失败：${error.message ?: "公开歌单暂不可用"}"
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            enabled = !isSyncing && playlistInput.isNotBlank(),
                        ) {
                            if (isSyncing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("同步")
                        }
                    }
                    if (isSyncing) Text("正在同步歌单，请稍候……")
                    playlist?.let { p ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (p.coverUrl.isNotBlank()) AsyncImage(model = p.coverUrl, contentDescription = "歌单封面", modifier = Modifier.size(76.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Text("歌单 ID：${p.id}", style = MaterialTheme.typography.bodySmall)
                                    Text("创建者：${p.creator.ifBlank { "未知" }} · ${p.tracks.size} 首", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("筛选歌名或歌手") },
                        )
                        p.tracks.filter {
                            search.isBlank() || it.title.contains(search, true) || it.artists.contains(search, true)
                        }.forEachIndexed { index, track ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (track.coverUrl.isNotBlank()) {
                                        AsyncImage(model = track.coverUrl, contentDescription = "专辑封面", modifier = Modifier.size(56.dp))
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${index + 1}. ${track.title}", style = MaterialTheme.typography.titleSmall)
                                        Text(track.artists, style = MaterialTheme.typography.bodySmall)
                                        if (track.album.isNotBlank()) Text(track.album, style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedButton(onClick = {
                                        selectedTrack = track
                                        flacQuery = "${track.title} ${track.artists}"
                                        flacResults = emptyList()
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("歌曲搜索", "${track.title} ${track.artists}"))
                                        address = DEFAULT_SOURCE
                                        browser?.loadUrl(DEFAULT_SOURCE)
                                        tab = 1
                                        status = "已选《${track.title}》，已复制歌名和歌手。可点上方“API 搜索”直连音源，也可在下方网页中手动搜索。"
                                    }) { Text("去下载") }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = flacQuery,
                            onValueChange = {
                                val expected = selectedTrack?.let { track -> "${track.title} ${track.artists}" }
                                if (expected != null && !it.equals(expected, ignoreCase = true)) selectedTrack = null
                                flacQuery = it
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("直接搜索下载站音源") },
                            placeholder = { Text("输入歌名或歌手") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        )
                        Button(
                            onClick = searchFlac,
                            enabled = !isSearchingFlac && !isDownloading && flacQuery.isNotBlank(),
                        ) {
                            if (isSearchingFlac) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("API 搜索")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("音源")
                        TextButton(onClick = { flacPlatform = "kuwo"; flacResults = emptyList() }) {
                            Text(if (flacPlatform == "kuwo") "✓ 酷我" else "酷我")
                        }
                        TextButton(onClick = { flacPlatform = "wyy"; flacResults = emptyList() }) {
                            Text(if (flacPlatform == "wyy") "✓ 网易云" else "网易云")
                        }
                        Text("直接 API 下载后会转为 MP3", style = MaterialTheme.typography.bodySmall)
                    }
                    if (flacResults.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            flacResults.forEach { song ->
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(song.title, style = MaterialTheme.typography.titleSmall)
                                            Text(song.artist, style = MaterialTheme.typography.bodySmall)
                                            if (song.album.isNotBlank()) Text(song.album, style = MaterialTheme.typography.bodySmall)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                if (isDownloading) {
                                                    status = "已有下载任务正在运行。"
                                                } else {
                                                    scope.launch {
                                                        try {
                                                            status = "正在解析《${song.title}》的下载地址……"
                                                            val cookie = CookieManager.getInstance().getCookie(DEFAULT_SOURCE)
                                                            val userAgent = browser?.settings?.userAgentString.orEmpty()
                                                            val directUrl = withContext(Dispatchers.IO) {
                                                                flacProvider.resolveDownloadUrl(
                                                                    song, "flac", "2000", cookie, userAgent,
                                                                )
                                                            }
                                                            latestDownloadAction(
                                                                directUrl,
                                                                userAgent,
                                                                "attachment; filename=\"${song.title}.flac\"",
                                                                "audio/flac",
                                                                DEFAULT_SOURCE,
                                                                null,
                                                            )
                                                        } catch (error: Exception) {
                                                            status = "下载站解析失败：${error.message ?: "未知错误"}"
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !isDownloading,
                                        ) { Text("下载并转 MP3") }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("下载站 / 音频直链") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        )
                        Button(onClick = {
                            val target = normalizeUrl(address)
                            if (target == null) status = "请输入有效的 HTTP/HTTPS 网址。"
                            else {
                                address = target
                                browser?.loadUrl(target)
                                status = "已请求打开网站。"
                            }
                        }) { Text("打开") }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val target = normalizeUrl(address)
                            if (target == null) {
                                status = "请输入有效的音频直链。"
                            } else {
                                latestDownloadAction(
                                    target,
                                    browser?.settings?.userAgentString.orEmpty(),
                                    null,
                                    null,
                                    browser?.url ?: address,
                                    CookieManager.getInstance().getCookie(browser?.url ?: address),
                                )
                            }
                        }, enabled = !isDownloading) { Text("下载直链") }
                        Text(
                            selectedTrack?.let { "当前歌曲：${it.title} — ${it.artists}" } ?: "先从歌单选择歌曲，再在网站内下载。",
                            modifier = Modifier.weight(1f).padding(top = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { viewContext ->
                            WebView(viewContext).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mediaPlaybackRequiresUserGesture = true
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        return request.url.scheme?.lowercase() !in setOf("http", "https")
                                    }
                                }
                                setDownloadListener { url, userAgent, disposition, mime, _ ->
                                    latestDownloadAction(
                                        url,
                                        userAgent.orEmpty(),
                                        disposition,
                                        mime,
                                        this.url ?: DEFAULT_SOURCE,
                                        CookieManager.getInstance().getCookie(this.url ?: DEFAULT_SOURCE),
                                    )
                                }
                                browser = this
                                loadUrl(DEFAULT_SOURCE)
                            }
                        },
                        update = { view -> if (browser !== view) browser = view },
                    )
                }
            }

            if (isDownloading) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在下载音频……")
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))

            if (downloadedFile != null) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("下载结果：${downloadedFile?.name.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                        if (taggedTrack != null && tagAssets != null) {
                            Text(
                                when {
                                    tagAssets?.originalLyrics.isNullOrBlank() -> "暂无歌词"
                                    tagAssets?.translatedLyrics.isNullOrBlank() -> "有原文，暂无中文翻译"
                                    else -> "原文 + 中文翻译"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = {
                                    val file = downloadedFile
                                    val track = taggedTrack
                                    val assets = tagAssets
                                    if (file == null) return@Button
                                    isTagging = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                if (track != null && assets != null) AudioTagWriter.write(file, track, assets)
                                                MediaPublisher.publish(context, file, track)
                                            }
                                            file.delete()
                                            downloadedFile = null
                                            tagAssets = null
                                            taggedTrack = null
                                            status = "已保存到手机的 音乐/MusicMeta 文件夹。"
                                        } catch (error: Exception) {
                                            status = "保存失败：${error.message ?: "未知错误"}"
                                        } finally {
                                            isTagging = false
                                        }
                                    }
                                },
                                enabled = !isTagging,
                            ) { Text(if (isTagging) "正在嵌入标签并保存……" else "嵌入封面 / 双语歌词并保存") }
                        }
                        OutlinedButton(
                            onClick = {
                                val file = downloadedFile ?: return@OutlinedButton
                                isTagging = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { MediaPublisher.publish(context, file, null) }
                                        file.delete()
                                        downloadedFile = null
                                        tagAssets = null
                                        taggedTrack = null
                                        status = "已保存原文件到手机的 音乐/MusicMeta 文件夹。"
                                    } catch (error: Exception) {
                                        status = "保存原文件失败：${error.message ?: "未知错误"}"
                                    } finally {
                                        isTagging = false
                                    }
                                }
                            },
                            enabled = !isTagging,
                        ) { Text("仅保存原文件") }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            browser?.stopLoading()
            browser?.destroy()
            browser = null
        }
    }
}

private fun normalizeUrl(input: String): String? {
    val value = input.trim()
    if (value.isBlank()) return null
    val normalized = if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else "https://$value"
    val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
    return if (uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) normalized else null
}
