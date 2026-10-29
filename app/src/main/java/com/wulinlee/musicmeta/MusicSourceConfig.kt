package com.wulinlee.musicmeta

import java.io.IOException
import org.json.JSONObject
import java.util.LinkedHashMap
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class MusicPlatformOption(val id: String, val label: String)

internal object MusicSourceConfig {
    const val BACKEND_GD_STUDIO = "gdstudio"
    const val BACKEND_FLAC_SITE = "flac"
    const val DEFAULT_BACKEND = BACKEND_GD_STUDIO
    const val DEFAULT_PLATFORM = "netease"

    // Keep this list aligned with GD Studio's published stable-source list.
    // The API docs currently list NetEase, Kuwo, JOOX, and Bilibili.
    val gdStudioPlatforms = listOf(
        MusicPlatformOption("netease", "网易云"),
        MusicPlatformOption("kuwo", "酷我"),
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
        (gdStudioPlatforms + flacSitePlatforms)
            .firstOrNull { it.id == normalizePlatform(value) }?.label ?: value

    fun toFlacSitePlatform(value: String): String = when (normalizePlatform(value)) {
        "netease" -> "wyy"
        "kuwo" -> "kuwo"
        else -> throw IOException("flac.music.hi.cn 当前适配器只支持网易云和酷我；请切换到 GD Studio 使用其他平台。")
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

    /** Low-level HTTP/cache primitive. Build signed API calls with GdStudioApiClient. */
    fun getText(client: OkHttpClient, request: Request, ttlMillis: Long = 0L): String {
        val key = request.url.newBuilder().removeAllQueryParameters("s").build().toString()
        if (ttlMillis > 0L) cached(key)?.let { return it }

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                throw IOException(formatGdStudioHttpErrorMessage(response.code, errorBody))
            }
            val content = response.body?.string() ?: throw IOException("GD Studio API 返回空内容。")
            val trimmed = content.trimStart()
            if (trimmed.startsWith("<!doctype html", true) || trimmed.startsWith("<html", true)) {
                throw IOException("GD Studio 返回了网页而不是 API 数据，可能暂时不可用或需要稍后重试。")
            }
            content
        }
        val hasApiErrorCode = runCatching {
            val json = JSONObject(body)
            json.has("code") && json.optInt("code", 0) !in setOf(0, 200)
        }.getOrDefault(false)
        if (!hasApiErrorCode) store(key, body, ttlMillis)
        return body
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
