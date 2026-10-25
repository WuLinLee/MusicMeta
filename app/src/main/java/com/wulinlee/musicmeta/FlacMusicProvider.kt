package com.wulinlee.musicmeta

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
)

class FlacMusicProvider {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        keyword: String,
        platform: String,
        cookie: String?,
        userAgent: String,
    ): List<FlacSiteSong> = withContext(Dispatchers.IO) {
        val normalizedPlatform = platform.lowercase().takeIf { it in setOf("kuwo", "wyy") }
            ?: throw IOException("不支持的音源平台。")
        if (keyword.isBlank()) throw IOException("请输入要搜索的歌名或歌手。")

        val primaryFailure = try {
            val url = apiUrl("search")
            val request = baseRequest(url, cookie, userAgent)
                .post(
                    FormBody.Builder()
                        .add("platform", normalizedPlatform)
                        .add("keyword", keyword.trim())
                        .add("page", "1")
                        .add("size", "20")
                        .build()
                )
                .build()
            val root = JSONObject(executeJson(request))
            checkApiCode(root)
            val data = root.optJSONObject("data")
                ?: throw IOException("下载站搜索响应中没有 data 字段。")
            val list = data.optJSONArray("list") ?: JSONArray()
            val songs = (0 until list.length()).mapNotNull { index ->
                parseSong(list.optJSONObject(index), normalizedPlatform)
            }
            if (songs.isEmpty()) throw IOException("没有搜索到歌曲。")
            return@withContext songs
        } catch (error: Exception) {
            error
        }

        try {
            val fallbackSongs = searchGdStudio(keyword, normalizedPlatform)
            if (fallbackSongs.isNotEmpty()) return@withContext fallbackSongs
            throw IOException("GD Studio 没有搜索到候选歌曲。")
        } catch (fallbackError: Exception) {
            throw IOException(
                "主音源搜索失败（" + (primaryFailure.message ?: "未知错误") +
                    "）；GD Studio 兜底也失败（" + (fallbackError.message ?: "未知错误") + "）。",
                primaryFailure,
            )
        }
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
            checkApiCode(root)
            root.optString("url")
                .ifBlank { root.optJSONObject("data")?.optString("url").orEmpty() }
        }
        val uri = runCatching { android.net.Uri.parse(direct) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw IOException("下载站没有返回有效的 HTTP/HTTPS 音频地址。")
        }
        direct
    }

    suspend fun resolveDownloadUrlWithFallback(
        song: FlacSiteSong,
        format: String,
        bitrate: String,
        cookie: String?,
        userAgent: String,
    ): Pair<String, Boolean> {
        try {
            val url = resolveDownloadUrl(song, format, bitrate, cookie, userAgent)
            return url to song.platform.startsWith("gd:")
        } catch (primaryError: Exception) {
            val platform = if (song.platform.contains("kuwo", ignoreCase = true)) "kuwo" else "wyy"
            val fallbackCandidates = try {
                withContext(Dispatchers.IO) {
                    searchGdStudio(song.title + " " + song.artist, platform)
                }
            } catch (searchError: Exception) {
                throw IOException(
                    "主音源解析失败（" + (primaryError.message ?: "未知错误") +
                        "）；GD Studio 搜索兜底失败（" + (searchError.message ?: "未知错误") + "）。",
                    primaryError,
                )
            }
            fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
            val wantedTitle = normalize(song.title)
            val wantedArtist = normalize(song.artist.substringBefore(" / "))
            val match = fallbackCandidates.firstOrNull { candidate ->
                val candidateTitle = normalize(candidate.title)
                val candidateArtist = normalize(candidate.artist)
                candidateTitle == wantedTitle &&
                    (wantedArtist.isBlank() || candidateArtist.contains(wantedArtist) || wantedArtist.contains(candidateArtist))
            } ?: throw IOException(
                "主音源解析失败；GD Studio 也没有找到歌名和歌手都匹配的版本，已停止以免下载错歌。",
                primaryError,
            )
            return try {
                resolveDownloadUrl(match, format, bitrate, cookie, userAgent) to true
            } catch (fallbackError: Exception) {
                throw IOException(
                    "主音源解析失败（" + (primaryError.message ?: "未知错误") +
                        "）；GD Studio 兜底解析也失败（" + (fallbackError.message ?: "未知错误") + "）。",
                    fallbackError,
                )
            }
        }
    }

    suspend fun resolveCoverUrl(song: FlacSiteSong): String = withContext(Dispatchers.IO) {
        normalizeCoverUrl(song.coverUrl).ifBlank {
            val source = when {
                song.platform.startsWith("gd:") -> song.platform.removePrefix("gd:").ifBlank { "netease" }
                song.platform == "wyy" -> "netease"
                else -> song.platform
            }
            var picId = song.coverId
            if (picId.isBlank() || picId == "0" || picId.equals("null", ignoreCase = true)) {
                val searchPlatform = if (source == "netease") "wyy" else source
                val match = runCatching {
                    searchGdStudio(song.title + " " + song.artist, searchPlatform)
                        .firstOrNull {
                            normalizeMatch(it.title) == normalizeMatch(song.title) &&
                                (normalizeMatch(it.artist).contains(normalizeMatch(song.artist.substringBefore(" / "))) ||
                                    normalizeMatch(song.artist).contains(normalizeMatch(it.artist)))
                        }
                }.getOrNull()
                picId = match?.coverId.orEmpty()
            }
            if (picId.isBlank() || picId == "0" || picId.equals("null", ignoreCase = true)) {
                ""
            } else {
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host("music-api.gdstudio.xyz")
                    .addPathSegments("api.php")
                    .addQueryParameter("types", "pic")
                    .addQueryParameter("source", source)
                    .addQueryParameter("id", picId)
                    .addQueryParameter("size", "500")
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", "https://music.gdstudio.xyz/")
                    .header("User-Agent", fallbackUserAgent)
                    .get()
                    .build()
                val body = executeBody(request).trim()
                val direct = if (body.startsWith("http://") || body.startsWith("https://")) {
                    body
                } else {
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    json?.optString("url").orEmpty()
                        .ifBlank { json?.optJSONObject("data")?.optString("url").orEmpty() }
                }
                normalizeCoverUrl(direct)
            }
        }
    }

    suspend fun fetchLyrics(
        song: FlacSiteSong,
        cookie: String?,
        userAgent: String,
    ): String = withContext(Dispatchers.IO) {
        val body = if (song.platform.startsWith("gd:")) {
            val source = song.platform.removePrefix("gd:").ifBlank { "netease" }
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("music-api.gdstudio.xyz")
                .addPathSegments("api.php")
                .addQueryParameter("types", "lyric")
                .addQueryParameter("source", source)
                .addQueryParameter("id", song.lyricId.ifBlank { song.id })
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://music.gdstudio.xyz/")
                .header("User-Agent", userAgent.ifBlank { fallbackUserAgent })
                .get()
                .build()
            executeBody(request).trim()
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
            executeBody(request).trim()
        }
        if (body.startsWith("{")) {
            val root = JSONObject(body)
            checkApiCode(root)
            val data = root.opt("data")
            val dataObject = data as? JSONObject
            val lyric = when (data) {
                is String -> data
                is JSONObject -> data.optString("lyric").ifBlank { data.optString("lrc") }
                else -> ""
            }
            val original = root.optString("lyric").ifBlank {
                root.optString("lrc").ifBlank { lyric }
            }
            val translated = root.optString("tlyric").ifBlank {
                root.optString("translated_lyric").ifBlank {
                    dataObject?.optString("tlyric")
                        .orEmpty().ifBlank { dataObject?.optString("translated_lyric").orEmpty() }
                }
            }
            LrcMerger.merge(original, translated).ifBlank { original }
        } else {
            body
        }
    }

    private fun searchGdStudio(keyword: String, normalizedPlatform: String): List<FlacSiteSong> {
        val source = if (normalizedPlatform == "wyy") "netease" else normalizedPlatform
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("music-api.gdstudio.xyz")
            .addPathSegments("api.php")
            .addQueryParameter("types", "search")
            .addQueryParameter("source", source)
            .addQueryParameter("name", keyword.trim())
            .addQueryParameter("count", "20")
            .addQueryParameter("pages", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://music.gdstudio.xyz/")
            .header("User-Agent", fallbackUserAgent)
            .get()
            .build()
        val body = executeBody(request).trim()
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
        if (songs.isEmpty()) throw IOException("GD Studio 没有搜索到候选歌曲。")
        return songs
    }

    private fun resolveGdStudioUrl(song: FlacSiteSong, bitrate: String, userAgent: String): String {
        val source = song.platform.removePrefix("gd:").ifBlank { "netease" }
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("music-api.gdstudio.xyz")
            .addPathSegments("api.php")
            .addQueryParameter("types", "url")
            .addQueryParameter("source", source)
            .addQueryParameter("id", song.id)
            .addQueryParameter("br", bitrate)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://music.gdstudio.xyz/")
            .header("User-Agent", userAgent.ifBlank { fallbackUserAgent })
            .get()
            .build()
        val body = executeBody(request).trim()
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
        val id = song.optString("id").ifBlank { song.optString("songid") }
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
        }
        return FlacSiteSong(
            id = id,
            platform = platform,
            title = title,
            artist = artist.ifBlank { "未知歌手" },
            album = song.optString("album_name").ifBlank { song.optString("album") },
            sign = song.optString("sign"),
            time = song.optString("time"),
            coverUrl = normalizeCoverUrl(
                listOf(song.optString("pic_url"), song.optString("coverUrl"), song.optString("cover"), song.optString("pic"))
                    .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()
            ),
            coverId = listOf(song.optString("pic_id"), song.optString("picId"), song.optString("picture_id"))
                .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty(),
            lyricId = song.optString("lyric_id").ifBlank { song.optString("lyricId") }.ifBlank { id },
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

    private fun checkApiCode(root: JSONObject) {
        if (root.has("code") && root.optInt("code") != 0 && root.optInt("code") != 200) {
            val code = root.optInt("code")
            val detail = root.optString("msg").ifBlank { root.optString("message") }
            if (code == 468 || detail.contains("验证") || detail.contains("challenge", true)) {
                throw IOException("下载站要求网页访问验证。请先在下方网站页面完成验证，再重新搜索。")
            }
            throw IOException("下载站接口返回错误 $code：${detail.ifBlank { "请稍后重试" }}")
        }
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
                    if (attempt == 0 && (response.code == 429 || response.code in 500..599)) {
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
