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
 * Public playlist resolver.
 *
 * GD Studio's web client uses:
 *   GET https://music-api.gdstudio.xyz/api.php?types=playlist&source=netease&id=<playlistId>
 *
 * The official site's public read-only playlist endpoint is tried first. If it is
 * unavailable or returns incomplete tracks, fall back to NetEase's read-only
 * playlist/detail + POST song/detail endpoints.
 */
class PublicPlaylistProvider {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun load(input: String, likedByUserId: Boolean = false): PublicPlaylist = withContext(Dispatchers.IO) {
        val suppliedId = parsePlaylistId(input)
        val playlistId = if (likedByUserId) findLikedPlaylistId(suppliedId) else suppliedId
        var gdError: Exception? = null

        try {
            return@withContext loadFromGdStudio(playlistId)
        } catch (error: Exception) {
            gdError = error
        }

        try {
            loadFromNetease(playlistId)
        } catch (neteaseError: Exception) {
            val gdMessage = gdError?.message?.takeIf { it.isNotBlank() }
            val neteaseMessage = neteaseError.message?.takeIf { it.isNotBlank() }
            throw IOException(
                buildString {
                    append("GD Studio 歌单接口未能完整返回曲目")
                    if (gdMessage != null) append("（").append(gdMessage).append("）")
                    append("；网易云备用接口也失败")
                    if (neteaseMessage != null) append("（").append(neteaseMessage).append("）")
                    append("。请检查网络，稍后重试。")
                },
                neteaseError,
            )
        }
    }

    private fun findLikedPlaylistId(userId: Long): Long {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("music.163.com")
            .addPathSegments("api/user/playlist")
            .addQueryParameter("uid", userId.toString())
            .addQueryParameter("limit", "100")
            .addQueryParameter("offset", "0")
            .build()
        val root = JSONObject(get(url.toString(), "https://music.163.com/"))
        if (root.optInt("code", -1) != 200) {
            throw IOException("网易云没有允许读取这个用户的歌单列表（用户 ID：$userId）。红心歌单可能需要网易云账号授权。")
        }
        val playlists = root.optJSONArray("playlist") ?: root.optJSONArray("playlists")
            ?: throw IOException("网易云没有返回该用户的歌单列表。请确认输入的是用户 ID，而不是歌单 ID。")
        val liked = (0 until playlists.length())
            .mapNotNull { playlists.optJSONObject(it) }
            .firstOrNull {
                it.optInt("specialType", 0) == 5 ||
                    it.optString("name").trim() == "我喜欢的音乐"
            }
        val id = liked?.optLong("id", 0L)?.takeIf { it > 0L }
        return id ?: throw IOException(
            "没有找到这个用户的“我喜欢的音乐”歌单。请检查用户 ID；如果红心歌单是私密的，可能需要先授权网易云账号。"
        )
    }

    private fun loadFromGdStudio(playlistId: Long): PublicPlaylist {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("music-api.gdstudio.xyz")
            .addPathSegments("api.php")
            .addQueryParameter("types", "playlist")
            .addQueryParameter("source", "netease")
            .addQueryParameter("id", playlistId.toString())
            .build()

        val root = JSONObject(get(url.toString(), "https://music.gdstudio.xyz/"))
        if (root.has("code") && root.optInt("code") != 200) {
            throw IOException(
                "GD Studio 返回代码 ${root.optInt("code")}：${root.optString("msg").ifBlank { "接口暂不可用" }}"
            )
        }

        val playlist = root.optJSONObject("playlist")
            ?: root.optJSONObject("data")?.optJSONObject("playlist")
            ?: root.optJSONObject("data")
            ?: throw IOException("GD Studio 返回中没有 playlist 数据。")

        val trackArray = playlist.optJSONArray("tracks")
            ?: root.optJSONArray("tracks")
            ?: JSONArray()
        val tracks = (0 until trackArray.length()).mapNotNull { index ->
            parseGdStudioTrack(trackArray.optJSONObject(index))
        }
        if (tracks.isEmpty()) throw IOException("GD Studio 没有返回可用曲目。")

        val expectedCount = playlist.optInt(
            "trackCount",
            playlist.optInt("track_count", tracks.size),
        )
        if (expectedCount > tracks.size) {
            throw IOException("GD Studio 仅返回 ${tracks.size}/${expectedCount} 首，尝试备用接口补全。")
        }

        val creatorValue = playlist.opt("creator")
        val creator = when (creatorValue) {
            is JSONObject -> creatorValue.optString("nickname").ifBlank { creatorValue.optString("name") }
            is String -> creatorValue
            else -> ""
        }
        val cover = playlist.optString("coverImgUrl")
            .ifBlank { playlist.optString("picUrl") }
            .ifBlank { playlist.optString("cover") }

        return PublicPlaylist(
            id = playlist.optLong("id", playlistId).takeIf { it > 0L } ?: playlistId,
            name = playlist.optString("name").ifBlank { "网易云歌单 $playlistId" },
            creator = creator,
            coverUrl = cover,
            tracks = tracks.distinctBy { it.id },
        )
    }

    private fun parseGdStudioTrack(song: JSONObject?): MusicTrack? {
        if (song == null) return null
        val id = song.optLong("id", 0L)
        if (id <= 0L) return null
        val albumValue = song.opt("al") ?: song.opt("album")
        val albumObject = albumValue as? JSONObject
        val albumName = when (albumValue) {
            is JSONObject -> albumValue.optString("name")
            is String -> albumValue
            else -> ""
        }
        val artistArray = song.optJSONArray("ar") ?: song.optJSONArray("artists")
        val artistNames = if (artistArray == null) {
            song.optString("artist")
        } else {
            (0 until artistArray.length()).mapNotNull { index ->
                when (val artist = artistArray.opt(index)) {
                    is JSONObject -> artist.optString("name").ifBlank { null }
                    is String -> artist.trim().ifBlank { null }
                    is Number -> artist.toString()
                    else -> null
                }
            }.joinToString(" / ")
        }
        val otherAlbum = song.optJSONObject("album")
        val cover = listOf(
            albumObject?.optString("picUrl"), albumObject?.optString("pic_url"),
            albumObject?.optString("coverUrl"), albumObject?.optString("cover"),
            otherAlbum?.optString("picUrl"), otherAlbum?.optString("pic_url"),
            otherAlbum?.optString("coverUrl"), song.optString("picUrl"),
            song.optString("pic_url"), song.optString("coverUrl"), song.optString("cover")
        ).mapNotNull { it?.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .orEmpty()

        return MusicTrack(
            id = id,
            title = song.optString("name").ifBlank { "未知歌曲" },
            artists = artistNames.ifBlank { "未知歌手" },
            album = albumName,
            coverUrl = cover,
            durationMs = song.optLong("dt", song.optLong("duration", 0L)),
        )
    }

    private fun loadFromNetease(playlistId: Long): PublicPlaylist {
        val detailUrl = HttpUrl.Builder()
            .scheme("https")
            .host("music.163.com")
            .addPathSegments("api/v6/playlist/detail")
            .addQueryParameter("id", playlistId.toString())
            .addQueryParameter("n", "1000")
            .build()

        val json = JSONObject(get(detailUrl.toString(), "https://music.163.com/"))
        if (json.optInt("code") != 200) {
            throw IOException("网易云歌单详情返回代码 ${json.optInt("code")}。")
        }
        val playlist = json.optJSONObject("playlist")
            ?: throw IOException("网易云没有返回歌单信息。")
        val idsArray = playlist.optJSONArray("trackIds")
        val ids = if (idsArray != null) {
            (0 until idsArray.length()).mapNotNull { idsArray.optJSONObject(it)?.optLong("id")?.takeIf { id -> id > 0L } }
        } else emptyList()

        val tracks = mutableListOf<MusicTrack>()
        if (ids.isNotEmpty()) {
            ids.chunked(100).forEach { batch -> tracks += fetchNeteaseTrackBatch(batch) }
        } else {
            val firstBatch = playlist.optJSONArray("tracks")
            if (firstBatch != null) {
                for (i in 0 until firstBatch.length()) {
                    firstBatch.optJSONObject(i)?.let { parseGdStudioTrack(it)?.let(tracks::add) }
                }
            }
        }
        val expected = playlist.optInt("trackCount", ids.size)
        if (tracks.isEmpty()) {
            throw IOException("网易云没有返回歌曲详情。")
        }
        if (expected > 0 && tracks.size < expected) {
            throw IOException("网易云歌单应有 ${expected} 首，但只拿到 ${tracks.size} 首。")
        }

        val creator = playlist.optJSONObject("creator")?.optString("nickname").orEmpty()
        return PublicPlaylist(
            id = playlistId,
            name = playlist.optString("name").ifBlank { "网易云歌单 $playlistId" },
            creator = creator,
            coverUrl = playlist.optString("coverImgUrl"),
            tracks = tracks.distinctBy { it.id },
        )
    }

    /**
     * Current read-only NetEase endpoint. POST avoids huge query strings for large playlists.
     * Anonymous playlist/detail often contains only a small tracks preview; trackIds is the
     * full list, so each batch is explicitly requested.
     */
    private fun fetchNeteaseTrackBatch(ids: List<Long>): List<MusicTrack> {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("music.163.com")
            .addPathSegments("api/v3/song/detail")
            .build()
        val c = JSONArray().apply {
            ids.forEach { id -> put(JSONObject().put("id", id)) }
        }.toString()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", userAgent)
            .post(FormBody.Builder().add("c", c).build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("网易云批量歌曲详情请求失败：HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: throw IOException("网易云批量接口返回空内容。"))
            val songs = json.optJSONArray("songs") ?: throw IOException("网易云批量接口没有 songs 字段。")
            return (0 until songs.length()).mapNotNull { index ->
                parseGdStudioTrack(songs.optJSONObject(index))
            }
        }
    }

    private fun parsePlaylistId(input: String): Long {
        val value = input.trim()
        if (value.matches(Regex("\\d{4,20}"))) return value.toLong()
        val id = Regex("(?:[?&]|/)(?:id=)?(\\d{4,20})(?:[&#/]|\\z)")
            .findAll(value).lastOrNull()?.groupValues?.getOrNull(1)?.toLongOrNull()
        return id ?: throw IOException("请输入网易云歌单 ID（纯数字）或完整歌单链接。")
    }

    private fun get(url: String, referer: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("Origin", referer.trimEnd('/'))
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", userAgent)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("请求失败：HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("服务器返回空内容。")
            if (body.trimStart().startsWith("<!doctype html", true) || body.trimStart().startsWith("<html", true)) {
                throw IOException("接口返回了网页而不是 JSON，可能遇到访问验证。")
            }
            return body
        }
    }

    private companion object {
        const val userAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
