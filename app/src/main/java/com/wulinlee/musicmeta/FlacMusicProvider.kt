package com.wulinlee.musicmeta

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Adapter for flac.music.hi.cn's public AJAX interface.
 *
 * The site requires its own browser-generated SafeLine verification cookies on some
 * visits. The caller supplies cookies from the embedded WebView; this class does not
 * attempt to solve or bypass any verification challenge.
 */
data class PreviewLyrics(val original: String = "", val translated: String = "")

data class FlacSiteSong(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String,
    val sign: String,
    val time: String,
    val coverUrl: String,
    val coverId: String = "",
    val lyricId: String = id,
    val urlId: String = id,
)

internal fun gdStudioUrlLookupId(song: FlacSiteSong): String =
    song.urlId.takeIf { it.isNotBlank() } ?: song.id

internal fun flacSiteDownloadUrlCacheKey(song: FlacSiteSong, format: String, bitrate: String): String =
    listOf(song.platform, song.id, format.lowercase(), bitrate, song.time, song.sign).joinToString("|")

/** Short-lived, bounded cache for direct URLs returned by flac.music.hi.cn. */
internal object FlacSiteDownloadUrlCache {
    const val TTL_MILLIS = 50 * 60 * 1000L
    private const val MAX_ENTRIES = 100

    private data class Entry(val url: String, val expiresAt: Long)
    private val cache = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): String? {
        val entry = cache[key] ?: return null
        if (System.nanoTime() / 1_000_000L >= entry.expiresAt) {
            cache.remove(key)
            return null
        }
        return entry.url
    }

    @Synchronized
    fun put(key: String, url: String) {
        if (url.startsWith("https://") || url.startsWith("http://")) {
            cache[key] = Entry(url, System.nanoTime() / 1_000_000L + TTL_MILLIS)
        }
    }
}

internal fun formatFlacSiteApiError(code: Int, detail: String, operation: String): String? {
    if (code == 0 || code == 200) return null
    // Prefer the precise per-track error over the generic "-1" wrapper. Otherwise users are
    // incorrectly sent to the verification WebView for songs the endpoint simply cannot resolve.
    if (code == -1 && (
            detail.contains("Song not found", ignoreCase = true) ||
                detail.contains("invalid response", ignoreCase = true)
        )
    ) {
        return if (operation.contains("下载地址")) {
            "下载站无法解析该候选歌曲的下载地址：曲目可能已失效，或此版本在当前平台暂不可用。请重新搜索并选择其他候选；如果仍失败，可切换 API 服务或稍后重试。"
        } else {
            "下载站暂时无法处理这次$operation；该平台或候选可能暂不可用，请稍后重试。"
        }
    }
    if (code == 468 || detail.contains("验证") || detail.contains("challenge", ignoreCase = true)) {
        return "下载站要求网页访问验证。请先在下方网站页面完成验证，再重新搜索。"
    }
    if (code == -1 && detail.contains("接口请求失败")) {
        return "下载站接口暂时无法处理请求（-1）。请重新搜索并重试；如果网站页面明确显示验证要求，再打开网页完成验证。"
    }
    val message = detail.ifBlank { "请稍后重试" }
    return "下载站接口返回错误 $code：$message"
}

internal fun isFlacInvalidCandidateError(error: Throwable): Boolean {
    val messages = throwableTree(error)
        .mapNotNull { it.message }
        .joinToString(" ")
    return messages.contains("Song not found", ignoreCase = true) ||
        messages.contains("invalid response", ignoreCase = true) ||
        messages.contains("无法解析该候选歌曲")
}

/** Generic HiCN code -1 errors may be transient or candidate-specific; retry once in-provider. */
internal fun isFlacRetryableResolutionError(error: Throwable): Boolean {
    if (isFlacInvalidCandidateError(error)) return true
    val messages = throwableTree(error).mapNotNull { it.message }
    return messages.any { message ->
        message.contains("下载站接口暂时无法处理请求（-1）") ||
            (message.contains("接口请求失败", ignoreCase = true) &&
                message.contains("-1", ignoreCase = true))
    }
}


internal fun gdStudioSearchKeywords(keyword: String, fallbackKeyword: String?): List<String> {
    val primary = keyword.trim()
    val fallback = fallbackKeyword?.trim().orEmpty()
    return listOf(primary, fallback)
        .filter { it.isNotBlank() }
        .distinctBy { value -> value.lowercase().filter { it.isLetterOrDigit() } }
}

internal fun formatGdStudioNoCandidatesMessage(): String =
    "GD Studio 没有搜索到候选歌曲：可能是当前平台暂无此版本，或关键词不匹配。请改用歌名搜索或稍后重试；如需换源，请在设置中手动选择。"

/**
 * Inspect both cause and suppressed exception chains. The GD Studio host failover adapter may
 * suppress an earlier failure when the second host fails at the transport layer; retaining the
 * chain helps report the actual failure without changing the user's selected source.
 */
private fun throwableTree(root: Throwable): Sequence<Throwable> = sequence {
    val queue = java.util.ArrayDeque<Throwable>()
    val seen = java.util.Collections.newSetFromMap(
        java.util.IdentityHashMap<Throwable, Boolean>(),
    )
    queue.addLast(root)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (!seen.add(current)) continue
        yield(current)
        current.cause?.let { queue.addLast(it) }
        current.suppressed.forEach { queue.addLast(it) }
    }
}

internal fun isGdStudioNoCandidatesError(error: Throwable): Boolean =
    throwableTree(error).any {
        it.message?.contains("GD Studio 没有搜索到候选歌曲") == true
    }

/** Require exact normalized titles and compatible artists when retrying candidates on the same platform. */
internal fun samePlatformCandidateMatches(
    requestedTitle: String,
    requestedArtist: String,
    candidateTitle: String,
    candidateArtist: String,
): Boolean {
    fun normalize(value: String): String = value.trim().lowercase().filter { it.isLetterOrDigit() }
    val requestedTitleKey = normalize(requestedTitle)
    if (requestedTitleKey.isBlank() || requestedTitleKey != normalize(candidateTitle)) return false
    val requestedArtistKey = normalize(requestedArtist.substringBefore(" / "))
    val candidateArtistKey = normalize(candidateArtist.substringBefore(" / "))
    return requestedArtistKey.isBlank() || candidateArtistKey.isBlank() ||
        candidateArtistKey.contains(requestedArtistKey) || requestedArtistKey.contains(candidateArtistKey)
}

class FlacMusicProvider {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun searchGdStudioCandidates(
        keyword: String,
        fallbackKeyword: String?,
        source: String,
    ): List<FlacSiteSong> {
        var candidates: List<FlacSiteSong> = emptyList()
        for (searchKeyword in gdStudioSearchKeywords(keyword, fallbackKeyword)) {
            candidates = searchGdStudio(searchKeyword, source)
            if (candidates.isNotEmpty()) break
        }
        if (candidates.isEmpty()) throw IOException(formatGdStudioNoCandidatesMessage())
        return candidates
    }

    suspend fun search(
        keyword: String,
        platform: String,
        cookie: String?,
        userAgent: String,
        backend: String = MusicSourceConfig.BACKEND_FLAC_SITE,
        fallbackKeyword: String? = null,
        forceRefresh: Boolean = false,
    ): List<FlacSiteSong> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) throw IOException("请输入要搜索的歌名或歌手。")
        val selectedBackend = MusicSourceConfig.normalizeBackend(backend)
        // Normalize against the selected backend, not the union of all known platform IDs.
        // A stale saved value such as Kuwo must never be sent to GD Studio as an unsupported source.
        val source = MusicSourceConfig.platformForBackend(selectedBackend, platform)
        val cacheKey = "$selectedBackend:$source:${keyword.trim().lowercase()}"
        if (forceRefresh) MusicSourceSearchCache.invalidate(cacheKey)
        else MusicSourceSearchCache.get(cacheKey)?.let { return@withContext it }

        val songs = if (selectedBackend == MusicSourceConfig.BACKEND_GD_STUDIO) {
            // Respect the backend and platform selected by the user. GD Studio failures must
            // remain visible instead of silently searching hi.cn or another music catalogue.
            searchGdStudioCandidates(keyword, fallbackKeyword, source)
        } else {
            val candidates = searchFlacSite(keyword, source, cookie, userAgent)
            if (candidates.isEmpty()) throw IOException("flac.music.hi.cn 没有搜索到歌曲。")
            candidates
        }
        if (songs.isEmpty()) throw IOException("没有搜索到歌曲。")
        MusicSourceSearchCache.put(cacheKey, songs)
        songs
    }

    suspend fun resolveDownloadUrl(
        song: FlacSiteSong,
        format: String,
        bitrate: String,
        cookie: String?,
        userAgent: String,
    ): String = withContext(Dispatchers.IO) {
        val normalizedFormat = format.lowercase().takeIf { it in setOf("flac", "mp3") }
            ?: throw IOException("不支持的下载格式。")
        val actualBitrate = when (normalizedFormat) {
            "flac" -> "2000"
            else -> bitrate.takeIf { it in setOf("128", "320") } ?: "320"
        }
        if (song.platform.startsWith("gd:")) {
            val gdBitrate = if (normalizedFormat == "flac") "999" else actualBitrate
            return@withContext resolveGdStudioUrl(song, gdBitrate, userAgent)
        }
        // The open-source FlacMusic client documents these direct URLs as valid for about
        // one hour. Reuse them for 50 minutes to avoid repeatedly hitting getUrl for the
        // same track/quality, while leaving a safety margin before expiry.
        val cacheKey = flacSiteDownloadUrlCacheKey(song, normalizedFormat, actualBitrate)
        FlacSiteDownloadUrlCache.get(cacheKey)?.let { return@withContext it }
        val request = baseRequest(apiUrl("getUrl"), cookie, userAgent)
            .post(
                FormBody.Builder()
                    .add("platform", song.platform)
                    .add("songid", song.id)
                    .add("format", normalizedFormat)
                    .add("bitrate", actualBitrate)
                    .add("time", song.time)
                    .add("sign", song.sign)
                    .build()
            )
            .build()
        val body = executeBody(request).trim()
        val direct = if (body.startsWith("http://") || body.startsWith("https://")) {
            body
        } else {
            val root = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw IOException("下载站没有返回有效的下载地址；可能需要先在网页中完成访问验证。", e)
            }
            checkApiCode(root, "下载地址解析")
            root.optString("url")
                .ifBlank { root.optJSONObject("data")?.optString("url").orEmpty() }
        }
        val uri = runCatching { android.net.Uri.parse(direct) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw IOException("下载站没有返回有效的 HTTP/HTTPS 音频地址。")
        }
        FlacSiteDownloadUrlCache.put(cacheKey, direct)
        direct
    }

    suspend fun resolveDownloadUrlForSource(
        song: FlacSiteSong,
        format: String,
        bitrate: String,
        cookie: String?,
        userAgent: String,
    ): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val requestedFromGdStudio = song.platform.startsWith("gd:")
        var didRetryFlacResolution = false
        try {
            resolveDownloadUrl(song, format, bitrate, cookie, userAgent) to requestedFromGdStudio
        } catch (error: CancellationException) {
            throw error
        } catch (primaryError: Exception) {
            // Retry only within the same hi.cn platform. Never switch the selected backend or
            // platform automatically; the user controls source changes in Settings.
            if (!requestedFromGdStudio && isFlacRetryableResolutionError(primaryError)) {
                didRetryFlacResolution = true
                val refreshKeywords = gdStudioSearchKeywords(
                    listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" "),
                    song.title,
                )
                val refreshedCandidates = mutableListOf<FlacSiteSong>()
                for (keyword in refreshKeywords) {
                    try {
                        val freshCandidates = search(
                            keyword = keyword,
                            platform = song.platform,
                            cookie = cookie,
                            userAgent = userAgent,
                            backend = MusicSourceConfig.BACKEND_FLAC_SITE,
                            forceRefresh = true,
                        )
                        refreshedCandidates += freshCandidates.filter { candidate ->
                            samePlatformCandidateMatches(
                                song.title,
                                song.artist,
                                candidate.title,
                                candidate.artist,
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (refreshError: Exception) {
                        primaryError.addSuppressed(refreshError)
                    }
                }

                val retryCandidates = refreshedCandidates
                    .distinctBy { listOf(it.id, it.time, it.sign).joinToString("|") }
                    .sortedBy { candidate ->
                        when {
                            candidate.id == song.id &&
                                (candidate.time != song.time || candidate.sign != song.sign) -> 0
                            candidate.id != song.id -> 1
                            else -> 2
                        }
                    }
                    .take(3)

                for (candidate in retryCandidates) {
                    try {
                        val url = resolveDownloadUrl(candidate, format, bitrate, cookie, userAgent)
                        return@withContext url to false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (retryError: Exception) {
                        // Preserve which same-platform candidate failed so the copied report
                        // can distinguish stale IDs from a general endpoint failure.
                        val candidateContext = IOException(
                            "hi.cn 同平台候选重试失败：平台=${candidate.platform}，歌曲=${candidate.title}，曲目ID=${candidate.id}；原因=${retryError.message.orEmpty()}",
                            retryError,
                        )
                        primaryError.addSuppressed(candidateContext)
                    }
                }
            }

            val provider = if (requestedFromGdStudio) "GD Studio" else "flac.music.hi.cn"
            val guidance = when {
                requestedFromGdStudio ->
                    "未自动切换 API 服务或音乐平台；请检查 GD Studio 接口状态，或由你在设置中手动选择其他服务后重试。"
                didRetryFlacResolution ->
                    "已在 hi.cn 的同一音乐平台内刷新搜索并尝试匹配候选，仍未取得地址；请稍后重试，或由你手动切换音源。只有网页明确显示访问验证要求时，才需要先在网页中完成验证。"
                else ->
                    "请重新搜索或由你手动切换音源；只有网页明确显示访问验证要求时，才需要先在网页中完成验证。"
            }
            throw IOException(
                "$provider 解析《${song.title}》的下载地址失败（${primaryError.message ?: "未知错误"}）。$guidance",
                primaryError,
            )
        }
    }

    suspend fun resolveCoverUrl(song: FlacSiteSong): String = withContext(Dispatchers.IO) {
        val suppliedCover = normalizeCoverUrl(song.coverUrl)
        if (suppliedCover.isNotBlank()) return@withContext suppliedCover

        val source = when {
            song.platform.startsWith("gd:") -> song.platform.removePrefix("gd:").ifBlank { "netease" }
            song.platform == "wyy" -> "netease"
            else -> song.platform
        }
        // Do not query GD Studio with a secondary-site-only catalog (e.g. Kuwo).
        // Returning blank lets the caller use its independent metadata-cover fallback.
        if (MusicSourceConfig.platformsFor(MusicSourceConfig.BACKEND_GD_STUDIO).none { it.id == source }) {
            return@withContext ""
        }

        var picId = song.coverId
        if (picId.isBlank() || picId == "0" || picId.equals("null", ignoreCase = true)) {
            val match = runCatching {
                searchGdStudio(song.title + " " + song.artist, source)
                    .firstOrNull {
                        normalizeMatch(it.title) == normalizeMatch(song.title) &&
                            (normalizeMatch(it.artist).contains(normalizeMatch(song.artist.substringBefore(" / "))) ||
                                normalizeMatch(song.artist).contains(normalizeMatch(it.artist)))
                    }
            }.getOrNull()
            picId = match?.coverId.orEmpty()
        }
        if (picId.isBlank() || picId == "0" || picId.equals("null", ignoreCase = true)) {
            return@withContext ""
        }

        val body = GdStudioApiClient.getText(
            client,
            linkedMapOf("types" to "pic", "source" to source, "id" to picId, "size" to "500"),
            GdStudioApiSupport.COVER_URL_TTL_MILLIS,
        ).trim()
        val direct = if (body.startsWith("http://") || body.startsWith("https://")) {
            body
        } else {
            val json = runCatching { JSONObject(body) }.getOrNull()
            json?.optString("url").orEmpty()
                .ifBlank { json?.optJSONObject("data")?.optString("url").orEmpty() }
        }
        normalizeCoverUrl(direct)
    }

    suspend fun fetchLyrics(
        song: FlacSiteSong,
        cookie: String?,
        userAgent: String,
    ): PreviewLyrics = withContext(Dispatchers.IO) {
        val isGdStudioSong = song.platform.startsWith("gd:")
        val primaryBody = if (isGdStudioSong) {
            fetchGdStudioLyricsBody(
                source = song.platform.removePrefix("gd:").ifBlank { "netease" },
                lyricId = song.lyricId.ifBlank { song.id },
            )
        } else {
            val request = baseRequest(apiUrl("getLyric"), cookie, userAgent)
                .post(
                    FormBody.Builder()
                        .add("platform", song.platform)
                        .add("songid", song.id)
                        .add("time", song.time)
                        .add("sign", song.sign)
                        .build()
                )
                .build()
            runCatching { executeBody(request).trim() }.getOrDefault("")
        }
        val primary = parsePreviewLyricsPayload(primaryBody)
        if (isGdStudioSong || (primary.original.isNotBlank() && primary.translated.isNotBlank())) {
            primary
        } else {
            // The FLAC search endpoint often supplies only the original LRC.
            // GD Studio documents an optional tlyric field, so use it as a translation fallback.
            val source = when (song.platform.lowercase()) {
                "wyy" -> "netease"
                "kuwo" -> "kuwo"
                else -> song.platform.lowercase()
            }
            // Do not send legacy/secondary-site IDs such as "kuwo" to a GD Studio
            // source that is currently unsupported; its API responds with HTTP 400.
            val fallback = if (MusicSourceConfig.platformsFor(MusicSourceConfig.BACKEND_GD_STUDIO)
                    .any { it.id == source }
            ) {
                runCatching {
                    parsePreviewLyricsPayload(
                        fetchGdStudioLyricsBody(
                            source = source,
                            lyricId = song.lyricId.ifBlank { song.id },
                        )
                    )
                }.getOrDefault(PreviewLyrics())
            } else {
                PreviewLyrics()
            }
            PreviewLyrics(
                original = primary.original.ifBlank { fallback.original },
                translated = primary.translated.ifBlank { fallback.translated },
            )
        }
    }

    private fun fetchGdStudioLyricsBody(source: String, lyricId: String): String =
        GdStudioApiClient.getText(
            client,
            linkedMapOf("types" to "lyric", "source" to source, "id" to lyricId),
            GdStudioApiSupport.LYRIC_TTL_MILLIS,
        ).trim()

    private fun parsePreviewLyricsPayload(body: String): PreviewLyrics {
        val text = body.trim()
        if (text.isBlank()) return PreviewLyrics()
        if (!text.startsWith("{")) return PreviewLyrics(original = text)
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return PreviewLyrics(original = text)
        val data = root.opt("data")
        val dataObject = data as? JSONObject
        fun pick(vararg candidates: String?): String = candidates
            .firstOrNull { !it.isNullOrBlank() && !it.equals("null", ignoreCase = true) }
            ?.trim().orEmpty()
        val original = pick(
            root.optString("lyric"), root.optString("lrc"),
            dataObject?.optString("lyric"), dataObject?.optString("lrc"),
            data as? String,
        )
        val translated = pick(
            root.optString("tlyric"), root.optString("trans"),
            root.optString("translated_lyric"), root.optString("translation"),
            dataObject?.optString("tlyric"), dataObject?.optString("trans"),
            dataObject?.optString("translated_lyric"), dataObject?.optString("translation"),
        )
        return PreviewLyrics(original = original, translated = translated)
    }
    private fun searchFlacSite(
        keyword: String,
        normalizedPlatform: String,
        cookie: String?,
        userAgent: String,
    ): List<FlacSiteSong> {
        val sitePlatform = MusicSourceConfig.toFlacSitePlatform(normalizedPlatform)
        val request = baseRequest(apiUrl("search"), cookie, userAgent)
            .post(
                FormBody.Builder()
                    .add("platform", sitePlatform)
                    .add("keyword", keyword.trim())
                    .add("page", "1")
                    .add("size", "20")
                    .build()
            )
            .build()
        val body = executeJson(request).trim()
        val list = if (body.startsWith("[")) {
            JSONArray(body)
        } else {
            val root = JSONObject(body)
            checkApiCode(root, "搜索")
            val data = root.optJSONObject("data")
            data?.optJSONArray("list")
                ?: root.optJSONArray("list")
                ?: root.optJSONArray("result")
                ?: root.optJSONArray("songs")
                ?: JSONArray()
        }
        return (0 until list.length()).mapNotNull { index ->
            parseSong(list.optJSONObject(index), sitePlatform)
        }
    }

    private fun searchGdStudio(keyword: String, normalizedPlatform: String): List<FlacSiteSong> {
        // This private helper is also used for artwork/lyrics fallback; normalize there too
        // so a legacy "kuwo" value cannot reach GD Studio from a non-search call site.
        val source = MusicSourceConfig.platformForBackend(
            MusicSourceConfig.BACKEND_GD_STUDIO,
            normalizedPlatform,
        )
        val body = GdStudioApiClient.getText(
            client,
            linkedMapOf(
                "types" to "search",
                "source" to source,
                "name" to keyword.trim(),
                "count" to "20",
                "pages" to "1",
            ),
            GdStudioApiSupport.SEARCH_TTL_MILLIS,
        ).trim()
        val root = runCatching { JSONObject(body) }.getOrNull()
        if (root != null && root.has("code") && root.optInt("code") != 0 && root.optInt("code") != 200) {
            throw IOException("GD Studio 搜索返回错误：" + root.optString("msg").ifBlank { root.optString("message") })
        }
        val list = if (body.startsWith("[")) {
            JSONArray(body)
        } else {
            root?.optJSONArray("data")
                ?: root?.optJSONObject("data")?.optJSONArray("list")
                ?: root?.optJSONArray("result")
                ?: root?.optJSONArray("list")
                ?: root?.optJSONArray("songs")
                ?: JSONArray()
        }
        val songs = (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").ifBlank { item.optString("track_id") }
            val title = item.optString("name").ifBlank { item.optString("title") }
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            val artistValue = item.opt("artist")
            val artist = when (artistValue) {
                is JSONArray -> (0 until artistValue.length()).mapNotNull { i ->
                    when (val a = artistValue.opt(i)) {
                        is JSONObject -> a.optString("name").ifBlank { null }
                        is String -> a.trim().ifBlank { null }
                        else -> null
                    }
                }.joinToString(" / ")
                is JSONObject -> artistValue.optString("name")
                is String -> artistValue
                else -> item.optString("artists")
            }
            val cover = normalizeCoverUrl(
                listOf(
                    item.optString("pic_url"),
                    item.optString("coverUrl"),
                    item.optString("cover"),
                    item.optString("pic"),
                ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()
            )
            FlacSiteSong(
                id = id,
                urlId = item.optString("url_id").ifBlank { id },
                platform = "gd:" + source,
                title = title,
                artist = artist.ifBlank { "未知歌手" },
                album = item.optString("album"),
                sign = "",
                time = "",
                coverUrl = cover,
                coverId = listOf(item.optString("pic_id"), item.optString("picId"), item.optString("picture_id"))
                    .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty(),
                lyricId = item.optString("lyric_id").ifBlank { item.optString("lyricId") }.ifBlank { id },
            )
        }
        return songs
    }

    private fun resolveGdStudioUrl(song: FlacSiteSong, bitrate: String, userAgent: String): String {
        val source = song.platform.removePrefix("gd:").ifBlank { "netease" }
        val urlLookupId = gdStudioUrlLookupId(song)
        val body = GdStudioApiClient.getText(
            client,
            linkedMapOf(
                "types" to "url",
                "source" to source,
                "id" to urlLookupId,
                "br" to bitrate,
            ),
            GdStudioApiSupport.DOWNLOAD_URL_TTL_MILLIS,
        ).trim()
        val direct = if (body.startsWith("http://") || body.startsWith("https://")) {
            body
        } else {
            val json = runCatching { JSONObject(body) }.getOrElse {
                throw IOException("GD Studio 没有返回有效的下载地址。", it)
            }
            val code = json.optInt("code", 0)
            if (code != 0 && code != 200) {
                throw IOException("GD Studio 下载接口返回错误：" + json.optString("msg").ifBlank { json.optString("message") })
            }
            json.optString("url")
                .ifBlank { json.optJSONObject("data")?.optString("url").orEmpty() }
        }
        val uri = runCatching { android.net.Uri.parse(direct) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw IOException("GD Studio 没有返回有效的 HTTP/HTTPS 音频地址。")
        }
        return direct
    }

    private fun apiUrl(action: String) = HttpUrl.Builder()
        .scheme("https")
        .host("flac.music.hi.cn")
        .addPathSegments("ajax.php")
        .addQueryParameter("act", action)
        .build()

    private fun baseRequest(url: HttpUrl, cookie: String?, userAgent: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Origin", "https://flac.music.hi.cn")
            .header("Referer", "https://flac.music.hi.cn/")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", userAgent.ifBlank { fallbackUserAgent })
        if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)
        return builder
    }

    private fun parseSong(song: JSONObject?, platform: String): FlacSiteSong? {
        if (song == null) return null
        val id = song.optString("rid")
            .ifBlank { song.optString("id") }
            .ifBlank { song.optString("songid") }
        val title = song.optString("name").ifBlank { song.optString("title") }
        if (id.isBlank() || title.isBlank()) return null
        val artistValue = song.opt("artist")
        val artist = when (artistValue) {
            is JSONArray -> (0 until artistValue.length()).mapNotNull { i ->
                when (val a = artistValue.opt(i)) {
                    is JSONObject -> a.optString("name").ifBlank { null }
                    is String -> a.trim().ifBlank { null }
                    else -> null
                }
            }.joinToString(" / ")
            is JSONObject -> artistValue.optString("name")
            is String -> artistValue
            else -> song.optString("artists")
                .ifBlank { song.optString("singer") }
                .ifBlank { song.optString("artistName") }
        }
        return FlacSiteSong(
            id = id,
            platform = platform,
            title = title,
            artist = artist.ifBlank { "未知歌手" },
            album = song.optString("album_name")
                .ifBlank { song.optString("album") }
                .ifBlank { song.optString("albumName") },
            sign = song.optString("sign"),
            time = song.optString("time"),
            coverUrl = normalizeCoverUrl(
                listOf(
                    song.optString("pic_url"),
                    song.optString("coverUrl"),
                    song.optString("cover"),
                    song.optString("pic"),
                    song.optString("picurl"),
                    song.optString("album_img"),
                ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()
            ),
            coverId = listOf(song.optString("pic_id"), song.optString("picId"), song.optString("picture_id"))
                .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty(),
            lyricId = song.optString("lyric_id").ifBlank { song.optString("lyricId") }.ifBlank { id },
            urlId = song.optString("url_id").ifBlank { id },
        )
    }

    private fun normalizeCoverUrl(raw: String?): String {
        val value = raw?.trim().orEmpty()
        return when {
            value.isBlank() || value.equals("null", ignoreCase = true) -> ""
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://", ignoreCase = true) -> "https://" + value.substringAfter("://")
            value.startsWith("https://", ignoreCase = true) -> value
            else -> ""
        }
    }

    private fun normalizeMatch(value: String): String =
        value.trim().lowercase().filter { it.isLetterOrDigit() }

    private fun checkApiCode(root: JSONObject, operation: String = "请求") {
        if (!root.has("code")) return
        val code = root.optInt("code")
        val detail = root.optString("msg").ifBlank { root.optString("message") }
        val message = formatFlacSiteApiError(code, detail, operation) ?: return
        throw IOException(message)
    }

    private fun executeJson(request: Request): String {
        val body = executeBody(request)
        if (body.trimStart().startsWith("<!doctype html", true) ||
            body.trimStart().startsWith("<html", true)
        ) {
            throw IOException("下载站返回了网页验证页面而不是 JSON。请先在下方网页中完成验证，再重试。")
        }
        return body
    }

    private fun executeBody(request: Request): String {
        for (attempt in 0..1) {
            val response = client.newCall(request).execute()
            try {
                if (response.code == 468) {
                    throw IOException("下载站访问验证未通过（HTTP 468）。请在下方网页中完成验证后重试。")
                }
                if (!response.isSuccessful) {
                    val error = IOException("下载站请求失败：HTTP ${response.code}")
                    if (attempt == 0 && response.code in 500..599) {
                        // Upstream endpoints sometimes return a transient 503; retry once only.
                        Thread.sleep(350L)
                        continue
                    }
                    throw error
                }
                return response.body?.string() ?: throw IOException("下载站返回空内容。")
            } finally {
                response.close()
            }
        }
        throw IOException("下载站请求失败：临时服务不可用，重试后仍失败。")
    }

    private companion object {
        const val fallbackUserAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
