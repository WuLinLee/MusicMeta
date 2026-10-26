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
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun Modifier.overlayDragGesture(
    key: Any? = Unit,
    onDrag: (Float, Float) -> Unit,
    onCircularDrag: () -> Unit,
    onDragEnd: () -> Unit = {},
): Modifier = pointerInput(key) {
    val touchSlop = viewConfiguration.touchSlop
    // Require three complete turns and meaningful movement to reduce accidental activation.
    val minimumCircularDistance = 360.dp.toPx()
    val requiredCircularRotation = 6f * 3.14159265f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val pointerId = down.id
        var dragged = false
        var previousAngle = Float.NaN
        var accumulatedRotation = 0f
        var totalDistance = 0f
        var totalX = 0f
        var totalY = 0f
        var triggered = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) break

                // Use movement deltas rather than absolute local positions: the WindowManager
                // window can move underneath the finger while the gesture is being processed.
                val movement = change.positionChangeIgnoreConsumed()
                val dx = movement.x
                val dy = movement.y
                if (!dragged) {
                    totalX += dx
                    totalY += dy
                    val totalFromStart = sqrt(totalX * totalX + totalY * totalY)
                    if (totalFromStart > touchSlop) {
                        dragged = true
                        totalDistance = totalFromStart
                        previousAngle = Float.NaN
                        onDrag(totalX, totalY)
                        change.consume()
                    }
                } else {
                    val distance = sqrt(dx * dx + dy * dy)
                    totalDistance += distance
                    if (distance >= 2f) {
                        val angle = atan2(dy, dx)
                        if (previousAngle.isFinite()) {
                            var deltaAngle = angle - previousAngle
                            if (deltaAngle > 3.14159265f) deltaAngle -= 6.2831853f
                            if (deltaAngle < -3.14159265f) deltaAngle += 6.2831853f
                            accumulatedRotation += deltaAngle
                        }
                        previousAngle = angle
                    }
                    onDrag(dx, dy)
                    change.consume()
                    if (!triggered &&
                        totalDistance >= minimumCircularDistance &&
                        abs(accumulatedRotation) >= requiredCircularRotation
                    ) {
                        triggered = true
                        onCircularDrag()
                    }
                }
            }
        } finally {
            if (dragged) onDragEnd()
        }
    }
}
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
        const val ACTION_PROGRESS = "com.wulinlee.musicmeta.overlay.PROGRESS"

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
        const val EXTRA_PLAYBACK_MODE = "playback_mode"
        const val EXTRA_OPEN_APP_AND_LOCATE = "open_app_and_locate"
        const val EXTRA_OPEN_MODE = "open_mode"
        const val EXTRA_OPEN_TRACK_ID = "open_track_id"
        const val EXTRA_OPEN_PLAYLIST_ID = "open_playlist_id"
        const val EXTRA_COMMAND = "command"

        const val COMMAND_TOGGLE_PLAY = "toggle_play"
        const val COMMAND_PREVIOUS = "previous_track"
        const val COMMAND_NEXT = "next_track"
        const val COMMAND_TOGGLE_DETAILS = "toggle_details"
        const val COMMAND_TOGGLE_TRANSLATION = "toggle_translation"
        const val COMMAND_TOGGLE_PLAYBACK_MODE = "toggle_playback_mode"
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
            playbackMode: Int,
            openMode: String,
            openTrackId: String,
            openPlaylistId: Long,
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
                .putExtra(EXTRA_PLAYBACK_MODE, playbackMode.coerceIn(0, 2))
                .putExtra(EXTRA_OPEN_MODE, openMode)
                .putExtra(EXTRA_OPEN_TRACK_ID, openTrackId)
                .putExtra(EXTRA_OPEN_PLAYLIST_ID, openPlaylistId)
            if (serviceAlive) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun updatePosition(context: Context, positionMs: Int) {
            if (!serviceAlive) return
            context.startService(
                Intent(context, FloatingMiniPlayerService::class.java)
                    .setAction(ACTION_PROGRESS)
                    .putExtra(EXTRA_POSITION_MS, positionMs.coerceAtLeast(0)),
            )
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
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

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
    private var playbackMode by mutableIntStateOf(0)
    private var openMode by mutableStateOf("search")
    private var openTrackId by mutableStateOf("")
    private var openPlaylistId by mutableStateOf(0L)

    private var coverX = 0
    private var coverY = 0
    private var initializedPosition = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var density = 1f
    private var positionUpdateScheduled = false
    private var dragRemainderX = 0f
    private var dragRemainderY = 0f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val positionFrameCallback = Choreographer.FrameCallback {
        positionUpdateScheduled = false
        updateWindowPositions()
    }
    private fun scheduleWindowPositionUpdate() {
        if (positionUpdateScheduled) return
        positionUpdateScheduled = true
        Choreographer.getInstance().postFrameCallback(positionFrameCallback)
    }
    private val progressTicker = object : Runnable {
        override fun run() {
            if (!Settings.canDrawOverlays(this@FloatingMiniPlayerService)) {
                sendCommand(this@FloatingMiniPlayerService, COMMAND_CLOSE)
                removeOverlayViews()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            // MainActivity is the source of truth for playback position (ACTION_PROGRESS).
            // Do not advance a second, drifting clock here: it could move lyric selection
            // backwards and forwards across a line boundary, causing visible flicker.
            if (overlayView != null) mainHandler.postDelayed(this, 1000L)
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

        if (intent?.action == ACTION_PROGRESS) {
            if (intent.hasExtra(EXTRA_POSITION_MS)) {
                positionMs = intent.getIntExtra(EXTRA_POSITION_MS, positionMs).coerceAtLeast(0)
            }
            return START_NOT_STICKY
        }
        val overlayWasMissing = overlayView == null
        val wasExpanded = expanded
        if (intent?.action == ACTION_UPDATE) applyUpdate(intent)
        startAsForegroundService()
        ensureOverlayWindow()
        // The cover and lyrics are composed in one attached view. Text, language and playback
        // updates recompose that view; only expand/collapse changes its WindowManager bounds.
        if (!overlayWasMissing && wasExpanded != expanded) updateWindowPositions()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        mainHandler.removeCallbacks(progressTicker)
        if (positionUpdateScheduled) {
            Choreographer.getInstance().removeFrameCallback(positionFrameCallback)
            positionUpdateScheduled = false
        }
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
        if (intent.hasExtra(EXTRA_PLAYBACK_MODE)) {
            playbackMode = intent.getIntExtra(EXTRA_PLAYBACK_MODE, playbackMode).coerceIn(0, 2)
        }
        if (intent.hasExtra(EXTRA_OPEN_MODE)) openMode = intent.getStringExtra(EXTRA_OPEN_MODE).orEmpty()
        if (intent.hasExtra(EXTRA_OPEN_TRACK_ID)) openTrackId = intent.getStringExtra(EXTRA_OPEN_TRACK_ID).orEmpty()
        if (intent.hasExtra(EXTRA_OPEN_PLAYLIST_ID)) openPlaylistId = intent.getLongExtra(EXTRA_OPEN_PLAYLIST_ID, 0L)
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
            coverY = (displayHeight - dp(106)).coerceAtLeast(dp(24))
        }
        initializedPosition = true
        clampPosition()
    }

    private fun clampPosition() {
        val minimumX = if (expanded) lyricsWidthPx() + dp(14) else dp(8)
        val maximumX = (displayWidth - dp(8) - dp(64)).coerceAtLeast(minimumX)
        coverX = coverX.coerceIn(minimumX.coerceAtMost(maximumX), maximumX)
        val maximumY = (displayHeight - dp(106)).coerceAtLeast(dp(24))
        coverY = coverY.coerceIn(dp(24), maximumY)
    }

    private fun savePosition() {
        getSharedPreferences("music_meta_settings", Context.MODE_PRIVATE).edit()
            .putFloat("overlay_cover_x_dp", coverX / density)
            .putFloat("overlay_cover_y_dp", coverY / density)
            .apply()
    }

    private fun moveBy(dx: Float, dy: Float) {
        // Keep fractional pixels across input samples; rounding every sample discarded tiny
        // movements and made the cross-app overlay feel slightly sticky.
        val minX = if (expanded) lyricsWidthPx() + dp(14) else dp(8)
        val maxX = (displayWidth - dp(8) - dp(64)).coerceAtLeast(minX)
        val nextX = (coverX + dragRemainderX + dx).coerceIn(minX.toFloat(), maxX.toFloat())
        coverX = nextX.roundToInt()
        dragRemainderX = nextX - coverX

        val minY = dp(24)
        val maxY = (displayHeight - dp(106)).coerceAtLeast(minY)
        val nextY = (coverY + dragRemainderY + dy).coerceIn(minY.toFloat(), maxY.toFloat())
        coverY = nextY.roundToInt()
        dragRemainderY = nextY - coverY

        clampPosition()
        // WindowManager updates and preference writes are expensive on some devices. Batch
        // layout updates by display frame and save only when the pointer gesture ends.
        scheduleWindowPositionUpdate()
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

    private fun overlayWindowX(): Int =
        if (expanded) coverX - dp(14) - lyricsWidthPx() else coverX

    private fun ensureOverlayWindow() {
        if (overlayView != null) return
        val wm = windowManager ?: return
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayViewTreeOwner)
            setViewTreeSavedStateRegistryOwner(overlayViewTreeOwner)
            setContent {
                OverlayPlayerUi(
                    expanded = expanded,
                    lyricsWidthDp = lyricsWidthPx() / density,
                    coverUrl = coverUrl,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = { sendCommand(this@FloatingMiniPlayerService, COMMAND_TOGGLE_PLAY) },
                    onLongPress = { toggleExpanded() },
                    onClose = { closePlayer() },
                    onTogglePlaybackMode = { sendCommand(this@FloatingMiniPlayerService, COMMAND_TOGGLE_PLAYBACK_MODE) },
                    playbackMode = playbackMode,
                    onOpenApp = { openAppAndLocateSong() },
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onDragEnd = {
                        dragRemainderX = 0f
                        dragRemainderY = 0f
                        savePosition()
                    },
                    originalLyrics = originalLyrics,
                    translatedLyrics = translatedLyrics,
                    showTranslation = showTranslation,
                    onToggleTranslation = {
                        // Activity owns the selected lyric language; only send the request here.
                        sendCommand(this@FloatingMiniPlayerService, COMMAND_TOGGLE_TRANSLATION)
                    },
                    onSeek = { seekMs ->
                        positionMs = seekMs.coerceIn(0, durationMs.coerceAtLeast(0))
                        sendCommand(this@FloatingMiniPlayerService, COMMAND_SEEK, positionMs)
                    },
                    onPrevious = { sendCommand(this@FloatingMiniPlayerService, COMMAND_PREVIOUS) },
                    onNext = { sendCommand(this@FloatingMiniPlayerService, COMMAND_NEXT) },
                    onCircularDrag = { openAppAndLocateSong() },
                )
            }
        }

        // Clamp after applying the latest expanded state, before deriving the combined window's
        // origin. Otherwise a first attach that starts expanded can place the lyrics off-screen.
        clampPosition()
        val params = createLayoutParams(
            if (expanded) groupWidthPx() else dp(64),
            dp(88),
            overlayWindowX(),
            coverY,
        )
        wm.addView(view, params)
        overlayView = view
        overlayParams = params
    }

    private fun updateWindowPositions() {
        val wm = windowManager ?: return
        clampPosition()
        val params = overlayParams ?: return
        params.x = overlayWindowX()
        params.y = coverY
        params.width = if (expanded) groupWidthPx() else dp(64)
        params.height = dp(88)
        overlayView?.let { view -> runCatching { wm.updateViewLayout(view, params) } }
    }

    private fun toggleExpanded() {
        // Activity owns expanded/collapsed state; optimistically mutating service state here
        // created a race with the Activity update and could flash the lyrics window.
        sendCommand(this, COMMAND_TOGGLE_DETAILS)
    }

    private fun openAppAndLocateSong() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPEN_APP_AND_LOCATE, true)
            putExtra(EXTRA_OPEN_MODE, openMode)
            putExtra(EXTRA_OPEN_TRACK_ID, openTrackId)
            putExtra(EXTRA_OPEN_PLAYLIST_ID, openPlaylistId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_ARTIST, artist)
        }
        runCatching { startActivity(intent) }
    }

    private fun closePlayer() {
        sendCommand(this, COMMAND_CLOSE)
        removeOverlayViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlayViews() {
        val wm = windowManager
        overlayView?.let { runCatching { wm?.removeView(it) } }
        overlayView = null
        overlayParams = null
    }
}

@Composable
private fun OverlayPlayerUi(
    expanded: Boolean,
    lyricsWidthDp: Float,
    coverUrl: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Int,
    durationMs: Int,
    onTogglePlay: () -> Unit,
    onLongPress: () -> Unit,
    onClose: () -> Unit,
    onTogglePlaybackMode: () -> Unit,
    playbackMode: Int,
    onOpenApp: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    originalLyrics: String,
    translatedLyrics: String,
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCircularDrag: () -> Unit,
    onDragEnd: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val handleLongPress = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongPress()
    }
    val lyricsWidth = lyricsWidthDp.dp
    Box(
        modifier = Modifier
            .width(if (expanded) lyricsWidth + 78.dp else 64.dp)
            .height(88.dp),
    ) {
        if (expanded) {
            OverlayLyricsUi(
                originalLyrics = originalLyrics,
                translatedLyrics = translatedLyrics,
                showTranslation = showTranslation,
                positionMs = positionMs,
                durationMs = durationMs,
                onToggleTranslation = onToggleTranslation,
                onSeek = onSeek,
                onPrevious = onPrevious,
                onNext = onNext,
                onDrag = onDrag,
                onCircularDrag = onCircularDrag,
                onLongPress = handleLongPress,
                onDragEnd = onDragEnd,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp)
                    .width(lyricsWidth)
                    .height(64.dp),
            )
        }
        OverlayCoverUi(
            coverUrl = coverUrl,
            isPlaying = isPlaying,
            isLoading = isLoading,
            positionMs = positionMs,
            durationMs = durationMs,
            onTogglePlay = onTogglePlay,
            onLongPress = handleLongPress,
            onClose = onClose,
            onTogglePlaybackMode = onTogglePlaybackMode,
            playbackMode = playbackMode,
            onOpenApp = onOpenApp,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            modifier = Modifier.align(Alignment.TopEnd),
        )
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
    onTogglePlaybackMode: () -> Unit,
    playbackMode: Int,
    onOpenApp: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF171E1A))
                .border(1.dp, Color(0xFF2B3730), RoundedCornerShape(18.dp))
                .overlayDragGesture(
                    onDrag = onDrag,
                    onCircularDrag = onOpenApp,
                    onDragEnd = onDragEnd,
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTogglePlay() },
                        onDoubleTap = { onClose() },
                        onLongPress = { onLongPress() },
                    )
                }
        ) {
            AsyncImage(
                model = coverUrl.takeIf { it.isNotBlank() },
                contentDescription = "单击播放 / 暂停，双击关闭悬浮窗，长按展开 / 收起歌词，拖动移动；画圈拖动打开应用并定位歌曲",
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
            Box(
                modifier = Modifier.size(18.dp)
                    .clickable { onTogglePlaybackMode() }
                    .semantics {
                        contentDescription = "当前播放模式：" + listOf("顺序播放", "乱序播放", "单曲循环")[playbackMode] + "，点击切换"
                    },
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
                    tint = Color(0xFF9FF0C9),
                    modifier = Modifier.size(15.dp),
                )
            }
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
                        contentDescription = if (isPlaying) "暂停播放" else "继续播放"
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
    onDrag: (Float, Float) -> Unit,
    onCircularDrag: () -> Unit,
    onLongPress: () -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeText = if (showTranslation && translatedLyrics.isNotBlank()) translatedLyrics else originalLyrics
    val currentLyric = when {
        activeText.isBlank() -> "正在等待歌词…"
        else -> findMiniLyric(activeText, positionMs + 180)
    }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Card(
        modifier = modifier.fillMaxSize().overlayDragGesture(
            onDrag = onDrag,
            onCircularDrag = onCircularDrag,
            onDragEnd = onDragEnd,
        ),
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
                        .semantics { contentDescription = "按当前播放队列播放上一首" },
                    contentAlignment = Alignment.Center,
                ) { OverlaySkipGlyph(previous = true) }
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF243631))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onToggleTranslation() },
                                onLongPress = { onLongPress() },
                            )
                        }
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
                        .semantics { contentDescription = "按当前播放队列播放下一首" },
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
