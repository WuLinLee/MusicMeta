package com.wulinlee.musicmeta

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONObject
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

    // GD Studio labels available sources dynamically. Its current documentation marks
    // NetEase, JOOX, and Bilibili as stable; keep the UI aligned to that published list.
    val gdStudioPlatforms = listOf(
        MusicPlatformOption("netease", "网易云"),
        MusicPlatformOption("joox", "JOOX"),
        MusicPlatformOption("bilibili", "Bilibili"),
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
        return (gdStudioPlatforms + flacSitePlatforms).firstOrNull { it.id == normalized }?.id ?: DEFAULT_PLATFORM
    }

    fun platformForBackend(backend: String, value: String?): String {
        val normalized = normalizePlatform(value)
        return platformsFor(backend).firstOrNull { it.id == normalized }?.id ?: DEFAULT_PLATFORM
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

internal class GdStudioRateLimitException(val retryAfterMillis: Long) : IOException(
    "GD Studio 请求较多，约 ${maxOf(1L, (retryAfterMillis + 999L) / 1000L)} 秒后可重试。请稍后重试。"
) {
    val retryAfterSeconds: Long
        get() = maxOf(1L, (retryAfterMillis + 999L) / 1000L)
}

/**
 * The GD Studio documentation states at most 50 requests per five-minute window.
 * MusicMeta uses the documented client-side ceiling; the server may also count other clients on a shared IP.
 * The budget persists across process restarts, but is per installation—not a guarantee about
 * the server-side IP quota. Expensive thumbnail lookups are intentionally not done per result row.
 */
internal object GdStudioRequestLimiter {
    private const val WINDOW_MILLIS = 5 * 60 * 1000L
    private const val MAX_REQUESTS = 50
    internal fun maxRequestsForTests(): Int = MAX_REQUESTS
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
        if (waitMillis > 0L) throw GdStudioRateLimitException(waitMillis)
    }

    private fun persistLocked() {
        preferences?.edit()?.putString(TIMESTAMPS_KEY, requestTimes.joinToString(","))?.apply()
    }
}
/** Formats a sanitized explanation for non-successful GD Studio HTTP responses. */
internal fun formatGdStudioHttpErrorMessage(statusCode: Int, responseBody: String): String {
    val prefix = "GD Studio API 请求失败：HTTP $statusCode"
    val body = responseBody.trim()
    if (body.isBlank() || body.startsWith("<!doctype html", true) || body.startsWith("<html", true)) {
        return prefix
    }
    val json = runCatching { JSONObject(body) }.getOrNull()
    val detail = listOf(
        json?.optString("msg"),
        json?.optString("message"),
        json?.optString("error"),
        json?.optString("detail"),
    ).firstOrNull { !it.isNullOrBlank() && !it.equals("null", ignoreCase = true) }
        ?: body.takeUnless { it.startsWith("{") || it.startsWith("[") }.orEmpty()
    val safeDetail = detail
        .replace(Regex("(?i)https?://\\S+"), "[网址已隐藏]")
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("<[^>]*>"), " ")
        .trim()
        .take(180)
    return if (safeDetail.isBlank()) prefix else "$prefix（$safeDetail）"
}


/** Encodes a GD Studio signature payload exactly as its current web client does. */
internal fun gdStudioEncodeSignatureValue(value: String): String {
    val hex = "0123456789ABCDEF"
    return buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val b = byte.toInt() and 0xFF
            val ch = b.toChar()
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in "-_.~") {
                append(ch)
            } else {
                append('%')
                append(hex[b ushr 4])
                append(hex[b and 0x0F])
            }
        }
    }
}

/**
 * GD Studio's protocol digest resembles MD5 but intentionally changes round constant 29.
 * Using MessageDigest("MD5") produces a different signature and is rejected by the API.
 */
internal fun gdStudioProtocolDigest(value: String): String {
    val input = value.toByteArray(Charsets.UTF_8)
    val paddedLength = ((input.size + 8) / 64 + 1) * 64
    val padded = ByteArray(paddedLength)
    System.arraycopy(input, 0, padded, 0, input.size)
    padded[input.size] = 0x80.toByte()
    val bitLength = input.size.toLong() * 8L
    for (index in 0 until 8) {
        padded[paddedLength - 8 + index] = (bitLength ushr (index * 8)).toByte()
    }

    val shifts = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    val constants = IntArray(64) { index ->
        floor(abs(sin((index + 1).toDouble())) * 4294967296.0).toLong().toInt()
    }
    constants[29] = 0xFCFEA3F8L.toInt()

    val state = intArrayOf(0x67452301, 0xEFCDAB89.toInt(), 0x98BADCFE.toInt(), 0x10325476)
    for (offset in padded.indices step 64) {
        val words = IntArray(16) { index ->
            val base = offset + index * 4
            (padded[base].toInt() and 0xFF) or
                ((padded[base + 1].toInt() and 0xFF) shl 8) or
                ((padded[base + 2].toInt() and 0xFF) shl 16) or
                ((padded[base + 3].toInt() and 0xFF) shl 24)
        }
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        for (index in 0 until 64) {
            val group = index / 16
            val function: Int
            val wordIndex: Int
            when (group) {
                0 -> {
                    function = (b and c) or (b.inv() and d)
                    wordIndex = index
                }
                1 -> {
                    function = (d and b) or (d.inv() and c)
                    wordIndex = (5 * index + 1) % 16
                }
                2 -> {
                    function = b xor c xor d
                    wordIndex = (3 * index + 5) % 16
                }
                else -> {
                    function = c xor (b or d.inv())
                    wordIndex = (7 * index) % 16
                }
            }
            val sum = a + function + constants[index] + words[wordIndex]
            val rotated = Integer.rotateLeft(sum, shifts[index])
            val previousD = d
            d = c
            c = b
            b += rotated
            a = previousD
        }
        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
    }

    val digest = ByteArray(16)
    for (stateIndex in state.indices) {
        for (byteIndex in 0 until 4) {
            digest[stateIndex * 4 + byteIndex] = (state[stateIndex] ushr (byteIndex * 8)).toByte()
        }
    }
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        for (byte in digest) {
            val unsigned = byte.toInt() and 0xFF
            append(hex[unsigned ushr 4])
            append(hex[unsigned and 0x0F])
        }
    }
}

/** Produces the short request signature required by the GD Studio web API. */
internal fun gdStudioApiSignature(
    value: String,
    serverTime: String,
    host: String,
    version: String,
): String {
    val versionParts = version.trim().split('.')
    val normalizedVersion = versionParts.map { part -> part.padStart(2, '0') }.joinToString("")
    val payload = "${serverTime.trim().take(9)}|$host|$normalizedVersion|${gdStudioEncodeSignatureValue(value)}"
    return gdStudioProtocolDigest(payload).takeLast(8).uppercase(Locale.ROOT)
}

/** Shared expiring GET-response cache for GD Studio API calls. */
internal object GdStudioApiSupport {
    const val SEARCH_TTL_MILLIS = 5 * 60 * 1000L
    const val DOWNLOAD_URL_TTL_MILLIS = 15 * 1000L
    const val COVER_URL_TTL_MILLIS = 12 * 60 * 60 * 1000L
    const val LYRIC_TTL_MILLIS = 12 * 60 * 60 * 1000L

    private const val GD_STUDIO_HOST = "music.gdstudio.xyz"
    private const val GD_STUDIO_VERSION_FALLBACK = "2026.09.25"
    private const val PLAYER_VERSION_TTL_MILLIS = 10 * 60 * 1000L
    private const val SIGNATURE_BOOTSTRAP_TIMEOUT_MILLIS = 1_500L
    private val playerVersionRegex = Regex("""mkPlayer\.version\s*[:=]\s*["'](20\d{2}\.\d{1,2}\.\d{1,2})["']""")
    private val genericVersionRegex = Regex("""\bversion\s*[:=]\s*["'](20\d{2}\.\d{1,2}\.\d{1,2})["']""")
    @Volatile private var cachedPlayerVersion: Pair<String, Long>? = null
    private val versionLock = Any()

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

    private fun signingClient(client: OkHttpClient): OkHttpClient =
        client.newBuilder()
            .callTimeout(SIGNATURE_BOOTSTRAP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .connectTimeout(SIGNATURE_BOOTSTRAP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(SIGNATURE_BOOTSTRAP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .build()

    private fun getServerTime(client: OkHttpClient): String {
        val localFallback = (System.currentTimeMillis() / 1000L).toString()
        val bootstrapClient = signingClient(client)
        val request = Request.Builder()
            .url("https://$GD_STUDIO_HOST/time")
            .header("Origin", "https://$GD_STUDIO_HOST")
            .header("Referer", "https://$GD_STUDIO_HOST/")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .get()
            .build()
        val value = runCatching {
            bootstrapClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.trim().orEmpty() else ""
            }
        }.getOrDefault("")
        val numericTime = value.toLongOrNull() ?: return localFallback
        val seconds = if (numericTime > 10_000_000_000L) numericTime / 1000L else numericTime
        return seconds.takeIf { it > 0L }?.toString() ?: localFallback
    }

    private fun getPlayerVersion(client: OkHttpClient, forceRefresh: Boolean): String {
        val now = System.nanoTime() / 1_000_000L
        synchronized(versionLock) {
            cachedPlayerVersion?.let { cached ->
                if (!forceRefresh && now < cached.second) return cached.first
            }
            val request = Request.Builder()
                .url("https://$GD_STUDIO_HOST/js/player.js")
                .header("Referer", "https://$GD_STUDIO_HOST/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                .get()
                .build()
            val script = runCatching {
                signingClient(client).newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string().orEmpty() else ""
                }
            }.getOrDefault("")
            val version = playerVersionRegex.find(script)?.groupValues?.getOrNull(1)
                ?: genericVersionRegex.find(script)?.groupValues?.getOrNull(1)
            val actualVersion = version ?: GD_STUDIO_VERSION_FALLBACK
            val ttl = if (version != null) PLAYER_VERSION_TTL_MILLIS else 5 * 60 * 1000L
            cachedPlayerVersion = actualVersion to (System.nanoTime() / 1_000_000L + ttl)
            return actualVersion
        }
    }

    private fun signApiRequest(
        client: OkHttpClient,
        request: Request,
        forceRefreshVersion: Boolean = false,
    ): Request {
        if (request.url.host != GD_STUDIO_HOST || request.url.encodedPath != "/api.php") return request
        val type = request.url.queryParameter("types") ?: return request
        val rawValue = (if (type == "search") {
            request.url.queryParameter("name")
        } else {
            request.url.queryParameter("id")
        }) ?: throw IOException("GD Studio 请求缺少签名参数。")
        val version = getPlayerVersion(client, forceRefreshVersion)
        val serverTime = getServerTime(client)
        val signature = gdStudioApiSignature(rawValue, serverTime, request.url.host, version)
        val signedUrl = request.url.newBuilder()
            .removeAllQueryParameters("s")
            .addQueryParameter("s", signature)
            .build()
        return request.newBuilder()
            .url(signedUrl)
            .header("Origin", "https://$GD_STUDIO_HOST")
            .header("Referer", "https://$GD_STUDIO_HOST/")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    fun getText(client: OkHttpClient, request: Request, ttlMillis: Long = 0L): String {
        // Keep the cache key independent of the time-based signature so the same payload
        // remains reusable for the normal response TTL.
        val key = request.url.toString()
        if (ttlMillis > 0L) cached(key)?.let { return it }
        var forceRefreshVersion = false
        for (attempt in 0..1) {
            GdStudioRequestLimiter.acquire()
            var retryRequest = false
            val signedRequest = signApiRequest(client, request, forceRefreshVersion)
            forceRefreshVersion = false
            val body = client.newCall(signedRequest).execute().use { response ->
                when {
                    response.code == 429 -> throw IOException(
                        "GD Studio 返回 HTTP 429，服务端可能已触发请求频率限制；已停止自动重试，请稍后再试。"
                    )
                    response.code == 401 && attempt == 0 -> {
                        // GD Studio uses 401 for an invalid/stale signature. Refresh player.js,
                        // re-read server time, and re-sign once. A 403 is an edge/host block,
                        // not a signature error, so it should go directly to provider fallback.
                        forceRefreshVersion = true
                        retryRequest = true
                        ""
                    }
                    response.code in 500..599 && attempt == 0 -> {
                        retryRequest = true
                        ""
                    }
                    !response.isSuccessful -> {
                        val errorBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                        throw IOException(formatGdStudioHttpErrorMessage(response.code, errorBody))
                    }
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
            if (!retryRequest) {
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
