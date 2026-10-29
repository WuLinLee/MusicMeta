package com.wulinlee.musicmeta

import android.annotation.SuppressLint
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.provider.MediaStore
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
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
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
import kotlin.math.pow
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.ceil

internal const val PREVIOUS_DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP = 15.04f
internal const val DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP = 17.28f
internal const val MIN_FLOATING_LYRICS_FONT_SIZE_SP = DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP / 2f
internal const val MAX_FLOATING_LYRICS_FONT_SIZE_SP = DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP * 2f
internal const val FLOATING_LYRICS_PANEL_HEIGHT_DP = 70
internal const val FLOATING_LYRICS_PANEL_VERTICAL_PADDING_DP = 2
internal const val FLOATING_LYRICS_PROGRESS_HEIGHT_DP = 18

/**
 * Keep the default lyric card compact. Increase its height only when the selected font
 * needs more room for two lines, while leaving the progress area independent.
 */
internal fun floatingLyricsPanelHeightDp(
    fontSizeSp: Float = DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP,
): Int {
    val safeFontSize = fontSizeSp.coerceIn(
        MIN_FLOATING_LYRICS_FONT_SIZE_SP,
        MAX_FLOATING_LYRICS_FONT_SIZE_SP,
    )
    val requiredTextHeightDp = safeFontSize * 1.12f * 2f
    val requiredPanelHeightDp = ceil(
        requiredTextHeightDp +
            2 * FLOATING_LYRICS_PANEL_VERTICAL_PADDING_DP +
            FLOATING_LYRICS_PROGRESS_HEIGHT_DP,
    ).toInt()
    return maxOf(FLOATING_LYRICS_PANEL_HEIGHT_DP, requiredPanelHeightDp)
}

internal fun floatingLyricsTextAreaHeightDp(
    fontSizeSp: Float = DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP,
): Float =
    floatingLyricsPanelHeightDp(fontSizeSp) -
        2 * FLOATING_LYRICS_PANEL_VERTICAL_PADDING_DP -
        FLOATING_LYRICS_PROGRESS_HEIGHT_DP.toFloat()

internal fun floatingLyricsFontSizeAfterDefaultMigration(
    storedSize: Float?,
    migrationComplete: Boolean,
): Float {
    val shouldAdoptLargerDefault = !migrationComplete &&
        (storedSize == null || storedSize == PREVIOUS_DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP)
    return (if (shouldAdoptLargerDefault) DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP
    else storedSize ?: DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP)
        .coerceIn(MIN_FLOATING_LYRICS_FONT_SIZE_SP, MAX_FLOATING_LYRICS_FONT_SIZE_SP)
}

internal fun localTrackIndexForOverlayUri(
    orderedTrackUris: List<String>,
    targetUri: String,
): Int = orderedTrackUris.indexOfFirst { it == targetUri }

internal fun floatingLyricsFontSizeFromSlider(progress: Float): Float =
    (DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP * 4.0.pow(progress.coerceIn(0f, 1f).toDouble() - 0.5)).toFloat()
        .coerceIn(MIN_FLOATING_LYRICS_FONT_SIZE_SP, MAX_FLOATING_LYRICS_FONT_SIZE_SP)

internal fun floatingLyricsFontSliderPosition(sizeSp: Float): Float =
    (0.5 + ln(
        sizeSp.coerceIn(MIN_FLOATING_LYRICS_FONT_SIZE_SP, MAX_FLOATING_LYRICS_FONT_SIZE_SP).toDouble() /
            DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP.toDouble(),
    ) / ln(4.0)).toFloat().coerceIn(0f, 1f)

private const val FLAC_SITE_HOME = "https://flac.music.hi.cn/"
private val PageBackground = Color(0xFF0D1210)
private val SurfaceColor = Color(0xFF171E1A)
private val Ink = Color(0xFFE5ECE7)
private val SubInk = Color(0xFF9AA69E)
private val Accent = Color(0xFF80C9AA)
private val SoftAccent = Color(0xFF203D30)
private val Hairline = Color(0xFF2B3730)

internal data class OverlayOpenRequest(
    val requestId: Long,
    val mode: String,
    val trackId: String,
    val playlistId: Long,
    val title: String,
    val artist: String,
)

class MainActivity : ComponentActivity() {
    internal var pendingOverlayOpenRequest by mutableStateOf<OverlayOpenRequest?>(null)
        private set
    private var overlayOpenRequestSequence = 0L

    private fun acceptOverlayOpenIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(FloatingMiniPlayerService.EXTRA_OPEN_APP_AND_LOCATE, false) != true) return
        pendingOverlayOpenRequest = OverlayOpenRequest(
            requestId = ++overlayOpenRequestSequence,
            mode = intent.getStringExtra(FloatingMiniPlayerService.EXTRA_OPEN_MODE).orEmpty(),
            trackId = intent.getStringExtra(FloatingMiniPlayerService.EXTRA_OPEN_TRACK_ID).orEmpty(),
            playlistId = intent.getLongExtra(FloatingMiniPlayerService.EXTRA_OPEN_PLAYLIST_ID, 0L),
            title = intent.getStringExtra(FloatingMiniPlayerService.EXTRA_TITLE).orEmpty(),
            artist = intent.getStringExtra(FloatingMiniPlayerService.EXTRA_ARTIST).orEmpty(),
        )
    }

    internal fun clearOverlayOpenRequest(requestId: Long) {
        if (pendingOverlayOpenRequest?.requestId == requestId) pendingOverlayOpenRequest = null
    }

    internal fun requestTrackLocation(
        mode: String,
        trackId: String,
        playlistId: Long,
        title: String,
        artist: String,
    ) {
        pendingOverlayOpenRequest = OverlayOpenRequest(
            requestId = ++overlayOpenRequestSequence,
            mode = mode,
            trackId = trackId,
            playlistId = playlistId,
            title = title,
            artist = artist,
        )
    }

    private val floatingOverlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra(FloatingMiniPlayerService.EXTRA_COMMAND) ?: return
            val position = intent.getIntExtra(FloatingMiniPlayerService.EXTRA_POSITION_MS, 0)
            FloatingMiniPlayerBridge.onCommand?.invoke(command, position)
        }
    }
    private var floatingOverlayReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptOverlayOpenIntent(intent)
        ContextCompat.registerReceiver(this, floatingOverlayReceiver, IntentFilter(FloatingMiniPlayerService.ACTION_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED)
        floatingOverlayReceiverRegistered = true
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptOverlayOpenIntent(intent)
    }

    override fun onDestroy() {
        if (floatingOverlayReceiverRegistered) {
            runCatching { unregisterReceiver(floatingOverlayReceiver) }
            floatingOverlayReceiverRegistered = false
        }
        super.onDestroy()
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
                .putString("naming_format", "song_artist")
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
    remember(folderPreferences) {
        if (!folderPreferences.getBoolean("v1_21_overlay_default_migrated", false)) {
            val editor = folderPreferences.edit().putBoolean("v1_21_overlay_default_migrated", true)
            if (Settings.canDrawOverlays(context)) editor.putBoolean("floating_overlay_enabled", true)
            editor.apply()
        }
        if (!folderPreferences.getBoolean("v1_12_naming_default_migrated", false)) {
            val savedNamingFormat = folderPreferences.getString("naming_format", null)
            val editor = folderPreferences.edit()
                .putBoolean("v1_12_naming_default_migrated", true)
            if (savedNamingFormat.isNullOrBlank() || savedNamingFormat == "song_title") {
                editor.putString("naming_format", "song_artist")
            }
            editor.apply()
        }
        if (!folderPreferences.getBoolean("v1_22_source_settings_migrated", false)) {
            val legacyPlatform = MusicSourceConfig.normalizePlatform(
                folderPreferences.getString("source_platform", MusicSourceConfig.DEFAULT_PLATFORM)
            )
            val editor = folderPreferences.edit()
                .putBoolean("v1_22_source_settings_migrated", true)
                .putString("source_provider", MusicSourceConfig.DEFAULT_BACKEND)
            if (folderPreferences.getString("source_platform_gdstudio", null).isNullOrBlank()) {
                editor.putString("source_platform_gdstudio", MusicSourceConfig.DEFAULT_PLATFORM)
            }
            if (folderPreferences.getString("source_platform_flac", null).isNullOrBlank()) {
                editor.putString("source_platform_flac", if (legacyPlatform == "kuwo") "kuwo" else MusicSourceConfig.DEFAULT_PLATFORM)
            }
            if (folderPreferences.getString("source_platform", null) == "wyy") {
                editor.putString("source_platform", MusicSourceConfig.DEFAULT_PLATFORM)
            }
            editor.apply()
        }
    }
    var destinationTreeUri by remember { mutableStateOf(loadSavedDestination(context)) }
    val playlistProvider = remember { PublicPlaylistProvider() }
    val metadataProvider = remember { MetadataProvider() }
    val flacProvider = remember { FlacMusicProvider() }
    val publicListState = rememberLazyListState()
    val likedListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val localListState = rememberLazyListState()
    val savedInputMode = folderPreferences.getString("input_mode", "playlist") ?: "playlist"
    var inputMode by remember { mutableStateOf(savedInputMode.takeIf { it in setOf("playlist", "liked", "search", "local") } ?: "playlist") }
    val listState = when (inputMode) {
        "liked" -> likedListState
        "search" -> searchListState
        "local" -> localListState
        else -> publicListState
    }
    var publicCurrentPage by remember { mutableStateOf(folderPreferences.getInt("public_current_page", 1).coerceAtLeast(1)) }
    var likedCurrentPage by remember { mutableStateOf(folderPreferences.getInt("liked_current_page", 1).coerceAtLeast(1)) }
    var localCurrentPage by remember { mutableStateOf(folderPreferences.getInt("local_current_page", 1).coerceAtLeast(1)) }
    val currentPage = when (inputMode) {
        "liked" -> likedCurrentPage
        "local" -> localCurrentPage
        else -> publicCurrentPage
    }
    val setCurrentPage: (Int) -> Unit = { value ->
        val page = value.coerceAtLeast(1)
        when (inputMode) {
            "liked" -> {
                likedCurrentPage = page
                folderPreferences.edit().putInt("liked_current_page", page).apply()
            }
            "local" -> {
                localCurrentPage = page
                folderPreferences.edit().putInt("local_current_page", page).apply()
            }
            else -> {
                publicCurrentPage = page
                folderPreferences.edit().putInt("public_current_page", page).apply()
            }
        }
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
        "search", "local" -> null
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
    var errorReports by remember { mutableStateOf<List<String>>(emptyList()) }
    val captureError: (String, Throwable) -> Unit = { stage, error ->
        val report = makeErrorReport(context, stage, error)
        errorReports = (errorReports + report).takeLast(20)
    }
    val copyErrorReport: () -> Unit = {
        if (errorReports.isNotEmpty()) {
            val report = buildString {
                appendLine("音笺 MusicMeta 汇总错误报告")
                appendLine("错误条数: ${errorReports.size}")
                appendLine("说明: 以下按发生顺序排列，最新错误在最后。")
                appendLine()
                errorReports.forEachIndexed { index, item ->
                    appendLine("========== 错误 ${index + 1}/${errorReports.size} ==========")
                    appendLine(item)
                    appendLine()
                }
            }
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("MusicMeta all error reports", report))
            }.onSuccess {
                status = "已复制全部 ${errorReports.size} 条错误报告，可一次性粘贴发送。"
                Toast.makeText(context, "已复制全部 ${errorReports.size} 条错误报告", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                status = "复制失败：" + (error.message ?: "请重试")
                Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
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
    var sourceProvider by remember {
        mutableStateOf(MusicSourceConfig.normalizeBackend(folderPreferences.getString("source_provider", MusicSourceConfig.DEFAULT_BACKEND)))
    }
    var flacPlatform by remember {
        mutableStateOf(
            MusicSourceConfig.platformForBackend(
                sourceProvider,
                folderPreferences.getString(
                    MusicSourceConfig.platformPreferenceKey(sourceProvider),
                    folderPreferences.getString("source_platform", MusicSourceConfig.DEFAULT_PLATFORM),
                ),
            )
        )
    }
    var selectedQuality by remember { mutableStateOf(folderPreferences.getString("quality", "320k") ?: "320k") }
    var showDownloadSettings by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
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
    val persistSourcePlatform: (String) -> Unit = { value ->
        val platform = MusicSourceConfig.platformForBackend(sourceProvider, value)
        flacPlatform = platform
        folderPreferences.edit()
            .putString("source_platform", platform)
            .putString(MusicSourceConfig.platformPreferenceKey(sourceProvider), platform)
            .apply()
        flacResults = emptyList()
    }
    val selectSourceProvider: (String) -> Unit = { value ->
        val backend = MusicSourceConfig.normalizeBackend(value)
        val saved = folderPreferences.getString(MusicSourceConfig.platformPreferenceKey(backend), null)
        val platform = MusicSourceConfig.platformForBackend(backend, saved ?: MusicSourceConfig.DEFAULT_PLATFORM)
        sourceProvider = backend
        flacPlatform = platform
        folderPreferences.edit()
            .putString("source_provider", backend)
            .putString("source_platform", platform)
            .putString(MusicSourceConfig.platformPreferenceKey(backend), platform)
            .apply()
        flacResults = emptyList()
    }
    var manualSearchQuery by remember { mutableStateOf("") }
    var manualSourceSearchActive by remember { mutableStateOf(false) }
    var isSearchingFlac by remember { mutableStateOf(false) }
    var isResolvingUrl by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var verificationOpen by remember { mutableStateOf(false) }
    var floatingOverlayEnabled by remember { mutableStateOf(folderPreferences.getBoolean("floating_overlay_enabled", Settings.canDrawOverlays(context))) }
    var floatingOverlayDismissedForSession by remember { mutableStateOf(false) }
    var floatingLyricsExpandedByDefault by remember {
        mutableStateOf(folderPreferences.getBoolean("floating_lyrics_expanded_by_default", true))
    }
    var floatingLyricsTranslatedByDefault by remember {
        mutableStateOf(folderPreferences.getBoolean("floating_lyrics_translated_by_default", true))
    }
    var floatingLyricsFontSizeSp by remember(folderPreferences) {
        val preferenceKey = "floating_lyrics_font_size_sp"
        val migrationKey = "floating_lyrics_font_default_migrated_v147"
        val migrationComplete = folderPreferences.getBoolean(migrationKey, false)
        val storedSize = if (folderPreferences.contains(preferenceKey)) {
            folderPreferences.getFloat(preferenceKey, DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP)
        } else null
        val initialSize = floatingLyricsFontSizeAfterDefaultMigration(storedSize, migrationComplete)
        if (!migrationComplete) {
            val editor = folderPreferences.edit()
                .putBoolean(migrationKey, true)
                .putBoolean("floating_lyrics_font_default_migrated_v137", true)
            if (storedSize == null || storedSize == PREVIOUS_DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP) {
                editor.putFloat(preferenceKey, initialSize)
            }
            editor.apply()
        }
        mutableStateOf(initialSize)
    }
    var showInteractionHelp by remember { mutableStateOf(false) }
    var playbackMode by remember { mutableStateOf(folderPreferences.getInt("playback_mode", 0).coerceIn(0, 2)) }
    val cyclePlaybackMode: () -> Unit = {
        playbackMode = (playbackMode + 1) % 3
        folderPreferences.edit().putInt("playback_mode", playbackMode).apply()
        status = "播放模式：" + listOf("顺序播放", "乱序播放", "单曲循环")[playbackMode]
    }
    var localMusicTracks by remember { mutableStateOf<List<LocalAudioTrack>>(emptyList()) }
    var localMusicSortMode by remember {
        mutableStateOf(LocalMusicSortMode.fromPreference(folderPreferences.getString("local_music_sort_mode", null)))
    }
    var isRestoringCaches by remember { mutableStateOf(true) }
    var didCheckLocalArtworkMigration by remember { mutableStateOf(false) }
    var highlightedPlaylistMode by remember { mutableStateOf("") }
    var highlightedPlaylistTrackId by remember { mutableStateOf<Long?>(null) }
    var highlightedLocalTrackUri by remember { mutableStateOf("") }
    var recentlyPlayedKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var recentPlaylistPositions by remember { mutableStateOf<Map<String, List<Int>>>(emptyMap()) }
    var localMusicFilter by remember { mutableStateOf("") }
    var isScanningLocalMusic by remember { mutableStateOf(false) }
    var localMusicFolderUri by remember { mutableStateOf(folderPreferences.getString("local_music_tree_uri", null)?.let(Uri::parse)) }
    var previewSong by remember { mutableStateOf<FlacSiteSong?>(null) }
    var previewCurrentTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var previewCurrentPlaylistId by remember { mutableStateOf<Long?>(null) }
    var previewNavigationHandler by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var previewQueue by remember { mutableStateOf<List<FlacSiteSong>>(emptyList()) }
    var queuedNextPreview by remember { mutableStateOf<FlacSiteSong?>(null) }
    var queuedNextTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var queuedNextPlaylistId by remember { mutableStateOf<Long?>(null) }
    var queuedNextMode by remember { mutableStateOf<String?>(null) }
    var previewCoverUrl by remember { mutableStateOf("") }
    var previewLyrics by remember { mutableStateOf("") }
    var previewTranslatedLyrics by remember { mutableStateOf("") }
    var isPreviewLoading by remember { mutableStateOf(false) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var mediaCurrentLyric by remember { mutableStateOf("") }
    var isPreviewLyricsLoading by remember { mutableStateOf(false) }
    var showPreviewDetails by remember { mutableStateOf(floatingLyricsExpandedByDefault) }
    var showTranslatedPreviewLyric by remember { mutableStateOf(floatingLyricsTranslatedByDefault) }
    var previewPositionMs by remember { mutableStateOf(0) }
    var previewDurationMs by remember { mutableStateOf(0) }
    var miniPlayerOffset by remember { mutableStateOf(Offset.Zero) }
    var fallbackMiniPlayerDismissedForSession by remember { mutableStateOf(false) }
    val previewPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    val playPreviewRef = remember { mutableStateOf<(FlacSiteSong) -> Unit>({}) }
    var previewPlaybackGeneration by remember { mutableStateOf(0L) }

    LaunchedEffect(folderPreferences) {
        try {
            val restored = withContext(Dispatchers.IO) {
                MusicMetaPersistence.loadCaches(context, folderPreferences)
            }
            publicPlaylist = restored.publicPlaylist
            likedPlaylist = restored.likedPlaylist
            pageCache = restored.pageCache
            localMusicTracks = restored.localTracks
        } catch (error: Exception) {
            status = "恢复本地缓存失败：" + (error.message ?: "可重新同步歌单或扫描本地音乐")
            captureError("恢复本地缓存", error)
        } finally {
            isRestoringCaches = false
        }
    }

    val localFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            localMusicFolderUri = uri
            folderPreferences.edit().putString("local_music_tree_uri", uri.toString()).apply()
        }.onFailure { captureError("选择本地音乐文件夹", it) }
    }
    val startLocalMusicScan: () -> Unit = {
        if (!isScanningLocalMusic) {
            isScanningLocalMusic = true
            scope.launch {
                try {
                    val scannedTracks = withContext(Dispatchers.IO) {
                        LocalMusicScanner.scan(context, localMusicFolderUri)
                    }
                    localMusicTracks = scannedTracks
                    withContext(Dispatchers.IO) {
                        MusicMetaPersistence.saveLocalTracks(context, scannedTracks)
                    }
                    folderPreferences.edit().putInt("local_artwork_migration_version", 2).apply()
                    localCurrentPage = 1
                    folderPreferences.edit().putInt("local_current_page", 1).apply()
                    jumpPageInput = "1"
                    status = "本地音乐扫描完成，共 " + localMusicTracks.size + " 首；下次启动会自动恢复。"
                } catch (error: Exception) {
                    status = "扫描本地音乐失败：" + (error.message ?: "请检查文件夹权限")
                    captureError("扫描本地音乐", error)
                } finally { isScanningLocalMusic = false }
            }
        }
    }
    val localAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startLocalMusicScan()
        else status = "未授予读取音频权限；仍可在设置中选择一个音乐文件夹扫描。"
    }
    val scanLocalMusic: () -> Unit = {
        val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (localMusicFolderUri != null ||
            ContextCompat.checkSelfPermission(context, audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startLocalMusicScan()
        } else {
            localAudioPermissionLauncher.launch(audioPermission)
        }
    }
    LaunchedEffect(isRestoringCaches) {
        if (!isRestoringCaches && !didCheckLocalArtworkMigration) {
            didCheckLocalArtworkMigration = true
            val migrationVersion = folderPreferences.getInt("local_artwork_migration_version", 0)
            val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
                android.Manifest.permission.READ_MEDIA_AUDIO
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val hasTreePermission = localMusicFolderUri != null &&
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == localMusicFolderUri && it.isReadPermission
                }
            val hasMediaPermission = ContextCompat.checkSelfPermission(
                context,
                audioPermission,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (migrationVersion < 2 && localMusicTracks.isNotEmpty() && (hasTreePermission || hasMediaPermission)) {
                // Refresh older cache entries to include file modification timestamps.
                scanLocalMusic()
            }
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Settings.canDrawOverlays(context)) {
            floatingOverlayEnabled = true
            folderPreferences.edit().putBoolean("floating_overlay_enabled", true).apply()
            Toast.makeText(context, "悬浮窗权限已开启；试听时会显示在其他应用上方。", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "未授予悬浮窗权限，播放器仍可在应用内使用。", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { previewPlayer.value?.release() }
            previewPlayer.value = null
        }
    }

    val mediaNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "未授予通知权限时，部分手机可能不在下拉通知栏显示播放卡片；可在系统设置中为音笺开启通知。",
                Toast.LENGTH_LONG,
            ).show()
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

    val busy = isRestoringCaches || isSyncing || isLoadingPage || isSearchingFlac || isResolvingUrl || isDownloading

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
                    val syncMode = inputMode
                    val loaded = withTimeout(28_000L) {
                        withContext(Dispatchers.IO) { playlistProvider.load(input, likedByUserId = syncMode == "liked", offset = 0, limit = 100) }
                    }
                    withContext(Dispatchers.IO) {
                        MusicMetaPersistence.savePlaylist(folderPreferences, syncMode, loaded)
                    }
                    if (syncMode == "liked") likedPlaylist = loaded else publicPlaylist = loaded
                    selectedTrack = null
                    flacResults = emptyList()
                    setCurrentPage(1)
                    jumpPageInput = "1"
                    manualSourceSearchActive = false
                    pageCache = pageCache.filterKeys { !it.startsWith("$syncMode:") }
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

    val searchTrackSource: (MusicTrack, Boolean) -> Unit = { track, playFirstMatch ->
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
                    val cookie = CookieManager.getInstance().getCookie(FLAC_SITE_HOME)
                    val results = withTimeout(28_000L) {
                        withContext(Dispatchers.IO) {
                            flacProvider.search(
                                keyword = keyword,
                                platform = flacPlatform,
                                backend = sourceProvider,
                                cookie = cookie,
                                userAgent = fallbackUserAgent,
                                fallbackKeyword = track.title,
                            )
                        }
                    }
                    flacResults = results.take(8)
                    if (playFirstMatch && flacResults.isNotEmpty()) {
                        playPreviewRef.value(flacResults.first())
                        status = "正在播放《" + track.title + "》的第一个匹配音源。"
                    } else {
                        status = if (flacResults.isEmpty()) {
                            "没有找到匹配音源。可以切换音源，或检查歌名后重试。"
                        } else {
                            "找到 " + flacResults.size + " 个候选版本，请核对歌手和专辑后下载。"
                        }
                    }
                    scope.launch {
                        val rowIndex = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == "track-" + track.id }?.index
                        if (rowIndex != null) listState.animateScrollToItem(rowIndex)
                    }
                } catch (error: Exception) {
                    if (isGdStudioNoCandidatesError(error)) {
                        status = "GD Studio 暂无《${track.title}》的候选音源。可尝试仅用歌名搜索，或切换音乐平台 / API 服务后重试。"
                    } else {
                        val detail = error.message?.takeIf { it.isNotBlank() } ?: "音源服务暂不可用"
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
                    }
                } finally {
                    isSearchingFlac = false
                }
            }
        }
    }

    val queueTrackForNext: (MusicTrack) -> Unit = { track ->
        if (isSearchingFlac || isResolvingUrl || isDownloading || isPreviewLoading) {
            status = "当前歌曲或音源正在准备，请稍候再排队。"
        } else {
            isSearchingFlac = true
            val requestedMode = inputMode
            val requestedPlaylistId = playlist?.id.takeIf { requestedMode == "playlist" || requestedMode == "liked" }
            scope.launch {
                try {
                    val cookie = CookieManager.getInstance().getCookie(FLAC_SITE_HOME)
                    val matches = withTimeout(28_000L) {
                        withContext(Dispatchers.IO) {
                            flacProvider.search(
                                keyword = listOf(track.title, track.artists).filter { it.isNotBlank() }.joinToString(" "),
                                platform = flacPlatform,
                                backend = sourceProvider,
                                cookie = cookie,
                                userAgent = fallbackUserAgent,
                                fallbackKeyword = track.title,
                            )
                        }
                    }.take(8)
                    if (matches.isEmpty()) {
                        status = "没有找到《${track.title}》的可用音源，未加入下一首。"
                    } else {
                        flacResults = matches
                        manualSourceSearchActive = false
                        if (previewSong == null || previewPlayer.value == null) {
                            selectedTrack = track
                            previewCurrentTrack = track
                            previewCurrentPlaylistId = requestedPlaylistId
                            status = "当前没有正在播放的歌曲，立即开始播放《${track.title}》。"
                            playPreviewRef.value(matches.first())
                        } else {
                            queuedNextPreview = matches.first()
                            queuedNextTrack = track
                            queuedNextPlaylistId = requestedPlaylistId
                            queuedNextMode = requestedMode
                            status = "已将《${track.title}》设为下一首；当前歌曲播放结束后自动切换。"
                        }
                    }
                } catch (error: Exception) {
                    status = "为《${track.title}》排入下一首失败：" + (error.message?.takeIf { it.isNotBlank() } ?: "请稍后重试")
                    captureError("下一首排队", error)
                } finally {
                    isSearchingFlac = false
                }
            }
        }
    }

    val queueLocalTrackForNext: (LocalAudioTrack, List<LocalAudioTrack>) -> Unit = { track, tracks ->
        val nextItem = FlacSiteSong(track.uri.toString(), "local", track.title, track.artist, track.album, "", track.durationMs.toString(), track.coverUri)
        previewQueue = tracks.map { FlacSiteSong(it.uri.toString(), "local", it.title, it.artist, it.album, "", it.durationMs.toString(), it.coverUri) }
        if (isPreviewLoading || isSearchingFlac || isResolvingUrl || isDownloading) {
            status = "当前歌曲或音源正在准备，请稍候再排队。"
        } else if (previewSong == null || previewPlayer.value == null) {
            selectedTrack = null
            previewCurrentTrack = null
            previewCurrentPlaylistId = null
            status = "当前没有正在播放的歌曲，立即开始播放本地歌曲《${track.title}》。"
            playPreviewRef.value(nextItem)
        } else {
            queuedNextPreview = nextItem
            queuedNextTrack = null
            queuedNextPlaylistId = null
            queuedNextMode = "local"
            status = "已将本地歌曲《${track.title}》设为下一首；当前歌曲结束后播放。"
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
                    val cookie = CookieManager.getInstance().getCookie(FLAC_SITE_HOME)
                    val results = withTimeout(28_000L) {
                        withContext(Dispatchers.IO) {
                            flacProvider.search(
                                keyword = keyword,
                                platform = flacPlatform,
                                backend = sourceProvider,
                                cookie = cookie,
                                userAgent = fallbackUserAgent,
                            )
                        }
                    }
                    flacResults = results.take(8)
                    status = if (flacResults.isEmpty()) {
                        "没有找到匹配音源。可以切换音源平台或检查关键词后重试。"
                    } else {
                        "找到 " + flacResults.size + " 个候选版本，请核对歌手和专辑后下载。"
                    }
                } catch (error: Exception) {
                        val detail = error.message?.takeIf { it.isNotBlank() } ?: "音源服务暂不可用"
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
        previewPlaybackGeneration += 1L
        val player = previewPlayer.value
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        previewPlayer.value = null
        previewSong = null
        previewCurrentTrack = null
        previewCurrentPlaylistId = null
        previewQueue = emptyList()
        queuedNextPreview = null
        queuedNextTrack = null
        queuedNextPlaylistId = null
        queuedNextMode = null
        previewCoverUrl = ""
        previewLyrics = ""
        previewTranslatedLyrics = ""
        isPreviewLoading = false
        isPreviewPlaying = false
        isPreviewLyricsLoading = false
        showPreviewDetails = floatingLyricsExpandedByDefault
        showTranslatedPreviewLyric = floatingLyricsTranslatedByDefault
        previewPositionMs = 0
        previewDurationMs = 0
        miniPlayerOffset = Offset.Zero
    }
    val playPreview: (FlacSiteSong) -> Unit = { song ->
        val songKey = sourceSongKey(song)
        val recentPlaylistTrack = selectedTrack
        val recentPlaylist = playlist.takeIf { recentPlaylistTrack != null }
        val recentPlaylistMode = inputMode
        val recentPlaylistPosition = if (recentPlaylist != null && recentPlaylistTrack != null) {
            findPlaylistTrackPosition(recentPlaylistTrack.id, recentPlaylist, pageCache, recentPlaylistMode)
        } else null
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
            queuedNextPreview = null
            queuedNextTrack = null
            queuedNextPlaylistId = null
            queuedNextMode = null
            previewPlaybackGeneration += 1L
            val playbackGeneration = previewPlaybackGeneration
            val oldPlayer = previewPlayer.value
            runCatching { oldPlayer?.stop() }
            runCatching { oldPlayer?.release() }
            previewPlayer.value = null
            previewSong = song
            previewCurrentTrack = selectedTrack
            previewCurrentPlaylistId = if (selectedTrack != null) playlist?.id else null
            previewQueue = when {
                flacResults.any { sourceSongKey(it) == songKey } -> flacResults.toList()
                previewQueue.any { sourceSongKey(it) == songKey } -> previewQueue
                else -> listOf(song)
            }
            previewCoverUrl = song.coverUrl
            previewLyrics = ""
            previewTranslatedLyrics = ""
            showTranslatedPreviewLyric = floatingLyricsTranslatedByDefault
            previewPositionMs = 0
            previewDurationMs = 0
            miniPlayerOffset = Offset.Zero
            isPreviewPlaying = false
            isPreviewLoading = true
            isPreviewLyricsLoading = true
            status = if (song.platform == "local") "正在准备播放本地歌曲《" + song.title + "》……" else "正在准备播放《" + song.title + "》的试听……"
            scope.launch {
                var newPlayer: MediaPlayer? = null
                try {
                    val cookie = if (song.platform == "local") null else CookieManager.getInstance().getCookie(FLAC_SITE_HOME)
                    val (audioUrl, usedGdStudio) = if (song.platform == "local") {
                        song.id to false
                    } else {
                        withContext(Dispatchers.IO) {
                            flacProvider.resolveDownloadUrlForSource(
                                song = song,
                                format = "mp3",
                                bitrate = "320",
                                cookie = cookie,
                                userAgent = fallbackUserAgent,
                            )
                        }
                    }
                    if (playbackGeneration != previewPlaybackGeneration ||
                        previewSong?.let(sourceSongKey) != songKey
                    ) return@launch

                    // The URL may have been resolved by the alternate provider; use the
                    // provider that actually returned the URL, not the original search row.
                    val referer = if (usedGdStudio) {
                        "https://music.gdstudio.xyz/"
                    } else {
                        FLAC_SITE_HOME
                    }
                    val headers = mutableMapOf(
                        "User-Agent" to fallbackUserAgent,
                        "Referer" to referer,
                    )
                    if (!cookie.isNullOrBlank() && !usedGdStudio) {
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
                        if (playbackGeneration == previewPlaybackGeneration &&
                            previewPlayer.value === prepared &&
                            previewSong?.let(sourceSongKey) == songKey
                        ) {
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
                                    recentlyPlayedKeys = rememberRecentTrackKeys(
                                        previousKeys = recentlyPlayedKeys,
                                        sourceKey = songKey,
                                        playlistMode = recentPlaylistMode.takeIf { recentPlaylist != null && recentPlaylistTrack != null },
                                        playlistId = recentPlaylist?.id,
                                        trackId = recentPlaylistTrack?.id,
                                    )
                                    if (recentPlaylistMode.isNotBlank() && recentPlaylist != null && recentPlaylistPosition != null) {
                                        val positionKey = "$recentPlaylistMode:${recentPlaylist.id}"
                                        recentPlaylistPositions = recentPlaylistPositions.toMutableMap().apply {
                                            val updated = (get(positionKey).orEmpty().filterNot { it == recentPlaylistPosition } + recentPlaylistPosition).takeLast(12)
                                            put(positionKey, updated)
                                        }
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
                        if (playbackGeneration != previewPlaybackGeneration ||
                            previewPlayer.value !== completed ||
                            previewSong?.let(sourceSongKey) != songKey
                        ) {
                            runCatching { completed.release() }
                            return@setOnCompletionListener
                        }
                        isPreviewPlaying = false
                        isPreviewLoading = false
                        previewPositionMs = previewDurationMs
                        if (playbackMode == 2) {
                            runCatching {
                                completed.seekTo(0)
                                completed.start()
                                isPreviewPlaying = true
                                isPreviewLoading = false
                                previewPositionMs = 0
                                status = "单曲循环播放《${song.title}》。"
                            }.onFailure {
                                status = "单曲循环失败：" + (it.message ?: "请重试")
                                runCatching { completed.release() }
                                if (previewPlayer.value === completed) previewPlayer.value = null
                            }
                            return@setOnCompletionListener
                        }
                        runCatching { completed.release() }
                        if (previewPlayer.value === completed) previewPlayer.value = null
                        val pendingNext = queuedNextPreview
                        if (pendingNext != null) {
                            val pendingTrack = queuedNextTrack
                            val pendingPlaylistId = queuedNextPlaylistId
                            val pendingMode = queuedNextMode
                            queuedNextPreview = null
                            queuedNextTrack = null
                            queuedNextPlaylistId = null
                            queuedNextMode = null
                            if (pendingMode == "local") {
                                selectedTrack = null
                                previewCurrentTrack = null
                                previewCurrentPlaylistId = null
                            } else {
                                selectedTrack = pendingTrack
                                previewCurrentTrack = pendingTrack
                                previewCurrentPlaylistId = pendingPlaylistId
                            }
                            status = "正在播放已排队的下一首：《${pendingNext.title}》。"
                            playPreviewRef.value(pendingNext)
                            return@setOnCompletionListener
                        }
                        if (song.platform == "local" || (previewCurrentTrack != null && previewCurrentPlaylistId != null) || previewQueue.size > 1) {
                            status = if (song.platform == "local") {
                                "本地歌曲播放结束，正在切换下一首。"
                            } else {
                                "《" + song.title + "》播放结束，正在切换下一首。"
                            }
                            previewNavigationHandler?.invoke(1)
                        } else {
                            status = "播放结束：《" + song.title + "》。"
                        }
                    }
                    player.setOnErrorListener { failed, what, extra ->
                        if (playbackGeneration != previewPlaybackGeneration ||
                            previewPlayer.value !== failed ||
                            previewSong?.let(sourceSongKey) != songKey
                        ) {
                            runCatching { failed.release() }
                            return@setOnErrorListener true
                        }
                        isPreviewPlaying = false
                        isPreviewLoading = false
                        status = "试听失败，音源可能不支持在线播放。"
                        captureError("试听音源", IOException("MediaPlayer error $what/$extra"))
                        runCatching { failed.release() }
                        if (previewPlayer.value === failed) previewPlayer.value = null
                        true
                    }
                    if (song.platform == "local") {
                        player.setDataSource(context, Uri.parse(audioUrl))
                    } else {
                        player.setDataSource(context, Uri.parse(audioUrl), headers)
                    }
                    previewPlayer.value = player
                    player.prepareAsync()

                    if (song.platform != "local") scope.launch {
                        val cover = withContext(Dispatchers.IO) { resolveSourceCoverUrl(song) }
                        if (playbackGeneration == previewPlaybackGeneration &&
                            previewSong?.let(sourceSongKey) == songKey && cover.isNotBlank()
                        ) {
                            previewCoverUrl = cover
                        }
                    }
                    scope.launch {
                        val lyrics = withContext(Dispatchers.IO) {
                            val nativeNetEaseId = if (song.platform == "wyy") {
                                song.id.toLongOrNull()?.takeIf { it > 0L } ?: 0L
                            } else 0L
                            val embeddedLocalLyrics = if (song.platform == "local") {
                                runCatching {
                                    LocalMusicScanner.readEmbeddedLyricsPair(context, Uri.parse(song.id))
                                }.getOrDefault(PreviewLyrics())
                            } else PreviewLyrics()
                            var tracks = if (song.platform == "local") {
                                embeddedLocalLyrics
                            } else {
                                runCatching {
                                    metadataProvider.resolveNetEaseLyrics(
                                        title = song.title,
                                        artist = song.artist,
                                        album = song.album,
                                        durationMs = 0L,
                                        neteaseId = nativeNetEaseId,
                                    )
                                }.getOrDefault(PreviewLyrics())
                            }

                            // Prefer NetEase lyrics for both local and streamed tracks, filling gaps
                            // from the selected audio source only when NetEase lyrics are incomplete.
                            if (song.platform != "local" &&
                                (tracks.original.isBlank() || tracks.translated.isBlank())
                            ) {
                                val sourceLyrics = runCatching {
                                    flacProvider.fetchLyrics(song, cookie, fallbackUserAgent)
                                }.getOrDefault(PreviewLyrics())
                                tracks = PreviewLyrics(
                                    original = tracks.original.ifBlank { sourceLyrics.original },
                                    translated = tracks.translated.ifBlank { sourceLyrics.translated },
                                )
                            }
                            if (song.platform != "local" && tracks.original.isBlank()) {
                                tracks = tracks.copy(
                                    original = runCatching { metadataProvider.resolveSourceLyrics(song) }
                                        .getOrDefault("").trim(),
                                )
                            }
                            tracks
                        }
                        if (playbackGeneration == previewPlaybackGeneration &&
                            previewSong?.let(sourceSongKey) == songKey
                        ) {
                            previewLyrics = lyrics.original.trim()
                            previewTranslatedLyrics = lyrics.translated.trim()
                            showTranslatedPreviewLyric = floatingLyricsTranslatedByDefault
                            isPreviewLyricsLoading = false
                        }
                    }
                } catch (error: Exception) {
                    runCatching { newPlayer?.release() }
                    if (previewPlayer.value === newPlayer) previewPlayer.value = null
                    if (playbackGeneration == previewPlaybackGeneration &&
                        previewSong?.let(sourceSongKey) == songKey
                    ) {
                        isPreviewLoading = false
                        isPreviewLyricsLoading = false
                        val chainMessage = generateSequence(error as Throwable?) { it.cause }
                            .mapNotNull { it.message }
                            .joinToString(" ")
                        val candidateUnavailable =
                            chainMessage.contains("下载站无法解析该候选歌曲的下载地址") ||
                                chainMessage.contains("Song not found", ignoreCase = true) ||
                                chainMessage.contains("下载站没有返回有效的下载地址") ||
                                chainMessage.contains("GD Studio API 请求失败：HTTP 503")
                        val queue = previewQueue
                        val currentIndex = queue.indexOfFirst { sourceSongKey(it) == songKey }
                        val nextCandidate = if (currentIndex >= 0) {
                            queue.drop(currentIndex + 1).firstOrNull()
                        } else null
                        if (candidateUnavailable && nextCandidate != null) {
                            status = "当前候选音源暂不可用，正在自动尝试下一个版本：《${nextCandidate.title}》。"
                            playPreviewRef.value(nextCandidate)
                        } else {
                            status = "试听失败：" + (error.message?.takeIf { it.isNotBlank() } ?: "音源暂不可用")
                            captureError("解析试听音源", error)
                        }
                    }
                }
            }
        }
    }

    SideEffect {
        playPreviewRef.value = playPreview
    }

    SideEffect {
        FloatingMiniPlayerBridge.onCommand = { command, seekPosition ->
            when (command) {
                FloatingMiniPlayerService.COMMAND_CLOSE_OVERLAY -> {
                    floatingOverlayDismissedForSession = true
                    FloatingMiniPlayerService.stop(context)
                }
                // Keep the v1.29 session-scoped close behavior separate from the
                // system media card's global on/off switch.
                FloatingMiniPlayerService.COMMAND_TOGGLE_OVERLAY -> {
                    if (floatingOverlayEnabled) {
                        floatingOverlayEnabled = false
                        folderPreferences.edit().putBoolean("floating_overlay_enabled", false).apply()
                        FloatingMiniPlayerService.stop(context)
                        Toast.makeText(context, "已关闭悬浮窗，试听继续播放。", Toast.LENGTH_SHORT).show()
                    } else if (Settings.canDrawOverlays(context)) {
                        floatingOverlayEnabled = true
                        folderPreferences.edit().putBoolean("floating_overlay_enabled", true).apply()
                        Toast.makeText(context, "已开启悬浮窗。", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "需要先授予显示在其他应用上层的权限。", Toast.LENGTH_SHORT).show()
                        overlayPermissionLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                        )
                    }
                }
                FloatingMiniPlayerService.COMMAND_TOGGLE_PLAY -> previewSong?.let { playPreview(it) }
                FloatingMiniPlayerService.COMMAND_MEDIA_PLAY -> {
                    val player = previewPlayer.value
                    when {
                        isPreviewLoading -> Unit
                        player != null && previewSong != null -> runCatching {
                            if (!player.isPlaying) {
                                player.start()
                                isPreviewPlaying = true
                                previewPositionMs = player.currentPosition.coerceAtLeast(0)
                                status = "正在播放《" + previewSong?.title.orEmpty() + "》。"
                            }
                        }.onFailure { status = "继续播放失败：" + (it.message ?: "请重试") }
                        previewSong != null -> playPreviewRef.value(previewSong!!)
                    }
                }
                FloatingMiniPlayerService.COMMAND_MEDIA_PAUSE -> {
                    val player = previewPlayer.value
                    if (player != null) runCatching {
                        if (player.isPlaying) {
                            player.pause()
                            isPreviewPlaying = false
                            previewPositionMs = player.currentPosition.coerceAtLeast(0)
                            status = "播放已暂停。"
                        }
                    }.onFailure { status = "暂停播放失败：" + (it.message ?: "请重试") }
                }
                FloatingMiniPlayerService.COMMAND_PREVIOUS -> previewNavigationHandler?.invoke(-1)
                FloatingMiniPlayerService.COMMAND_NEXT -> previewNavigationHandler?.invoke(1)
                FloatingMiniPlayerService.COMMAND_TOGGLE_DETAILS -> showPreviewDetails = !showPreviewDetails
                FloatingMiniPlayerService.COMMAND_TOGGLE_TRANSLATION -> showTranslatedPreviewLyric = !showTranslatedPreviewLyric
                FloatingMiniPlayerService.COMMAND_TOGGLE_PLAYBACK_MODE -> cyclePlaybackMode()
                FloatingMiniPlayerService.COMMAND_SEEK -> {
                    val target = seekPosition.coerceAtLeast(0)
                    previewPositionMs = target
                    runCatching { previewPlayer.value?.seekTo(target) }
                }
                FloatingMiniPlayerService.COMMAND_CLOSE -> {
                    // Stop playback without changing the user's overlay preference.
                    stopPreview()
                    FloatingMiniPlayerService.stop(context)
                    MediaPlaybackService.stop(context)
                }
                FloatingMiniPlayerService.COMMAND_CLOSE_APP -> {
                    // A five-second hold is an explicit full exit: stop playback, remove both
                    // services (including the media notification/session), and remove the task.
                    floatingOverlayDismissedForSession = true
                    fallbackMiniPlayerDismissedForSession = true
                    stopPreview()
                    FloatingMiniPlayerService.stop(context)
                    MediaPlaybackService.stop(context)
                    (context as? MainActivity)?.finishAndRemoveTask()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { FloatingMiniPlayerBridge.onCommand = null }
    }

    LaunchedEffect(
        floatingOverlayEnabled,
        floatingOverlayDismissedForSession,
        previewSong?.platform,
        previewSong?.id,
        previewCoverUrl,
        previewLyrics,
        previewTranslatedLyrics,
        previewDurationMs,
        previewCurrentTrack?.id,
        previewCurrentPlaylistId,
        likedPlaylist?.id,
        playbackMode,
        isPreviewPlaying,
        isPreviewLoading,
        showPreviewDetails,
        showTranslatedPreviewLyric,
        floatingLyricsFontSizeSp,
    ) {
        val song = previewSong
        if (floatingOverlayEnabled && !floatingOverlayDismissedForSession && song != null && Settings.canDrawOverlays(context)) {
            FloatingMiniPlayerService.showOrUpdate(
                context = context,
                coverUrl = previewCoverUrl.ifBlank { song.coverUrl },
                title = song.title,
                artist = song.artist,
                originalLyrics = previewLyrics,
                translatedLyrics = previewTranslatedLyrics,
                durationMs = previewDurationMs,
                positionMs = previewPositionMs,
                isPlaying = isPreviewPlaying,
                isLoading = isPreviewLoading,
                expanded = showPreviewDetails,
                showTranslation = showTranslatedPreviewLyric,
                lyricsFontSizeSp = floatingLyricsFontSizeSp,
                playbackMode = playbackMode,
                openMode = when {
                    song.platform == "local" -> "local"
                    previewCurrentPlaylistId != null && likedPlaylist?.id == previewCurrentPlaylistId -> "liked"
                    previewCurrentPlaylistId != null -> "playlist"
                    else -> "search"
                },
                openTrackId = if (song.platform == "local") song.id else previewCurrentTrack?.id?.toString().orEmpty(),
                openPlaylistId = previewCurrentPlaylistId ?: 0L,
            )
        } else {
            FloatingMiniPlayerService.stop(context)
        }
    }

    LaunchedEffect(floatingOverlayEnabled, previewSong?.id, previewPositionMs) {
        val song = previewSong
        if (floatingOverlayEnabled && !floatingOverlayDismissedForSession && song != null && Settings.canDrawOverlays(context)) {
            // Progress uses a lightweight service action, not a foreground-service restart.
            FloatingMiniPlayerService.updatePosition(context, previewPositionMs)
        }
    }

    LaunchedEffect(
        previewSong?.id,
        previewPositionMs,
        previewLyrics,
        previewTranslatedLyrics,
        showTranslatedPreviewLyric,
    ) {
        val song = previewSong
        if (song == null) {
            mediaCurrentLyric = ""
        } else {
            val showingTranslation = showTranslatedPreviewLyric && previewTranslatedLyrics.isNotBlank()
            val activeLyrics = if (showingTranslation) previewTranslatedLyrics else previewLyrics
            val currentLine = currentLyricLine(
                activeLyrics = activeLyrics,
                originalLyrics = previewLyrics,
                positionMs = previewPositionMs + 180,
                isTranslation = showingTranslation,
            )
            if (mediaCurrentLyric != currentLine) mediaCurrentLyric = currentLine
        }
    }

    // The notification and lock-screen session mirror the same in-app player state.
    // Lyric changes stay in the in-app / floating lyrics UI and do not rebuild the system card.
    LaunchedEffect(
        previewSong?.id,
        previewSong?.title,
        previewSong?.artist,
        previewCoverUrl,
        previewDurationMs,
        isPreviewPlaying,
        isPreviewLoading,
        floatingOverlayEnabled,
        playbackMode,
    ) {
        val song = previewSong
        if (song == null) {
            MediaPlaybackService.stop(context)
        } else {
            MediaPlaybackService.update(
                context = context,
                title = song.title,
                artist = song.artist,
                coverUri = previewCoverUrl.ifBlank { song.coverUrl },
                durationMs = previewDurationMs,
                positionMs = previewPositionMs,
                isPlaying = isPreviewPlaying,
                isLoading = isPreviewLoading,
                overlayEnabled = floatingOverlayEnabled,
                playbackMode = playbackMode,
            )
        }
    }

    LaunchedEffect(previewSong?.id, previewPositionMs, previewDurationMs) {
        if (previewSong != null) {
            MediaPlaybackService.updatePosition(context, previewPositionMs, previewDurationMs)
        }
    }

    LaunchedEffect(previewSong?.id) {
        if (previewSong != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !folderPreferences.getBoolean("media_notification_permission_requested", false)
        ) {
            folderPreferences.edit().putBoolean("media_notification_permission_requested", true).apply()
            mediaNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(floatingOverlayEnabled) {
        // Explicit global off/on toggles restore an overlay dismissed from media controls.
        floatingOverlayDismissedForSession = false
        if (floatingOverlayEnabled && !Settings.canDrawOverlays(context)) {
            // Keep the preference: granting the permission again should restore the overlay.
            floatingOverlayEnabled = false
        }
    }

    LaunchedEffect(previewSong?.platform, previewSong?.id, floatingOverlayEnabled) {
        fallbackMiniPlayerDismissedForSession = false
    }
    val playAdjacentPreview: (Int) -> Unit = { direction ->
        previewNavigationHandler?.invoke(direction)
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
            delay(250)
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
                    val cookie = CookieManager.getInstance().getCookie(FLAC_SITE_HOME)
                    val (directUrl, usedGdStudio) = withContext(Dispatchers.IO) {
                        flacProvider.resolveDownloadUrlForSource(
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
                    if (usedGdStudio) {
                        status = "本次由 GD Studio 解析下载地址。"
                    }
                    isResolvingUrl = false
                    manualSourceSearchActive = false
                    latestDownloadAndSave(
                        directUrl,
                        fallbackUserAgent,
                        null,
                        if (selectedQuality == "FLAC") "audio/flac" else "audio/mpeg",
                        (if (usedGdStudio) "https://music.gdstudio.xyz/" else FLAC_SITE_HOME),
                        if (usedGdStudio) null else cookie,
                        selectedTrack ?: metadataTrackForSource(song),
                        song,
                    )
                } catch (error: Exception) {
                        val detail = error.message?.takeIf { it.isNotBlank() } ?: "音源服务暂不可用"
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
        playlistBatchCacheKey(mode, id, size, page)
    }

    LaunchedEffect(pageCache, isRestoringCaches) {
        if (isRestoringCaches) return@LaunchedEffect
        delay(350)
        val snapshot = pageCache
        withContext(Dispatchers.IO) {
            MusicMetaPersistence.savePageCache(folderPreferences, snapshot)
        }
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
    // Playback can outlive a tab switch. Keep previous/next bound to the playlist that started playback.
    val previewPlaylistModeForNavigation = previewPlaylistModeForTrack(
        currentPlaylistId = previewCurrentPlaylistId,
        likedPlaylistId = likedPlaylist?.id,
        publicPlaylistId = publicPlaylist?.id,
        fallbackMode = inputMode,
    )
    val previewPlaylistForNavigation = when (previewPlaylistModeForNavigation) {
        "liked" -> likedPlaylist
        "playlist" -> publicPlaylist
        else -> null
    }
    val previewFilterTextForNavigation = if (previewPlaylistModeForNavigation == "liked") likedFilterText else publicFilterText
    val previewKnownTracksForNavigation = previewPlaylistForNavigation?.let { active ->
        val prefix = "$previewPlaylistModeForNavigation:${active.id}:"
        val cached = pageCache.entries
            .filter { it.key.startsWith(prefix) }
            .sortedBy { entry ->
                val parts = entry.key.split(":")
                val size = parts.getOrNull(2)?.toIntOrNull() ?: 100
                val page = parts.getOrNull(3)?.toIntOrNull() ?: 1
                (page - 1) * size
            }
            .flatMap { it.value }
        (active.tracks + cached).distinctBy { it.id }
    }.orEmpty()
    val previewFilteredTracksForNavigation = previewKnownTracksForNavigation.filter {
        previewFilterTextForNavigation.isBlank() ||
            it.title.contains(previewFilterTextForNavigation.trim(), ignoreCase = true) ||
            it.artists.contains(previewFilterTextForNavigation.trim(), ignoreCase = true) ||
            it.album.contains(previewFilterTextForNavigation.trim(), ignoreCase = true)
    }
    val navigatePlaylistPreview: (Int) -> Unit = remember(
        previewKnownTracksForNavigation,
        previewFilteredTracksForNavigation,
        previewPlaylistForNavigation?.id,
        previewPlaylistModeForNavigation,
        previewFilterTextForNavigation,
        pageSize,
        pageCache,
        playbackMode,
        recentlyPlayedKeys,
        recentPlaylistPositions,
        previewCurrentTrack?.id,
        previewCurrentPlaylistId,
        isSearchingFlac,
        isResolvingUrl,
        isDownloading,
        isPreviewLoading,
    ) {
        { direction ->
            if (isSearchingFlac || isResolvingUrl || isDownloading || isPreviewLoading) {
                status = "正在准备当前试听，请稍候。"
            } else {
                val currentTrack = previewCurrentTrack
                val currentPlaylist = previewPlaylistForNavigation
                val activePlaylistId = previewCurrentPlaylistId
                if (currentTrack == null || currentPlaylist == null || activePlaylistId != currentPlaylist.id) {
                    status = "当前播放歌曲不在已加载的原歌单中，无法定位相邻曲目；不会切换到直接搜歌队列。"
                } else {
                    val modeAtRequest = previewPlaylistModeForNavigation
                    val filterAtRequest = previewFilterTextForNavigation
                    val playlistIdAtRequest = currentPlaylist.id
                    val expectedTrackId = currentTrack.id
                    val currentIndex: Int? = if (filterAtRequest.isNotBlank()) {
                        previewFilteredTracksForNavigation.indexOfFirst { it.id == expectedTrackId }.takeIf { it >= 0 }
                    } else {
                        val fromBase = currentPlaylist.tracks.indexOfFirst { it.id == expectedTrackId }
                        val locatedIndex = if (fromBase >= 0) {
                            fromBase
                        } else {
                            pageCache.entries.asSequence()
                                .filter { it.key.startsWith("$modeAtRequest:$playlistIdAtRequest:") }
                                .mapNotNull { entry ->
                                    val parts = entry.key.split(":")
                                    val cacheSize = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                                    val cachePage = parts.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
                                    val localIndex = entry.value.indexOfFirst { it.id == expectedTrackId }
                                    if (localIndex < 0) null else (cachePage - 1) * cacheSize + localIndex
                                }
                                .minOrNull() ?: -1
                        }
                        locatedIndex.takeIf { it >= 0 }
                    }
                    val navigationQueueSize = if (filterAtRequest.isNotBlank()) {
                        previewFilteredTracksForNavigation.size
                    } else currentPlaylist.totalTrackCount
                    val targetPosition: Int? = when {
                        currentIndex == null -> null
                        playbackMode == 1 && direction > 0 && navigationQueueSize > 1 -> run {
                            val playlistRecentPrefix = "playlist:$modeAtRequest:$playlistIdAtRequest:"
                            val recentPlaylistKeys = recentlyPlayedKeys
                                .filter { it.startsWith(playlistRecentPrefix) }
                                .toSet()
                            val recentPositions = if (filterAtRequest.isBlank()) {
                                recentPlaylistPositions["$modeAtRequest:$playlistIdAtRequest"].orEmpty().toSet()
                            } else emptySet()
                            shuffleCandidatePositions(
                                totalCount = navigationQueueSize,
                                currentIndex = currentIndex ?: -1,
                                recentPositions = recentPositions,
                                recentTrackKeys = recentPlaylistKeys,
                                trackKeyAt = { position ->
                                    val candidate = if (filterAtRequest.isNotBlank()) {
                                        previewFilteredTracksForNavigation.getOrNull(position)
                                    } else {
                                        playlistTrackAtPosition(position, currentPlaylist, pageCache, modeAtRequest)
                                    }
                                    candidate?.let {
                                        MusicMetaPersistence.playlistTrackKey(modeAtRequest, playlistIdAtRequest, it.id)
                                    }
                                },
                            ).randomOrNull()
                        }
                        else -> currentIndex + direction
                    }
                    if (targetPosition == null) {
                        status = "当前曲目不在已加载的歌单列表中，无法定位上一首 / 下一首。"
                    } else if (filterAtRequest.isBlank() && targetPosition !in 0 until currentPlaylist.totalTrackCount) {
                        status = if (direction < 0) "已经是歌单第一首。" else "已经是歌单最后一首。"
                    } else if (filterAtRequest.isNotBlank() && targetPosition !in previewFilteredTracksForNavigation.indices) {
                        status = if (direction < 0) "已经是当前筛选结果的第一首。" else "已经是当前筛选结果的最后一首。"
                    } else {
                        val cachePrefix = "$modeAtRequest:$playlistIdAtRequest:"
                        val targetFromLoaded = if (filterAtRequest.isNotBlank()) {
                            previewFilteredTracksForNavigation.getOrNull(targetPosition)
                        } else {
                            currentPlaylist.tracks.getOrNull(targetPosition) ?:
                                pageCache.entries.asSequence()
                                    .filter { it.key.startsWith(cachePrefix) }
                                    .sortedBy { entry ->
                                        val parts = entry.key.split(":")
                                        if (parts.getOrNull(2)?.toIntOrNull() == pageSize) 0 else 1
                                    }
                                    .mapNotNull { entry ->
                                        val parts = entry.key.split(":")
                                        val cacheSize = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                                        val cachePage = parts.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
                                        val batchStart = (cachePage - 1) * cacheSize
                                        val localTarget = targetPosition - batchStart
                                        entry.value.getOrNull(localTarget)
                                    }
                                    .firstOrNull()
                        }
                        val searchAndPlay = { targetTrack: MusicTrack ->
                            isSearchingFlac = true
                            status = "正在为《${targetTrack.title}》查找第一个匹配音源……"
                            scope.launch {
                                try {
                                    val cookie = CookieManager.getInstance().getCookie(FLAC_SITE_HOME)
                                    val matches = withTimeout(28_000L) {
                                        withContext(Dispatchers.IO) {
                                            flacProvider.search(
                                                keyword = listOf(targetTrack.title, targetTrack.artists)
                                                    .filter { it.isNotBlank() }.joinToString(" "),
                                                platform = flacPlatform,
                                                backend = sourceProvider,
                                                cookie = cookie,
                                                userAgent = fallbackUserAgent,
                                                fallbackKeyword = targetTrack.title,
                                            )
                                        }
                                    }.take(8)
                                    if (previewCurrentTrack?.id != expectedTrackId ||
                                        previewCurrentPlaylistId != playlistIdAtRequest
                                    ) {
                                        isSearchingFlac = false
                                        return@launch
                                    }
                                    if (matches.isEmpty()) {
                                        isSearchingFlac = false
                                        status = "《${targetTrack.title}》没有找到可用音源，保留当前试听。"
                                    } else {
                                        selectedTrack = targetTrack
                                        previewCurrentTrack = targetTrack
                                        previewCurrentPlaylistId = playlistIdAtRequest
                                        flacResults = matches
                                        manualSourceSearchActive = false
                                        isSearchingFlac = false
                                        status = "正在播放《${targetTrack.title}》的第一个匹配音源。"
                                        playPreview(matches.first())
                                        // Restore the playlist context after playPreview updates UI state.
                                        previewCurrentTrack = targetTrack
                                        previewCurrentPlaylistId = playlistIdAtRequest
                                    }
                                } catch (error: Exception) {
                                    // Always release the busy gate for this navigation when the
                                    // active track is still ours. A stale request must not leave
                                    // "下一首" locked forever after a page/context change.
                                    if (previewCurrentTrack?.id == expectedTrackId &&
                                        previewCurrentPlaylistId == playlistIdAtRequest
                                    ) {
                                        isSearchingFlac = false
                                        if (inputMode == modeAtRequest) {
                                            if (isGdStudioNoCandidatesError(error)) {
                                                status = "GD Studio 暂无《${targetTrack.title}》的可用候选，当前试听保持不变。可稍后重试或切换音乐平台 / API 服务。"
                                            } else {
                                                status = "查找上一首 / 下一首音源失败：" + (error.message?.takeIf { it.isNotBlank() } ?: "请重试")
                                                captureError("上一首 / 下一首试听", error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (targetFromLoaded != null) {
                            searchAndPlay(targetFromLoaded)
                        } else if (filterAtRequest.isNotBlank()) {
                            status = "目标曲目尚未加载，请先翻到对应页。"
                        } else {
                            isSearchingFlac = true
                            status = "正在读取歌单中的下一首曲目……"
                            scope.launch {
                                try {
                                    val batchOffset = (targetPosition / 100) * 100
                                    val batchLimit = minOf(100, currentPlaylist.totalTrackCount - batchOffset)
                                    val loadedPage = withTimeout(28_000L) {
                                        withContext(Dispatchers.IO) {
                                            playlistProvider.load(
                                                input = playlistIdAtRequest.toString(),
                                                likedByUserId = false,
                                                offset = batchOffset,
                                                limit = batchLimit.coerceAtLeast(1),
                                            )
                                        }
                                    }
                                    if (previewCurrentTrack?.id != expectedTrackId ||
                                        previewCurrentPlaylistId != playlistIdAtRequest
                                    ) {
                                        isSearchingFlac = false
                                        return@launch
                                    }
                                    val batchNumber = batchOffset / 100 + 1
                                    val batchKey = pageCacheKey(modeAtRequest, playlistIdAtRequest.toString(), 100, batchNumber)
                                    pageCache = pageCache + (batchKey to loadedPage.tracks)
                                    val targetTrack = loadedPage.tracks.getOrNull(targetPosition - batchOffset)
                                    isSearchingFlac = false
                                    if (targetTrack != null) {
                                        searchAndPlay(targetTrack)
                                    } else {
                                        status = "歌单未返回目标曲目，无法自动切换。"
                                    }
                                } catch (error: Exception) {
                                    isSearchingFlac = false
                                    status = "读取下一首曲目失败：" + (error.message?.takeIf { it.isNotBlank() } ?: "请重试")
                                    captureError("读取相邻歌单曲目", error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val navigatePreview: (Int) -> Unit = { direction ->
        val activeSong = previewSong
        when {
            isSearchingFlac || isResolvingUrl || isDownloading || isPreviewLoading -> {
                status = "正在准备当前歌曲，请稍候。"
            }
            activeSong == null -> status = "当前没有正在播放的歌曲。"
            activeSong.platform == "local" -> {
                val queue = previewQueue.filter { it.platform == "local" }
                val currentIndex = queue.indexOfFirst { it.id == activeSong.id }
                val next = if (playbackMode == 1 && direction > 0 && queue.size > 1) {
                    shuffleCandidates(queue, sourceSongKey(activeSong), recentlyPlayedKeys.toSet(), sourceSongKey).randomOrNull()
                } else {
                    queue.getOrNull(currentIndex + direction)
                }
                if (next != null) {
                    status = "正在切换本地歌曲：" + next.title
                    playPreviewRef.value(next)
                } else {
                    status = if (direction < 0) "已经是本地列表第一首。" else "已经是本地列表最后一首。"
                }
            }
            previewCurrentTrack != null && previewCurrentPlaylistId != null &&
                (likedPlaylist?.id == previewCurrentPlaylistId || publicPlaylist?.id == previewCurrentPlaylistId) ->
                navigatePlaylistPreview(direction)
            else -> {
                val queue = previewQueue
                val currentIndex = queue.indexOfFirst { sourceSongKey(it) == sourceSongKey(activeSong) }
                val next = if (playbackMode == 1 && direction > 0 && queue.size > 1) {
                    shuffleCandidates(queue, sourceSongKey(activeSong), recentlyPlayedKeys.toSet(), sourceSongKey).randomOrNull()
                } else queue.getOrNull(currentIndex + direction)
                if (next != null) {
                    status = "正在切换音源队列：" + next.title
                    playPreviewRef.value(next)
                } else {
                    status = if (direction < 0) "当前队列没有上一首。" else "当前队列没有下一首。"
                }
            }
        }
    }
    SideEffect {
        previewNavigationHandler = navigatePreview
    }
    val filteredLocalMusicTracks = sortLocalMusicItems(
        items = localMusicTracks.filter {
            localMusicFilter.isBlank() || it.title.contains(localMusicFilter, true) ||
                it.artist.contains(localMusicFilter, true) || it.album.contains(localMusicFilter, true)
        },
        mode = localMusicSortMode,
        modifiedAtMs = { it.modifiedAtMs },
        title = { it.title },
    )
    val pageItemCount = if (inputMode == "local") filteredLocalMusicTracks.size
        else if (filterText.isBlank()) playlist?.totalTrackCount ?: 0 else filteredKnownTracks.size
    val pageCount = maxOf(1, (pageItemCount + pageSize - 1) / pageSize)
    val switchInputMode: (String) -> Unit = { mode ->
        if (mode in setOf("playlist", "liked", "search", "local") && mode != inputMode && !busy) {
            focusManager.clearFocus()
            inputMode = mode
            folderPreferences.edit().putString("input_mode", mode).apply()
            jumpPageInput = (when (mode) { "liked" -> likedCurrentPage; "local" -> localCurrentPage; else -> publicCurrentPage }).toString()
            selectedTrack = null
            flacResults = emptyList()
            manualSourceSearchActive = false
            status = when (mode) {
                "search" -> "直接搜歌：输入歌名或歌手搜索音源。"
                "liked" -> if (likedPlaylist != null) "已切换到红心歌单。" else "输入网易云用户 ID 同步红心歌单。"
                "local" -> "已切换到本地歌曲列表。"
                else -> if (publicPlaylist != null) "已切换到公开歌单。" else "输入公开歌单链接或 ID 开始。"
            }
        }
    }

    val requestPage: (Int) -> Unit = { requestedPage ->
        focusManager.clearFocus()
        if (inputMode == "local") {
            if (!busy) {
                val targetPage = requestedPage.coerceIn(1, pageCount)
                val pageChanged = targetPage != currentPage
                setCurrentPage(targetPage)
                jumpPageInput = targetPage.toString()
                selectedTrack = null
                flacResults = emptyList()
                if (pageChanged) scope.launch { listState.animateScrollToItem(6) }
                status = "已切换到本地第 $targetPage 页。"
            }
        } else {
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
                                    val batchNumber = batchOffset / 100 + 1
                                    val batchKey = pageCacheKey(targetMode, current.id.toString(), 100, batchNumber)
                                    pageCache = pageCache + (batchKey to loadedPage.tracks)
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
    }
    val activity = context as MainActivity
    val pendingOpenRequest = activity.pendingOverlayOpenRequest
    LaunchedEffect(
        pendingOpenRequest?.requestId,
        inputMode,
        localMusicTracks,
        isRestoringCaches,
        localMusicFilter,
        publicPlaylist?.id,
        likedPlaylist?.id,
        pageSize,
        pageCache,
        isScanningLocalMusic,
        isSyncing,
        busy,
    ) {
        val request = pendingOpenRequest ?: return@LaunchedEffect
        if (isRestoringCaches) return@LaunchedEffect
        val targetMode = request.mode.takeIf { it in setOf("local", "playlist", "liked", "search") }
            ?: if (request.playlistId > 0L && likedPlaylist?.id == request.playlistId) "liked" else "playlist"

        if (inputMode != targetMode) {
            inputMode = targetMode
            folderPreferences.edit().putString("input_mode", targetMode).apply()
            manualSourceSearchActive = false
            return@LaunchedEffect
        }

        if (targetMode == "local") {
            highlightedLocalTrackUri = request.trackId
            if (localMusicTracks.isEmpty()) {
                if (!isScanningLocalMusic) scanLocalMusic()
                return@LaunchedEffect
            }
            val targetIndex = localTrackIndexForOverlayUri(
                orderedTrackUris = filteredLocalMusicTracks.map { it.uri.toString() },
                targetUri = request.trackId,
            )
            if (targetIndex < 0) {
                status = "本地列表中没有找到正在播放的歌曲，可能文件夹已变更。"
                activity.clearOverlayOpenRequest(request.requestId)
                return@LaunchedEffect
            }
            if (localMusicFilter.isNotBlank()) {
                localMusicFilter = ""
                return@LaunchedEffect
            }
            manualSourceSearchActive = false
            val targetPage = targetIndex / pageSize + 1
            localCurrentPage = targetPage
            folderPreferences.edit().putInt("local_current_page", targetPage).apply()
            jumpPageInput = targetPage.toString()
            selectedTrack = null
            flacResults = emptyList()
            withFrameNanos { }
            val itemIndex = 3 + (targetIndex % pageSize)
            runCatching { localListState.animateScrollToItem(itemIndex) }
            status = "已定位到本地歌曲：" + request.title
            activity.clearOverlayOpenRequest(request.requestId)
            return@LaunchedEffect
        }

        if (targetMode == "search") {
            manualSearchQuery = request.title
            manualSourceSearchActive = flacResults.isNotEmpty()
            status = "已打开直接搜歌页：" + request.title
            withFrameNanos { }
            runCatching { searchListState.animateScrollToItem(if (manualSourceSearchActive) 2 else 0) }
            activity.clearOverlayOpenRequest(request.requestId)
            return@LaunchedEffect
        }

        val activePlaylist = if (targetMode == "liked") likedPlaylist else publicPlaylist
        if (activePlaylist == null || activePlaylist.id != request.playlistId) {
            if (busy) return@LaunchedEffect
            if (playlistInput.isBlank()) {
                status = "已打开应用，但还没有加载该歌曲所属的歌单；请先同步歌单后重新使用双指手势定位。"
                activity.clearOverlayOpenRequest(request.requestId)
                return@LaunchedEffect
            }
            syncPlaylist()
            return@LaunchedEffect
        }

        val trackId = request.trackId.toLongOrNull()
        val baseIndex = trackId?.let { id -> activePlaylist.tracks.indexOfFirst { it.id == id } } ?: -1
        val cachedIndex = if (baseIndex >= 0 || trackId == null) null else {
            pageCache.entries.asSequence()
                .filter { it.key.startsWith("$targetMode:${activePlaylist.id}:") }
                .mapNotNull { entry ->
                    val parts = entry.key.split(":")
                    val cacheSize = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                    val cachePage = parts.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
                    val localIndex = entry.value.indexOfFirst { it.id == trackId }
                    if (localIndex < 0) null else (cachePage - 1) * cacheSize + localIndex
                }
                .minOrNull()
        }
        val targetIndex = if (baseIndex >= 0) baseIndex else cachedIndex
        if (targetIndex != null && trackId != null) {
            highlightedPlaylistMode = targetMode
            highlightedPlaylistTrackId = trackId
        }
        if (targetIndex == null) {
            status = "已打开应用，但当前已加载的歌单页中未找到《${request.title}》。"
            activity.clearOverlayOpenRequest(request.requestId)
            return@LaunchedEffect
        }
        if (targetMode == "liked") likedFilterText = "" else publicFilterText = ""
        manualSourceSearchActive = false
        val targetPage = targetIndex / pageSize + 1
        val targetOffset = (targetPage - 1) * pageSize
        val targetCount = minOf(pageSize, maxOf(0, activePlaylist.totalTrackCount - targetOffset))
        val targetPageKey = pageCacheKey(targetMode, activePlaylist.id.toString(), pageSize, targetPage)
        val targetPageLoaded = targetOffset + targetCount <= activePlaylist.tracks.size || pageCache.containsKey(targetPageKey)
        if (!targetPageLoaded) {
            requestPage(targetPage)
            return@LaunchedEffect
        }
        setCurrentPage(targetPage)
        jumpPageInput = targetPage.toString()
        selectedTrack = null
        flacResults = emptyList()
        withFrameNanos { }
        runCatching { listState.animateScrollToItem(5 + (targetIndex % pageSize)) }
        status = "已定位到歌单歌曲：" + request.title
        activity.clearOverlayOpenRequest(request.requestId)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        IconButton(
                            onClick = copyErrorReport,
                            enabled = errorReports.isNotEmpty(),
                            modifier = Modifier.padding(start = 4.dp).size(38.dp).semantics {
                                contentDescription = "复制全部错误报告（${errorReports.size}条）"
                            },
                        ) {
                            ErrorReportGlyph(enabled = errorReports.isNotEmpty())
                        }
                        IconButton(
                            onClick = { verificationOpen = true },
                            modifier = Modifier.size(38.dp).semantics { contentDescription = "下载站验证" },
                        ) { WebpageGlyph() }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.musicmeta_cover),
                            contentDescription = "音笺应用图标",
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
                    IconButton(
                        onClick = {
                            if (floatingOverlayEnabled) {
                                floatingOverlayEnabled = false
                                folderPreferences.edit().putBoolean("floating_overlay_enabled", false).apply()
                                FloatingMiniPlayerService.stop(context)
                                Toast.makeText(context, "已关闭跨应用悬浮窗。", Toast.LENGTH_SHORT).show()
                            } else if (Settings.canDrawOverlays(context)) {
                                floatingOverlayEnabled = true
                                folderPreferences.edit().putBoolean("floating_overlay_enabled", true).apply()
                                if (previewSong == null) {
                                    Toast.makeText(context, "悬浮窗已开启；播放歌曲试听后会显示。", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                overlayPermissionLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        },
                        modifier = Modifier.size(38.dp).semantics {
                            contentDescription = if (floatingOverlayEnabled) "关闭跨应用悬浮窗" else "开启跨应用悬浮窗"
                        },
                    ) { FloatingWindowGlyph(enabled = floatingOverlayEnabled) }
                    IconButton(
                        onClick = { showDownloadSettings = true },
                        enabled = !busy,
                        modifier = Modifier.size(38.dp).semantics { contentDescription = "设置与维护" },
                    ) { SettingsGlyph() }
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
                    if ((playlist != null || inputMode == "local") && pageItemCount > 0) {
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
                            Text(
                                "${currentPage}/${pageCount}页",
                                modifier = Modifier.padding(start = 3.dp, end = 2.dp),
                                color = Accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
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
                    if ((playlist == null && inputMode != "local") || pageItemCount <= 0) {
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
                            val modes = listOf("playlist", "liked", "search", "local")
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (inputMode == "local") "LOCAL MUSIC" else "PLAYLIST",
                            color = Accent,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(
                            onClick = { showInteractionHelp = true },
                            modifier = Modifier.size(22.dp).semantics { contentDescription = "交互与使用指南" },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.lucide_circle_help),
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (inputMode == "local") "管理本地音乐" else "把喜欢的歌，存进音乐库",
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
                        if (inputMode == "local") "扫描已授权文件夹 · 使用同一悬浮播放器播放" else "公开歌单免登录 · 保留所选音质与格式 · 封面歌词写入文件",
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
                            FilterChip(
                                selected = inputMode == "local",
                                onClick = { switchInputMode("local") },
                                enabled = !busy,
                                label = { Text("本地", fontSize = 12.sp) },
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
                        } else if (inputMode != "local") {
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
                        backend = sourceProvider,
                        quality = selectedQuality,
                        isResolvingUrl = isResolvingUrl,
                        isDownloading = isDownloading,
                        onPlatformChange = { value ->
                            if (flacPlatform != value) {
                                persistSourcePlatform(value)
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

            if (inputMode == "local") {
                item(key = "local-controls") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = localMusicFilter, onValueChange = { localMusicFilter = it; setCurrentPage(1); jumpPageInput = "1" }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("搜索本地歌曲") })
                        OutlinedButton(onClick = scanLocalMusic, enabled = !isRestoringCaches && !isScanningLocalMusic) {
                            Text(if (isRestoringCaches) "恢复中" else if (isScanningLocalMusic) "扫描中" else "扫描")
                        }
                        IconButton(
                            onClick = {
                                val nextMode = localMusicSortMode.next()
                                localMusicSortMode = nextMode
                                folderPreferences.edit().putString("local_music_sort_mode", nextMode.name).apply()
                                setCurrentPage(1)
                                jumpPageInput = "1"
                                status = "本地音乐排序：" + nextMode.description
                            },
                            enabled = !isRestoringCaches && !isScanningLocalMusic,
                            modifier = Modifier.semantics {
                                contentDescription = "本地音乐排序：${localMusicSortMode.description}，点击切换"
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.lucide_arrow_up_down),
                                contentDescription = null,
                                tint = if (isRestoringCaches || isScanningLocalMusic) SubInk else Accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                val localVisible = filteredLocalMusicTracks
                val localPageSongs = localVisible.drop((currentPage - 1) * pageSize).take(pageSize)
                if (localVisible.isEmpty()) item(key = "local-empty") {
                    Text(
                        when {
                            isRestoringCaches -> "正在恢复上次的本地曲库…"
                            isScanningLocalMusic -> "正在扫描本地音乐…"
                            else -> "没有找到歌曲。首次使用请点击扫描，或在设置中选择本地音乐文件夹。"
                        },
                        color = SubInk,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
                items(localPageSongs, key = { "local-" + it.uri }) { track ->
                    val isActuallyPlaying = previewSong?.platform == "local" &&
                        previewSong?.id == track.uri.toString() && isPreviewPlaying
                    val isLocalHighlight = highlightedLocalTrackUri == track.uri.toString() ||
                        (previewSong?.platform == "local" && previewSong?.id == track.uri.toString())
                    val rowShape = RoundedCornerShape(18.dp)
                    Card(
                        Modifier.fillMaxWidth()
                            .then(if (isLocalHighlight) Modifier.border(1.5.dp, Accent, rowShape) else Modifier)
                            .pointerInput(track.uri) {
                                detectTapGestures(
                                    onTap = {
                                        val song = FlacSiteSong(track.uri.toString(), "local", track.title, track.artist, track.album, "", track.durationMs.toString(), track.coverUri)
                                        highlightedLocalTrackUri = track.uri.toString()
                                        previewQueue = localVisible.map { FlacSiteSong(it.uri.toString(), "local", it.title, it.artist, it.album, "", it.durationMs.toString(), it.coverUri) }
                                        previewCurrentTrack = null
                                        previewCurrentPlaylistId = null
                                        selectedTrack = null
                                        playPreview(song)
                                    },
                                    onLongPress = { copySongAndArtist(track.title, track.artist) },
                                )
                            },
                        shape = rowShape,
                        colors = CardDefaults.cardColors(containerColor = if (isLocalHighlight) SoftAccent else SurfaceColor),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(
                                model = track.coverUri.takeIf { it.isNotBlank() },
                                contentDescription = "本地歌曲封面；点击加入下一首",
                                placeholder = painterResource(R.drawable.musicmeta_cover),
                                error = painterResource(R.drawable.musicmeta_cover),
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { queueLocalTrackForNext(track, localVisible) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = SubInk, fontSize = 12.sp, maxLines = 1)
                                if (track.album.isNotBlank()) Text(track.album, color = SubInk, fontSize = 10.sp, maxLines = 1)
                                if (isLocalHighlight) Text(if (isActuallyPlaying) "正在播放" else "已定位", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(if (isActuallyPlaying) "♫" else "▶", color = Accent)
                        }
                    }
                }
            } else {
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
                    val batchTracks = pageCache[currentCacheKey].orEmpty()
                    batchTracks.drop(pageOffset % 100).take(pageSize)
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
                        val isActuallyPlaying = isPreviewPlaying &&
                            previewCurrentPlaylistId == currentPlaylist.id &&
                            previewCurrentTrack?.id == track.id
                        val isLocated = highlightedPlaylistMode == inputMode && highlightedPlaylistTrackId == track.id
                        val rowHighlightLabel = when {
                            isActuallyPlaying -> "正在播放"
                            isLocated -> "已定位"
                            else -> null
                        }
                        TrackRow(
                            track = track,
                            busy = busy,
                            onFindSource = { searchTrackSource(track, false) },
                            onFindAndPlay = { searchTrackSource(track, true) },
                            onQueueNext = { queueTrackForNext(track) },
                            onCopy = { copySongAndArtist(track.title, track.artists) },
                            highlightLabel = rowHighlightLabel,
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
                                        Row(
                                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            MusicSourceConfig.platformsFor(sourceProvider).forEach { option ->
                                                FilterChip(
                                                    selected = flacPlatform == option.id,
                                                    onClick = {
                                                        if (flacPlatform != option.id) {
                                                            persistSourcePlatform(option.id)
                                                            selectedTrack?.let { searchTrackSource(it, false) }
                                                        }
                                                    },
                                                    label = { Text(option.label, fontSize = 10.sp) },
                                                )
                                            }
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
                                                    Text("GD Studio · " + MusicSourceConfig.labelForPlatform(song.platform.removePrefix("gd:")), color = Accent, fontSize = 10.sp)
                                                } else if (sourceProvider == MusicSourceConfig.BACKEND_GD_STUDIO &&
                                                    song.platform in setOf("wyy", "netease")
                                                ) {
                                                    Text("备用源 · flac.music.hi.cn", color = Accent, fontSize = 10.sp)
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
                                                                val cookie = CookieManager.getInstance().getCookie(FLAC_SITE_HOME)
                                                                val (directUrl, usedGdStudio) = withContext(Dispatchers.IO) {
                                                                    flacProvider.resolveDownloadUrlForSource(
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
                                                                if (usedGdStudio) {
                                                                    status = "本次由 GD Studio 解析下载地址。"
                                                                }
                                                                isResolvingUrl = false
                                                                latestDownloadAndSave(
                                                                    directUrl,
                                                                    fallbackUserAgent,
                                                                    null,
                                                                    if (selectedQuality == "FLAC") "audio/flac" else "audio/mpeg",
                                                                    (if (usedGdStudio) "https://music.gdstudio.xyz/" else FLAC_SITE_HOME),
                                                                    if (usedGdStudio) null else cookie,
                                                                    selectedTrack ?: metadataTrackForSource(song),
                                                                    song,
                                                                )
                                                            } catch (error: Exception) {
                                                                    val detail = error.message?.takeIf { it.isNotBlank() } ?: "音源服务暂不可用"
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
        }

        if (previewSong != null &&
            !fallbackMiniPlayerDismissedForSession &&
            !(floatingOverlayEnabled && Settings.canDrawOverlays(context))
        ) {
            val activePreviewSong = previewSong!!
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val coverWidth = 64.dp
                val sideControlsWidth = 28.dp
                val preferredGap = 4.dp
                val margin = 4.dp
                val displayWidthPx = with(density) { maxWidth.roundToPx() }
                val coverWidthPx = with(density) { coverWidth.roundToPx() }
                val sideControlsWidthPx = with(density) { sideControlsWidth.roundToPx() }
                val marginPx = with(density) { margin.roundToPx() }
                val preferredGapPx = with(density) { preferredGap.roundToPx() }
                val desiredLyricsWidthPx = FloatingOverlayPlacement.scaledPreferredLyricsWidthPx(
                    baseGroupWidthPx = with(density) { 356.dp.roundToPx() },
                    nonLyricsWidthPx = with(density) { 78.dp.roundToPx() },
                    scale = 1.587f,
                )
                // Match the system overlay: drag the cover across the screen and mirror the lyric panel around it.
                val coverBaseLeftPx = FloatingOverlayPlacement.centeredCoverXForExpandedGroup(
                    displayWidth = displayWidthPx,
                    coverWidthPx = coverWidthPx,
                    desiredLyricsWidthPx = desiredLyricsWidthPx,
                    preferredGapPx = preferredGapPx,
                    marginPx = marginPx,
                    sideControlsWidthPx = sideControlsWidthPx,
                )
                val minDragX = (marginPx - coverBaseLeftPx).toFloat()
                val maxDragX = (displayWidthPx - marginPx - coverWidthPx - coverBaseLeftPx).toFloat()
                val coverXpx = (coverBaseLeftPx + miniPlayerOffset.x.roundToInt())
                    .coerceIn(marginPx, (displayWidthPx - marginPx - coverWidthPx).coerceAtLeast(marginPx))
                val lyricsPanelOnRight = FloatingOverlayPlacement.lyricsPanelOnRight(
                    coverX = coverXpx,
                    coverWidthPx = coverWidthPx,
                    displayWidth = displayWidthPx,
                )
                val panelGapPx = FloatingOverlayPlacement.panelGapPx(
                    displayWidth = displayWidthPx,
                    coverX = coverXpx,
                    coverWidthPx = coverWidthPx,
                    preferredGapPx = preferredGapPx,
                    marginPx = marginPx,
                    lyricsOnRight = lyricsPanelOnRight,
                )
                val panelWidthPx = FloatingOverlayPlacement.lyricsWidthPx(
                    displayWidth = displayWidthPx,
                    coverX = coverXpx,
                    coverWidthPx = coverWidthPx,
                    desiredLyricsWidthPx = desiredLyricsWidthPx,
                    gapPx = panelGapPx,
                    marginPx = marginPx,
                    lyricsOnRight = lyricsPanelOnRight,
                    reservedSideControlsPx = sideControlsWidthPx,
                )
                val panelWidth = with(density) { panelWidthPx.toDp() }
                val controlsLyricsGap = with(density) { panelGapPx.toDp() }
                val panelOffset = if (lyricsPanelOnRight) coverWidth + sideControlsWidth + controlsLyricsGap else 0.dp
                val controlsOffset = when {
                    lyricsPanelOnRight -> coverWidth
                    showPreviewDetails -> panelWidth + controlsLyricsGap
                    else -> 0.dp
                }
                val coverOffset = when {
                    lyricsPanelOnRight -> 0.dp
                    showPreviewDetails -> panelWidth + controlsLyricsGap + sideControlsWidth
                    else -> sideControlsWidth
                }
                val groupWidth = if (showPreviewDetails) {
                    coverWidth + sideControlsWidth + controlsLyricsGap + panelWidth
                } else {
                    coverWidth + sideControlsWidth
                }
                val groupLeftPx = if (lyricsPanelOnRight) {
                    coverXpx
                } else {
                    coverXpx - if (showPreviewDetails) panelWidthPx + panelGapPx + sideControlsWidthPx else sideControlsWidthPx
                }
                val coverShape = RoundedCornerShape(18.dp)
                val fallbackGroupHeight =
                    maxOf(108.dp, 22.dp + floatingLyricsPanelHeightDp(floatingLyricsFontSizeSp).dp)
                val maxDragY = with(density) {
                    (maxHeight - fallbackGroupHeight - 12.dp).toPx().coerceAtLeast(0f)
                }
                val toggleLyricsPanel = { showPreviewDetails = !showPreviewDetails }
                val locateCurrentPreviewSong = {
                    val song = previewSong
                    if (song != null) {
                        val mode = when {
                            song.platform == "local" -> "local"
                            previewCurrentPlaylistId != null && likedPlaylist?.id == previewCurrentPlaylistId -> "liked"
                            previewCurrentPlaylistId != null -> "playlist"
                            else -> "search"
                        }
                        val trackId = if (song.platform == "local") song.id else previewCurrentTrack?.id?.toString().orEmpty()
                        activity.requestTrackLocation(
                            mode = mode,
                            trackId = trackId,
                            playlistId = previewCurrentPlaylistId ?: 0L,
                            title = song.title,
                            artist = song.artist,
                        )
                        status = "正在定位当前播放歌曲：" + song.title
                    }
                }
                val closePlayerCompletely = {
                    floatingOverlayDismissedForSession = true
                    fallbackMiniPlayerDismissedForSession = true
                    stopPreview()
                    FloatingMiniPlayerService.stop(context)
                    MediaPlaybackService.stop(context)
                    activity.finishAndRemoveTask()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 12.dp)
                        .width(groupWidth)
                        .height(fallbackGroupHeight)
                        .offset {
                            IntOffset(
                                groupLeftPx,
                                miniPlayerOffset.y.coerceIn(-maxDragY, 0f).roundToInt(),
                            )
                        },
                ) {
                    if (showPreviewDetails) {
                        val useTranslatedLyrics = showTranslatedPreviewLyric && previewTranslatedLyrics.isNotBlank()
                        val activeTrackLyrics = if (useTranslatedLyrics) previewTranslatedLyrics else previewLyrics
                        val activeLyric = when {
                            isPreviewLyricsLoading -> "正在找歌词…"
                            activeTrackLyrics.isBlank() -> "暂未找到歌词"
                            else -> currentLyricLine(
                                activeLyrics = activeTrackLyrics,
                                originalLyrics = previewLyrics,
                                positionMs = previewPositionMs + 180,
                                isTranslation = useTranslatedLyrics,
                            ).ifBlank { "♪" }
                        }
                        Card(
                            modifier = Modifier.align(Alignment.TopStart).offset(x = panelOffset, y = 22.dp)
                                .width(panelWidth).height(floatingLyricsPanelHeightDp(floatingLyricsFontSizeSp).dp)
                                .overlayDragGesture(
                                    key = listOf(activePreviewSong, minDragX, maxDragX, maxDragY, showPreviewDetails),
                                    onDrag = { dx, dy ->
                                        miniPlayerOffset = Offset(
                                            (miniPlayerOffset.x + dx).coerceIn(minDragX, maxDragX),
                                            (miniPlayerOffset.y + dy).coerceIn(-maxDragY, 0f),
                                        )
                                    },
                                    onCircularDrag = locateCurrentPreviewSong,
                                    ignoreDragBelowFraction = 0.68f,
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = FLOATING_LYRICS_PANEL_VERTICAL_PADDING_DP.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(9.dp))
                                        .tapAndHoldToClose(
                                            key = listOf(activePreviewSong, showTranslatedPreviewLyric, showPreviewDetails),
                                            onTap = { showTranslatedPreviewLyric = !showTranslatedPreviewLyric },
                                            onLongHold = { closePlayerCompletely() },
                                        )
                                        .padding(horizontal = 2.dp, vertical = 0.dp)
                                        .semantics {
                                            contentDescription = if (previewTranslatedLyrics.isBlank()) "歌词，点击查看是否存在译文"
                                                else "歌词，点击切换原文或译文"
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        activeLyric,
                                        style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.82f), offset = Offset(0f, 1f), blurRadius = 2f)),
                                        color = Color(0xFF9FF0C9),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = floatingLyricsFontSizeSp.sp,
                                        lineHeight = (floatingLyricsFontSizeSp * 1.12f).sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                val progress = if (previewDurationMs > 0)
                                    (previewPositionMs.toFloat() / previewDurationMs.toFloat()).coerceIn(0f, 1f)
                                else 0f
                                Box(
                                    modifier = Modifier.fillMaxWidth(0.94f).height(FLOATING_LYRICS_PROGRESS_HEIGHT_DP.dp).offset(y = 2.dp)
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
                                        if (progress > 0f) {
                                            drawLine(Color(0xFF9FF0C9), Offset(0f, y), Offset(size.width * progress, y), strokeWidth, cap = StrokeCap.Round)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // When collapsed, only the cover remains draggable; no invisible lyrics hit area.
                    MiniPlayerSideTrackControls(
                        onPrevious = { playAdjacentPreview(-1) },
                        onNext = { playAdjacentPreview(1) },
                        modifier = Modifier.align(Alignment.CenterStart)
                            .offset(x = controlsOffset)
                            .width(sideControlsWidth).height(64.dp),
                    )
                    Column(
                        modifier = Modifier.width(64.dp).height(108.dp).padding(top = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = coverOffset)
                                .size(64.dp)
                                .clip(coverShape)
                                .background(Color(0xFF171E1A))
                                .border(1.dp, Color(0xFF2B3730), coverShape)
                                .overlayDragGesture(
                                    key = listOf(activePreviewSong, minDragX, maxDragX, maxDragY, showPreviewDetails),
                                    onDrag = { dx, dy ->
                                        miniPlayerOffset = Offset(
                                            (miniPlayerOffset.x + dx).coerceIn(minDragX, maxDragX),
                                            (miniPlayerOffset.y + dy).coerceIn(-maxDragY, 0f),
                                        )
                                    },
                                    onCircularDrag = locateCurrentPreviewSong,
                                )
                                .tapAndHoldToClose(
                                    key = activePreviewSong,
                                    onTap = { playPreview(activePreviewSong) },
                                    onDoubleTap = { toggleLyricsPanel() },
                                    onLongHold = { closePlayerCompletely() },
                                ),
                        ) {
                            AsyncImage(
                                model = previewCoverUrl.ifBlank { activePreviewSong.coverUrl }.takeIf { it.isNotBlank() },
                                contentDescription = "单击播放 / 暂停，双击展开 / 收起歌词栏，长按 5 秒彻底关闭并停止播放，拖动移动，画圈定位当前歌曲",
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
                        }
                        Row(
                            modifier = Modifier.offset(x = coverOffset).fillMaxWidth().height(22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier.size(14.dp).clickable { cyclePlaybackMode() }
                                    .semantics { contentDescription = "播放模式：" + listOf("顺序播放", "乱序播放", "单曲循环")[playbackMode] + "，点击切换" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = when (playbackMode) {
                                            1 -> R.drawable.ic_playback_shuffle
                                            2 -> R.drawable.ic_playback_repeat_one
                                            else -> R.drawable.ic_playback_sequential
                                        },
                                    ),
                                    contentDescription = null,
                                    tint = Accent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Text(
                                formatPreviewTime(previewPositionMs) + "/" + formatPreviewTime(previewDurationMs),
                                modifier = Modifier.weight(1f),
                                color = SubInk,
                                fontSize = 6.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                            Box(
                                modifier = Modifier.size(22.dp)
                                    .clickable(enabled = !isPreviewLoading) { playPreview(activePreviewSong) }
                                    .semantics { contentDescription = if (isPreviewPlaying) "暂停播放" else "继续播放" },
                                contentAlignment = Alignment.Center,
                            ) { PlayGlyph(isPlaying = isPreviewPlaying, isLoading = isPreviewLoading) }
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
            title = { Text("设置与维护", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("音源服务（主 API）", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        "所选 API 服务会用于搜索、试听和下载。若服务失败会提示切换，不会悄悄改用另一家 API。",
                        color = SubInk,
                        fontSize = 11.sp,
                    )
                    listOf(
                        MusicSourceConfig.BACKEND_GD_STUDIO to "GD Studio API",
                        MusicSourceConfig.BACKEND_FLAC_SITE to "flac.music.hi.cn",
                    ).forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = sourceProvider == value,
                                onClick = { selectSourceProvider(value) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, color = Ink, fontSize = 13.sp)
                                Text(
                                    if (value == MusicSourceConfig.BACKEND_GD_STUDIO)
                                        "多平台参数；可用性由服务端动态决定"
                                    else
                                        "当前适配网易云、酷我",
                                    color = SubInk,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    Text("次级音乐源", fontWeight = FontWeight.SemiBold, color = Ink)
                    MusicSourceConfig.platformsFor(sourceProvider).chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { option ->
                                Row(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .clickable { persistSourcePlatform(option.id) }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = flacPlatform == option.id,
                                        onClick = { persistSourcePlatform(option.id) },
                                    )
                                    Text(option.label, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        if (sourceProvider == MusicSourceConfig.BACKEND_GD_STUDIO)
                            "默认使用 GD Studio 的 netease。文档列出的参数不代表所有源都始终开放；当前可用源以服务端为准。GD Studio 要求注明出处并遵守非商业使用限制。"
                        else
                            "flac.music.hi.cn 当前只适配网易云和酷我。若搜索或下载失败，可在此切换 API 服务入口。",
                        color = SubInk,
                        fontSize = 11.sp,
                    )
                    Text("默认音质", fontWeight = FontWeight.SemiBold, color = Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedQuality == "128k", onClick = { setQuality("128k") }, label = { Text("MP3 128k") })
                        FilterChip(selected = selectedQuality == "320k", onClick = { setQuality("320k") }, label = { Text("MP3 320k") })
                        FilterChip(selected = selectedQuality == "FLAC", onClick = { setQuality("FLAC") }, label = { Text("FLAC") })
                    }
                    Text("悬浮窗默认行为", fontWeight = FontWeight.SemiBold, color = Ink)
                    DownloadSettingSwitchRow(
                        title = "悬浮窗左栏默认展开",
                        summary = "默认显示歌词栏；播放下一首时保留当前展开状态",
                        checked = floatingLyricsExpandedByDefault,
                        onCheckedChange = {
                            floatingLyricsExpandedByDefault = it
                            showPreviewDetails = it
                            folderPreferences.edit()
                                .putBoolean("floating_lyrics_expanded_by_default", it)
                                .apply()
                        },
                    )
                    DownloadSettingSwitchRow(
                        title = "优先显示翻译歌词",
                        summary = "本地与在线歌曲通用；有译文时优先显示，仍可点击歌词手动切换",
                        checked = floatingLyricsTranslatedByDefault,
                        onCheckedChange = {
                            floatingLyricsTranslatedByDefault = it
                            showTranslatedPreviewLyric = it
                            folderPreferences.edit()
                                .putBoolean("floating_lyrics_translated_by_default", it)
                                .apply()
                        },
                    )
                    Text("悬浮窗歌词字体大小", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(String.format("%.2f sp", floatingLyricsFontSizeSp), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = floatingLyricsFontSliderPosition(floatingLyricsFontSizeSp),
                        onValueChange = { progress ->
                            val size = floatingLyricsFontSizeFromSlider(progress)
                            floatingLyricsFontSizeSp = size
                            folderPreferences.edit().putFloat("floating_lyrics_font_size_sp", size).apply()
                        },
                        valueRange = 0f..1f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("较小 · 0.5×", color = SubInk, fontSize = 10.sp)
                        Text("默认 · 1×", color = SubInk, fontSize = 10.sp)
                        Text("较大 · 2×", color = SubInk, fontSize = 10.sp)
                    }
                    Text("滑块正中间是默认字号，左右两端分别为默认值的一半和两倍。设置会立即应用并自动保存。", color = SubInk, fontSize = 11.sp, lineHeight = 16.sp)
                    Text("本地音乐文件夹", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(localMusicFolderUri?.let { runCatching { DocumentFile.fromTreeUri(context, it)?.name }.getOrNull() } ?: "默认：下载/-07-MusicMeta 与 Music", color = SubInk, fontSize = 12.sp)
                    OutlinedButton(onClick = { localFolderPicker.launch(localMusicFolderUri) }, modifier = Modifier.fillMaxWidth()) { Text("选择本地音乐文件夹") }
                    OutlinedButton(onClick = scanLocalMusic, enabled = !isScanningLocalMusic, modifier = Modifier.fillMaxWidth()) { Text("重新扫描本地音乐") }
                    Text("下载目录", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(destinationFolderName, color = SubInk, fontSize = 12.sp)
                    OutlinedButton(
                        onClick = { destinationPicker.launch(destinationTreeUri) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("选择下载目录") }
                    Text("文件命名", fontWeight = FontWeight.SemiBold, color = Ink)
                    listOf(
                        "song_title" to "仅歌曲名",
                        "song_artist" to "歌曲名 - 艺术家（默认）",
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
                    Text("缓存与维护", fontWeight = FontWeight.SemiBold, color = Ink)
                    OutlinedButton(
                        onClick = { showClearCacheConfirm = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("一键清除临时缓存") }
                    Text(
                        "只清理临时文件与图片缓存；不会删除已下载歌曲、已保存歌单、本地扫描结果或设置。",
                        color = SubInk,
                        fontSize = 11.sp,
                    )
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

    if (showInteractionHelp) {
        AlertDialog(
            onDismissRequest = { showInteractionHelp = false },
            title = { Text("交互与使用指南", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("LOCAL MUSIC｜本地音乐", fontWeight = FontWeight.SemiBold)
                    Text("点歌曲行开始播放；长按歌曲行可复制歌曲名和艺人。排序按钮可在修改时间新到旧、旧到新、A–Z 之间循环。首次使用请扫描本地音乐，或在「设置与维护」中选择有权限访问的音乐文件夹。")
                    Text("PLAYLIST｜公开歌单与红心歌单", fontWeight = FontWeight.SemiBold)
                    Text("公开歌单粘贴网易云分享链接或歌单 ID；红心歌单需要网易云用户 ID。点歌单中的歌曲封面会把曲目排到下一首；若当前没有歌曲播放，则会直接开始播放。")
                    Text("直接搜歌与音源", fontWeight = FontWeight.SemiBold)
                    Text("直接搜歌可按歌曲名查询候选版本。点「找音源」查看并核对候选；点播放图标会尝试播放第一个匹配结果。下载前请检查歌曲名、艺人和版本，避免选到翻唱或现场版。")
                    Text("悬浮播放器手势", fontWeight = FontWeight.SemiBold)
                    Text("单击封面：播放 / 暂停。双击封面：展开 / 收起歌词栏。长按封面或歌词正文满 5 秒：彻底关闭音笺、停止播放并退出最近任务。拖动封面或歌词卡片：移动悬浮播放器。")
                    Text("画圈定位：在可拖动区域连续画圈（至少约三整圈，并累计足够移动距离）会打开音笺并尝试定位当前播放曲目。")
                    Text("彻底关闭只清理音笺自身：释放播放器、关闭系统媒体服务与通知并移除音笺任务，不会结束其他应用。")
                    Text("侧边上 / 下箭头：切换上一首 / 下一首。点击歌词正文：在已加载的原文 / 译文间切换；拖动进度条可跳转播放位置。收起歌词后，歌词窗口会让出后方应用的触摸区域。")
                    Text("本地内嵌歌词：能识别重复时间戳时分开显示原文与译文；时间轴歌词显示当前带时间戳的行，无时间轴的作曲、编曲等夹杂行不会抢占当前歌词。无法可靠拆分时保留原样，不会为了配对而丢弃原文。")
                    Text("设置与维护中可调整悬浮歌词字号、是否优先显示已识别的译文，以及下载目录、音质和歌词保存方式。悬浮歌词默认字号为 15.04sp。")
                }
            },
            confirmButton = { TextButton(onClick = { showInteractionHelp = false }) { Text("知道了", color = Accent) } },
            containerColor = SurfaceColor,
        )
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清除临时缓存？", fontWeight = FontWeight.SemiBold) },
            text = { Text("将清理图片和临时缓存文件，不会删除歌曲文件、已保存歌单、本地曲库缓存或下载设置。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        scope.launch {
                            val clearedBytes = withContext(Dispatchers.IO) {
                                MusicMetaPersistence.clearTemporaryCache(context)
                            }
                            status = "临时缓存已清理，释放约 " + (clearedBytes / 1024) + " KB。"
                            Toast.makeText(context, "临时缓存已清理", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text("清除缓存", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("取消", color = SubInk) }
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
                                    val referer = this.url ?: FLAC_SITE_HOME
                                    latestWebsiteDownload(
                                        url,
                                        userAgent.orEmpty(),
                                        disposition,
                                        mimeType,
                                        referer,
                                        CookieManager.getInstance().getCookie(referer),
                                    )
                                }
                                loadUrl(FLAC_SITE_HOME)
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
    backend: String,
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
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MusicSourceConfig.platformsFor(backend).forEach { option ->
                        FilterChip(
                            selected = platform == option.id,
                            onClick = { onPlatformChange(option.id) },
                            label = { Text(option.label, fontSize = 10.sp) },
                            enabled = !isSearching && !isResolvingUrl && !isDownloading,
                        )
                    }
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
                                        Text("GD Studio · " + MusicSourceConfig.labelForPlatform(song.platform.removePrefix("gd:")), color = Accent, fontSize = 9.sp)
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
    resolveMissingCover: Boolean = false,
) {
    var resolvedCoverUrl by remember(song.platform, song.id, song.coverUrl) {
        mutableStateOf(song.coverUrl)
    }
    LaunchedEffect(song.platform, song.id, song.coverUrl, resolveMissingCover) {
        // GD Studio's search returns pic_id rather than a direct image URL. Resolving every row
        // issues one additional API call per candidate and quickly exhausts its five-minute quota.
        // Keep a placeholder in result lists; playback resolves artwork for the selected song.
        if (resolveMissingCover && resolvedCoverUrl.isBlank()) {
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
private fun MiniPlayerSideTrackControls(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrowColor = Color(0xFF9FF0C9)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(31.dp)
                .clickable { onPrevious() }
                .semantics { contentDescription = "上一首" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val stroke = 1.7.dp.toPx()
                val centerX = size.width / 2f
                val top = 1.75.dp.toPx()
                val bottom = size.height - 1.75.dp.toPx()
                val headDepth = 5.5.dp.toPx()
                drawLine(arrowColor, Offset(centerX, bottom), Offset(centerX, top + headDepth), stroke, cap = StrokeCap.Round)
                drawLine(arrowColor, Offset(centerX, top), Offset(centerX - headDepth, top + headDepth), stroke, cap = StrokeCap.Round)
                drawLine(arrowColor, Offset(centerX, top), Offset(centerX + headDepth, top + headDepth), stroke, cap = StrokeCap.Round)
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(31.dp)
                .clickable { onNext() }
                .semantics { contentDescription = "下一首" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val stroke = 1.7.dp.toPx()
                val centerX = size.width / 2f
                val top = 1.75.dp.toPx()
                val bottom = size.height - 1.75.dp.toPx()
                val headDepth = 5.5.dp.toPx()
                drawLine(arrowColor, Offset(centerX, top), Offset(centerX, bottom - headDepth), stroke, cap = StrokeCap.Round)
                drawLine(arrowColor, Offset(centerX, bottom), Offset(centerX - headDepth, bottom - headDepth), stroke, cap = StrokeCap.Round)
                drawLine(arrowColor, Offset(centerX, bottom), Offset(centerX + headDepth, bottom - headDepth), stroke, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun PlayGlyph(isPlaying: Boolean, isLoading: Boolean) {
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SubInk, strokeWidth = 1.6.dp)
    } else {
        Icon(
            painter = painterResource(if (isPlaying) R.drawable.lucide_pause else R.drawable.lucide_play),
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ErrorGlyph(enabled: Boolean) {
    Icon(
        painter = painterResource(R.drawable.lucide_circle_alert),
        contentDescription = null,
        tint = if (enabled) Accent else SubInk,
        modifier = Modifier.size(22.dp),
    )
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
    Icon(painterResource(R.drawable.lucide_log_in), null, tint = Accent, modifier = Modifier.size(20.dp))
}

@Composable
private fun PageArrowGlyph(pointsLeft: Boolean, tint: Color) {
    Icon(
        painterResource(if (pointsLeft) R.drawable.lucide_chevron_left else R.drawable.lucide_chevron_right),
        null,
        tint = tint,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun FloatingWindowGlyph(enabled: Boolean) {
    Icon(painterResource(R.drawable.lucide_picture_in_picture_2), null,
        tint = if (enabled) Accent else SubInk, modifier = Modifier.size(22.dp))
}

@Composable
private fun SettingsGlyph() {
    Icon(painterResource(R.drawable.lucide_settings), null, tint = Accent, modifier = Modifier.size(22.dp))
}

@Composable
private fun WebpageGlyph() {
    Icon(painterResource(R.drawable.lucide_globe), null, tint = Accent, modifier = Modifier.size(22.dp))
}

@Composable
private fun CopyGlyph(enabled: Boolean = true) {
    Icon(painterResource(R.drawable.lucide_copy), null,
        tint = if (enabled) Accent else SubInk, modifier = Modifier.size(19.dp))
}

@Composable
private fun ErrorReportGlyph(enabled: Boolean = true) {
    Icon(painterResource(R.drawable.lucide_circle_alert), null,
        tint = if (enabled) Accent else SubInk, modifier = Modifier.size(19.dp))
}

@Composable
private fun HomeGlyph() {
    Icon(painterResource(R.drawable.lucide_house), null, tint = Accent, modifier = Modifier.size(23.dp))
}


@Composable
private fun TrackRow(
    track: MusicTrack,
    busy: Boolean,
    onFindSource: () -> Unit,
    onFindAndPlay: () -> Unit,
    onQueueNext: () -> Unit,
    onCopy: () -> Unit,
    highlightLabel: String? = null,
) {
    val rowShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier.fillMaxWidth()
            .then(if (highlightLabel != null) Modifier.border(1.5.dp, Accent, rowShape) else Modifier)
            .pointerInput(track.id) {
                detectTapGestures(onLongPress = { onCopy() })
            },
        shape = rowShape,
        color = if (highlightLabel != null) SoftAccent else SurfaceColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            AsyncImage(
                model = track.coverUrl.takeIf { it.isNotBlank() },
                contentDescription = "歌曲封面；点击加入下一首",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftAccent)
                    .clickable(enabled = !busy, onClick = onQueueNext),
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
                if (highlightLabel != null) {
                    Text(highlightLabel, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedButton(
                onClick = onFindSource,
                enabled = !busy,
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                Text("找音源", fontSize = 12.sp)
            }
            IconButton(
                onClick = onFindAndPlay,
                enabled = !busy,
                modifier = Modifier.size(34.dp).semantics {
                    contentDescription = "查找并播放第一个匹配音源"
                },
            ) {
                PlayGlyph(isPlaying = false, isLoading = false)
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
    val frames = error.stackTrace.take(30).joinToString("\n") { frame ->
        "  at " + frame.className + "." + frame.methodName +
            "(" + (frame.fileName ?: "unknown") + ":" + frame.lineNumber + ")"
    }
    val device = Build.MANUFACTURER + " " + Build.MODEL
    return buildString {
        appendLine("音笺 MusicMeta 错误报告")
        appendLine("错误编号: MM-" + System.currentTimeMillis())
        appendLine("阶段: " + stage)
        appendLine("应用版本: " + appVersion)
        appendLine("设备: " + device)
        appendLine("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        appendLine("异常链:")
        appendLine(errorChain)
        appendLine("调用位置（最多 30 层）:")
        appendLine(frames)
        appendLine("隐私说明: 未附加歌单输入、下载链接或 Cookie；消息中的网址已隐藏。")
    }
}

private const val fallbackUserAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
