package com.wulinlee.musicmeta

import android.annotation.SuppressLint
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import kotlin.math.abs

private const val DEFAULT_SOURCE = "https://flac.music.hi.cn/"
private val PageBackground = Color(0xFF0D1210)
private val SurfaceColor = Color(0xFF171E1A)
private val Ink = Color(0xFFE5ECE7)
private val SubInk = Color(0xFF9AA69E)
private val Accent = Color(0xFF80C9AA)
private val SoftAccent = Color(0xFF203D30)
private val Hairline = Color(0xFF2B3730)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Color(0xFF102019),
                    primaryContainer = SoftAccent,
                    onPrimaryContainer = Ink,
                    secondary = Color(0xFF9BBDAA),
                    background = PageBackground,
                    surface = SurfaceColor,
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
    remember(folderPreferences) {
        if (folderPreferences.getInt("download_defaults_version", 0) < 4) {
            folderPreferences.edit()
                .remove("destination_tree_uri")
                .putString("quality", "320k")
                .putString("source_platform", "wyy")
                .putString("naming_format", "song_title")
                .putString("folder_rule", "none")
                .putBoolean("download_lyrics", false)
                .putBoolean("write_audio_tags", true)
                .putBoolean("save_translated_lyrics", true)
                .putBoolean("save_romanization_lyrics", false)
                .putBoolean("save_word_by_word_lyrics", true)
                .putInt("download_defaults_version", 4)
                .apply()
        }
    }
    var destinationTreeUri by remember { mutableStateOf(loadSavedDestination(context)) }
    val playlistProvider = remember { PublicPlaylistProvider() }
    val metadataProvider = remember { MetadataProvider() }
    val flacProvider = remember { FlacMusicProvider() }
    val listState = rememberLazyListState()
    var currentPage by remember { mutableStateOf(1) }
    val savedPageSize = folderPreferences.getInt("songs_per_page", 25)
    var pageSize by remember { mutableStateOf(if (savedPageSize in listOf(10, 25, 50, 100)) savedPageSize else 25) }
    var jumpPageInput by remember { mutableStateOf("1") }
    var pageCache by remember { mutableStateOf<Map<String, List<MusicTrack>>>(emptyMap()) }
    var isLoadingPage by remember { mutableStateOf(false) }

    var inputMode by remember { mutableStateOf(if (folderPreferences.getString("input_mode", "playlist") == "liked") "liked" else "playlist") }
    // Keep the two sources independent: a public playlist ID is not the same as
    // the user ID used to fetch a liked-song list, and both lists remain cached.
    var publicPlaylistInput by remember { mutableStateOf(folderPreferences.getString("last_public_playlist_input", "") ?: "") }
    var likedUserIdInput by remember { mutableStateOf(folderPreferences.getString("last_liked_user_id_input", "") ?: "") }
    var publicPlaylist by remember { mutableStateOf<PublicPlaylist?>(null) }
    var likedPlaylist by remember { mutableStateOf<PublicPlaylist?>(null) }
    var publicFilterText by remember { mutableStateOf("") }
    var likedFilterText by remember { mutableStateOf("") }
    val playlistInput = if (inputMode == "liked") likedUserIdInput else publicPlaylistInput
    val playlist = if (inputMode == "liked") likedPlaylist else publicPlaylist
    val filterText = if (inputMode == "liked") likedFilterText else publicFilterText
    val setPlaylistInput: (String) -> Unit = { value ->
        if (inputMode == "liked") {
            val userId = value.filter { it.isDigit() }
            likedUserIdInput = userId
            folderPreferences.edit().putString("last_liked_user_id_input", userId).apply()
        } else {
            publicPlaylistInput = value
            folderPreferences.edit().putString("last_public_playlist_input", value).apply()
        }
    }
    val setFilterText: (String) -> Unit = { value ->
        if (inputMode == "liked") likedFilterText = value else publicFilterText = value
        currentPage = 1
        jumpPageInput = "1"
    }
    var isSyncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("输入公开歌单 ID 或链接开始。无需登录网易云账号。") }
    var lastErrorReport by remember { mutableStateOf<String?>(null) }
    val captureError: (String, Throwable) -> Unit = { stage, error ->
        lastErrorReport = makeErrorReport(context, stage, error)
    }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var flacPlatform by remember { mutableStateOf(folderPreferences.getString("source_platform", "wyy") ?: "wyy") }
    var selectedQuality by remember { mutableStateOf(folderPreferences.getString("quality", "320k") ?: "320k") }
    var showDownloadSettings by remember { mutableStateOf(false) }
    var namingFormat by remember { mutableStateOf(folderPreferences.getString("naming_format", "song_title") ?: "song_title") }
    var downloadLyrics by remember { mutableStateOf(folderPreferences.getBoolean("download_lyrics", false)) }
    var writeAudioTags by remember { mutableStateOf(folderPreferences.getBoolean("write_audio_tags", true)) }
    var saveTranslatedLyrics by remember { mutableStateOf(folderPreferences.getBoolean("save_translated_lyrics", true)) }
    var saveRomanizationLyrics by remember { mutableStateOf(folderPreferences.getBoolean("save_romanization_lyrics", false)) }
    var saveWordByWordLyrics by remember { mutableStateOf(folderPreferences.getBoolean("save_word_by_word_lyrics", true)) }
    var folderRule by remember { mutableStateOf(folderPreferences.getString("folder_rule", "none") ?: "none") }
    val setQuality: (String) -> Unit = { value ->
        selectedQuality = value
        folderPreferences.edit().putString("quality", value).apply()
    }
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
                captureError("选择保存目录", error)
            }
        }
    }
    val destinationFolderName = remember(destinationTreeUri) {
        destinationTreeUri?.let { uri ->
            runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        } ?: "Download/MusicMeta（默认）"
    }

    val busy = isSyncing || isLoadingPage || isSearchingFlac || isResolvingUrl || isDownloading

    val syncPlaylist: () -> Unit = {
        val input = playlistInput.trim()
        if (input.isBlank()) {
            status = if (inputMode == "liked") "请输入网易云用户 ID。" else "先粘贴网易云公开歌单链接或 ID。"
        } else if (busy) {
            status = "当前任务还没完成，请稍候。"
        } else {
            isSyncing = true
            status = "正在读取歌单信息……"
            scope.launch {
                try {
                    val loaded = withTimeout(28_000L) {
                        withContext(Dispatchers.IO) { playlistProvider.load(input, likedByUserId = inputMode == "liked", offset = 0, limit = 100) }
                    }
                    if (inputMode == "liked") likedPlaylist = loaded else publicPlaylist = loaded
                    selectedTrack = null
                    flacResults = emptyList()
                    currentPage = 1
                    jumpPageInput = "1"
                    pageCache = emptyMap()
                    setFilterText("")
                    scope.launch { listState.scrollToItem(0) }
                    status = "已读取歌单信息，共 " + loaded.totalTrackCount + " 首。可调整每页数量或输入页码快速跳转。"
                } catch (error: Exception) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "网络或接口暂不可用"
                    status = "同步失败：" + detail
                    captureError("同步公开歌单", error)
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
                    scope.launch {
                        val rowIndex = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == "track-" + track.id }?.index
                        if (rowIndex != null) listState.animateScrollToItem(rowIndex)
                    }
                } catch (error: Exception) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "下载站暂不可用"
                    status = "搜索失败：" + detail
                    captureError("搜索音源", error)
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
                    status = "下载完成，正在核对所选音质并写入歌曲信息……"
                    val outputFile = withContext(Dispatchers.IO) {
                        val actualExtension = sourceFile.extension.lowercase()
                        if (selectedQuality == "FLAC" && actualExtension != "flac") {
                            throw IOException("你选择的是无损 FLAC，但网站返回的文件是 " + actualExtension.uppercase() + "；应用不会偷偷转换格式，请切换音质或音源重试。")
                        }
                        if (selectedQuality != "FLAC" && actualExtension != "mp3") {
                            throw IOException("你选择的是 " + selectedQuality + " MP3，但网站返回的是 " + actualExtension.uppercase() + " 文件；应用不会转换格式，请切换音质或音源重试。")
                        }
                        AudioFileNamer.renameForTrack(sourceFile, track, namingFormat, selectedQuality)
                    }

                    var coverAvailable = false
                    var lyricSummary = "未匹配歌单歌曲"
                    var tagWriteSucceeded = true
                    var sidecarLyrics = ""
                    var sidecarWriteSucceeded = true
                    if (track != null) {
                        status = "正在获取《" + track.title + "》的封面和歌词……"
                        val assets = try {
                            metadataProvider.fetchAssets(track)
                        } catch (error: Exception) {
                            captureError("获取封面或歌词", error)
                            null
                        }
                        val safeAssets = assets ?: TrackAssets(
                            coverBytes = null,
                            coverMimeType = null,
                            originalLyrics = "",
                            translatedLyrics = "",
                            bilingualLyrics = "",
                        )
                        val normalizedWordByWordLyrics = if (saveWordByWordLyrics) {
                            LrcMerger.normalizeWordByWordToLrc(safeAssets.wordByWordLyrics)
                        } else {
                            ""
                        }
                        val usingWordByWordLyrics = normalizedWordByWordLyrics.isNotBlank()
                        // Convert YRC to line-timed LRC before writing it into ordinary audio tags.
                        val primaryLyrics = if (usingWordByWordLyrics) {
                            normalizedWordByWordLyrics
                        } else {
                            safeAssets.originalLyrics.trim()
                        }
                        val normalizedWordByWordTranslation = if (usingWordByWordLyrics) {
                            LrcMerger.normalizeWordByWordToLrc(safeAssets.wordByWordTranslatedLyrics)
                        } else {
                            ""
                        }
                        val matchingTranslation = if (usingWordByWordLyrics) {
                            normalizedWordByWordTranslation.ifBlank { safeAssets.translatedLyrics }
                        } else {
                            safeAssets.translatedLyrics
                        }
                        val translationToInclude = matchingTranslation.trim().takeIf {
                            primaryLyrics.isNotBlank() &&
                                saveTranslatedLyrics &&
                                it.isNotBlank() &&
                                shouldAppendTranslation(primaryLyrics, it)
                        }.orEmpty()
                        val baseLyrics = combineLyricsWithoutLabels(
                            original = primaryLyrics,
                            translation = translationToInclude,
                        )
                        val chosenLyrics = buildString {
                            if (baseLyrics.isNotBlank()) append(baseLyrics)
                            if (saveRomanizationLyrics && safeAssets.romanizationLyrics.isNotBlank()) {
                                if (isNotEmpty()) append("\n")
                                append(safeAssets.romanizationLyrics.trim())
                            }
                        }.trim()
                        // Store the lyric body as-is: no app-added [ti:], [ar:] or [al:] labels.
                        sidecarLyrics = chosenLyrics
                        coverAvailable = writeAudioTags && safeAssets.coverBytes != null && safeAssets.coverBytes.isNotEmpty()
                        lyricSummary = when {
                            chosenLyrics.isBlank() -> "暂无歌词"
                            downloadLyrics -> "歌词已准备保存为 .lrc"
                            saveTranslatedLyrics && matchingTranslation.isNotBlank() &&
                                shouldAppendTranslation(primaryLyrics, matchingTranslation) -> "原文及可用翻译"
                            else -> "已找到歌词"
                        }
                        if (writeAudioTags) {
                            tagWriteSucceeded = withContext(Dispatchers.IO) {
                                runCatching {
                                    AudioTagWriter.write(
                                        outputFile,
                                        track,
                                        safeAssets,
                                        lyrics = chosenLyrics,
                                        includeCover = true,
                                    )
                                }.isSuccess
                            }
                        }
                    }

                    val subfolderName = resolveDownloadFolder(folderRule, playlist, track)
                    status = "正在保存到手机音乐库……"
                    withContext(Dispatchers.IO) {
                        MediaPublisher.publish(context, outputFile, track, destinationTreeUri, subfolderName)
                        if (downloadLyrics && sidecarLyrics.isNotBlank() && track != null) {
                            sidecarWriteSucceeded = runCatching {
                                MediaPublisher.publishText(
                                    context = context,
                                    fileName = outputFile.nameWithoutExtension + ".lrc",
                                    content = sidecarLyrics,
                                    destinationTreeUri = destinationTreeUri,
                                    subfolderName = subfolderName,
                                )
                            }.isSuccess
                        }
                    }

                    val outputName = outputFile.name
                    outputFile.delete()
                    if (sourceFile.absolutePath != outputFile.absolutePath) sourceFile.delete()

                    status = buildString {
                        append("已保存到 ")
                        append(destinationFolderName)
                        if (!subfolderName.isNullOrBlank()) append("/").append(subfolderName)
                        append("：")
                        append(outputName)
                        if (track != null) {
                            append(" · ")
                            append(if (writeAudioTags && coverAvailable && tagWriteSucceeded) "已写入封面" else "未嵌入封面")
                            append(" · ")
                            append(lyricSummary)
                            if (downloadLyrics && sidecarLyrics.isNotBlank()) {
                                append(if (sidecarWriteSucceeded) " · 已保存 .lrc" else " · .lrc 保存失败")
                            }
                            if (writeAudioTags && !tagWriteSucceeded) append(" · 标签写入失败")
                        }
                    }
                    flacResults = emptyList()
                    selectedTrack = null
                } catch (error: Exception) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "未知错误"
                    status = "下载或保存失败：" + detail
                    captureError("下载或保存歌曲", error)
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

    val pageCacheKey: (String, String, Int, Int) -> String = { mode, id, size, page ->
        "$mode:$id:$size:$page"
    }
    val knownTracks = playlist?.let { current ->
        val prefix = "$inputMode:${current.id}:"
        (current.tracks + pageCache.filterKeys { it.startsWith(prefix) }.values.flatten())
            .distinctBy { it.id }
    }.orEmpty()
    val filteredKnownTracks = knownTracks.filter {
        filterText.isBlank() ||
            it.title.contains(filterText.trim(), ignoreCase = true) ||
            it.artists.contains(filterText.trim(), ignoreCase = true)
    }
    val pageItemCount = if (filterText.isBlank()) playlist?.totalTrackCount ?: 0 else filteredKnownTracks.size
    val pageCount = maxOf(1, (pageItemCount + pageSize - 1) / pageSize)
    val pullDistance = remember { mutableStateOf(0f) }
    val pullThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 84.dp.toPx() }
    val latestPagingAvailable = rememberUpdatedState(!busy && pageItemCount > pageSize)
    val latestPage = rememberUpdatedState(currentPage)
    val latestPageCount = rememberUpdatedState(pageCount)
    val latestRequestPage = rememberUpdatedState(requestPage)

    val pagePullConnection = remember(pullThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!latestPagingAvailable.value) {
                    pullDistance.value = 0f
                    return Offset.Zero
                }
                // Keep the last drag distance through the fling callback so the
                // release can still trigger a page change at the boundary.
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val deltaY = available.y
                if (deltaY == 0f) return Offset.Zero
                val page = latestPage.value
                val maxPage = latestPageCount.value
                val canNavigate = if (deltaY > 0f) page < maxPage else page > 1
                if (!canNavigate) {
                    pullDistance.value = 0f
                    return Offset.Zero
                }
                val previous = pullDistance.value
                val accum = if (previous == 0f || (previous > 0f) == (deltaY > 0f)) {
                    previous + deltaY
                } else {
                    deltaY
                }
                pullDistance.value = accum.coerceIn(-pullThresholdPx * 1.35f, pullThresholdPx * 1.35f)
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val distance = pullDistance.value
                pullDistance.value = 0f
                if (latestPagingAvailable.value && abs(distance) >= pullThresholdPx) {
                    val current = latestPage.value
                    val target = if (distance > 0f) current + 1 else current - 1
                    if (target in 1..latestPageCount.value) {
                        latestRequestPage.value(target)
                    }
                }
                return Velocity.Zero
            }
        }
    }

    val switchInputMode: (String) -> Unit = { mode ->
        if (mode != inputMode && !busy) {
            inputMode = mode
            folderPreferences.edit().putString("input_mode", mode).apply()
            currentPage = 1
            jumpPageInput = "1"
            selectedTrack = null
            flacResults = emptyList()
            status = if ((if (mode == "liked") likedPlaylist else publicPlaylist) != null) {
                if (mode == "liked") "已切换到红心歌单。" else "已切换到公开歌单。"
            } else {
                if (mode == "liked") "输入网易云用户 ID 同步红心歌单。" else "输入公开歌单链接或 ID 开始。"
            }
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    val requestPage: (Int) -> Unit = { requestedPage ->
        val current = playlist
        if (current != null && !busy) {
            val maxPage = pageCount
            val targetPage = requestedPage.coerceIn(1, maxPage)
            currentPage = targetPage
            jumpPageInput = targetPage.toString()
            selectedTrack = null
            flacResults = emptyList()

            if (filterText.isBlank()) {
                val offset = (targetPage - 1) * pageSize
                val needed = minOf(pageSize, maxOf(0, current.totalTrackCount - offset))
                val key = pageCacheKey(inputMode, current.id.toString(), pageSize, targetPage)
                val alreadyLoaded = offset + needed <= current.tracks.size
                if (!alreadyLoaded && !pageCache.containsKey(key) && needed > 0) {
                    val targetMode = inputMode
                    isLoadingPage = true
                    status = "正在加载第 " + targetPage + " 页……"
                    scope.launch {
                        try {
                            val loadedPage = withTimeout(28_000L) {
                                withContext(Dispatchers.IO) {
                                    playlistProvider.load(
                                        input = current.id.toString(),
                                        likedByUserId = false,
                                        offset = offset,
                                        limit = pageSize,
                                    )
                                }
                            }
                            val activePlaylist = if (targetMode == "liked") likedPlaylist else publicPlaylist
                            if (inputMode == targetMode && activePlaylist?.id == current.id) {
                                pageCache = pageCache + (key to loadedPage.tracks)
                                status = "第 " + targetPage + " / " + maxPage + " 页已加载。"
                            }
                        } catch (error: Exception) {
                            val detail = error.message?.takeIf { it.isNotBlank() } ?: "网络或接口暂不可用"
                            val activePlaylist = if (targetMode == "liked") likedPlaylist else publicPlaylist
                            if (inputMode == targetMode && activePlaylist?.id == current.id) {
                                status = "加载第 " + targetPage + " 页失败：" + detail + "。点击页码可重试。"
                                captureError("加载歌单页", error)
                            }
                        } finally {
                            isLoadingPage = false
                        }
                    }
                } else {
                    status = "第 " + targetPage + " / " + maxPage + " 页"
                }
            } else {
                status = "显示已加载歌曲中的筛选结果。"
            }
        }
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.semantics { contentDescription = "回到顶部" },
                    ) {
                        HomeGlyph()
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.musicmeta_cover),
                            contentDescription = "音笺应用封面",
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("音笺", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                            Text("MUSIC META", color = SubInk, fontSize = 9.sp, letterSpacing = 2.sp)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showDownloadSettings = true }, enabled = !busy) {
                        Text("下载设置", color = Accent, fontSize = 12.sp)
                    }
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
            Surface(color = SurfaceColor, shadowElevation = 3.dp) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                val report = lastErrorReport
                                if (report != null) {
                                    runCatching {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("MusicMeta error report", report))
                                    }.onSuccess {
                                        status = "错误报告已复制到剪贴板，请粘贴发送给助手。"
                                    }.onFailure { error ->
                                        status = "复制失败：" + (error.message ?: "请重试")
                                    }
                                }
                            },
                            enabled = lastErrorReport != null,
                        ) {
                            Text("复制错误报告", color = if (lastErrorReport != null) Accent else SubInk, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(pagePullConnection),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(inputMode, busy) {
                    var dragDistance = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            dragDistance += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            if (!busy && dragDistance < -72f) switchInputMode("liked")
                            else if (!busy && dragDistance > 72f) switchInputMode("playlist")
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f },
                    )
                },
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
                        "公开歌单免登录 · 保留所选音质与格式 · 封面歌词写入文件",
                        color = SubInk,
                        fontSize = 12.sp,
                    )
                }
            }

            item(key = "playlist-input") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = inputMode == "playlist",
                                onClick = { switchInputMode("playlist") },
                                enabled = !busy,
                                label = { Text("公开歌单") },
                            )
                            FilterChip(
                                selected = inputMode == "liked",
                                onClick = { switchInputMode("liked") },
                                enabled = !busy,
                                label = { Text("我的红心歌单") },
                            )
                        }
                        OutlinedTextField(
                            value = playlistInput,
                            onValueChange = setPlaylistInput,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(if (inputMode == "liked") "网易云用户 ID" else "网易云歌单链接或 ID") },
                            placeholder = { Text(if (inputMode == "liked") "输入你的网易云个人主页 ID" else "粘贴分享链接，或输入纯数字 ID") },
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
                                Text(if (inputMode == "liked") "同步我的红心歌单" else "同步公开歌单", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item(key = "destination-folder") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
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
                                        status = "已恢复默认目录：Download/MusicMeta"
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

            val currentPlaylist = playlist
            if (currentPlaylist != null) {
                item(key = "playlist-card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
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
                                Text("已载入 " + currentPlaylist.tracks.size + " / " + currentPlaylist.totalTrackCount + " 首", color = SubInk, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item(key = "track-filter") {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = {
                            setFilterText(it)
                            selectedTrack = null
                            flacResults = emptyList()
                        },
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
                                currentPlaylist.totalTrackCount.toString(),
                                color = Accent,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                item(key = "pagination-controls") {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(SurfaceColor)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                when {
                                    pageItemCount > pageSize -> "第 $currentPage / $pageCount 页"
                                    filterText.isNotBlank() -> filteredKnownTracks.size.toString() + " 首匹配歌曲"
                                    else -> pageItemCount.toString() + " 首歌曲"
                                },
                                color = SubInk,
                                fontSize = 11.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(10, 25, 50, 100).forEach { count ->
                                    FilterChip(
                                        selected = pageSize == count,
                                        onClick = {
                                            if (!busy && pageSize != count) {
                                                pageSize = count
                                                folderPreferences.edit().putInt("songs_per_page", count).apply()
                                                currentPage = 1
                                                jumpPageInput = "1"
                                                selectedTrack = null
                                                flacResults = emptyList()
                                            }
                                        },
                                        enabled = !busy,
                                        label = { Text(count.toString(), fontSize = 10.sp) },
                                    )
                                }
                            }
                        }
                        if (pageItemCount > pageSize) {
                            val pageNumbers = if (pageCount <= 7) {
                                (1..pageCount).toList()
                            } else {
                                buildList {
                                    add(1)
                                    if (currentPage > 3) add(-1)
                                    val windowStart = if (currentPage <= 3) 2 else maxOf(2, currentPage - 1)
                                    val windowEnd = if (currentPage <= 3) 5 else minOf(pageCount - 1, currentPage + 2)
                                    for (number in windowStart..windowEnd) if (number !in this) add(number)
                                    if (currentPage < pageCount - 2) add(-1)
                                    if (pageCount !in this) add(pageCount)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                IconButton(
                                    onClick = { requestPage(currentPage - 1) },
                                    enabled = !busy && currentPage > 1,
                                    modifier = Modifier.semantics { contentDescription = "上一页" },
                                ) {
                                    PageArrowGlyph(pointsLeft = true, tint = if (!busy && currentPage > 1) Ink else SubInk)
                                }
                                Row(
                                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                                ) {
                                    pageNumbers.forEach { number ->
                                        if (number == -1) {
                                            Text("…", color = SubInk, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                        } else {
                                            TextButton(
                                                onClick = { requestPage(number) },
                                                enabled = !busy && number != currentPage,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            ) {
                                                Text(
                                                    number.toString(),
                                                    color = if (number == currentPage) Accent else Ink,
                                                    fontWeight = if (number == currentPage) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { requestPage(currentPage + 1) },
                                    enabled = !busy && currentPage < pageCount,
                                    modifier = Modifier.semantics { contentDescription = "下一页" },
                                ) {
                                    PageArrowGlyph(pointsLeft = false, tint = if (!busy && currentPage < pageCount) Ink else SubInk)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                OutlinedTextField(
                                    value = jumpPageInput,
                                    onValueChange = { jumpPageInput = it.filter(Char::isDigit).take(5) },
                                    modifier = Modifier.width(76.dp),
                                    singleLine = true,
                                    placeholder = { Text("页码", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = { jumpPageInput.toIntOrNull()?.let(requestPage) }
                                    ),
                                    enabled = !busy,
                                )
                                TextButton(
                                    onClick = {
                                        val target = jumpPageInput.toIntOrNull()
                                        if (target == null) status = "请输入页码。" else requestPage(target)
                                    },
                                    enabled = !busy,
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                                ) {
                                    Text("跳", color = Accent, fontSize = 12.sp)
                                }
                            }
    
                        }
                        if (filterText.isNotBlank()) {
                            Text("筛选范围：已加载或访问过的歌曲", color = SubInk, fontSize = 10.sp)
                        }
                    }
                }
                val pageOffset = (currentPage - 1) * pageSize
                val currentCacheKey = pageCacheKey(inputMode, currentPlaylist.id.toString(), pageSize, currentPage)
                val loadedPageTracks = currentPlaylist.tracks.drop(pageOffset).take(pageSize)
                val requiredPageSize = minOf(pageSize, maxOf(0, currentPlaylist.totalTrackCount - pageOffset))
                val pageTracks = if (filterText.isNotBlank()) {
                    filteredKnownTracks.drop(pageOffset).take(pageSize)
                } else if (loadedPageTracks.size >= requiredPageSize) {
                    loadedPageTracks
                } else {
                    pageCache[currentCacheKey].orEmpty()
                }
                val visibleTracks = pageTracks

                if (isLoadingPage && visibleTracks.isEmpty()) {
                    item(key = "page-loading") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceColor,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                                Spacer(Modifier.size(9.dp))
                                Text("正在加载第 $currentPage 页…", color = SubInk, fontSize = 12.sp)
                            }
                        }
                    }
                } else if (visibleTracks.isEmpty()) {
                    item(key = "page-empty") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = SurfaceColor,
                        ) {
                            Text(
                                if (filterText.isBlank()) "这一页暂时没有歌曲，点一下页码可重试。" else "没有找到匹配的已加载歌曲。",
                                color = SubInk,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                } else {
                items(visibleTracks, key = { "track-" + it.id }) { track ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TrackRow(
                            track = track,
                            busy = busy,
                            onFindSource = { searchTrackSource(track) },
                        )
                        if (selectedTrack?.id == track.id) {
                    when {
                        isSearchingFlac -> {
                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
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
                                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
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
                                                        folderPreferences.edit().putString("source_platform", "kuwo").apply()
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
                                                        folderPreferences.edit().putString("source_platform", "wyy").apply()
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
                                            onClick = { setQuality("128k") },
                                            enabled = !isDownloading && !isResolvingUrl,
                                            label = { Text("标准 128k") },
                                        )
                                        FilterChip(
                                            selected = selectedQuality == "320k",
                                            onClick = { setQuality("320k") },
                                            enabled = !isDownloading && !isResolvingUrl,
                                            label = { Text("高品质 320k") },
                                        )
                                        FilterChip(
                                            selected = selectedQuality == "FLAC",
                                            onClick = { setQuality("FLAC") },
                                            enabled = !isDownloading && !isResolvingUrl,
                                            label = { Text("无损 FLAC") },
                                        )
                                    }
                                    Text(
                                        if (selectedQuality == "FLAC") "保存无损 FLAC 文件" else "保存 MP3 · " + selectedQuality,
                                        color = SubInk,
                                        fontSize = 11.sp,
                                    )
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                            .heightIn(max = 280.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
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
                                                if (song.platform.startsWith("gd:")) {
                                                    Text("GD Studio 兜底音源", color = Accent, fontSize = 10.sp)
                                                }
                                            }
                                            Button(
                                                onClick = {
                                                    if (!isResolvingUrl && !isDownloading) {
                                                        isResolvingUrl = true
                                                        status = "正在解析《" + song.title + "》的下载地址……"
                                                        scope.launch {
                                                            try {
                                                                val cookie = CookieManager.getInstance().getCookie(DEFAULT_SOURCE)
                                                                val (directUrl, usedGdStudioFallback) = withContext(Dispatchers.IO) {
                                                                    flacProvider.resolveDownloadUrlWithFallback(
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
                                                                if (usedGdStudioFallback) {
                                                                    status = "主音源不可用，本次使用 GD Studio 兜底下载地址。"
                                                                }
                                                                isResolvingUrl = false
                                                                latestDownloadAndSave(
                                                                    directUrl,
                                                                    fallbackUserAgent,
                                                                    null,
                                                                    if (selectedQuality == "FLAC") "audio/flac" else "audio/mpeg",
                                                                    DEFAULT_SOURCE,
                                                                    cookie,
                                                                    selectedTrack,
                                                                )
                                                            } catch (error: Exception) {
                                                                val detail = error.message?.takeIf { it.isNotBlank() } ?: "下载站暂不可用"
                                                                status = "解析下载地址失败：" + detail
                                                                captureError("解析下载地址", error)
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
                        }
                    }
                }
                }
            } else {
                item(key = "empty-playlist") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
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
        if (abs(pullDistance.value) > 2f && latestPagingAvailable.value) {
            val pullingToPrevious = pullDistance.value < 0f
            val pullProgress = (abs(pullDistance.value) / pullThresholdPx).coerceIn(0f, 1f)
            Surface(
                modifier = Modifier
                    .align(if (pullingToPrevious) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = SurfaceColor,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Hairline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PullArrowGlyph(pointsUp = !pullingToPrevious, tint = Accent)
                    Column(
                        modifier = Modifier.width(180.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            when {
                                pullProgress >= 1f && pullingToPrevious -> "松开返回上一页"
                                pullProgress >= 1f -> "松开进入下一页"
                                pullingToPrevious -> "继续下拉，返回上一页"
                                else -> "继续上拉，进入下一页"
                            },
                            color = Ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        LinearProgressIndicator(
                            progress = pullProgress,
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                            color = Accent,
                            trackColor = SoftAccent,
                        )
                    }
                }
            }
        }
        }
    }

    if (showDownloadSettings) {
        AlertDialog(
            onDismissRequest = { showDownloadSettings = false },
            title = { Text("下载设置", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("默认音源", fontWeight = FontWeight.SemiBold, color = Ink)
                    listOf("wyy" to "网易云", "kuwo" to "酷我").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = flacPlatform == value,
                                onClick = {
                                    flacPlatform = value
                                    folderPreferences.edit().putString("source_platform", value).apply()
                                },
                            )
                            Text(label, color = Ink, fontSize = 13.sp)
                        }
                    }
                    Text("默认音质", fontWeight = FontWeight.SemiBold, color = Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedQuality == "128k", onClick = { setQuality("128k") }, label = { Text("MP3 128k") })
                        FilterChip(selected = selectedQuality == "320k", onClick = { setQuality("320k") }, label = { Text("MP3 320k") })
                        FilterChip(selected = selectedQuality == "FLAC", onClick = { setQuality("FLAC") }, label = { Text("FLAC") })
                    }
                    Text("下载目录", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(destinationFolderName, color = SubInk, fontSize = 12.sp)
                    OutlinedButton(
                        onClick = { destinationPicker.launch(destinationTreeUri) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("选择下载目录") }
                    Text("文件命名", fontWeight = FontWeight.SemiBold, color = Ink)
                    listOf(
                        "song_title" to "歌曲名（默认）",
                        "song_artist" to "歌曲名 - 艺术家",
                        "artist_song" to "艺术家 - 歌曲名",
                        "song_artist_quality" to "歌曲名 - 艺术家 [音质]",
                        "artist_song_quality" to "艺术家 - 歌曲名 [音质]",
                    ).forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = namingFormat == value,
                                onClick = {
                                    namingFormat = value
                                    folderPreferences.edit().putString("naming_format", value).apply()
                                },
                            )
                            Text(label, color = Ink, fontSize = 13.sp)
                        }
                    }
                    Text("下载文件夹规则", fontWeight = FontWeight.SemiBold, color = Ink)
                    listOf(
                        "none" to "不使用",
                        "playlist" to "按歌单名称",
                        "artist" to "按歌手名称",
                        "album" to "按专辑名称",
                        "artist_album" to "歌手 - 专辑",
                    ).forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = folderRule == value,
                                onClick = {
                                    folderRule = value
                                    folderPreferences.edit().putString("folder_rule", value).apply()
                                },
                            )
                            Text(label, color = Ink, fontSize = 13.sp)
                        }
                    }
                    DownloadSettingSwitchRow(
                        title = "下载歌词",
                        summary = "歌曲旁边额外保存 .lrc 歌词文件",
                        checked = downloadLyrics,
                        onCheckedChange = {
                            downloadLyrics = it
                            folderPreferences.edit().putBoolean("download_lyrics", it).apply()
                        },
                    )
                    DownloadSettingSwitchRow(
                        title = "写入音频标签",
                        summary = "写入歌曲名、歌手、专辑与可用封面",
                        checked = writeAudioTags,
                        onCheckedChange = {
                            writeAudioTags = it
                            folderPreferences.edit().putBoolean("write_audio_tags", it).apply()
                        },
                    )
                    DownloadSettingSwitchRow(
                        title = "保存歌词翻译",
                        summary = "歌词有翻译时一并保存",
                        checked = saveTranslatedLyrics,
                        onCheckedChange = {
                            saveTranslatedLyrics = it
                            folderPreferences.edit().putBoolean("save_translated_lyrics", it).apply()
                        },
                    )
                    DownloadSettingSwitchRow(
                        title = "保存歌词罗马音",
                        summary = "歌词接口提供罗马音时一并保存",
                        checked = saveRomanizationLyrics,
                        onCheckedChange = {
                            saveRomanizationLyrics = it
                            folderPreferences.edit().putBoolean("save_romanization_lyrics", it).apply()
                        },
                    )
                    DownloadSettingSwitchRow(
                        title = "保存逐字歌词",
                        summary = "优先使用逐字歌词；没有时自动回退普通歌词",
                        checked = saveWordByWordLyrics,
                        onCheckedChange = {
                            saveWordByWordLyrics = it
                            folderPreferences.edit().putBoolean("save_word_by_word_lyrics", it).apply()
                        },
                    )
                    Text(
                        "说明：当前音源只提供 MP3 / FLAC 选项，未提供 Hachimi 的 AI 增强音质筛选，因此不会显示一个无效开关。",
                        color = SubInk,
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadSettings = false }) {
                    Text("完成", color = Accent)
                }
            },
            containerColor = SurfaceColor,
        )
    }

    if (verificationOpen) {
        Dialog(onDismissRequest = { verificationOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(560.dp),
                shape = RoundedCornerShape(24.dp),
                color = SurfaceColor,
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
private fun DownloadSettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Ink)
            Text(summary, color = SubInk, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun resolveDownloadFolder(rule: String, playlist: PublicPlaylist?, track: MusicTrack?): String? {
    val value = when (rule) {
        "playlist" -> playlist?.name
        "artist" -> track?.artists
        "album" -> track?.album
        "artist_album" -> listOfNotNull(
            track?.artists?.takeIf { it.isNotBlank() },
            track?.album?.takeIf { it.isNotBlank() },
        ).joinToString(" - ").ifBlank { null }
        else -> null
    } ?: return null
    return AudioFileNamer.sanitizeComponent(value).takeIf { it.isNotBlank() }
}

private fun shouldAppendTranslation(original: String, translated: String): Boolean {
    if (translated.isBlank()) return false
    if (original.isBlank()) return false
    val cleaned = original.replace(Regex("\\[[^]]*]"), " ")
    val letters = cleaned.count { it.isLetter() }
    if (letters == 0) return true
    val han = cleaned.count { it in '\u4E00'..'\u9FFF' }
    val kana = cleaned.count { it in '\u3040'..'\u30FF' }
    val likelyChinese = han >= 8 && han.toDouble() / letters >= 0.28 &&
        kana.toDouble() / letters < 0.035
    return !likelyChinese
}

private fun combineLyricsWithoutLabels(
    original: String,
    translation: String,
): String {
    val source = original.trim()
    val translated = translation.trim()
    if (source.isBlank()) return ""
    if (translated.isBlank()) return source

    val lrcTimestamp = Regex("^\\s*\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?]")
    val originalIsTimedLrc = source.lineSequence().any { lrcTimestamp.containsMatchIn(it) }
    val translationIsTimedLrc = translated.lineSequence().any { lrcTimestamp.containsMatchIn(it) }

    if (originalIsTimedLrc && translationIsTimedLrc) {
        return LrcMerger.merge(source, translated)
    }
    return listOf(source, translated).filter { it.isNotBlank() }.joinToString("\n")
}

@Composable
private fun PullArrowGlyph(pointsUp: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val centerX = size.width / 2f
        val tipY = size.height * if (pointsUp) 0.20f else 0.80f
        val baseY = size.height * if (pointsUp) 0.75f else 0.25f
        drawLine(color = tint, start = Offset(centerX, baseY), end = Offset(centerX, tipY), strokeWidth = stroke, cap = StrokeCap.Round)
        val headY = size.height * if (pointsUp) 0.43f else 0.57f
        val leftX = size.width * 0.27f
        val rightX = size.width * 0.73f
        drawLine(color = tint, start = Offset(leftX, headY), end = Offset(centerX, tipY), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(centerX, tipY), end = Offset(rightX, headY), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun PageArrowGlyph(pointsLeft: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val centerY = size.height / 2f
        val tipX = size.width * if (pointsLeft) 0.24f else 0.76f
        val shaftStartX = size.width * if (pointsLeft) 0.72f else 0.28f
        val shaftEndX = size.width * if (pointsLeft) 0.28f else 0.72f
        drawLine(
            color = tint,
            start = Offset(shaftStartX, centerY),
            end = Offset(shaftEndX, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val headX = size.width * if (pointsLeft) 0.48f else 0.52f
        drawLine(
            color = tint,
            start = Offset(headX, size.height * 0.25f),
            end = Offset(tipX, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(tipX, centerY),
            end = Offset(headX, size.height * 0.75f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun HomeGlyph() {
    Canvas(modifier = Modifier.size(23.dp)) {
        val stroke = 2.dp.toPx()
        val left = size.width * 0.17f
        val right = size.width * 0.83f
        val top = size.height * 0.20f
        val wallTop = size.height * 0.47f
        val base = size.height * 0.84f
        val middle = size.width / 2f
        drawLine(Accent, Offset(left, wallTop), Offset(middle, top), stroke, cap = StrokeCap.Round)
        drawLine(Accent, Offset(middle, top), Offset(right, wallTop), stroke, cap = StrokeCap.Round)
        drawLine(Accent, Offset(left + size.width * 0.08f, wallTop), Offset(left + size.width * 0.08f, base), stroke, cap = StrokeCap.Round)
        drawLine(Accent, Offset(right - size.width * 0.08f, wallTop), Offset(right - size.width * 0.08f, base), stroke, cap = StrokeCap.Round)
        drawLine(Accent, Offset(left + size.width * 0.08f, base), Offset(right - size.width * 0.08f, base), stroke, cap = StrokeCap.Round)
        drawLine(Accent, Offset(middle, base), Offset(middle, size.height * 0.61f), stroke, cap = StrokeCap.Round)
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
        color = SurfaceColor,
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


private fun makeErrorReport(context: Context, stage: String, error: Throwable): String {
    val appVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")
    fun redact(value: String): String =
        value.replace(Regex("https?://\\S+"), "<URL已隐藏>")
            .replace(Regex("(?i)(cookie|authorization|token)\\s*[:=]\\s*[^\\s,;]+"), "<敏感字段已隐藏>")
            .take(500)

    val errorChain = generateSequence(error) { it.cause }
        .take(4)
        .joinToString("\n") { cause ->
            cause.javaClass.name + if (cause.message.isNullOrBlank()) "" else ": " + redact(cause.message!!)
        }
    val frames = error.stackTrace.take(8).joinToString("\n") { frame ->
        "  at " + frame.className + "." + frame.methodName +
            "(" + (frame.fileName ?: "unknown") + ":" + frame.lineNumber + ")"
    }
    return buildString {
        appendLine("音笺 MusicMeta 错误报告")
        appendLine("错误编号: MM-" + System.currentTimeMillis())
        appendLine("阶段: " + stage)
        appendLine("应用版本: " + appVersion)
        appendLine("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        appendLine("异常链:")
        appendLine(errorChain)
        appendLine("调用位置:")
        appendLine(frames)
        appendLine("隐私说明: 未附加歌单输入、下载链接或 Cookie；消息中的网址已隐藏。")
    }
}

private const val fallbackUserAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
