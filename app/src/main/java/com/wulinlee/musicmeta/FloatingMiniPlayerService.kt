package com.wulinlee.musicmeta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private class FloatingOverlayViewTreeOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}

internal object FloatingMiniPlayerBridge {
    @Volatile
    var onCommand: ((String, Int) -> Unit)? = null
}

class FloatingMiniPlayerService : LifecycleService() {
    companion object {
        const val ACTION_UPDATE = "com.wulinlee.musicmeta.overlay.UPDATE"
        const val ACTION_CONTROL = "com.wulinlee.musicmeta.overlay.CONTROL"
        const val ACTION_STOP = "com.wulinlee.musicmeta.overlay.STOP"

        const val EXTRA_COVER_URL = "cover_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ORIGINAL_LYRICS = "original_lyrics"
        const val EXTRA_TRANSLATED_LYRICS = "translated_lyrics"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_POSITION_MS = "position_ms"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_IS_LOADING = "is_loading"
        const val EXTRA_EXPANDED = "expanded"
        const val EXTRA_SHOW_TRANSLATION = "show_translation"
        const val EXTRA_COMMAND = "command"

        const val COMMAND_TOGGLE_PLAY = "toggle_play"
        const val COMMAND_PREVIOUS = "previous_track"
        const val COMMAND_NEXT = "next_track"
        const val COMMAND_TOGGLE_DETAILS = "toggle_details"
        const val COMMAND_TOGGLE_TRANSLATION = "toggle_translation"
        const val COMMAND_SEEK = "seek"
        const val COMMAND_CLOSE = "close"

        private const val CHANNEL_ID = "musicmeta_floating_player"
        private const val NOTIFICATION_ID = 1101
        @Volatile private var serviceAlive = false

        fun showOrUpdate(
            context: Context,
            coverUrl: String,
            title: String,
            artist: String,
            originalLyrics: String,
            translatedLyrics: String,
            durationMs: Int,
            positionMs: Int,
            isPlaying: Boolean,
            isLoading: Boolean,
            expanded: Boolean,
            showTranslation: Boolean,
        ) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatingMiniPlayerService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_COVER_URL, coverUrl)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_ORIGINAL_LYRICS, originalLyrics)
                .putExtra(EXTRA_TRANSLATED_LYRICS, translatedLyrics)
                .putExtra(EXTRA_DURATION_MS, durationMs)
                .putExtra(EXTRA_POSITION_MS, positionMs)
                .putExtra(EXTRA_IS_PLAYING, isPlaying)
                .putExtra(EXTRA_IS_LOADING, isLoading)
                .putExtra(EXTRA_EXPANDED, expanded)
                .putExtra(EXTRA_SHOW_TRANSLATION, showTranslation)
            if (serviceAlive) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingMiniPlayerService::class.java))
        }

        fun sendCommand(context: Context, command: String, positionMs: Int = 0) {
            context.sendBroadcast(
                Intent(ACTION_CONTROL)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_COMMAND, command)
                    .putExtra(EXTRA_POSITION_MS, positionMs),
            )
        }
    }

    private var windowManager: WindowManager? = null
    private val overlayViewTreeOwner = FloatingOverlayViewTreeOwner()
    private var coverView: ComposeView? = null
    private var lyricsView: ComposeView? = null
    private var coverParams: WindowManager.LayoutParams? = null
    private var lyricsParams: WindowManager.LayoutParams? = null

    private var coverUrl by mutableStateOf("")
    private var title by mutableStateOf("")
    private var artist by mutableStateOf("")
    private var originalLyrics by mutableStateOf("")
    private var translatedLyrics by mutableStateOf("")
    private var durationMs by mutableIntStateOf(0)
    private var positionMs by mutableIntStateOf(0)
    private var isPlaying by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)
    private var expanded by mutableStateOf(false)
    private var showTranslation by mutableStateOf(false)

    private var coverX = 0
    private var coverY = 0
    private var initializedPosition = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var density = 1f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            if (!Settings.canDrawOverlays(this@FloatingMiniPlayerService)) {
                sendCommand(this@FloatingMiniPlayerService, COMMAND_CLOSE)
                removeOverlayViews()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            if (isPlaying && durationMs > 0) {
                positionMs = (positionMs + 300).coerceAtMost(durationMs)
                if (positionMs >= durationMs) isPlaying = false
            }
            if (coverView != null) mainHandler.postDelayed(this, 300L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceAlive = true
        overlayViewTreeOwner.start()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureNotificationChannel()
        updateDisplaySize()
        restoreOrInitializePosition()
        mainHandler.postDelayed(progressTicker, 300L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            sendCommand(this, COMMAND_CLOSE)
            removeOverlayViews()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_UPDATE) applyUpdate(intent)
        startAsForegroundService()
        ensureCoverWindow()
        syncLyricsWindow()
        updateWindowPositions()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        mainHandler.removeCallbacks(progressTicker)
        removeOverlayViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        overlayViewTreeOwner.destroy()
        serviceAlive = false
        super.onDestroy()
    }

    private fun applyUpdate(intent: Intent) {
        if (intent.hasExtra(EXTRA_COVER_URL)) coverUrl = intent.getStringExtra(EXTRA_COVER_URL).orEmpty()
        if (intent.hasExtra(EXTRA_TITLE)) title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (intent.hasExtra(EXTRA_ARTIST)) artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        if (intent.hasExtra(EXTRA_ORIGINAL_LYRICS)) {
            originalLyrics = intent.getStringExtra(EXTRA_ORIGINAL_LYRICS).orEmpty()
        }
        if (intent.hasExtra(EXTRA_TRANSLATED_LYRICS)) {
            translatedLyrics = intent.getStringExtra(EXTRA_TRANSLATED_LYRICS).orEmpty()
        }
        if (intent.hasExtra(EXTRA_DURATION_MS)) durationMs = intent.getIntExtra(EXTRA_DURATION_MS, durationMs).coerceAtLeast(0)
        if (intent.hasExtra(EXTRA_POSITION_MS)) positionMs = intent.getIntExtra(EXTRA_POSITION_MS, positionMs).coerceAtLeast(0)
        if (intent.hasExtra(EXTRA_IS_PLAYING)) isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
        if (intent.hasExtra(EXTRA_IS_LOADING)) isLoading = intent.getBooleanExtra(EXTRA_IS_LOADING, isLoading)
        if (intent.hasExtra(EXTRA_EXPANDED)) expanded = intent.getBooleanExtra(EXTRA_EXPANDED, expanded)
        if (intent.hasExtra(EXTRA_SHOW_TRANSLATION)) {
            showTranslation = intent.getBooleanExtra(EXTRA_SHOW_TRANSLATION, showTranslation)
        }
    }

    private fun startAsForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "音笺跨应用悬浮播放器",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "在其他应用上方显示已启用的迷你播放器"
                        setShowBadge(false)
                    },
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1102,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1103,
            Intent(this, FloatingMiniPlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title.ifBlank { "音笺悬浮播放器" })
            .setContentText(if (isPlaying) "正在播放试听" else "悬浮播放器已开启")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭悬浮窗", stopIntent)
            .build()
    }

    private fun updateDisplaySize() {
        density = resources.displayMetrics.density.coerceAtLeast(1f)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        displayWidth = metrics.widthPixels.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        displayHeight = metrics.heightPixels.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun groupWidthPx(): Int = minOf(dp(356), (displayWidth - dp(16)).coerceAtLeast(dp(120)))

    private fun lyricsWidthPx(): Int = (groupWidthPx() - dp(78)).coerceAtLeast(dp(48))

    private fun restoreOrInitializePosition() {
        if (initializedPosition) return
        val preferences = getSharedPreferences("music_meta_settings", Context.MODE_PRIVATE)
        val storedX = preferences.getFloat("overlay_cover_x_dp", -1f)
        val storedY = preferences.getFloat("overlay_cover_y_dp", -1f)
        if (storedX >= 0f && storedY >= 0f) {
            coverX = (storedX * density).roundToInt()
            coverY = (storedY * density).roundToInt()
        } else {
            val groupWidth = groupWidthPx()
            coverX = (displayWidth - groupWidth) / 2 + groupWidth - dp(64)
            coverY = (displayHeight - dp(124)).coerceAtLeast(dp(24))
        }
        initializedPosition = true
        clampPosition()
    }

    private fun clampPosition() {
        val minimumX = lyricsWidthPx() + dp(14)
        val maximumX = (displayWidth - dp(8) - dp(64)).coerceAtLeast(minimumX)
        coverX = coverX.coerceIn(minimumX.coerceAtMost(maximumX), maximumX)
        val maximumY = (displayHeight - dp(124)).coerceAtLeast(dp(24))
        coverY = coverY.coerceIn(dp(24), maximumY)
    }

    private fun savePosition() {
        getSharedPreferences("music_meta_settings", Context.MODE_PRIVATE).edit()
            .putFloat("overlay_cover_x_dp", coverX / density)
            .putFloat("overlay_cover_y_dp", coverY / density)
            .apply()
    }

    private fun moveBy(dx: Float, dy: Float) {
        updateDisplaySize()
        coverX += dx.roundToInt()
        coverY += dy.roundToInt()
        clampPosition()
        savePosition()
        updateWindowPositions()
    }

    private fun createLayoutParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

    private fun ensureCoverWindow() {
        if (coverView != null) return
        val wm = windowManager ?: return
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayViewTreeOwner)
            setViewTreeSavedStateRegistryOwner(overlayViewTreeOwner)
            setContent {
                OverlayCoverUi(
                    coverUrl = coverUrl,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { sendCommand(this@FloatingMiniPlayerService, COMMAND_TOGGLE_PLAY) },
                    onLongPress = { toggleExpanded() },
                    onClose = { closePlayer() },
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                )
            }
        }
        val params = createLayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            coverX,
            coverY,
        )
        wm.addView(view, params)
        coverView = view
        coverParams = params
    }

    private fun syncLyricsWindow() {
        val wm = windowManager ?: return
        if (!expanded) {
            lyricsView?.let { runCatching { wm.removeView(it) } }
            lyricsView = null
            lyricsParams = null
            return
        }
        if (lyricsView == null) {
            val view = ComposeView(this).apply {
                setViewTreeLifecycleOwner(overlayViewTreeOwner)
                setViewTreeSavedStateRegistryOwner(overlayViewTreeOwner)
                setContent {
                    OverlayLyricsUi(
                        originalLyrics = originalLyrics,
                        translatedLyrics = translatedLyrics,
                        showTranslation = showTranslation,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onToggleTranslation = {
                            showTranslation = !showTranslation
                            sendCommand(this@FloatingMiniPlayerService, COMMAND_TOGGLE_TRANSLATION)
                        },
                        onSeek = { seekMs ->
                            positionMs = seekMs.coerceIn(0, durationMs.coerceAtLeast(0))
                            sendCommand(this@FloatingMiniPlayerService, COMMAND_SEEK, positionMs)
                        },
                        onPrevious = { sendCommand(this@FloatingMiniPlayerService, COMMAND_PREVIOUS) },
                        onNext = { sendCommand(this@FloatingMiniPlayerService, COMMAND_NEXT) },
                    )
                }
            }
            val params = createLayoutParams(
                lyricsWidthPx(),
                dp(64),
                coverX - dp(14) - lyricsWidthPx(),
                coverY + dp(20),
            )
            wm.addView(view, params)
            lyricsView = view
            lyricsParams = params
        }
    }

    private fun updateWindowPositions() {
        val wm = windowManager ?: return
        clampPosition()
        coverParams?.let { params ->
            params.x = coverX
            params.y = coverY
            coverView?.let { view -> runCatching { wm.updateViewLayout(view, params) } }
        }
        lyricsParams?.let { params ->
            params.x = coverX - dp(14) - lyricsWidthPx()
            params.y = coverY + dp(20)
            params.width = lyricsWidthPx()
            lyricsView?.let { view -> runCatching { wm.updateViewLayout(view, params) } }
        }
    }

    private fun toggleExpanded() {
        expanded = !expanded
        syncLyricsWindow()
        updateWindowPositions()
        sendCommand(this, COMMAND_TOGGLE_DETAILS)
    }

    private fun closePlayer() {
        sendCommand(this, COMMAND_CLOSE)
        removeOverlayViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlayViews() {
        val wm = windowManager
        lyricsView?.let { runCatching { wm?.removeView(it) } }
        coverView?.let { runCatching { wm?.removeView(it) } }
        lyricsView = null
        coverView = null
        lyricsParams = null
        coverParams = null
    }
}

@Composable
private fun OverlayCoverUi(
    coverUrl: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Int,
    durationMs: Int,
    onTogglePlay: () -> Unit,
    onLongPress: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(18.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier.size(18.dp)
                    .clickable { onClose() }
                    .semantics { contentDescription = "关闭迷你播放器" },
                contentAlignment = Alignment.Center,
            ) {
                OverlayCloseGlyph()
            }
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF171E1A))
                .border(1.dp, Color(0xFF2B3730), RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTogglePlay() },
                        onDoubleTap = { onLongPress() },
                        onLongPress = { onLongPress() },
                    )
                },
        ) {
            AsyncImage(
                model = coverUrl.takeIf { it.isNotBlank() },
                contentDescription = "单击播放 / 暂停，长按或双击展开 / 收起歌词，拖动移动播放器",
                placeholder = painterResource(id = R.drawable.musicmeta_cover),
                error = painterResource(id = R.drawable.musicmeta_cover),
                modifier = Modifier.fillMaxSize(),
            )
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                    color = Color(0xFF9FF0C9),
                    strokeWidth = 2.dp,
                )
            }
        }
        Row(
            modifier = Modifier.width(64.dp).height(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = formatMiniTime(positionMs) + "/" + formatMiniTime(durationMs),
                modifier = Modifier.weight(1f),
                color = Color(0xFFB3C0BB),
                fontSize = 6.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier.size(22.dp)
                    .clickable { onTogglePlay() }
                    .semantics {
                        contentDescription = if (isPlaying) "暂停试听" else "继续试听"
                    },
                contentAlignment = Alignment.Center,
            ) {
                OverlayPlayGlyph(isPlaying = isPlaying, isLoading = isLoading)
            }
        }
    }
}

@Composable
private fun OverlayLyricsUi(
    originalLyrics: String,
    translatedLyrics: String,
    showTranslation: Boolean,
    positionMs: Int,
    durationMs: Int,
    onToggleTranslation: () -> Unit,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val activeText = if (showTranslation) translatedLyrics else originalLyrics
    val currentLyric = when {
        activeText.isBlank() && showTranslation -> "这首歌暂无译文"
        activeText.isBlank() -> "正在等待歌词…"
        else -> findMiniLyric(activeText, positionMs)
    }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17211F)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .clickable { onPrevious() }
                        .semantics { contentDescription = "上一首，播放上一首歌的首个匹配音源" },
                    contentAlignment = Alignment.Center,
                ) { OverlaySkipGlyph(previous = true) }
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF243631))
                        .clickable { onToggleTranslation() }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .semantics {
                            contentDescription = if (translatedLyrics.isBlank()) {
                                "歌词，当前歌曲没有已加载的译文，点击切换显示状态"
                            } else {
                                "歌词，点击切换原文或译文"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        currentLyric,
                        color = Color(0xFF9FF0C9),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .clickable { onNext() }
                        .semantics { contentDescription = "下一首，播放下一首歌的首个匹配音源" },
                    contentAlignment = Alignment.Center,
                ) { OverlaySkipGlyph(previous = false) }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(14.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { tap ->
                            if (durationMs > 0 && size.width > 0) {
                                onSeek((tap.x / size.width.toFloat()).coerceIn(0f, 1f).times(durationMs).toInt())
                            }
                        }
                    }
                    .pointerInput(durationMs) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (durationMs > 0 && size.width > 0) {
                                onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f).times(durationMs).toInt())
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height / 2f
                    val stroke = 3.dp.toPx()
                    drawLine(Color(0xFF3B514B), Offset(0f, y), Offset(size.width, y), stroke, cap = StrokeCap.Round)
                    if (progress > 0f) {
                        drawLine(
                            Color(0xFF9FF0C9),
                            Offset(0f, y),
                            Offset(size.width * progress, y),
                            stroke,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlaySkipGlyph(previous: Boolean) {
    Canvas(modifier = Modifier.size(17.dp)) {
        val tint = Color(0xFF9FF0C9)
        val stroke = 1.8.dp.toPx()
        val centerY = size.height / 2f
        val edgeX = if (previous) size.width * 0.24f else size.width * 0.76f
        val barX = if (previous) size.width * 0.34f else size.width * 0.66f
        drawLine(tint, Offset(barX, size.height * 0.20f), Offset(barX, size.height * 0.80f), stroke, cap = StrokeCap.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            if (previous) {
                moveTo(size.width * 0.76f, size.height * 0.20f)
                lineTo(edgeX, centerY)
                lineTo(size.width * 0.76f, size.height * 0.80f)
            } else {
                moveTo(size.width * 0.24f, size.height * 0.20f)
                lineTo(edgeX, centerY)
                lineTo(size.width * 0.24f, size.height * 0.80f)
            }
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
private fun OverlayCloseGlyph() {
    Canvas(modifier = Modifier.size(12.dp)) {
        val stroke = 1.8.dp.toPx()
        drawLine(Color(0xCC101512), Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), stroke + 1.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color(0xCC101512), Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.2f, size.height * 0.8f), stroke + 1.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color(0xFFF4F7F5), Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), stroke, cap = StrokeCap.Round)
        drawLine(Color(0xFFF4F7F5), Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.2f, size.height * 0.8f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun OverlayPlayGlyph(isPlaying: Boolean, isLoading: Boolean) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val color = if (isLoading) Color(0xFF9AA69E) else Color(0xFF9FF0C9)
        if (isPlaying) {
            val stroke = 2.2.dp.toPx()
            drawLine(color, Offset(size.width * 0.38f, size.height * 0.22f), Offset(size.width * 0.38f, size.height * 0.78f), stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.66f, size.height * 0.22f), Offset(size.width * 0.66f, size.height * 0.78f), stroke, cap = StrokeCap.Round)
        } else {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.32f, size.height * 0.18f)
                lineTo(size.width * 0.78f, size.height * 0.5f)
                lineTo(size.width * 0.32f, size.height * 0.82f)
                close()
            }
            drawPath(path, color)
        }
    }
}

private fun findMiniLyric(raw: String, positionMs: Int): String {
    val tagPattern = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    val rows = raw.lineSequence().flatMap { line ->
        val tags = tagPattern.findAll(line).toList()
        if (tags.isEmpty()) {
            emptyList()
        } else {
            val text = tagPattern.replace(line, "").trim()
            if (text.isBlank()) {
                emptyList()
            } else {
                tags.map { tag ->
                    val minute = tag.groupValues[1].toIntOrNull() ?: 0
                    val second = tag.groupValues[2].toIntOrNull() ?: 0
                    val rawFraction = tag.groupValues.getOrNull(3).orEmpty()
                    val fractionMs = when (rawFraction.length) {
                        1 -> (rawFraction.toIntOrNull() ?: 0) * 100
                        2 -> (rawFraction.toIntOrNull() ?: 0) * 10
                        3 -> rawFraction.toIntOrNull() ?: 0
                        else -> 0
                    }
                    (minute * 60_000 + second * 1_000 + fractionMs) to text
                }
            }
        }
    }.sortedBy { it.first }.toList()
    return rows.lastOrNull { it.first <= positionMs }?.second
        ?: rows.firstOrNull()?.second
        ?: raw.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("[ti:", true) }?.trim().orEmpty()
}

private fun formatMiniTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
