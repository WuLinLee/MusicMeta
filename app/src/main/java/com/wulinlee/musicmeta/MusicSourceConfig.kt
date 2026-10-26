package com.wulinlee.musicmeta

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.util.ArrayDeque
import java.util.LinkedHashMap
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class MusicPlatformOption(val id: String, val label: String)

internal object MusicSourceConfig {
    const val BACKEND_GD_STUDIO = "gdstudio"
    const val BACKEND_FLAC_SITE = "flac"
    const val DEFAULT_BACKEND = BACKEND_GD_STUDIO
    const val DEFAULT_PLATFORM = "netease"

    val gdStudioPlatforms = listOf(
        MusicPlatformOption("netease", "网易云"),
        MusicPlatformOption("tencent", "QQ音乐"),
        MusicPlatformOption("kuwo", "酷我"),
        MusicPlatformOption("tidal", "Tidal"),
        MusicPlatformOption("qobuz", "Qobuz"),
        MusicPlatformOption("joox", "JOOX"),
        MusicPlatformOption("bilibili", "Bilibili"),
        MusicPlatformOption("apple", "Apple Music"),
        MusicPlatformOption("ytmusic", "YouTube Music"),
        MusicPlatformOption("spotify", "Spotify"),
    )
    // The flac.music.hi.cn adapter currently implements only NetEase and Kuwo.
    val flacSitePlatforms = listOf(
        MusicPlatformOption("netease", "网易云"),
        MusicPlatformOption("kuwo", "酷我"),
    )

    fun normalizeBackend(value: String?): String = when (value?.trim()?.lowercase()) {
        BACKEND_FLAC_SITE, "flac.music.hi.cn", "flac_site" -> BACKEND_FLAC_SITE
        else -> BACKEND_GD_STUDIO
    }

    fun normalizePlatform(value: String?): String {
        val candidate = value?.trim()?.lowercase().orEmpty()
        // v1.21 and earlier used "wyy" for NetEase.
        val normalized = if (candidate == "wyy") "netease" else candidate
        return gdStudioPlatforms.firstOrNull { it.id == normalized }?.id ?: DEFAULT_PLATFORM
    }

    fun platformsFor(backend: String): List<MusicPlatformOption> =
        if (normalizeBackend(backend) == BACKEND_FLAC_SITE) flacSitePlatforms else gdStudioPlatforms

    fun platformPreferenceKey(backend: String): String =
        if (normalizeBackend(backend) == BACKEND_FLAC_SITE) "source_platform_flac" else "source_platform_gdstudio"

    fun labelForPlatform(value: String): String =
        gdStudioPlatforms.firstOrNull { it.id == normalizePlatform(value) }?.label ?: value

    fun toFlacSitePlatform(value: String): String = when (normalizePlatform(value)) {
        "netease" -> "wyy"
        "kuwo" -> "kuwo"
        else -> throw IOException("flac.music.hi.cn 当前适配器只支持网易云和酷我；请切换到 GD Studio 使用其他平台。")
    }
}

/**
 * Conservative per-process pacing for GD Studio's documented 50 requests / 5 minutes limit.
 * Leaves margin at 45 requests; other devices sharing a public IP may also consume quota.
 * Requests are rejected while this app's local budget is exhausted, instead of blocking an IO
 * thread for minutes or letting cancelled coroutine jobs linger in Thread.sleep().
 */
internal object GdStudioRequestLimiter {
    private const val WINDOW_MILLIS = 5 * 60 * 1000L
    private const val MAX_REQUESTS = 45
    private const val PREFS_NAME = "music_meta_api_limits"
    private const val TIMESTAMPS_KEY = "gd_studio_request_timestamps_v1"
    private val lock = Any()
    private val requestTimes = ArrayDeque<Long>()
    private var preferences: SharedPreferences? = null
    private var configured = false

    /** Restores the sliding-window budget across process restarts. */
    fun configure(context: Context) {
        synchronized(lock) {
            if (configured) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            preferences = prefs
            requestTimes.clear()
            val now = System.currentTimeMillis()
            prefs.getString(TIMESTAMPS_KEY, null)
                ?.split(',')
                ?.mapNotNull { it.toLongOrNull() }
                ?.filter { it <= now && now - it < WINDOW_MILLIS }
                ?.forEach { requestTimes.addLast(it) }
            configured = true
            persistLocked()
        }
    }

    fun acquire() {
        val waitMillis = synchronized(lock) {
            val now = System.currentTimeMillis()
            while (requestTimes.isNotEmpty()) {
                val first = requestTimes.peekFirst()
                if (first > now || now - first >= WINDOW_MILLIS) requestTimes.removeFirst() else break
            }
            if (requestTimes.size < MAX_REQUESTS) {
                requestTimes.addLast(now)
                persistLocked()
                0L
            } else {
                (WINDOW_MILLIS - (now - requestTimes.peekFirst())).coerceAtLeast(1L)
            }
        }
        if (waitMillis > 0L) {
            val seconds = maxOf(1L, (waitMillis + 999L) / 1000L)
            throw IOException("应用近期已使用 GD Studio 的请求预算；约 ${seconds} 秒后可重试。为避免触发服务端限制，本次请求已暂停。")
        }
    }

    private fun persistLocked() {
        val serialized = requestTimes.joinToString(",")
        preferences?.edit()?.putString(TIMESTAMPS_KEY, serialized)?.apply()
    }
}

/** Shared expiring GET-response cache for GD Studio API calls. */
internal object GdStudioApiSupport {
    const val SEARCH_TTL_MILLIS = 5 * 60 * 1000L
    const val DOWNLOAD_URL_TTL_MILLIS = 15 * 1000L
    const val COVER_URL_TTL_MILLIS = 12 * 60 * 60 * 1000L
    const val LYRIC_TTL_MILLIS = 12 * 60 * 60 * 1000L

    private data class Entry(val body: String, val expiresAt: Long)
    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 180
    }

    @Synchronized
    private fun cached(key: String): String? {
        val entry = cache[key] ?: return null
        if (System.nanoTime() / 1_000_000L >= entry.expiresAt) {
            cache.remove(key)
            return null
        }
        return entry.body
    }

    @Synchronized
    private fun store(key: String, body: String, ttlMillis: Long) {
        if (ttlMillis > 0L && body.isNotBlank()) {
            cache[key] = Entry(body, System.nanoTime() / 1_000_000L + ttlMillis)
        }
    }

    fun getText(client: OkHttpClient, request: Request, ttlMillis: Long = 0L): String {
        val key = request.url.toString()
        if (ttlMillis > 0L) cached(key)?.let { return it }
        for (attempt in 0..1) {
            GdStudioRequestLimiter.acquire()
            var retryTransient = false
            val body = client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> throw IOException(
                        "GD Studio 返回 HTTP 429，服务端可能已触发请求频率限制；已停止自动重试，请稍后再试。"
                    )
                    response.code in 500..599 && attempt == 0 -> {
                        retryTransient = true
                        ""
                    }
                    !response.isSuccessful -> throw IOException("GD Studio API 请求失败：HTTP ${response.code}")
                    else -> {
                        val content = response.body?.string() ?: throw IOException("GD Studio API 返回空内容。")
                        val trimmed = content.trimStart()
                        if (trimmed.startsWith("<!doctype html", true) || trimmed.startsWith("<html", true)) {
                            throw IOException("GD Studio 返回了网页而不是 API 数据，可能暂时不可用或需要稍后重试。")
                        }
                        content
                    }
                }
            }
            if (!retryTransient) {
                store(key, body, ttlMillis)
                return body
            }
            try {
                Thread.sleep(350L)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("GD Studio 请求重试被中断。", error)
            }
        }
        throw IOException("GD Studio API 暂时不可用，重试后仍失败。")
    }
}

/** All page sizes share one cached 100-track batch. */
internal fun playlistBatchCacheKey(mode: String, playlistId: String, pageSize: Int, page: Int): String {
    val safeSize = pageSize.coerceAtLeast(1)
    val safePage = page.coerceAtLeast(1)
    val firstTrackOffset = (safePage - 1) * safeSize
    val batch = firstTrackOffset / 100 + 1
    return "$mode:$playlistId:100:$batch"
}
