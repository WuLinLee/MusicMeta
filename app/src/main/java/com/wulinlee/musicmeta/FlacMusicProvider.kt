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
        if (songs.isEmpty()) throw IOException("没有搜索到歌曲。若网页提示访问验证，请先在下方网站中完成人机验证后重试。")
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

    suspend fun fetchLyrics(
        song: FlacSiteSong,
        cookie: String?,
        userAgent: String,
    ): String = withContext(Dispatchers.IO) {
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
        val body = executeBody(request).trim()
        if (body.startsWith("{")) {
            val root = JSONObject(body)
            checkApiCode(root)
            val data = root.opt("data")
            when (data) {
                is String -> data
                is JSONObject -> data.optString("lyric").ifBlank { data.optString("lrc") }
                else -> root.optString("lyric")
            }
        } else {
            body
        }
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
            coverUrl = song.optString("pic_url").ifBlank { song.optString("cover") },
        )
    }

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
        client.newCall(request).execute().use { response ->
            if (response.code == 468) {
                throw IOException("下载站访问验证未通过（HTTP 468）。请在下方网页中完成验证后重试。")
            }
            if (!response.isSuccessful) throw IOException("下载站请求失败：HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("下载站返回空内容。")
        }
    }

    private companion object {
        const val fallbackUserAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
