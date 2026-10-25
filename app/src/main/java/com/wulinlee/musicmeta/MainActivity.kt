package com.wulinlee.musicmeta

import android.annotation.SuppressLint
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

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
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var jumpFieldFocused by remember { mutableStateOf(false) }
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
    val publicListState = rememberLazyListState()
    val likedListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val savedInputMode = folderPreferences.getString("input_mode", "playlist") ?: "playlist"
    var inputMode by remember { mutableStateOf(savedInputMode.takeIf { it in setOf("playlist", "liked", "search") } ?: "playlist") }
    val listState = when (inputMode) {
        "liked" -> likedListState
        "search" -> searchListState
        else -> publicListState
    }
    var publicCurrentPage by remember { mutableStateOf(1) }
    var likedCurrentPage by remember { mutableStateOf(1) }
    val currentPage = if (inputMode == "liked") likedCurrentPage else publicCurrentPage
    val setCurrentPage: (Int) -> Unit = { value ->
        if (inputMode == "liked") likedCurrentPage = value else publicCurrentPage = value
    }
    val savedPageSize = folderPreferences.getInt("songs_per_page", 25)
    var pageSize by remember { mutableStateOf(if (savedPageSize in listOf(25, 50, 100)) savedPageSize else 25) }
    var jumpPageInput by remember { mutableStateOf("1") }
    var pageCache by remember { mutableStateOf<Map<String, List<MusicTrack>>>(emptyMap()) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var isFilteringTracks by remember { mutableStateOf(false) }
    // Keep the two sources independent: a public playlist ID is not the same as
    // the user ID used to fetch a liked-song list, and both lists remain cached.
    var publicPlaylistInput by remember { mutableStateOf(folderPreferences.getString("last_public_playlist_input", "") ?: "") }
    var likedUserIdInput by remember { mutableStateOf(folderPreferences.getString("last_liked_user_id_input", "") ?: "") }
    var publicPlaylist by remember { mutableStateOf<PublicPlaylist?>(null) }
    var likedPlaylist by remember { mutableStateOf<PublicPlaylist?>(null) }
    var publicFilterText by remember { mutableStateOf("") }
    var likedFilterText by remember { mutableStateOf("") }
    val playlistInput = if (inputMode == "liked") likedUserIdInput else publicPlaylistInput
    val playlist = when (inputMode) {
        "liked" -> likedPlaylist
        "search" -> null
        else -> publicPlaylist
    }
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
        setCurrentPage(1)
        jumpPageInput = "1"
    }
    var isSyncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("输入公开歌单 ID 或链接开始。无需登录网易云账号。") }
    var lastErrorReport by remember { mutableStateOf<String?>(null) }
    val captureError: (String, Throwable) -> Unit = { stage, error ->
        lastErrorReport = makeErrorReport(context, stage, error)
    }
    val copyErrorReport: () -> Unit = {
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
    }
    val copySongAndArtist: (String, String) -> Unit = { title, artist ->
        val textToCopy = listOf(title.trim(), artist.trim())
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        if (textToCopy.isNotBlank()) {
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("歌曲和艺人", textToCopy))
            }.onSuccess {
                status = "已复制：" + textToCopy
                Toast.makeText(context, "已复制歌曲和艺人", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                status = "复制失败：" + (error.message ?: "请重试")
                Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
            }
        }
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
    var manualSearchQuery by remember { mutableStateOf("") }
    var manualSourceSearchActive by remember { mutableStateOf(false) }
    var isSearchingFlac by remember { mutableStateOf(false) }
    var isResolvingUrl by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var verificationOpen by remember { mutableStateOf(false) }
    var previewSong by remember { mutableStateOf<FlacSiteSong?>(null) }
    var previewQueue by remember { mutableStateOf<List<FlacSiteSong>>(emptyList()) }
    var previewCoverUrl by remember { mutableStateOf("") }
    var previewLyrics by remember { mutableStateOf("") }
    var previewTranslatedLyrics by remember { mutableStateOf("") }
    var isPreviewLoading by remember { mutableStateOf(false) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var isPreviewLyricsLoading by remember { mutableStateOf(false) }
    var showPreviewDetails by remember { mutableStateOf(false) }
    var showTranslatedPreviewLyric by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableStateOf(0) }
    var previewDurationMs by remember { mutableStateOf(0) }
    var miniPlayerOffset by remember { mutableStateOf(Offset.Zero) }
    val previewPlayer = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { previewPlayer.value?.release() }
            previewPlayer.value = null
        }
    }

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
        } ?: "下载/-07-MusicMeta（默认）"
    }

    val busy = isSyncing || isLoadingPage || isSearchingFlac || isResolvingUrl || isDownloading

    val syncPlaylist: () -> Unit = {
        focusManager.clearFocus()
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
                    setCurrentPage(1)
                    jumpPageInput = "1"
                    manualSourceSearchActive = false
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
        focusManager.clearFocus()
        if (busy) {
            status = "当前任务还没完成，请稍候。"
        } else {
            selectedTrack = track
            manualSourceSearchActive = false
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

    val searchSourceKeyword: () -> Unit = {
        focusManager.clearFocus()
        val keyword = manualSearchQuery.trim()
        if (keyword.isBlank()) {
            status = "请输入要搜索的歌名或歌手。"
        } else if (busy) {
            status = "当前任务还没完成，请稍候。"
        } else {
            selectedTrack = null
            manualSourceSearchActive = true
            flacResults = emptyList()
            isSearchingFlac = true
            status = "正在搜索音源《" + keyword + "》……"
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
                        "没有找到匹配音源。可以切换音源平台或检查关键词后重试。"
                    } else {
                        "找到 " + flacResults.size + " 个候选版本，请核对歌手和专辑后下载。"
                    }
                } catch (error: Exception) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "下载站暂不可用"
                    status = "搜索失败：" + detail
                    captureError("直接搜索音源", error)
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
        String, String, String?, String?, String, String?, MusicTrack?, FlacSiteSong?
    ) -> Unit = { url, userAgent, disposition, mimeType, referer, cookie, track, sourceSong ->
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
                var processedFile: File? = null
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
                    processedFile = outputFile

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
                        val metadataAssets = assets ?: TrackAssets(
                            coverBytes = null,
                            coverMimeType = null,
                            originalLyrics = "",
                            translatedLyrics = "",
                            bilingualLyrics = "",
                        )
                        // Prefer lyrics matched to the exact selected source result; retain metadata-provider
                        // lyrics as fallback if this source does not expose its own lyrics.
                        val sourceLyrics = sourceSong?.let { candidate ->
                            runCatching {
                                flacProvider.fetchLyrics(
                                    song = candidate,
                                    cookie = cookie,
                                    userAgent = fallbackUserAgent,
                                )
                            }.getOrDefault(PreviewLyrics())
                        } ?: PreviewLyrics()
                        val sourceTranslation = sourceLyrics.translated.ifBlank { metadataAssets.translatedLyrics }
                        val safeAssets = if (sourceLyrics.original.isNotBlank()) {
                            metadataAssets.copy(
                                originalLyrics = sourceLyrics.original,
                                translatedLyrics = sourceTranslation,
                                bilingualLyrics = LrcMerger.merge(sourceLyrics.original, sourceTranslation),
                            )
                        } else {
                            metadataAssets
                        }
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
                    // Always clean cache artifacts, including failures during tagging or publishing.
                    processedFile?.delete()
                    downloaded?.delete()
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
            null,
        )
    }
    val latestWebsiteDownload by rememberUpdatedState(onWebsiteDownload)

    val resolveSourceCoverUrl: suspend (FlacSiteSong) -> String = { song ->
        val sourceCover = runCatching { flacProvider.resolveCoverUrl(song) }.getOrDefault("")
        if (sourceCover.isNotBlank()) sourceCover
        else runCatching { metadataProvider.resolveSourceCoverUrl(song) }.getOrDefault("")
    }
    val sourceSongKey: (FlacSiteSong) -> String = { song -> song.platform + ":" + song.id }
    val stopPreview: () -> Unit = {
        val player = previewPlayer.value
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        previewPlayer.value = null
        previewSong = null
        previewQueue = emptyList()
        previewCoverUrl = ""
        previewLyrics = ""
        previewTranslatedLyrics = ""
        isPreviewLoading = false
        isPreviewPlaying = false
        isPreviewLyricsLoading = false
        showPreviewDetails = false
        showTranslatedPreviewLyric = false
        previewPositionMs = 0
        previewDurationMs = 0
        miniPlayerOffset = Offset.Zero
    }
    val playPreview: (FlacSiteSong) -> Unit = { song ->
        val songKey = sourceSongKey(song)
        if (previewSong?.let(sourceSongKey) == songKey && previewPlayer.value != null) {
            val player = previewPlayer.value
            if (player != null) {
                runCatching {
                    if (player.isPlaying) {
                        player.pause()
                        isPreviewPlaying = false
                        previewPositionMs = runCatching { player.currentPosition }.getOrDefault(previewPositionMs)
                        status = "播放已暂停。"
                    } else {
                        player.start()
                        isPreviewPlaying = true
                        previewPositionMs = runCatching { player.currentPosition }.getOrDefault(previewPositionMs)
                        previewDurationMs = runCatching { player.duration }.getOrDefault(previewDurationMs).coerceAtLeast(0)
                        status = "正在播放《" + song.title + "》。"
                    }
                }.onFailure { error ->
                    status = "试听控制失败：" + (error.message ?: "请重试")
                }
            }
        } else if (!isPreviewLoading) {
            val oldPlayer = previewPlayer.value
            runCatching { oldPlayer?.stop() }
            runCatching { oldPlayer?.release() }
            previewPlayer.value = null
            previewSong = song
            previewQueue = when {
                flacResults.any { sourceSongKey(it) == songKey } -> flacResults.toList()
                previewQueue.any { sourceSongKey(it) == songKey } -> previewQueue
                else -> listOf(song)
            }
            previewCoverUrl = song.coverUrl
            previewLyrics = ""
            previewTranslatedLyrics = ""
            showTranslatedPreviewLyric = false
            previewPositionMs = 0
            previewDurationMs = 0
            showPreviewDetails = false
            miniPlayerOffset = Offset.Zero
            isPreviewPlaying = false
            isPreviewLoading = true
            isPreviewLyricsLoading = true
            status = "正在准备播放《" + song.title + "》的试听……"
            scope.launch {
                var newPlayer: MediaPlayer? = null
                try {
                    val cookie = CookieManager.getInstance().getCookie(DEFAULT_SOURCE)
                    val (audioUrl, usedGdStudio) = withContext(Dispatchers.IO) {
                        flacProvider.resolveDownloadUrlWithFallback(
                            song = song,
                            format = "mp3",
                            bitrate = "320",
                            cookie = cookie,
                            userAgent = fallbackUserAgent,
                        )
                    }
                    if (previewSong?.let(sourceSongKey) != songKey) return@launch

                    val referer = if (song.platform.startsWith("gd:")) {
                        "https://music.gdstudio.xyz/"
                    } else {
                        DEFAULT_SOURCE
                    }
                    val headers = mutableMapOf(
                        "User-Agent" to fallbackUserAgent,
                        "Referer" to referer,
                    )
                    if (!cookie.isNullOrBlank() && !song.platform.startsWith("gd:")) {
                        headers["Cookie"] = cookie
                    }

                    val player = MediaPlayer()
                    newPlayer = player
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    player.setOnPreparedListener { prepared ->
                        if (previewSong?.let(sourceSongKey) == songKey) {
                            previewDurationMs = runCatching { prepared.duration }.getOrDefault(0).coerceAtLeast(0)
                            previewPositionMs = runCatching { prepared.currentPosition }.getOrDefault(0).coerceAtLeast(0)
                            runCatching { prepared.start() }
                                .onSuccess {
                                    isPreviewPlaying = true
                                    isPreviewLoading = false
                                    status = if (usedGdStudio) {
                                        "正在通过 GD Studio 试听《" + song.title + "》。"
                                    } else {
                                        "正在播放《" + song.title + "》。"
                                    }
                                }
                                .onFailure { error ->
                                    isPreviewLoading = false
                                    status = "无法开始试听：" + (error.message ?: "音频暂不可用")
                                    captureError("开始试听", error)
                                }
                        } else {
                            runCatching { prepared.release() }
                        }
                    }
                    player.setOnCompletionListener { completed ->
                        isPreviewPlaying = false
                        isPreviewLoading = false
                        previewPositionMs = previewDurationMs
                        status = "播放结束：《" + song.title + "》。"
                        runCatching { completed.release() }
                        if (previewPlayer.value === completed) previewPlayer.value = null
                    }
                    player.setOnErrorListener { failed, what, extra ->
                        isPreviewPlaying = false
                        isPreviewLoading = false
                        status = "试听失败，音源可能不支持在线播放。"
                        captureError("试听音源", IOException("MediaPlayer error $what/$extra"))
                        runCatching { failed.release() }
                        if (previewPlayer.value === failed) previewPlayer.value = null
                        true
                    }
                    player.setDataSource(context, Uri.parse(audioUrl), headers)
                    previewPlayer.value = player
                    player.prepareAsync()

                    scope.launch {
                        val cover = withContext(Dispatchers.IO) { resolveSourceCoverUrl(song) }
                        if (previewSong?.let(sourceSongKey) == songKey && cover.isNotBlank()) {
                            previewCoverUrl = cover
                        }
                    }
                    scope.launch {
                        val lyrics = withContext(Dispatchers.IO) {
                            var tracks = runCatching {
                                flacProvider.fetchLyrics(song, cookie, fallbackUserAgent)
                            }.getOrDefault(PreviewLyrics())
                            if (tracks.original.isBlank()) {
                                tracks = tracks.copy(
                                    original = runCatching { metadataProvider.resolveSourceLyrics(song) }
                                        .getOrDefault("").trim(),
                                )
                            }
                            tracks
                        }
                        if (previewSong?.let(sourceSongKey) == songKey) {
                            previewLyrics = lyrics.original.trim()
                            previewTranslatedLyrics = lyrics.translated.trim()
                            showTranslatedPreviewLyric = false
                            isPreviewLyricsLoading = false
                        }
                    }
                } catch (error: Exception) {
                    runCatching { newPlayer?.release() }
                    if (previewPlayer.value === newPlayer) previewPlayer.value = null
                    if (previewSong?.let(sourceSongKey) == songKey) {
                        isPreviewLoading = false
                        isPreviewLyricsLoading = false
                        status = "试听失败：" + (error.message?.takeIf { it.isNotBlank() } ?: "音源暂不可用")
                        captureError("解析试听音源", error)
                    }
                }
            }
        }
    }

    val playAdjacentPreview: (Int) -> Unit = { direction ->
        val current = previewSong
        val currentIndex = if (current == null) -1 else
            previewQueue.indexOfFirst { sourceSongKey(it) == sourceSongKey(current) }
        val targetIndex = currentIndex + direction
        if (currentIndex >= 0 && targetIndex in previewQueue.indices) {
            playPreview(previewQueue[targetIndex])
        } else {
            status = if (direction < 0) "已经是播放队列第一首。" else "已经是播放队列最后一首。"
        }
    }

    LaunchedEffect(previewSong, isPreviewPlaying) {
        while (isPreviewPlaying) {
            val player = previewPlayer.value
            if (player != null) {
                runCatching {
                    val duration = player.duration
                    if (duration > 0) previewDurationMs = duration
                    previewPositionMs = player.currentPosition.coerceAtLeast(0)
                }
            }
            delay(350)
        }
    }

    val downloadManualSearchResult: (FlacSiteSong) -> Unit = { song ->
        if (isResolvingUrl || isDownloading) {
            status = "当前下载任务还没完成，请稍候。"
        } else {
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
                    manualSourceSearchActive = false
                    latestDownloadAndSave(
                        directUrl,
                        fallbackUserAgent,
                        null,
                        if (selectedQuality == "FLAC") "audio/flac" else "audio/mpeg",
                        DEFAULT_SOURCE,
                        cookie,
                        selectedTrack ?: metadataTrackForSource(song),
                        song,
                    )
                } catch (error: Exception) {
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "下载站暂不可用"
                    status = "解析下载地址失败：" + detail
                    captureError("解析直接搜索音源", error)
                    if (detail.contains("验证") || detail.contains("468")) {
                        verificationOpen = true
                    }
                } finally {
                    isResolvingUrl = false
                }
            }
        }
    }

    val pageCacheKey: (String, String, Int, Int) -> String = { mode, id, size, page ->
        "$mode:$id:$size:$page"
    }
    val knownTracks = playlist?.let { current ->
        val prefix = "$inputMode:${current.id}:"
        val cachedTracks = pageCache.entries
            .filter { it.key.startsWith(prefix) }
            .sortedBy { entry ->
                val parts = entry.key.split(":")
                val size = parts.getOrNull(2)?.toIntOrNull() ?: 100
                val page = parts.getOrNull(3)?.toIntOrNull() ?: 1
                (page - 1) * size
            }
            .flatMap { it.value }
        (current.tracks + cachedTracks).distinctBy { it.id }
    }.orEmpty()
    val filteredKnownTracks = knownTracks.filter {
        filterText.isBlank() ||
            it.title.contains(filterText.trim(), ignoreCase = true) ||
            it.artists.contains(filterText.trim(), ignoreCase = true) ||
            it.album.contains(filterText.trim(), ignoreCase = true)
    }
    val pageItemCount = if (filterText.isBlank()) playlist?.totalTrackCount ?: 0 else filteredKnownTracks.size
    val pageCount = maxOf(1, (pageItemCount + pageSize - 1) / pageSize)
    val switchInputMode: (String) -> Unit = { mode ->
        if (mode in setOf("playlist", "liked", "search") && mode != inputMode && !busy) {
            focusManager.clearFocus()
            inputMode = mode
            folderPreferences.edit().putString("input_mode", mode).apply()
            jumpPageInput = (if (mode == "liked") likedCurrentPage else publicCurrentPage).toString()
            selectedTrack = null
            flacResults = emptyList()
            manualSourceSearchActive = false
            status = when (mode) {
                "search" -> "直接搜歌：输入歌名或歌手搜索音源。"
                "liked" -> if (likedPlaylist != null) "已切换到红心歌单。" else "输入网易云用户 ID 同步红心歌单。"
                else -> if (publicPlaylist != null) "已切换到公开歌单。" else "输入公开歌单链接或 ID 开始。"
            }
        }
    }

    val requestPage: (Int) -> Unit = { requestedPage ->
        focusManager.clearFocus()
        val current = playlist
        if (current != null && !busy) {
            val maxPage = pageCount
            val targetPage = requestedPage.coerceIn(1, maxPage)
            val pageChanged = targetPage != currentPage
            setCurrentPage(targetPage)
            jumpPageInput = targetPage.toString()
            if (pageChanged) scope.launch { listState.animateScrollToItem(6) }
            selectedTrack = null
            flacResults = emptyList()

            if (filterText.isBlank()) {
                val offset = (targetPage - 1) * pageSize
                val needed = minOf(pageSize, maxOf(0, current.totalTrackCount - offset))
                val key = pageCacheKey(inputMode, current.id.toString(), pageSize, targetPage)
                val alreadyLoaded = offset + needed <= current.tracks.size || pageCache.containsKey(key)
                if (!alreadyLoaded && needed > 0) {
                    val targetMode = inputMode
                    val batchOffset = (offset / 100) * 100
                    isLoadingPage = true
                    status = "正在加载第 " + targetPage + " 页……"
                    scope.launch {
                        try {
                            val loadedPage = withTimeout(28_000L) {
                                withContext(Dispatchers.IO) {
                                    playlistProvider.load(
                                        input = current.id.toString(),
                                        likedByUserId = false,
                                        offset = batchOffset,
                                        limit = 100,
                                    )
                                }
                            }
                            val activePlaylist = if (targetMode == "liked") likedPlaylist else publicPlaylist
                            if (inputMode == targetMode && activePlaylist?.id == current.id) {
                                val pageEntries = loadedPage.tracks.chunked(pageSize).mapIndexed { index, tracks ->
                                    val pageNumber = batchOffset / pageSize + index + 1
                                    pageCacheKey(targetMode, current.id.toString(), pageSize, pageNumber) to tracks
                                }.toMap()
                                pageCache = pageCache + pageEntries
                                status = "第 " + targetPage + " 页已加载（每批读取100首）。"
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
                    status = "已切换到第 " + targetPage + " 页。"
                }
            } else {
                status = "正在显示整份歌单的筛选结果。"
            }
        }
    }

    LaunchedEffect(pageSize, inputMode, playlist?.id) {
        val current = playlist
        if (current != null && filterText.isBlank() && !busy) {
            requestPage(currentPage)
        }
    }

    LaunchedEffect(inputMode, playlist?.id, filterText) {
        val current = playlist
        val query = filterText.trim()
        if (current == null || query.isBlank() || inputMode !in setOf("playlist", "liked")) {
            isFilteringTracks = false
            return@LaunchedEffect
        }

        delay(350)
        val targetMode = inputMode
        isFilteringTracks = true
        try {
            val batchCount = (current.totalTrackCount + 99) / 100
            for (batch in 1..batchCount) {
                val offset = (batch - 1) * 100
                val expectedCount = minOf(100, current.totalTrackCount - offset)
                val key = pageCacheKey(targetMode, current.id.toString(), 100, batch)
                val hasWholeFirstBatch = batch == 1 && current.tracks.size >= expectedCount
                if (pageCache[key].orEmpty().size >= expectedCount || hasWholeFirstBatch) continue

                val loaded = withTimeout(28_000L) {
                    withContext(Dispatchers.IO) {
                        playlistProvider.load(
                            input = current.id.toString(),
                            likedByUserId = false,
                            offset = offset,
                            limit = 100,
                        )
                    }
                }
                val activePlaylist = if (targetMode == "liked") likedPlaylist else publicPlaylist
                if (
                    inputMode != targetMode ||
                    activePlaylist?.id != current.id ||
                    filterText.trim() != query
                ) return@LaunchedEffect

                pageCache = pageCache + (key to loaded.tracks)
                status = "正在搜索整份歌单：已检查 ${minOf(offset + loaded.tracks.size, current.totalTrackCount)} / ${current.totalTrackCount} 首……"
            }
            val activePlaylist = if (targetMode == "liked") likedPlaylist else publicPlaylist
            if (inputMode == targetMode && activePlaylist?.id == current.id && filterText.trim() == query) {
                val prefix = "$targetMode:${current.id}:"
                val allTracksForSearch = (current.tracks + pageCache.filterKeys { it.startsWith(prefix) }.values.flatten())
                    .distinctBy { it.id }
                val count = allTracksForSearch.count {
                    it.title.contains(query, ignoreCase = true) ||
                        it.artists.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
                }
                status = "全歌单筛选完成：找到 $count 首匹配歌曲。"
            }
        } catch (error: Exception) {
            if (error is CancellationException && (inputMode != targetMode || filterText.trim() != query)) {
                throw error
            }
            if (inputMode == targetMode && filterText.trim() == query) {
                val detail = error.message?.takeIf { it.isNotBlank() } ?: "网络或接口暂不可用"
                status = "全歌单筛选未完成：" + detail + "。已加载的匹配结果仍可查看。"
                captureError("全歌单筛选", error)
            }
        } finally {
            isFilteringTracks = false
        }
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = copyErrorReport,
                        enabled = lastErrorReport != null,
                        modifier = Modifier.padding(start = 4.dp).size(42.dp).semantics {
                            contentDescription = "复制错误报告"
                        },
                    ) {
                        CopyGlyph(enabled = lastErrorReport != null)
                    }
                },
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text("音笺", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Text("MUSIC META", color = SubInk, fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDownloadSettings = true },
                        enabled = !busy,
                        modifier = Modifier.size(38.dp).semantics { contentDescription = "下载设置" },
                    ) { SettingsGlyph() }
                    IconButton(
                        onClick = { verificationOpen = true },
                        modifier = Modifier.size(38.dp).semantics { contentDescription = "下载站验证" },
                    ) { WebpageGlyph() }
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
                    if (playlist != null && pageItemCount > 0) {
                        if (pageCount > 1) {
                            val footerPageNumbers = if (pageCount <= 7) {
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
                                modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                IconButton(
                                    onClick = { requestPage(currentPage - 1) },
                                    enabled = !busy && currentPage > 1,
                                    modifier = Modifier.size(28.dp).semantics { contentDescription = "上一页" },
                                ) {
                                    PageArrowGlyph(pointsLeft = true, tint = if (!busy && currentPage > 1) Ink else SubInk)
                                }
                                Row(
                                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    footerPageNumbers.forEach { number ->
                                        if (number == -1) {
                                            Text("…", color = SubInk, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 1.dp))
                                        } else {
                                            TextButton(
                                                onClick = { requestPage(number) },
                                                enabled = !busy && number != currentPage,
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            ) {
                                                Text(
                                                    number.toString(),
                                                    color = if (number == currentPage) Accent else Ink,
                                                    fontWeight = if (number == currentPage) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 10.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { requestPage(currentPage + 1) },
                                    enabled = !busy && currentPage < pageCount,
                                    modifier = Modifier.size(28.dp).semantics { contentDescription = "下一页" },
                                ) {
                                    PageArrowGlyph(pointsLeft = false, tint = if (!busy && currentPage < pageCount) Ink else SubInk)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text("每页", color = SubInk, fontSize = 9.sp)
                            listOf(25, 50, 100).forEach { count ->
                                TextButton(
                                    onClick = {
                                        if (!busy && pageSize != count) {
                                            pageSize = count
                                            folderPreferences.edit().putInt("songs_per_page", count).apply()
                                            setCurrentPage(1)
                                            jumpPageInput = "1"
                                            selectedTrack = null
                                            flacResults = emptyList()
                                            scope.launch { listState.scrollToItem(0) }
                                        }
                                    },
                                    enabled = !busy,
                                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        count.toString(),
                                        color = if (pageSize == count) Accent else Ink,
                                        fontSize = 10.sp,
                                        fontWeight = if (pageSize == count) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(PageBackground)
                                    .border(
                                        width = 1.dp,
                                        color = if (jumpFieldFocused) Accent else Hairline,
                                        shape = RoundedCornerShape(7.dp),
                                    )
                                    .padding(horizontal = 4.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicTextField(
                                    value = jumpPageInput,
                                    onValueChange = { jumpPageInput = it.filter(Char::isDigit).take(5) },
                                    modifier = Modifier.fillMaxWidth().onFocusChanged { jumpFieldFocused = it.isFocused },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Ink, fontSize = 11.sp, textAlign = TextAlign.Center),
                                    cursorBrush = SolidColor(Accent),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        val target = jumpPageInput.toIntOrNull()
                                        if (target == null) {
                                            status = "请输入页码。"
                                            focusManager.clearFocus()
                                        } else requestPage(target)
                                    }),
                                    enabled = !busy && pageCount > 1,
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.Center) {
                                            if (jumpPageInput.isEmpty()) Text("页", color = SubInk, fontSize = 9.sp)
                                            innerTextField()
                                        }
                                    },
                                )
                            }
                            IconButton(
                                onClick = {
                                    val target = jumpPageInput.toIntOrNull()
                                    if (target == null) status = "请输入页码。" else requestPage(target)
                                },
                                enabled = !busy && pageCount > 1,
                                modifier = Modifier.size(28.dp).semantics { contentDescription = "跳转到页码" },
                            ) {
                                EnterGlyph()
                            }
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    scope.launch { listState.animateScrollToItem(0) }
                                },
                                modifier = Modifier.size(28.dp).semantics { contentDescription = "回到顶部" },
                            ) { HomeGlyph() }
                        }
                    }
                    if (playlist == null || pageItemCount <= 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(25.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    scope.launch { listState.animateScrollToItem(0) }
                                },
                                modifier = Modifier.size(28.dp).semantics { contentDescription = "回到顶部" },
                            ) { HomeGlyph() }
                        }
                    }
                    if (busy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Accent,
                            trackColor = SoftAccent,
                        )
                    }
                    Text(
                        text = status,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
                        color = SubInk,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                            val modes = listOf("playlist", "liked", "search")
                            val currentIndex = modes.indexOf(inputMode)
                            if (!busy && dragDistance < -72f && currentIndex in 0 until modes.lastIndex) {
                                switchInputMode(modes[currentIndex + 1])
                            } else if (!busy && dragDistance > 72f && currentIndex > 0) {
                                switchInputMode(modes[currentIndex - 1])
                            }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "把喜欢的歌，存进音乐库",
                            modifier = Modifier.weight(1f),
                            color = Ink,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/WuLinLee/MusicMeta"))
                                    )
                                }.onFailure {
                                    status = "无法打开 GitHub 项目主页，请检查浏览器是否可用。"
                                }
                            },
                            modifier = Modifier.size(32.dp).semantics { contentDescription = "打开 GitHub 项目主页" },
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.github_logo),
                                contentDescription = "GitHub 项目主页",
                                modifier = Modifier.size(23.dp),
                            )
                        }
                    }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = inputMode == "playlist",
                                onClick = { switchInputMode("playlist") },
                                enabled = !busy,
                                label = { Text("公开歌单", fontSize = 12.sp) },
                            )
                            FilterChip(
                                selected = inputMode == "liked",
                                onClick = { switchInputMode("liked") },
                                enabled = !busy,
                                label = { Text("红心歌单", fontSize = 12.sp) },
                            )
                            FilterChip(
                                selected = inputMode == "search",
                                onClick = { switchInputMode("search") },
                                enabled = !busy,
                                label = { Text("直接搜歌", fontSize = 12.sp) },
                            )
                        }
                        if (inputMode == "search") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = manualSearchQuery,
                                    onValueChange = { manualSearchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("直接搜歌") },
                                    placeholder = { Text("输入歌名或歌手") },
                                    shape = RoundedCornerShape(13.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { searchSourceKeyword() }),
                                    enabled = !busy,
                                )
                                OutlinedButton(
                                    onClick = { searchSourceKeyword() },
                                    enabled = !busy && manualSearchQuery.isNotBlank(),
                                    shape = RoundedCornerShape(13.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                ) {
                                    Text("搜索", fontSize = 12.sp)
                                }
                            }
                        } else {
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
                                modifier = Modifier.fillMaxWidth().height(48.dp),
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
            }

            if (manualSourceSearchActive) {
                item(key = "direct-source-results") {
                    ManualSourceSearchPanel(
                        title = manualSearchQuery,
                        isSearching = isSearchingFlac,
                        results = flacResults,
                        platform = flacPlatform,
                        quality = selectedQuality,
                        isResolvingUrl = isResolvingUrl,
                        isDownloading = isDownloading,
                        onPlatformChange = { value ->
                            if (flacPlatform != value) {
                                flacPlatform = value
                                folderPreferences.edit().putString("source_platform", value).apply()
                                searchSourceKeyword()
                            }
                        },
                        onQualityChange = setQuality,
                        onDownload = { song -> downloadManualSearchResult(song) },
                        onPlay = { song -> playPreview(song) },
                        onCopy = { song -> copySongAndArtist(song.title, song.artist) },
                        coverResolver = resolveSourceCoverUrl,
                        previewingSourceKey = previewSong?.let(sourceSongKey),
                        isPreviewPlaying = isPreviewPlaying,
                        isPreviewLoading = isPreviewLoading,
                    )
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
                            Text(
                                if (isFilteringTracks) "正在搜索整份歌单 · 当前匹配 ${filteredKnownTracks.size} 首"
                                else "核对版本后下载，自动写入标签",
                                fontSize = 12.sp,
                                color = SubInk,
                            )
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
                                when {
                                    isFilteringTracks -> "正在搜索整份歌单，请稍候……"
                                    filterText.isBlank() -> "这一页暂时没有歌曲，点一下页码可重试。"
                                    else -> "没有找到匹配歌曲。"
                                },
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
                            onCopy = { copySongAndArtist(track.title, track.artists) },
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
                                                .padding(10.dp)
                                                .pointerInput(song.id, song.platform) {
                                                    detectTapGestures(onLongPress = {
                                                        copySongAndArtist(song.title, song.artist)
                                                    })
                                                },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            SourceCoverImage(
                                                song = song,
                                                size = 46.dp,
                                                coverResolver = resolveSourceCoverUrl,
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
                                            IconButton(
                                                onClick = { playPreview(song) },
                                                enabled = !isPreviewLoading && !isResolvingUrl && !isDownloading,
                                                modifier = Modifier.size(32.dp).semantics {
                                                    contentDescription = "播放音源"
                                                },
                                            ) {
                                                PlayGlyph(
                                                    isPlaying = previewSong?.let(sourceSongKey) == sourceSongKey(song) && isPreviewPlaying,
                                                    isLoading = previewSong?.let(sourceSongKey) == sourceSongKey(song) && isPreviewLoading,
                                                )
                                            }
                                            IconButton(
                                                onClick = { copySongAndArtist(song.title, song.artist) },
                                                modifier = Modifier.size(30.dp).semantics {
                                                    contentDescription = "复制歌曲名和艺人"
                                                },
                                            ) { CopyGlyph() }
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
                                                                    selectedTrack ?: metadataTrackForSource(song),
                                                                    song,
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
                if (inputMode != "search") item(key = "empty-playlist") {
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

        if (previewSong != null) {
            val activePreviewSong = previewSong!!
            val activeQueueIndex = previewQueue.indexOfFirst {
                sourceSongKey(it) == sourceSongKey(activePreviewSong)
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val playerAreaWidth = maxWidth
                val dragWidth = if (showPreviewDetails) 356.dp else 76.dp
                val maxDragX = with(density) { (maxWidth - dragWidth).toPx().coerceAtLeast(0f) }
                val dragHeight = 108.dp
                val maxDragY = with(density) { (maxHeight - dragHeight).toPx().coerceAtLeast(0f) }

                if (showPreviewDetails) {
                    val originalTimedLyrics = remember(previewLyrics) { parseTimedPreviewLyrics(previewLyrics) }
                    val translatedTimedLyrics = remember(previewTranslatedLyrics) {
                        parseTimedPreviewLyrics(previewTranslatedLyrics)
                    }
                    val originalFallbackLyrics = remember(previewLyrics) {
                        formatPreviewLyrics(previewLyrics).lineSequence().filter { it.isNotBlank() }.toList()
                    }
                    val translatedFallbackLyrics = remember(previewTranslatedLyrics) {
                        formatPreviewLyrics(previewTranslatedLyrics).lineSequence().filter { it.isNotBlank() }.toList()
                    }
                    val activeTrackLyrics = if (showTranslatedPreviewLyric) translatedTimedLyrics else originalTimedLyrics
                    val activeFallbackLyrics = if (showTranslatedPreviewLyric) translatedFallbackLyrics else originalFallbackLyrics
                    val activeLyric = when {
                        isPreviewLyricsLoading -> "正在找歌词…"
                        previewLyrics.isBlank() -> "暂未找到歌词"
                        showTranslatedPreviewLyric && previewTranslatedLyrics.isBlank() -> "这首歌暂无译文"
                        activeTrackLyrics.isNotEmpty() -> activeTrackLyrics.lastOrNull { it.first <= previewPositionMs }?.second
                            ?: activeTrackLyrics.first().second
                        activeFallbackLyrics.isNotEmpty() -> activeFallbackLyrics.first()
                        else -> "♪"
                    }
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 78.dp, bottom = 36.dp)
                            .offset {
                                IntOffset(
                                    miniPlayerOffset.x.coerceIn(-maxDragX, 0f).roundToInt(),
                                    miniPlayerOffset.y.coerceIn(-maxDragY, 0f).roundToInt(),
                                )
                            }
                            .width(278.dp)
                            .height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF17211F)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF243631))
                                    .clickable { showTranslatedPreviewLyric = !showTranslatedPreviewLyric }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .semantics {
                                        contentDescription = if (previewTranslatedLyrics.isBlank())
                                            "歌词，点击尝试显示译文" else "歌词，点击切换原文或译文"
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    activeLyric,
                                    color = Color(0xFF9FF0C9),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val progress = if (previewDurationMs > 0)
                                (previewPositionMs.toFloat() / previewDurationMs.toFloat()).coerceIn(0f, 1f)
                            else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .pointerInput(previewDurationMs, activePreviewSong) {
                                        detectTapGestures { tap ->
                                            if (previewDurationMs > 0 && size.width > 0) {
                                                val fraction = (tap.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                val target = (fraction * previewDurationMs).toInt()
                                                previewPositionMs = target
                                                runCatching { previewPlayer.value?.seekTo(target) }
                                            }
                                        }
                                    }
                                    .pointerInput(previewDurationMs, activePreviewSong) {
                                        detectHorizontalDragGestures { change, _ ->
                                            change.consume()
                                            if (previewDurationMs > 0 && size.width > 0) {
                                                val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                val target = (fraction * previewDurationMs).toInt()
                                                previewPositionMs = target
                                                runCatching { previewPlayer.value?.seekTo(target) }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val y = size.height / 2f
                                    val strokeWidth = 3.dp.toPx()
                                    drawLine(
                                        color = Color(0xFF3B514B),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = strokeWidth,
                                        cap = StrokeCap.Round,
                                    )
                                    if (progress > 0f) {
                                        drawLine(
                                            color = Color(0xFF9FF0C9),
                                            start = Offset(0f, y),
                                            end = Offset(size.width * progress, y),
                                            strokeWidth = strokeWidth,
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp)
                        .offset {
                            IntOffset(
                                miniPlayerOffset.x.coerceIn(-maxDragX, 0f).roundToInt(),
                                miniPlayerOffset.y.coerceIn(-maxDragY, 0f).roundToInt(),
                            )
                        }
                        .width(64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(SurfaceColor)
                            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                            .pointerInput(activePreviewSong, maxDragX, maxDragY) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    miniPlayerOffset = Offset(
                                        (miniPlayerOffset.x + dragAmount.x).coerceIn(-maxDragX, 0f),
                                        (miniPlayerOffset.y + dragAmount.y).coerceIn(-maxDragY, 0f),
                                    )
                                }
                            }
                            .pointerInput(activePreviewSong) {
                                detectTapGestures(
                                    onTap = { playPreview(activePreviewSong) },
                                    onDoubleTap = {
                                        val expanding = !showPreviewDetails
                                        if (expanding) {
                                            val expandedMaxDragX = with(density) {
                                                (playerAreaWidth - 356.dp).toPx().coerceAtLeast(0f)
                                            }
                                            miniPlayerOffset = Offset(
                                                miniPlayerOffset.x.coerceIn(-expandedMaxDragX, 0f),
                                                miniPlayerOffset.y,
                                            )
                                        }
                                        showPreviewDetails = expanding
                                    },
                                )
                            },
                    ) {
                        AsyncImage(
                            model = previewCoverUrl.ifBlank { activePreviewSong.coverUrl }.takeIf { it.isNotBlank() },
                            contentDescription = "单击播放或暂停，双击展开或收起歌词，拖动移动播放器",
                            placeholder = painterResource(id = R.drawable.musicmeta_cover),
                            error = painterResource(id = R.drawable.musicmeta_cover),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (isPreviewLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(22.dp),
                                color = Accent,
                                strokeWidth = 2.dp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .size(20.dp)
                                .clickable { stopPreview() }
                                .semantics { contentDescription = "关闭迷你播放器" },
                            contentAlignment = Alignment.Center,
                        ) {
                            CloseGlyph()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "${formatPreviewTime(previewPositionMs)}/${formatPreviewTime(previewDurationMs)}",
                            color = SubInk,
                            fontSize = 7.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clickable(enabled = !isPreviewLoading) { playPreview(activePreviewSong) }
                                .semantics {
                                    contentDescription = if (isPreviewPlaying) "暂停播放" else "继续播放"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayGlyph(isPlaying = isPreviewPlaying, isLoading = isPreviewLoading)
                        }
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
private fun ManualSourceSearchPanel(
    title: String,
    isSearching: Boolean,
    results: List<FlacSiteSong>,
    platform: String,
    quality: String,
    isResolvingUrl: Boolean,
    isDownloading: Boolean,
    onPlatformChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
    onDownload: (FlacSiteSong) -> Unit,
    onPlay: (FlacSiteSong) -> Unit,
    onCopy: (FlacSiteSong) -> Unit,
    coverResolver: suspend (FlacSiteSong) -> String,
    previewingSourceKey: String?,
    isPreviewPlaying: Boolean,
    isPreviewLoading: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("搜索结果", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(title, color = SubInk, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilterChip(
                        selected = platform == "wyy",
                        onClick = { onPlatformChange("wyy") },
                        label = { Text("网易云", fontSize = 10.sp) },
                        enabled = !isSearching && !isResolvingUrl && !isDownloading,
                    )
                    FilterChip(
                        selected = platform == "kuwo",
                        onClick = { onPlatformChange("kuwo") },
                        label = { Text("酷我", fontSize = 10.sp) },
                        enabled = !isSearching && !isResolvingUrl && !isDownloading,
                    )
                }
            }
            if (isSearching) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("正在搜索音源…", color = SubInk, fontSize = 12.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("音质", color = SubInk, fontSize = 11.sp)
                    FilterChip(
                        selected = quality == "128k",
                        onClick = { onQualityChange("128k") },
                        enabled = !isResolvingUrl && !isDownloading,
                        label = { Text("128k", fontSize = 10.sp) },
                    )
                    FilterChip(
                        selected = quality == "320k",
                        onClick = { onQualityChange("320k") },
                        enabled = !isResolvingUrl && !isDownloading,
                        label = { Text("320k", fontSize = 10.sp) },
                    )
                    FilterChip(
                        selected = quality == "FLAC",
                        onClick = { onQualityChange("FLAC") },
                        enabled = !isResolvingUrl && !isDownloading,
                        label = { Text("FLAC", fontSize = 10.sp) },
                    )
                }
                if (results.isEmpty()) {
                    Text("没有找到匹配音源，切换平台或修改关键词再试。", color = SubInk, fontSize = 12.sp)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        results.forEach { song ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                                    .background(PageBackground).padding(9.dp)
                                    .pointerInput(song.id, song.platform) {
                                        detectTapGestures(onLongPress = { onCopy(song) })
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SourceCoverImage(
                                    song = song,
                                    size = 42.dp,
                                    coverResolver = coverResolver,
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(song.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                                        color = SubInk, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (song.platform.startsWith("gd:")) {
                                        Text("GD Studio 兜底音源", color = Accent, fontSize = 9.sp)
                                    }
                                }
                                IconButton(
                                    onClick = { onPlay(song) },
                                    enabled = !isPreviewLoading && !isResolvingUrl && !isDownloading,
                                    modifier = Modifier.size(30.dp).semantics { contentDescription = "播放音源" },
                                ) {
                                    PlayGlyph(
                                        isPlaying = previewingSourceKey == song.platform + ":" + song.id && isPreviewPlaying,
                                        isLoading = previewingSourceKey == song.platform + ":" + song.id && isPreviewLoading,
                                    )
                                }
                                IconButton(
                                    onClick = { onCopy(song) },
                                    modifier = Modifier.size(28.dp).semantics {
                                        contentDescription = "复制歌曲名和艺人"
                                    },
                                ) { CopyGlyph() }
                                Button(
                                    onClick = { onDownload(song) },
                                    enabled = !isResolvingUrl && !isDownloading,
                                    shape = RoundedCornerShape(11.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                ) {
                                    if (isResolvingUrl) {
                                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color.White)
                                    } else {
                                        Text("下载", fontSize = 11.sp)
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

@Composable
private fun SourceCoverImage(
    song: FlacSiteSong,
    size: Dp,
    coverResolver: suspend (FlacSiteSong) -> String,
) {
    var resolvedCoverUrl by remember(song.platform, song.id, song.coverUrl) {
        mutableStateOf(song.coverUrl)
    }
    LaunchedEffect(song.platform, song.id, song.coverUrl) {
        if (resolvedCoverUrl.isBlank()) {
            resolvedCoverUrl = runCatching { coverResolver(song) }.getOrDefault("")
        }
    }
    AsyncImage(
        model = resolvedCoverUrl.takeIf { it.isNotBlank() },
        contentDescription = "音源封面",
        placeholder = painterResource(id = R.drawable.musicmeta_cover),
        error = painterResource(id = R.drawable.musicmeta_cover),
        modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(SoftAccent),
        contentScale = ContentScale.Crop,
    )
}

private fun parseTimedPreviewLyrics(raw: String): List<Pair<Int, String>> {
    val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    val lines = mutableListOf<Pair<Int, String>>()
    for (line in raw.lineSequence()) {
        val matches = timestamp.findAll(line).toList()
        if (matches.isEmpty()) continue
        val lyricText = timestamp.replace(line, "").trim()
        if (lyricText.isBlank()) continue
        for (match in matches) {
            val minutes = match.groupValues[1].toIntOrNull() ?: continue
            val seconds = match.groupValues[2].toIntOrNull() ?: continue
            if (seconds >= 60) continue
            val fraction = match.groupValues.getOrElse(3) { "" }
            val millis = when (fraction.length) {
                1 -> (fraction.toIntOrNull() ?: 0) * 100
                2 -> (fraction.toIntOrNull() ?: 0) * 10
                3 -> fraction.toIntOrNull() ?: 0
                else -> 0
            }
            lines += ((minutes * 60 + seconds) * 1000 + millis) to lyricText
        }
    }
    return lines.sortedBy { it.first }
}

private fun formatPreviewLyrics(raw: String): String {
    val timestampPrefix = Regex("^\\s*(?:\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?])+") 
    return raw.lineSequence()
        .map { it.replace(timestampPrefix, "").trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .ifBlank { raw.trim() }
}

private fun formatPreviewTime(milliseconds: Int): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    return (totalSeconds / 60).toString() + ":" + (totalSeconds % 60).toString().padStart(2, '0')
}

@Composable
private fun CloseGlyph() {
    Canvas(modifier = Modifier.size(13.dp)) {
        val start = Offset(size.width * 0.22f, size.height * 0.22f)
        val end = Offset(size.width * 0.78f, size.height * 0.78f)
        val oppositeStart = Offset(size.width * 0.78f, size.height * 0.22f)
        val oppositeEnd = Offset(size.width * 0.22f, size.height * 0.78f)
        val stroke = 1.8.dp.toPx()
        drawLine(Color(0xCC101512), start, end, strokeWidth = stroke + 1.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color(0xCC101512), oppositeStart, oppositeEnd, strokeWidth = stroke + 1.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color(0xFFF4F7F5), start, end, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color(0xFFF4F7F5), oppositeStart, oppositeEnd, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun PlayGlyph(isPlaying: Boolean, isLoading: Boolean) {
    Canvas(modifier = Modifier.size(19.dp)) {
        val tint = if (isLoading) SubInk else Accent
        if (isLoading) {
            drawCircle(color = tint, radius = size.minDimension * 0.28f, style = Stroke(width = 1.6.dp.toPx()))
            drawLine(
                color = tint,
                start = Offset(size.width * 0.5f, size.height * 0.08f),
                end = Offset(size.width * 0.5f, size.height * 0.30f),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else if (isPlaying) {
            drawLine(tint, Offset(size.width * 0.35f, size.height * 0.24f), Offset(size.width * 0.35f, size.height * 0.76f), 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.66f, size.height * 0.24f), Offset(size.width * 0.66f, size.height * 0.76f), 2.5.dp.toPx(), cap = StrokeCap.Round)
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.16f)
                lineTo(size.width * 0.78f, size.height * 0.50f)
                lineTo(size.width * 0.28f, size.height * 0.84f)
                close()
            }
            drawPath(path, tint)
        }
    }
}

@Composable
private fun ErrorGlyph(enabled: Boolean) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val tint = if (enabled) Accent else SubInk
        val stroke = 1.7.dp.toPx()
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.39f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = tint,
            start = Offset(size.width / 2f, size.height * 0.29f),
            end = Offset(size.width / 2f, size.height * 0.55f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(color = tint, radius = 1.1.dp.toPx(), center = Offset(size.width / 2f, size.height * 0.71f))
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
private fun EnterGlyph() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val color = Accent
        val left = size.width * 0.18f
        val right = size.width * 0.82f
        val top = size.height * 0.22f
        val cornerY = size.height * 0.58f
        val bottom = size.height * 0.78f
        val arrowX = size.width * 0.34f
        drawLine(color, Offset(right, top), Offset(right, cornerY), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(right, cornerY), Offset(left, cornerY), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(left, cornerY), Offset(arrowX, cornerY - size.height * 0.17f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(left, cornerY), Offset(arrowX, bottom), stroke, cap = StrokeCap.Round)
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
private fun SettingsGlyph() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension * 0.31f
        val stroke = 1.8.dp.toPx()
        for (i in 0 until 8) {
            val angle = i * Math.PI / 4.0
            val dx = kotlin.math.cos(angle).toFloat()
            val dy = kotlin.math.sin(angle).toFloat()
            drawLine(Accent, Offset(cx + dx * radius * 0.78f, cy + dy * radius * 0.78f),
                Offset(cx + dx * radius * 1.30f, cy + dy * radius * 1.30f), stroke, cap = StrokeCap.Round)
        }
        drawCircle(Accent, radius = radius * 0.90f, center = Offset(cx, cy), style = Stroke(width = stroke))
        drawCircle(Accent, radius = radius * 0.30f, center = Offset(cx, cy))
    }
}

@Composable
private fun WebpageGlyph() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        val left = size.width * 0.16f
        val right = size.width * 0.84f
        val top = size.height * 0.12f
        val bottom = size.height * 0.88f
        drawRoundRect(color = Accent, topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(width = stroke))
        drawLine(Accent, Offset(left, size.height * 0.34f), Offset(right, size.height * 0.34f), stroke, cap = StrokeCap.Round)
        drawCircle(Accent, radius = 1.dp.toPx(), center = Offset(size.width * 0.28f, size.height * 0.23f))
        drawCircle(Accent, radius = 1.dp.toPx(), center = Offset(size.width * 0.39f, size.height * 0.23f))
        drawLine(Accent, Offset(size.width * 0.30f, size.height * 0.51f), Offset(size.width * 0.70f, size.height * 0.51f), stroke, cap = StrokeCap.Round)
        drawLine(Accent, Offset(size.width * 0.30f, size.height * 0.67f), Offset(size.width * 0.62f, size.height * 0.67f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun CopyGlyph(enabled: Boolean = true) {
    Canvas(modifier = Modifier.size(19.dp)) {
        val stroke = 1.7.dp.toPx()
        val tint = if (enabled) Accent else SubInk
        drawRoundRect(color = tint, topLeft = Offset(size.width * 0.30f, size.height * 0.12f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(width = stroke))
        drawRoundRect(color = tint, topLeft = Offset(size.width * 0.12f, size.height * 0.30f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(width = stroke))
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
    onCopy: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().pointerInput(track.id) {
            detectTapGestures(onLongPress = { onCopy() })
        },
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

private fun metadataTrackForSource(song: FlacSiteSong): MusicTrack {
    // Only a native NetEase ID is safe to pass to NetEase-specific metadata APIs.
    val neteaseId = if (song.platform == "wyy") song.id.toLongOrNull()?.takeIf { it > 0L } ?: 0L else 0L
    return MusicTrack(
        id = neteaseId,
        title = song.title,
        artists = song.artist,
        album = song.album,
        coverUrl = song.coverUrl,
    )
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
