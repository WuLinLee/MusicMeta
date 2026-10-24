package com.wulinlee.musicmeta

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

private const val DEFAULT_SOURCE = "https://flac.music.hi.cn/"
private val PageBackground = Color(0xFFF7F8F5)
private val Ink = Color(0xFF202923)
private val SubInk = Color(0xFF788179)
private val Accent = Color(0xFF427564)
private val SoftAccent = Color(0xFFE5F0E9)
private val Hairline = Color(0xFFE7EAE5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    onPrimary = Color.White,
                    primaryContainer = SoftAccent,
                    onPrimaryContainer = Color(0xFF244C3D),
                    secondary = Color(0xFF65786C),
                    background = PageBackground,
                    surface = Color.White,
                    onSurface = Ink,
                    onSurfaceVariant = SubInk,
                    outline = Hairline,
                ),
            ) {
                MusicMetaApp()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicMetaApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderPreferences = remember { context.getSharedPreferences("music_meta_settings", Context.MODE_PRIVATE) }
    var destinationTreeUri by remember { mutableStateOf(loadSavedDestination(context)) }
    val playlistProvider = remember { PublicPlaylistProvider() }
    val metadataProvider = remember { MetadataProvider() }
    val flacProvider = remember { FlacMusicProvider() }
    val listState = rememberLazyListState()

    var playlistInput by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<PublicPlaylist?>(null) }
    var filterText by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("输入公开歌单 ID 或链接开始。无需登录网易云账号。") }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var flacPlatform by remember { mutableStateOf("kuwo") }
    var selectedQuality by remember { mutableStateOf("320k") }
    var flacResults by remember { mutableStateOf<List<FlacSiteSong>>(emptyList()) }
    var isSearchingFlac by remember { mutableStateOf(false) }
    var isResolvingUrl by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var verificationOpen by remember { mutableStateOf(false) }

    val destinationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                folderPreferences.edit().putString("destination_tree_uri", uri.toString()).apply()
                destinationTreeUri = uri
                val folderName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
                    ?: "所选文件夹"
                status = "保存目录已设置为：" + folderName
            } catch (error: Exception) {
                status = "系统未允许长期访问该文件夹，请重新选择一个普通文件夹。"
            }
        }
    }
    val destinationFolderName = remember(destinationTreeUri) {
        destinationTreeUri?.let { uri ->
            runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        } ?: "Music/MusicMeta（默认）"
    }

    val busy = isSyncing || isSearchingFlac || isResolvingUrl || isDownloading

    val syncPlaylist: () -> Unit = {
        val input = playlistInput.trim()
        if (input.isBlank()) {
            status = "先粘贴网易云公开歌单链接或 ID。"
        } else if (busy) {
            status = "当前任务还没完成，请稍候。"
        } else {
            isSyncing = true
            status = "正在读取歌单信息……"
            scope.launch {
                try {
                    val loaded = withTimeout(28_000L) {
                        withContext(Dispatchers.IO) { playlistProvider.load(input) }
                    }
                    playlist = loaded
                    selectedTrack = null
                    flacResults = emptyList()
                    filterText = ""
                    status = "已同步 " + loaded.tracks.size + " 首歌曲。点歌曲右侧“找音源”继续。"
                } catch (error: Exception) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "网络或接口暂不可用"
                    status = "同步失败：" + detail
                } finally {
                    isSyncing = false
                }
            }
        }
    }

    val searchTrackSource: (MusicTrack) -> Unit = { track ->
        if (busy) {
            status = "当前任务还没完成，请稍候。"
        } else {
            selectedTrack = track
            val keyword = listOf(track.title, track.artists).filter { it.isNotBlank() }.joinToString(" ")
            flacResults = emptyList()
            isSearchingFlac = true
            status = "正在为《" + track.title + "》查找音源……"
            scope.launch {
                try {
                    val cookie = CookieManager.getInstance().getCookie(DEFAULT_SOURCE)
                    val results = withContext(Dispatchers.IO) {
                        flacProvider.search(
                            keyword = keyword,
                            platform = flacPlatform,
                            cookie = cookie,
                            userAgent = fallbackUserAgent,
                        )
                    }
                    flacResults = results.take(8)
                    status = if (flacResults.isEmpty()) {
                        "没有找到匹配音源。可以切换音源，或检查歌名后重试。"
                    } else {
                        "找到 " + flacResults.size + " 个候选版本，请核对歌手和专辑后下载。"
                    }
                    scope.launch { listState.animateScrollToItem(2) }
                } catch (error: Exception) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "下载站暂不可用"
                    status = "搜索失败：" + detail
                    if (
                        detail.contains("验证") ||
                        detail.contains("468") ||
                        detail.contains("challenge", ignoreCase = true) ||
                        detail.contains("网页", ignoreCase = true)
                    ) {
                        verificationOpen = true
                    }
                } finally {
                    isSearchingFlac = false
                }
            }
        }
    }

    val downloadAndSave: (
        String, String, String?, String?, String, String?, MusicTrack?
    ) -> Unit = { url, userAgent, disposition, mimeType, referer, cookie, track ->
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            status = "这不是有效的音频下载地址，请重新获取音源。"
        } else if (isSyncing || isSearchingFlac || isDownloading) {
            status = "已有任务正在运行，请稍候。"
        } else {
            verificationOpen = false
            isDownloading = true
            scope.launch {
                var downloaded: File? = null
                var mp3File: File? = null
                try {
                    status = "正在下载 " + (track?.title ?: "音频") + "……"
                    val sourceFile = withContext(Dispatchers.IO) {
                        AudioDownloader.download(
                            context = context,
                            url = url,
                            userAgent = userAgent,
                            cookie = cookie,
                            disposition = disposition,
                            mimeTypeHint = mimeType,
                            referer = referer,
                        )
                    }
                    downloaded = sourceFile
                    status = if (selectedQuality == "FLAC") {
                        "下载完成，正在处理无损音频标签……"
                    } else if (sourceFile.extension.equals("mp3", ignoreCase = true)) {
                        "下载完成，正在处理歌曲信息……"
                    } else {
                        "音频已下载，正在转码为 \${selectedQuality} MP3……"
                    }
                    val outputFile = withContext(Dispatchers.IO) {
                        if (selectedQuality == "FLAC") {
                            if (!sourceFile.extension.equals("flac", ignoreCase = true)) {
                                throw IOException("网站没有返回 FLAC 无损文件，请切换到 320k MP3 后重试。")
                            }
                            sourceFile
                        } else {
                            AudioConverter.toMp3(sourceFile, selectedQuality)
                        }
                    }
                    mp3File = outputFile

                    var coverAvailable = false
                    var lyricSummary = "未匹配歌单歌曲"
                    var tagWriteSucceeded = true
                    if (track != null) {
                        status = "正在获取《" + track.title + "》的封面和歌词……"
                        val assets = try {
                            metadataProvider.fetchAssets(track)
                        } catch (_: Exception) {
                            null
                        }
                        val safeAssets = assets ?: TrackAssets(
                            coverBytes = null,
                            coverMimeType = null,
                            originalLyrics = "",
                            translatedLyrics = "",
                            bilingualLyrics = "",
                        )
                        coverAvailable = safeAssets.coverBytes != null && safeAssets.coverBytes.isNotEmpty()
                        lyricSummary = when {
                            safeAssets.originalLyrics.isBlank() -> "暂无歌词"
                            safeAssets.translatedLyrics.isNotBlank() -> "原文 + 中文翻译"
                            else -> "已找到原文，暂无中文翻译"
                        }
                        tagWriteSucceeded = withContext(Dispatchers.IO) {
                            runCatching { AudioTagWriter.write(outputFile, track, safeAssets) }.isSuccess
                        }
                    }

                    status = "正在保存到手机音乐库……"
                    withContext(Dispatchers.IO) {
                        MediaPublisher.publish(context, outputFile, track, destinationTreeUri)
                    }

                    val outputName = outputFile.name
                    outputFile.delete()
                    if (sourceFile.absolutePath != outputFile.absolutePath) sourceFile.delete()

                    status = buildString {
                        append("已保存到 音乐/MusicMeta：")
                        append(outputName)
                        if (track != null) {
                            append(" · ")
                            append(if (coverAvailable) "已写入封面" else "暂无封面")
                            append(" · ")
                            append(lyricSummary)
                            if (!tagWriteSucceeded) append(" · 标签写入失败")
                        }
                    }
                    flacResults = emptyList()
                    selectedTrack = null
                } catch (error: Exception) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "未知错误"
                    status = "下载或保存失败：" + detail
                } finally {
                    isDownloading = false
                }
            }
        }
    }
    val latestDownloadAndSave by rememberUpdatedState(downloadAndSave)

    val onWebsiteDownload: (
        String, String, String?, String?, String, String?
    ) -> Unit = { url, userAgent, disposition, mimeType, referer, cookie ->
        latestDownloadAndSave(
            url,
            userAgent,
            disposition,
            mimeType,
            referer,
            cookie,
            selectedTrack,
        )
    }
    val latestWebsiteDownload by rememberUpdatedState(onWebsiteDownload)

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("音笺", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text("MUSIC META", color = SubInk, fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                },
                actions = {
                    TextButton(onClick = { verificationOpen = true }) {
                        Text("下载站验证", color = Accent, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PageBackground,
                    titleContentColor = Ink,
                ),
            )
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 3.dp) {
                Column(Modifier.fillMaxWidth()) {
                    if (busy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Accent,
                            trackColor = SoftAccent,
                        )
                    }
                    Text(
                        text = status,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        color = SubInk,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "PLAYLIST",
                        color = Accent,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "把喜欢的歌，存进音乐库",
                        color = Ink,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "公开歌单免登录 · 自动转 MP3 · 封面与歌词写入文件",
                        color = SubInk,
                        fontSize = 12.sp,
                    )
                }
            }

            item(key = "playlist-input") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = playlistInput,
                            onValueChange = { playlistInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("网易云歌单链接或 ID") },
                            placeholder = { Text("粘贴分享链接，或输入纯数字 ID") },
                            shape = RoundedCornerShape(15.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { syncPlaylist() }),
                            enabled = !busy,
                        )
                        Button(
                            onClick = syncPlaylist,
                            enabled = !busy && playlistInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(17.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(9.dp))
                                Text("正在同步")
                            } else {
                                Text("同步公开歌单", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item(key = "destination-folder") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 15.dp, top = 13.dp, end = 12.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            color = SoftAccent,
                            shape = RoundedCornerShape(13.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("♫", color = Accent, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text("保存位置", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                destinationFolderName,
                                color = SubInk,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            OutlinedButton(
                                onClick = { destinationPicker.launch(destinationTreeUri) },
                                enabled = !busy,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text("选择文件夹", fontSize = 12.sp)
                            }
                            if (destinationTreeUri != null) {
                                TextButton(
                                    onClick = {
                                        folderPreferences.edit().remove("destination_tree_uri").apply()
                                        destinationTreeUri = null
                                        status = "已恢复默认目录：Music/MusicMeta"
                                    },
                                    enabled = !busy,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text("恢复默认", color = SubInk, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "source-results") {
                when {
                    isSearchingFlac -> {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Accent,
                                    strokeWidth = 2.dp,
                                )
                                Column {
                                    Text("正在找音源", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        selectedTrack?.let { it.title + " · " + it.artists }.orEmpty(),
                                        color = SubInk,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    flacResults.isNotEmpty() -> {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                        ) {
                            Column(
                                modifier = Modifier.padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text("选择音源", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                        Text(selectedTrack?.title.orEmpty(), color = SubInk, fontSize = 12.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilterChip(
                                            selected = flacPlatform == "kuwo",
                                            onClick = {
                                                if (flacPlatform != "kuwo") {
                                                    flacPlatform = "kuwo"
                                                    selectedTrack?.let { searchTrackSource(it) }
                                                }
                                            },
                                            label = { Text("酷我") },
                                        )
                                        FilterChip(
                                            selected = flacPlatform == "wyy",
                                            onClick = {
                                                if (flacPlatform != "wyy") {
                                                    flacPlatform = "wyy"
                                                    selectedTrack?.let { searchTrackSource(it) }
                                                }
                                            },
                                            label = { Text("网易云") },
                                        )
                                    }
                                }
                                Text("下载音质", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = SubInk)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = selectedQuality == "128k",
                                        onClick = { selectedQuality = "128k" },
                                        enabled = !isDownloading && !isResolvingUrl,
                                        label = { Text("标准 128k") },
                                    )
                                    FilterChip(
                                        selected = selectedQuality == "320k",
                                        onClick = { selectedQuality = "320k" },
                                        enabled = !isDownloading && !isResolvingUrl,
                                        label = { Text("高品质 320k") },
                                    )
                                    FilterChip(
                                        selected = selectedQuality == "FLAC",
                                        onClick = { selectedQuality = "FLAC" },
                                        enabled = !isDownloading && !isResolvingUrl,
                                        label = { Text("无损 FLAC") },
                                    )
                                }
                                Text(
                                    if (selectedQuality == "FLAC") "保存无损 FLAC 文件" else "保存 MP3 · \${selectedQuality}",
                                    color = SubInk,
                                    fontSize = 11.sp,
                                )
                                flacResults.forEach { song ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(15.dp))
                                            .background(PageBackground)
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        AsyncImage(
                                            model = song.coverUrl.takeIf { it.isNotBlank() },
                                            contentDescription = "音源封面",
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(SoftAccent),
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                song.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                                                color = SubInk,
                                                fontSize = 11.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (!isResolvingUrl && !isDownloading) {
                                                    isResolvingUrl = true
                                                    status = "正在解析《" + song.title + "》的下载地址……"
                                                    scope.launch {
                                                        try {
                                                            val cookie = CookieManager.getInstance().getCookie(DEFAULT_SOURCE)
                                                            val directUrl = withContext(Dispatchers.IO) {
                                                                flacProvider.resolveDownloadUrl(
                                                                    song = song,
                                                                    format = if (selectedQuality == "FLAC") "flac" else "mp3",
                                                                    bitrate = when (selectedQuality) {
                                                                        "128k" -> "128"
                                                                        "320k" -> "320"
                                                                        else -> "2000"
                                                                    },
                                                                    cookie = cookie,
                                                                    userAgent = fallbackUserAgent,
                                                                )
                                                            }
                                                            isResolvingUrl = false
                                                            latestDownloadAndSave(
                                                                directUrl,
                                                                fallbackUserAgent,
                                                                if (selectedQuality == "FLAC") "attachment; filename=\"audio.flac\"" else "attachment; filename=\"audio.mp3\"",
                                                                if (selectedQuality == "FLAC") "audio/flac" else "audio/mpeg",
                                                                DEFAULT_SOURCE,
                                                                cookie,
                                                                selectedTrack,
                                                            )
                                                        } catch (error: Exception) {
                                                            val detail = error.message?.takeIf { it.isNotBlank() } ?: "下载站暂不可用"
                                                            status = "解析下载地址失败：" + detail
                                                            if (detail.contains("验证") || detail.contains("468")) {
                                                                verificationOpen = true
                                                            }
                                                        } finally {
                                                            isResolvingUrl = false
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !isResolvingUrl && !isDownloading,
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        ) {
                                            if (isResolvingUrl) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White,
                                                )
                                            } else {
                                                Text("下载", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val currentPlaylist = playlist
            if (currentPlaylist != null) {
                item(key = "playlist-card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AsyncImage(
                                model = currentPlaylist.coverUrl.takeIf { it.isNotBlank() },
                                contentDescription = "歌单封面",
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(SoftAccent),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "网易云歌单",
                                    color = Accent,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    currentPlaylist.name,
                                    color = Ink,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    currentPlaylist.creator.ifBlank { "未知创建者" },
                                    color = SubInk,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(currentPlaylist.tracks.size.toString() + " 首歌曲", color = SubInk, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item(key = "track-filter") {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("筛选歌曲") },
                        placeholder = { Text("按歌名或歌手查找") },
                        shape = RoundedCornerShape(15.dp),
                        enabled = !busy,
                    )
                }
                item(key = "track-heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("歌曲列表", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("核对版本后下载，自动写入标签", fontSize = 12.sp, color = SubInk)
                        }
                        Surface(color = SoftAccent, shape = CircleShape) {
                            Text(
                                currentPlaylist.tracks.size.toString(),
                                color = Accent,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                val visibleTracks = currentPlaylist.tracks.filter {
                    filterText.isBlank() ||
                        it.title.contains(filterText.trim(), ignoreCase = true) ||
                        it.artists.contains(filterText.trim(), ignoreCase = true)
                }
                items(visibleTracks, key = { "track-" + it.id }) { track ->
                    TrackRow(
                        track = track,
                        busy = busy,
                        onFindSource = { searchTrackSource(track) },
                    )
                }
            } else {
                item(key = "empty-playlist") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Surface(modifier = Modifier.size(68.dp), color = SoftAccent, shape = CircleShape) {
                                Box(contentAlignment = Alignment.Center) {
                                    Surface(modifier = Modifier.size(26.dp), shape = CircleShape, color = Accent) {}
                                }
                            }
                            Text("从公开歌单开始", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text(
                                "粘贴网易云分享链接或歌单 ID。同步成功后，歌曲和音源都在这个页面完成。",
                                color = SubInk,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (verificationOpen) {
        Dialog(onDismissRequest = { verificationOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(560.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("下载站验证", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("若网页要求验证，请在这里手动完成", color = SubInk, fontSize = 11.sp)
                        }
                        TextButton(onClick = { verificationOpen = false }) {
                            Text("完成", color = Accent)
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        factory = { viewContext: Context ->
                            WebView(viewContext).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                CookieManager.getInstance().setAcceptCookie(true)
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        return request.url.scheme?.lowercase() !in setOf("http", "https")
                                    }
                                }
                                setDownloadListener { url, userAgent, disposition, mimeType, _ ->
                                    val referer = this.url ?: DEFAULT_SOURCE
                                    latestWebsiteDownload(
                                        url,
                                        userAgent.orEmpty(),
                                        disposition,
                                        mimeType,
                                        referer,
                                        CookieManager.getInstance().getCookie(referer),
                                    )
                                }
                                loadUrl(DEFAULT_SOURCE)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: MusicTrack,
    busy: Boolean,
    onFindSource: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            AsyncImage(
                model = track.coverUrl.takeIf { it.isNotBlank() },
                contentDescription = "歌曲封面",
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(SoftAccent),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    track.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(track.artists, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                    color = SubInk,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onFindSource,
                enabled = !busy,
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                Text("找音源", fontSize = 12.sp)
            }
        }
    }
}

private fun loadSavedDestination(context: Context): Uri? {
    val preferences = context.getSharedPreferences("music_meta_settings", Context.MODE_PRIVATE)
    val raw = preferences.getString("destination_tree_uri", null) ?: return null
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    val permissionIsPersisted = context.contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission && it.isWritePermission
    }
    if (!permissionIsPersisted) {
        preferences.edit().remove("destination_tree_uri").apply()
        return null
    }
    return uri
}

private const val fallbackUserAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
