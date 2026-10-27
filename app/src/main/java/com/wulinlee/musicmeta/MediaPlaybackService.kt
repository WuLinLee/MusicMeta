package com.wulinlee.musicmeta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Publishes the current MusicMeta preview as a system media session.
 *
 * The activity remains the owner of the existing MediaPlayer. This service bridges the platform
 * media controls to that player while keeping a MediaStyle notification available on the lock
 * screen, notification shade, and compatible Bluetooth/headset controls.
 */
class MediaPlaybackService : Service() {
    companion object {
        const val ACTION_UPDATE = "com.wulinlee.musicmeta.media.UPDATE"
        const val ACTION_POSITION = "com.wulinlee.musicmeta.media.POSITION"
        const val ACTION_COMMAND = "com.wulinlee.musicmeta.media.COMMAND"
        const val ACTION_STOP = "com.wulinlee.musicmeta.media.STOP"

        const val EXTRA_TITLE = "media_title"
        const val EXTRA_ARTIST = "media_artist"
        const val EXTRA_COVER_URI = "media_cover_uri"
        const val EXTRA_LYRIC = "media_current_lyric"
        const val EXTRA_DURATION = "media_duration"
        const val EXTRA_POSITION = "media_position"
        const val EXTRA_IS_PLAYING = "media_is_playing"
        const val EXTRA_IS_LOADING = "media_is_loading"
        const val EXTRA_IS_MUTED = "media_is_muted"
        const val EXTRA_PLAYBACK_MODE = "media_playback_mode"
        const val EXTRA_COMMAND = "media_command"

        private const val CHANNEL_ID = "musicmeta_media_controls"
        private const val NOTIFICATION_ID = 1201
        private const val REQUEST_OPEN_APP = 1202
        private const val REQUEST_PREVIOUS = 1203
        private const val REQUEST_PLAY_PAUSE = 1204
        private const val REQUEST_NEXT = 1205
        private const val REQUEST_MUTE = 1206
        private const val CUSTOM_ACTION_MUTE = "com.wulinlee.musicmeta.media.TOGGLE_MUTE"

        @Volatile
        private var serviceAlive = false

        fun update(
            context: Context,
            title: String,
            artist: String,
            coverUri: String,
            currentLyric: String,
            durationMs: Int,
            positionMs: Int,
            isPlaying: Boolean,
            isLoading: Boolean,
            isMuted: Boolean,
            playbackMode: Int,
        ) {
            val intent = Intent(context, MediaPlaybackService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_COVER_URI, coverUri)
                .putExtra(EXTRA_LYRIC, currentLyric)
                .putExtra(EXTRA_DURATION, durationMs.coerceAtLeast(0))
                .putExtra(EXTRA_POSITION, positionMs.coerceAtLeast(0))
                .putExtra(EXTRA_IS_PLAYING, isPlaying)
                .putExtra(EXTRA_IS_LOADING, isLoading)
                .putExtra(EXTRA_IS_MUTED, isMuted)
                .putExtra(EXTRA_PLAYBACK_MODE, playbackMode.coerceIn(0, 2))
            if (serviceAlive) context.startService(intent)
            else ContextCompat.startForegroundService(context, intent)
        }

        fun updatePosition(context: Context, positionMs: Int, durationMs: Int) {
            if (!serviceAlive) return
            context.startService(
                Intent(context, MediaPlaybackService::class.java)
                    .setAction(ACTION_POSITION)
                    .putExtra(EXTRA_POSITION, positionMs.coerceAtLeast(0))
                    .putExtra(EXTRA_DURATION, durationMs.coerceAtLeast(0)),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaPlaybackService::class.java))
        }

        private fun controlIntent(context: Context, command: String): Intent =
            Intent(context, MediaPlaybackService::class.java)
                .setAction(ACTION_COMMAND)
                .putExtra(EXTRA_COMMAND, command)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var mediaSession: MediaSessionCompat
    private var foregroundStarted = false
    private var trackTitle = ""
    private var trackArtist = ""
    private var coverUri = ""
    private var currentLyric = ""
    private var durationMs = 0
    private var positionMs = 0
    private var isPlaying = false
    private var isLoading = false
    private var isMuted = false
    private var playbackMode = 0
    private var albumArt: Bitmap? = null
    private var artworkLoadInProgressFor = ""

    override fun onCreate() {
        super.onCreate()
        serviceAlive = true
        ensureNotificationChannel()
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSessionCompat(this, "MusicMetaMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setSessionActivity(openApp)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = dispatch(FloatingMiniPlayerService.COMMAND_MEDIA_PLAY)
                override fun onPause() = dispatch(FloatingMiniPlayerService.COMMAND_MEDIA_PAUSE)
                override fun onSkipToNext() = dispatch(FloatingMiniPlayerService.COMMAND_NEXT)
                override fun onSkipToPrevious() = dispatch(FloatingMiniPlayerService.COMMAND_PREVIOUS)
                override fun onSeekTo(pos: Long) {
                    dispatch(
                        FloatingMiniPlayerService.COMMAND_SEEK,
                        pos.coerceIn(0L, durationMs.toLong().coerceAtLeast(0L)).toInt(),
                    )
                }
                override fun onStop() = dispatch(FloatingMiniPlayerService.COMMAND_MEDIA_PAUSE)
                override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                    if (action == CUSTOM_ACTION_MUTE) {
                        dispatch(FloatingMiniPlayerService.COMMAND_MEDIA_TOGGLE_MUTE)
                    }
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_COMMAND -> {
                val command = intent.getStringExtra(EXTRA_COMMAND)
                if (!command.isNullOrBlank()) dispatch(command)
                return START_NOT_STICKY
            }
            ACTION_POSITION -> {
                positionMs = intent.getIntExtra(EXTRA_POSITION, positionMs).coerceAtLeast(0)
                durationMs = intent.getIntExtra(EXTRA_DURATION, durationMs).coerceAtLeast(0)
                publishPlaybackState()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> applyUpdate(intent)
            else -> return START_NOT_STICKY
        }

        mediaSession.isActive = true
        publishMetadata()
        publishPlaybackState()
        val notification = buildNotification()
        if (!foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        } else {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
        loadArtworkIfNeeded()
        return START_NOT_STICKY
    }

    private fun dispatch(command: String, position: Int = 0) {
        FloatingMiniPlayerService.sendCommand(this, command, position)
    }

    private fun applyUpdate(intent: Intent) {
        trackTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        trackArtist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        val nextCoverUri = intent.getStringExtra(EXTRA_COVER_URI).orEmpty()
        if (nextCoverUri != coverUri) {
            coverUri = nextCoverUri
            albumArt = null
        }
        currentLyric = intent.getStringExtra(EXTRA_LYRIC).orEmpty()
        durationMs = intent.getIntExtra(EXTRA_DURATION, durationMs).coerceAtLeast(0)
        positionMs = intent.getIntExtra(EXTRA_POSITION, positionMs).coerceAtLeast(0)
        isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
        isLoading = intent.getBooleanExtra(EXTRA_IS_LOADING, isLoading)
        isMuted = intent.getBooleanExtra(EXTRA_IS_MUTED, isMuted)
        playbackMode = intent.getIntExtra(EXTRA_PLAYBACK_MODE, playbackMode).coerceIn(0, 2)
    }

    private fun publishMetadata() {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, trackArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "音笺")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, trackTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, trackArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, currentLyric)
            .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, coverUri)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs.toLong())
        if (albumArt != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, albumArt)
        }
        mediaSession.setMetadata(metadata.build())
    }

    private fun publishPlaybackState() {
        val builder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP,
            )
            .setState(
                when {
                    isLoading -> PlaybackStateCompat.STATE_BUFFERING
                    isPlaying -> PlaybackStateCompat.STATE_PLAYING
                    durationMs > 0 && positionMs >= durationMs -> PlaybackStateCompat.STATE_PAUSED
                    else -> PlaybackStateCompat.STATE_PAUSED
                },
                positionMs.toLong(),
                if (isPlaying) 1f else 0f,
                SystemClock.elapsedRealtime(),
            )
        builder.addCustomAction(
            PlaybackStateCompat.CustomAction.Builder(
                CUSTOM_ACTION_MUTE,
                if (isMuted) "取消静音" else "静音",
                if (isMuted) android.R.drawable.ic_lock_silent_mode_off
                else android.R.drawable.ic_lock_silent_mode,
            ).build(),
        )
        mediaSession.setPlaybackState(builder.build())
        mediaSession.setRepeatMode(
            if (playbackMode == 2) PlaybackStateCompat.REPEAT_MODE_ONE
            else PlaybackStateCompat.REPEAT_MODE_NONE,
        )
        mediaSession.setShuffleMode(
            if (playbackMode == 1) PlaybackStateCompat.SHUFFLE_MODE_ALL
            else PlaybackStateCompat.SHUFFLE_MODE_NONE,
        )
    }

    private fun buildNotification(): Notification {
        val contentIntent = mediaSession.controller.sessionActivity
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(trackTitle.ifBlank { "音笺" })
            .setContentText(listOf(trackArtist, currentLyric).filter { it.isNotBlank() }.joinToString(" · "))
            .setSubText(currentLyric.takeIf { it.isNotBlank() })
            .setLargeIcon(albumArt)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying || isLoading)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "上一首",
                    commandPendingIntent(REQUEST_PREVIOUS, FloatingMiniPlayerService.COMMAND_PREVIOUS),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "暂停" else "播放",
                    commandPendingIntent(
                        REQUEST_PLAY_PAUSE,
                        if (isPlaying) FloatingMiniPlayerService.COMMAND_MEDIA_PAUSE
                        else FloatingMiniPlayerService.COMMAND_MEDIA_PLAY,
                    ),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "下一首",
                    commandPendingIntent(REQUEST_NEXT, FloatingMiniPlayerService.COMMAND_NEXT),
                ),
            )
            .addAction(
                NotificationCompat.Action(
                    if (isMuted) android.R.drawable.ic_lock_silent_mode_off
                    else android.R.drawable.ic_lock_silent_mode,
                    if (isMuted) "取消静音" else "静音",
                    commandPendingIntent(REQUEST_MUTE, FloatingMiniPlayerService.COMMAND_MEDIA_TOGGLE_MUTE),
                ),
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        return builder.build()
    }

    private fun commandPendingIntent(requestCode: Int, command: String): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, MediaPlaybackService::class.java)
                .setAction(ACTION_COMMAND)
                .putExtra(EXTRA_COMMAND, command),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "音笺媒体播放", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "在锁屏和通知栏显示当前歌曲、歌词与播放控制"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun loadArtworkIfNeeded() {
        val source = coverUri
        if (source.isBlank() || albumArt != null || artworkLoadInProgressFor == source) return
        artworkLoadInProgressFor = source
        Thread({
            val bitmap = decodeArtwork(source)
            mainHandler.post {
                if (coverUri != source) {
                    bitmap?.recycle()
                    if (artworkLoadInProgressFor == source) artworkLoadInProgressFor = ""
                    return@post
                }
                if (bitmap == null) {
                    artworkLoadInProgressFor = ""
                    return@post
                }
                albumArt?.recycle()
                albumArt = bitmap
                artworkLoadInProgressFor = ""
                publishMetadata()
                if (foregroundStarted) {
                    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
                }
            }
        }, "MusicMeta-media-art").start()
    }

    private fun decodeArtwork(source: String): Bitmap? {
        fun openStream(): InputStream? {
            val uri = runCatching { Uri.parse(source) }.getOrNull() ?: return null
            if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
                val connection = (URL(source).openConnection() as? HttpURLConnection) ?: return null
                connection.connectTimeout = 4500
                connection.readTimeout = 5500
                connection.instanceFollowRedirects = true
                return try {
                    val stream = connection.inputStream
                    object : FilterInputStream(stream) {
                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                connection.disconnect()
                            }
                        }
                    }
                } catch (_: Exception) {
                    connection.disconnect()
                    null
                }
            }
            return runCatching { contentResolver.openInputStream(uri) }.getOrNull()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 640 || bounds.outHeight / sampleSize > 640) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return runCatching { openStream()?.use { BitmapFactory.decodeStream(it, null, options) } }.getOrNull()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceAlive = false
        runCatching { mediaSession.isActive = false }
        runCatching { mediaSession.release() }
        albumArt?.recycle()
        albumArt = null
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
