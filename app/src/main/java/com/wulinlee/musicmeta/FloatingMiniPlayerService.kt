package com.wulinlee.musicmeta

import android.content.Context
import android.content.res.Configuration
import android.content.Intent
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.rotate
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
    ignoreDragBelowFraction: Float? = null,
): Modifier = pointerInput(key) {
    val touchSlop = viewConfiguration.touchSlop
    // Require three complete turns and meaningful movement to reduce accidental activation.
    val minimumCircularDistance = 360.dp.toPx()
    val requiredCircularRotation = 6f * 3.14159265f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (ignoreDragBelowFraction != null && down.position.y >= size.height * ignoreDragBelowFraction) {
            return@awaitEachGesture
        }
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
internal object FloatingOverlayPlacement {
    fun lyricsPanelOnRight(coverX: Int, coverWidthPx: Int, displayWidth: Int): Boolean =
        displayWidth > 0 && coverX + coverWidthPx / 2 < displayWidth / 2

    fun availableSpacePx(
        displayWidth: Int,
        coverX: Int,
        coverWidthPx: Int,
        marginPx: Int,
        lyricsOnRight: Boolean,
    ): Int = (if (lyricsOnRight) {
        displayWidth - marginPx - coverX - coverWidthPx
    } else {
        coverX - marginPx
    }).coerceAtLeast(0)

    fun panelGapPx(
        displayWidth: Int,
        coverX: Int,
        coverWidthPx: Int,
        preferredGapPx: Int,
        marginPx: Int,
        lyricsOnRight: Boolean,
    ): Int = minOf(
        preferredGapPx,
        availableSpacePx(displayWidth, coverX, coverWidthPx, marginPx, lyricsOnRight),
    )

    fun scaledPreferredLyricsWidthPx(baseGroupWidthPx: Int, nonLyricsWidthPx: Int, scale: Float = 1.2f): Int =
        ((baseGroupWidthPx - nonLyricsWidthPx).coerceAtLeast(0) * scale.coerceAtLeast(0f)).roundToInt()

    fun centeredCoverXForExpandedGroup(
        displayWidth: Int,
        coverWidthPx: Int,
        desiredLyricsWidthPx: Int,
        preferredGapPx: Int,
        marginPx: Int,
        sideControlsWidthPx: Int,
    ): Int {
        val safeDisplayWidth = displayWidth.coerceAtLeast(0)
        val safeCoverWidth = coverWidthPx.coerceAtLeast(0)
        val safeMargin = marginPx.coerceAtLeast(0)
        val safeGap = preferredGapPx.coerceAtLeast(0)
        val safeControlsWidth = sideControlsWidthPx.coerceAtLeast(0)
        val availableLyricsWidth = (
            safeDisplayWidth - 2 * safeMargin - safeCoverWidth - safeGap - safeControlsWidth
        ).coerceAtLeast(0)
        val lyricsWidth = minOf(desiredLyricsWidthPx.coerceAtLeast(0), availableLyricsWidth)
        val gap = minOf(
            safeGap,
            (safeDisplayWidth - 2 * safeMargin - safeCoverWidth - safeControlsWidth - lyricsWidth).coerceAtLeast(0),
        )
        val groupWidth = safeCoverWidth + safeControlsWidth + gap + lyricsWidth
        return ((safeDisplayWidth + groupWidth) / 2 - safeCoverWidth)
            .coerceIn(safeMargin, (safeDisplayWidth - safeMargin - safeCoverWidth).coerceAtLeast(safeMargin))
    }

    fun lyricsWidthPx(
        displayWidth: Int,
        coverX: Int,
        coverWidthPx: Int,
        desiredLyricsWidthPx: Int,
        gapPx: Int,
        marginPx: Int,
        lyricsOnRight: Boolean,
        reservedSideControlsPx: Int = 0,
    ): Int = minOf(
        desiredLyricsWidthPx.coerceAtLeast(0),
        (availableSpacePx(displayWidth, coverX, coverWidthPx, marginPx, lyricsOnRight) -
            gapPx - reservedSideControlsPx.coerceAtLeast(0)).coerceAtLeast(0),
    )

    fun windowWidthPx(
        coverWidthPx: Int,
        lyricsWidthPx: Int,
        gapPx: Int,
        expandedBounds: Boolean,
        sideControlsWidthPx: Int = 0,
    ): Int = coverWidthPx + if (expandedBounds) lyricsWidthPx + gapPx + sideControlsWidthPx.coerceAtLeast(0) else 0

    fun windowOriginX(
        coverX: Int,
        lyricsWidthPx: Int,
        gapPx: Int,
        lyricsOnRight: Boolean,
        expandedBounds: Boolean,
        sideControlsWidthPx: Int = 0,
    ): Int = if (expandedBounds && !lyricsOnRight) {
        coverX - lyricsWidthPx - gapPx - sideControlsWidthPx.coerceAtLeast(0)
    } else {
        coverX
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
        const val EXTRA_LYRICS_FONT_SIZE_SP = "lyrics_font_size_sp"
        const val EXTRA_PLAYBACK_MODE = "playback_mode"
        const val EXTRA_OPEN_APP_AND_LOCATE = "open_app_and_locate"
        const val EXTRA_OPEN_MODE = "open_mode"
        const val EXTRA_OPEN_TRACK_ID = "open_track_id"
        const val EXTRA_OPEN_PLAYLIST_ID = "open_playlist_id"
        const val EXTRA_COMMAND = "command"

        const val COMMAND_TOGGLE_PLAY = "toggle_play"
        const val COMMAND_MEDIA_PLAY = "media_play"
        const val COMMAND_MEDIA_PAUSE = "media_pause"
        const val COMMAND_PREVIOUS = "previous_track"
        const val COMMAND_NEXT = "next_track"
        const val COMMAND_CLOSE_OVERLAY = "close_overlay"
        // Compatibility command used by the unchanged system media card.
        const val COMMAND_TOGGLE_OVERLAY = "toggle_overlay"
        const val COMMAND_TOGGLE_DETAILS = "toggle_details"
        const val COMMAND_TOGGLE_TRANSLATION = "toggle_translation"
        const val COMMAND_TOGGLE_PLAYBACK_MODE = "toggle_playback_mode"
        const val COMMAND_SEEK = "seek"
        const val COMMAND_CLOSE = "close"

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
            lyricsFontSizeSp: Float = DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP,
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
                .putExtra(EXTRA_LYRICS_FONT_SIZE_SP, lyricsFontSizeSp.coerceIn(
                    MIN_FLOATING_LYRICS_FONT_SIZE_SP,
                    MAX_FLOATING_LYRICS_FONT_SIZE_SP,
                ))
                .putExtra(EXTRA_PLAYBACK_MODE, playbackMode.coerceIn(0, 2))
                .putExtra(EXTRA_OPEN_MODE, openMode)
                .putExtra(EXTRA_OPEN_TRACK_ID, openTrackId)
                .putExtra(EXTRA_OPEN_PLAYLIST_ID, openPlaylistId)
            context.startService(intent)
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
    private var lyricsOverlayView: ComposeView? = null
    private var lyricsOverlayParams: WindowManager.LayoutParams? = null
    private val sideControlsWidthDp = 28

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
    private var lyricsPanelOnRight by mutableStateOf(false)
    private var lyricsPanelWidthPx by mutableIntStateOf(0)
    private var lyricsPanelGapPx by mutableIntStateOf(0)
    private var lyricsFontSizeSp by mutableStateOf(DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP)
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
        updateDisplaySize()
        restoreOrInitializePosition()
        mainHandler.postDelayed(progressTicker, 300L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            sendCommand(this, COMMAND_CLOSE_OVERLAY)
            removeOverlayViews()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_PROGRESS) {
            if (intent.hasExtra(EXTRA_POSITION_MS)) {
                positionMs = intent.getIntExtra(EXTRA_POSITION_MS, positionMs).coerceAtLeast(0)
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_UPDATE) applyUpdate(intent)
        ensureOverlayWindow()
        ensureLyricsOverlayWindow()
        updateWindowPositions()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The service can outlive a rotation or display-size change. Re-read pixel dimensions
        // before clamping so the complete expanded cover + lyrics group remains on-screen.
        updateDisplaySize()
        clampPosition()
        updateWindowPositions()
        savePosition()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(progressTicker)
        if (positionUpdateScheduled) {
            Choreographer.getInstance().removeFrameCallback(positionFrameCallback)
            positionUpdateScheduled = false
        }
        removeOverlayViews()
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
        if (intent.hasExtra(EXTRA_LYRICS_FONT_SIZE_SP)) {
            lyricsFontSizeSp = intent.getFloatExtra(EXTRA_LYRICS_FONT_SIZE_SP, lyricsFontSizeSp)
                .coerceIn(MIN_FLOATING_LYRICS_FONT_SIZE_SP, MAX_FLOATING_LYRICS_FONT_SIZE_SP)
        }
        if (intent.hasExtra(EXTRA_PLAYBACK_MODE)) {
            playbackMode = intent.getIntExtra(EXTRA_PLAYBACK_MODE, playbackMode).coerceIn(0, 2)
        }
        if (intent.hasExtra(EXTRA_OPEN_MODE)) openMode = intent.getStringExtra(EXTRA_OPEN_MODE).orEmpty()
        if (intent.hasExtra(EXTRA_OPEN_TRACK_ID)) openTrackId = intent.getStringExtra(EXTRA_OPEN_TRACK_ID).orEmpty()
        if (intent.hasExtra(EXTRA_OPEN_PLAYLIST_ID)) openPlaylistId = intent.getLongExtra(EXTRA_OPEN_PLAYLIST_ID, 0L)
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

    private fun desiredLyricsWidthPx(): Int =
        FloatingOverlayPlacement.scaledPreferredLyricsWidthPx(
            baseGroupWidthPx = dp(356),
            nonLyricsWidthPx = dp(78),
            scale = 1.2f,
        )

    private fun defaultCenteredOverlayCoverX(): Int =
        FloatingOverlayPlacement.centeredCoverXForExpandedGroup(
            displayWidth = displayWidth,
            coverWidthPx = dp(64),
            desiredLyricsWidthPx = desiredLyricsWidthPx(),
            preferredGapPx = dp(4),
            marginPx = dp(4),
            sideControlsWidthPx = dp(sideControlsWidthDp),
        )

    private fun restoreOrInitializePosition() {
        if (initializedPosition) return
        val preferences = getSharedPreferences("music_meta_settings", Context.MODE_PRIVATE)
        val storedX = preferences.getFloat("overlay_cover_x_dp", -1f)
        val storedY = preferences.getFloat("overlay_cover_y_dp", -1f)
        val layoutMigrated = preferences.getBoolean("overlay_side_controls_layout_v3", false)
        if (storedX >= 0f && storedY >= 0f) {
            coverX = (storedX * density).roundToInt()
            coverY = (storedY * density).roundToInt()
            if (!layoutMigrated) {
                // Older releases centered only the cover. If that legacy default is still in
                // use, recenter the whole group once so the new side controls do not steal
                // space from the lyrics panel. Preserve clearly user-positioned overlays.
                val legacyCenteredX = ((displayWidth - dp(64)) / 2).coerceAtLeast(dp(4))
                if (abs(coverX - legacyCenteredX) <= dp(24)) {
                    coverX = defaultCenteredOverlayCoverX()
                }
            }
            preferences.edit().putBoolean("overlay_side_controls_layout_v3", true).apply()
        } else {
            coverX = defaultCenteredOverlayCoverX()
            coverY = (displayHeight - dp(106)).coerceAtLeast(dp(24))
            preferences.edit().putBoolean("overlay_side_controls_layout_v3", true).apply()
        }
        initializedPosition = true
        clampPosition()
    }

    private fun clampPosition() {
        val margin = dp(4)
        val coverWidth = dp(64)
        coverX = coverX.coerceIn(margin, (displayWidth - margin - coverWidth).coerceAtLeast(margin))

        val panelOnRight = FloatingOverlayPlacement.lyricsPanelOnRight(coverX, coverWidth, displayWidth)
        lyricsPanelOnRight = panelOnRight
        val gap = FloatingOverlayPlacement.panelGapPx(
            displayWidth = displayWidth,
            coverX = coverX,
            coverWidthPx = coverWidth,
            preferredGapPx = dp(4),
            marginPx = margin,
            lyricsOnRight = panelOnRight,
        )
        lyricsPanelGapPx = gap
        lyricsPanelWidthPx = FloatingOverlayPlacement.lyricsWidthPx(
            displayWidth = displayWidth,
            coverX = coverX,
            coverWidthPx = coverWidth,
            desiredLyricsWidthPx = desiredLyricsWidthPx(),
            gapPx = gap,
            marginPx = margin,
            lyricsOnRight = panelOnRight,
            reservedSideControlsPx = dp(sideControlsWidthDp),
        )
        val minimumY = dp(24)
        val maximumY = (displayHeight - dp(106)).coerceAtLeast(minimumY)
        coverY = coverY.coerceIn(minimumY, maximumY)
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
        val margin = dp(4)
        val coverWidth = dp(64)
        val minX = margin
        val maxX = (displayWidth - margin - coverWidth).coerceAtLeast(minX)
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

    private fun lyricsWindowX(): Int =
        if (lyricsPanelOnRight) coverX + dp(64)
        else coverX - lyricsPanelWidthPx - lyricsPanelGapPx - dp(sideControlsWidthDp)

    private fun onExpansionAnimationFinished(isExpanded: Boolean) {
        mainHandler.post { savePosition() }
    }

    private fun ensureOverlayWindow() {
        if (overlayView != null) return
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
                    onTogglePlaybackMode = { sendCommand(this@FloatingMiniPlayerService, COMMAND_TOGGLE_PLAYBACK_MODE) },
                    playbackMode = playbackMode,
                    onOpenApp = { openAppAndLocateSong() },
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onDragEnd = {
                        dragRemainderX = 0f
                        dragRemainderY = 0f
                        savePosition()
                    },
                            modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val params = createLayoutParams(dp(64), dp(88), coverX, coverY)
        overlayView = view
        overlayParams = params
        try {
            wm.addView(view, params)
        } catch (error: Exception) {
            overlayView = null
            overlayParams = null
            throw error
        }
    }

    private fun ensureLyricsOverlayWindow() {
        if (lyricsOverlayView != null) return
        val wm = windowManager ?: return
        clampPosition()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayViewTreeOwner)
            setViewTreeSavedStateRegistryOwner(overlayViewTreeOwner)
            setContent {
                OverlayLyricsWindowUi(
                    expanded = expanded,
                    lyricsFontSizeSp = lyricsFontSizeSp,
                    lyricsWidthDp = lyricsPanelWidthPx / density,
                    sideControlsWidthDp = sideControlsWidthDp.toFloat(),
                    lyricsGapDp = lyricsPanelGapPx / density,
                    originalLyrics = originalLyrics,
                    translatedLyrics = translatedLyrics,
                    showTranslation = showTranslation,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onExpansionAnimationFinished = { onExpansionAnimationFinished(it) },
                    onToggleTranslation = {
                        sendCommand(this@FloatingMiniPlayerService, COMMAND_TOGGLE_TRANSLATION)
                    },
                    onSeek = { seekMs ->
                        positionMs = seekMs.coerceIn(0, durationMs.coerceAtLeast(0))
                        sendCommand(this@FloatingMiniPlayerService, COMMAND_SEEK, positionMs)
                    },
                    onPrevious = { sendCommand(this@FloatingMiniPlayerService, COMMAND_PREVIOUS) },
                    onNext = { sendCommand(this@FloatingMiniPlayerService, COMMAND_NEXT) },
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onCircularDrag = { openAppAndLocateSong() },
                    onLongPress = { toggleExpanded() },
                    onDragEnd = {
                        dragRemainderX = 0f
                        dragRemainderY = 0f
                        savePosition()
                    },
                    lyricsOnRight = lyricsPanelOnRight,
                )
            }
        }
        val params = createLayoutParams(
            (lyricsPanelWidthPx + dp(sideControlsWidthDp) + lyricsPanelGapPx).coerceAtLeast(dp(sideControlsWidthDp)),
            dp(88),
            lyricsWindowX(),
            coverY,
        )
        lyricsOverlayView = view
        lyricsOverlayParams = params
        try {
            wm.addView(view, params)
        } catch (error: Exception) {
            lyricsOverlayView = null
            lyricsOverlayParams = null
            throw error
        }
    }

    private fun removeLyricsOverlayWindow() {
        val wm = windowManager
        lyricsOverlayView?.let { view -> runCatching { wm?.removeView(view) } }
        lyricsOverlayView = null
        lyricsOverlayParams = null
    }

    private fun updateWindowPositions() {
        val wm = windowManager ?: return
        overlayParams?.let { params ->
            val nextX = coverX
            val nextY = coverY
            val nextWidth = dp(64)
            val nextHeight = dp(88)
            if (params.x != nextX || params.y != nextY ||
                params.width != nextWidth || params.height != nextHeight
            ) {
                params.x = nextX
                params.y = nextY
                params.width = nextWidth
                params.height = nextHeight
                overlayView?.let { view -> runCatching { wm.updateViewLayout(view, params) } }
            }
        }
        lyricsOverlayParams?.let { params ->
            val nextX = lyricsWindowX()
            val nextY = coverY
            val nextWidth = (lyricsPanelWidthPx + dp(sideControlsWidthDp) + lyricsPanelGapPx).coerceAtLeast(dp(sideControlsWidthDp))
            val nextHeight = dp(88)
            if (params.x != nextX || params.y != nextY ||
                params.width != nextWidth || params.height != nextHeight
            ) {
                params.x = nextX
                params.y = nextY
                params.width = nextWidth
                params.height = nextHeight
                lyricsOverlayView?.let { view -> runCatching { wm.updateViewLayout(view, params) } }
            }
        }
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
        stopSelf()
    }

    private fun removeOverlayViews() {
        removeLyricsOverlayWindow()
        val wm = windowManager
        overlayView?.let { runCatching { wm?.removeView(it) } }
        overlayView = null
        overlayParams = null
    }
}

@Composable
private fun OverlayLyricsWindowUi(
    expanded: Boolean,
    lyricsFontSizeSp: Float,
    lyricsWidthDp: Float,
    sideControlsWidthDp: Float,
    lyricsGapDp: Float,
    originalLyrics: String,
    translatedLyrics: String,
    showTranslation: Boolean,
    positionMs: Int,
    durationMs: Int,
    onExpansionAnimationFinished: (Boolean) -> Unit,
    onToggleTranslation: () -> Unit,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onCircularDrag: () -> Unit,
    onLongPress: () -> Unit,
    onDragEnd: () -> Unit,
    lyricsOnRight: Boolean,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        )
        onExpansionAnimationFinished(expanded)
    }
    // Collapsing the lyrics panel must not make its transparent area draggable.
    // Only the cover overlay owns the drag gesture while the panel is collapsed.
    Box(Modifier.fillMaxSize()) {
        val sideWidth = sideControlsWidthDp.dp
        val lyricsGap = lyricsGapDp.dp
        // The control strip is attached to the cover; only the lyric panel gets a small gap.
        // Reserve a transparent 28dp hit target for each independent arrow; the lyric card stays separate.
        val lyricsOffset = if (lyricsOnRight) sideWidth + lyricsGap else 0.dp
        if (progress.value > 0.001f) {
            OverlayLyricsUi(
                lyricsFontSizeSp = lyricsFontSizeSp,
                originalLyrics = originalLyrics,
                translatedLyrics = translatedLyrics,
                showTranslation = showTranslation,
                positionMs = positionMs,
                durationMs = durationMs,
                onToggleTranslation = onToggleTranslation,
                onSeek = onSeek,
                onDrag = onDrag,
                onCircularDrag = onCircularDrag,
                onLongPress = onLongPress,
                onDragEnd = onDragEnd,
                modifier = Modifier.offset(x = lyricsOffset, y = 4.dp).width(lyricsWidthDp.dp).height(64.dp)
                    .graphicsLayer {
                        alpha = progress.value
                        scaleX = progress.value
                        transformOrigin = if (lyricsOnRight) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                    },
            )
        }
        OverlaySideTrackControls(
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = Modifier.width(sideWidth).height(64.dp)
                .align(if (lyricsOnRight) Alignment.TopStart else Alignment.TopEnd),
        )
    }
}

@Composable
private fun OverlayPlayerUi(
    expanded: Boolean,
    windowBoundsExpanded: Boolean,
    lyricsOnRight: Boolean,
    lyricsWidthDp: Float,
    lyricsGapDp: Float,
    lyricsFontSizeSp: Float,
    onExpansionAnimationFinished: (Boolean) -> Unit,
    onWindowBoundsCompositionReady: () -> Unit,
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
    val expansionProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        finishedListener = { onExpansionAnimationFinished(expanded) },
        label = "lyricsPanelExpansion",
    )

    // WindowManager bounds are fixed through animation: only lyrics alpha/scale changes,
    // so the cover's absolute X coordinate remains stable on both sides of the screen.
    val groupWidthDp = if (windowBoundsExpanded) 64f + lyricsWidthDp + lyricsGapDp else 64f
    SideEffect { onWindowBoundsCompositionReady() }
    val lyricsModifier = Modifier
        .offset(x = if (lyricsOnRight) (64f + lyricsGapDp).dp else 0.dp)
        .width(lyricsWidthDp.dp)
        .height(64.dp)
        .graphicsLayer {
            scaleX = expansionProgress
            alpha = expansionProgress
            transformOrigin = if (lyricsOnRight) {
                TransformOrigin(0f, 0.5f)
            } else {
                TransformOrigin(1f, 0.5f)
            }
        }

    Box(
        modifier = Modifier
            .width(groupWidthDp.dp)
            .height(88.dp)
            .clipToBounds(),
    ) {
        if (windowBoundsExpanded && expansionProgress > 0.001f) {
            OverlayLyricsUi(
                originalLyrics = originalLyrics,
                translatedLyrics = translatedLyrics,
                showTranslation = showTranslation,
                positionMs = positionMs,
                durationMs = durationMs,
                onToggleTranslation = onToggleTranslation,
                onSeek = onSeek,
                onDrag = onDrag,
                onCircularDrag = onCircularDrag,
                onLongPress = handleLongPress,
                onDragEnd = onDragEnd,
                lyricsFontSizeSp = lyricsFontSizeSp,
                modifier = lyricsModifier,
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
            modifier = Modifier.align(if (lyricsOnRight) Alignment.TopStart else Alignment.TopEnd),
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
    val coverShape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(coverShape)
                .background(Color(0xFF171E1A))
                .border(1.dp, Color(0xFF2B3730), coverShape)
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
    lyricsFontSizeSp: Float,
    originalLyrics: String,
    translatedLyrics: String,
    showTranslation: Boolean,
    positionMs: Int,
    durationMs: Int,
    onToggleTranslation: () -> Unit,
    onSeek: (Int) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onCircularDrag: () -> Unit,
    onLongPress: () -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeText = if (showTranslation && translatedLyrics.isNotBlank()) translatedLyrics else originalLyrics
    val currentLyric = when {
        activeText.isBlank() -> "正在等待歌词…"
        else -> currentLyricLine(
            activeLyrics = activeText,
            originalLyrics = originalLyrics,
            positionMs = positionMs + 180,
            isTranslation = showTranslation && translatedLyrics.isNotBlank(),
        )
    }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Card(
        modifier = modifier.fillMaxSize().overlayDragGesture(
            onDrag = onDrag,
            onCircularDrag = onCircularDrag,
            onDragEnd = onDragEnd,
            ignoreDragBelowFraction = 0.68f,
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17211F)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF243631))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggleTranslation() },
                            onLongPress = { onLongPress() },
                        )
                    }
                    .padding(horizontal = 5.dp, vertical = 0.dp)
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
                    fontSize = lyricsFontSizeSp.sp,
                    lineHeight = (lyricsFontSizeSp * 1.12f).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(0.94f).height(18.dp).offset(y = 2.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { tap ->
                            if (durationMs > 0 && size.width > 0) {
                                onSeek((tap.x / size.width.toFloat()).coerceIn(0f, 1f).times(durationMs).toInt())
                            }
                        }
                    }
                    .pointerInput(durationMs) {
                        detectDragGestures(
                            onDragStart = { start ->
                                if (durationMs > 0 && size.width > 0) {
                                    onSeek((start.x / size.width.toFloat()).coerceIn(0f, 1f).times(durationMs).toInt())
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (durationMs > 0 && size.width > 0) {
                                    onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f).times(durationMs).toInt())
                                }
                            },
                        )
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
private fun OverlaySideTrackControls(
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
                drawLine(
                    arrowColor,
                    Offset(centerX, bottom),
                    Offset(centerX, top + headDepth),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    arrowColor,
                    Offset(centerX, top),
                    Offset(centerX - headDepth, top + headDepth),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    arrowColor,
                    Offset(centerX, top),
                    Offset(centerX + headDepth, top + headDepth),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
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
                drawLine(
                    arrowColor,
                    Offset(centerX, top),
                    Offset(centerX, bottom - headDepth),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    arrowColor,
                    Offset(centerX, bottom),
                    Offset(centerX - headDepth, bottom - headDepth),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    arrowColor,
                    Offset(centerX, bottom),
                    Offset(centerX + headDepth, bottom - headDepth),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun OverlaySkipGlyph(previous: Boolean) {
    Icon(
        painter = painterResource(if (previous) R.drawable.lucide_skip_back else R.drawable.lucide_skip_forward),
        contentDescription = null,
        tint = Color(0xFF9FF0C9),
        modifier = Modifier.size(17.dp),
    )
}

@Composable
private fun OverlayPlayGlyph(isPlaying: Boolean, isLoading: Boolean) {
    if (isLoading) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = Color(0xFF9AA69E),
            strokeWidth = 1.6.dp,
        )
    } else {
        Icon(
            painter = painterResource(if (isPlaying) R.drawable.lucide_pause else R.drawable.lucide_play),
            contentDescription = null,
            tint = Color(0xFF9FF0C9),
            modifier = Modifier.size(16.dp),
        )
    }
}


private fun formatMiniTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
