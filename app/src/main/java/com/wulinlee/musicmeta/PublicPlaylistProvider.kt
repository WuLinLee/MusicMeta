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

    suspend fun load(
        input: String,
        likedByUserId: Boolean = false,
        offset: Int = 0,
        limit: Int = 100,
    ): PublicPlaylist = withContext(Dispatchers.IO) {
        if (offset < 0) throw IOException("分页位置不能小于 0。")
        if (limit !in 1..100) throw IOException("每批最多读取 100 首歌曲。")
        val suppliedId = parsePlaylistId(input)
        val playlistId = if (likedByUserId) findLikedPlaylistId(suppliedId) else suppliedId

        try {
            return@withContext loadFromNetease(playlistId, offset, limit)
        } catch (neteaseError: Exception) {
            try {
                return@withContext loadFromGdStudio(playlistId, offset, limit)
            } catch (gdError: Exception) {
                val neteaseMessage = neteaseError.message?.takeIf { it.isNotBlank() }
                val gdMessage = gdError.message?.takeIf { it.isNotBlank() }
                throw IOException(
                    "读取歌单第 " + (offset + 1) + " 批失败" +
                        (if (neteaseMessage != null) "（网易云：" + neteaseMessage + "）" else "") +
                        (if (gdMessage != null) "（GD Studio：" + gdMessage + "）" else "") +
                        "。请检查网络后重试；已加载的歌曲会保留。",
                    neteaseError,
                )
            }
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

    private fun loadFromGdStudio(playlistId: Long, offset: Int, limit: Int): PublicPlaylist {
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
                "GD Studio 返回代码 " + root.optInt("code") + "：" +
                    root.optString("msg").ifBlank { "接口暂不可用" }
            )
        }

        val playlist = root.optJSONObject("playlist")
            ?: root.optJSONObject("data")?.optJSONObject("playlist")
            ?: root.optJSONObject("data")
            ?: throw IOException("GD Studio 返回中没有 playlist 数据。")
        val trackArray = playlist.optJSONArray("tracks")
            ?: root.optJSONArray("tracks")
            ?: JSONArray()
        val expectedCount = playlist.optInt(
            "trackCount",
            playlist.optInt("track_count", trackArray.length()),
        )
        val totalCount = maxOf(expectedCount, trackArray.length())
        if (offset >= trackArray.length()) {
            throw IOException("GD Studio 只提供了歌单前 " + trackArray.length() + " 首，无法读取更后面的分页。")
        }
        val end = minOf(offset + limit, trackArray.length())
        val parsed = (offset until end).mapNotNull { index ->
            parseGdStudioTrack(trackArray.optJSONObject(index))
        }
        if (parsed.isEmpty()) throw IOException("GD Studio 没有返回这一批可用曲目。")
        val tracks = enrichMissingCovers(parsed)

        val creatorValue = playlist.opt("creator")
        val creator = when (creatorValue) {
            is JSONObject -> creatorValue.optString("nickname").ifBlank { creatorValue.optString("name") }
            is String -> creatorValue
            else -> ""
        }
        val cover = normalizeCoverUrl(
            playlist.optString("coverImgUrl")
                .ifBlank { playlist.optString("picUrl") }
                .ifBlank { playlist.optString("cover") }
        )

        return PublicPlaylist(
            id = playlist.optLong("id", playlistId).takeIf { it > 0L } ?: playlistId,
            name = playlist.optString("name").ifBlank { "网易云歌单 " + playlistId },
            creator = creator,
            coverUrl = cover,
            tracks = tracks.distinctBy { it.id },
            totalTrackCount = totalCount,
            nextOffset = minOf(end, totalCount),
        )
    }

    private fun normalizeCoverUrl(raw: String?): String {
        val value = raw?.trim().orEmpty()
        return when {
            value.isBlank() || value.equals("null", ignoreCase = true) -> ""
            value.startsWith("//") -> "https:" + value
            value.startsWith("http://", ignoreCase = true) -> "https://" + value.substringAfter("://")
            value.startsWith("https://", ignoreCase = true) -> value
            else -> ""
        }
    }

    private fun enrichMissingCovers(tracks: List<MusicTrack>): List<MusicTrack> {
        val ids = tracks.filter { it.coverUrl.isBlank() }.map { it.id }.distinct()
        if (ids.isEmpty()) return tracks
        val covers = ids.chunked(100)
            .flatMap { batch -> runCatching { fetchNeteaseTrackBatch(batch) }.getOrDefault(emptyList()) }
            .filter { it.coverUrl.isNotBlank() }
            .associateBy { it.id }
        return tracks.map { track ->
            if (track.coverUrl.isNotBlank()) track
            else {
                val replacement = covers[track.id]?.coverUrl.orEmpty()
                if (replacement.isBlank()) track else track.copy(coverUrl = normalizeCoverUrl(replacement))
            }
        }
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
            albumObject?.optString("coverUrl"), albumObject?.optString("cover_url"),
            albumObject?.optString("coverImgUrl"), albumObject?.optString("blurPicUrl"),
            albumObject?.optString("pic"), albumObject?.optString("image"),
            albumObject?.optString("picture"), albumObject?.optString("imgUrl"),
            otherAlbum?.optString("picUrl"), otherAlbum?.optString("pic_url"),
            otherAlbum?.optString("coverUrl"), otherAlbum?.optString("cover_url"),
            otherAlbum?.optString("coverImgUrl"), otherAlbum?.optString("blurPicUrl"),
            otherAlbum?.optString("pic"), otherAlbum?.optString("image"),
            song.optString("picUrl"), song.optString("pic_url"),
            song.optString("coverUrl"), song.optString("cover_url"),
            song.optString("coverImgUrl"), song.optString("pic"),
            song.optString("image"), song.optString("picture"), song.optString("imgUrl")
        ).mapNotNull { it?.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.let(::normalizeCoverUrl)
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

    private fun loadFromNetease(playlistId: Long, offset: Int, limit: Int): PublicPlaylist {
        val detailUrl = HttpUrl.Builder()
            .scheme("https")
            .host("music.163.com")
            .addPathSegments("api/v6/playlist/detail")
            .addQueryParameter("id", playlistId.toString())
            .addQueryParameter("n", "1000")
            .build()

        val json = JSONObject(get(detailUrl.toString(), "https://music.163.com/"))
        if (json.optInt("code") != 200) {
            throw IOException("网易云歌单详情返回代码 " + json.optInt("code") + "。")
        }
        val playlist = json.optJSONObject("playlist")
            ?: throw IOException("网易云没有返回歌单信息。")
        val idsArray = playlist.optJSONArray("trackIds")
        val allIds = if (idsArray != null) {
            (0 until idsArray.length()).mapNotNull {
                idsArray.optJSONObject(it)?.optLong("id")?.takeIf { id -> id > 0L }
            }
        } else emptyList()

        val previewArray = playlist.optJSONArray("tracks")
        val previewTracks = if (previewArray != null) {
            (0 until previewArray.length()).mapNotNull { parseGdStudioTrack(previewArray.optJSONObject(it)) }
        } else emptyList()
        val sourceIds = if (allIds.isNotEmpty()) allIds else previewTracks.map { it.id }
        val totalCount = maxOf(
            playlist.optInt("trackCount", 0),
            sourceIds.size,
            previewTracks.size,
        )
        if (offset >= sourceIds.size) {
            throw IOException("网易云没有提供第 " + (offset + 1) + " 首之后的曲目 ID。")
        }
        val pageIds = sourceIds.drop(offset).take(limit)
        if (pageIds.isEmpty()) throw IOException("歌单已没有更多可读取的歌曲。")

        val fetched = try {
            fetchNeteaseTrackBatch(pageIds)
        } catch (error: Exception) {
            val previewPage = previewTracks.filter { it.id in pageIds }
            if (previewPage.isEmpty()) throw error
            previewPage
        }
        val tracks = enrichMissingCovers(
            (fetched + previewTracks.filter { it.id in pageIds }).distinctBy { it.id }
        )
        if (tracks.isEmpty()) {
            throw IOException("网易云没有返回这一批歌曲的详情，请稍后重试。")
        }

        val creator = playlist.optJSONObject("creator")?.optString("nickname").orEmpty()
        return PublicPlaylist(
            id = playlistId,
            name = playlist.optString("name").ifBlank { "网易云歌单 " + playlistId },
            creator = creator,
            coverUrl = normalizeCoverUrl(playlist.optString("coverImgUrl")),
            tracks = tracks,
            totalTrackCount = totalCount,
            nextOffset = minOf(offset + pageIds.size, totalCount),
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
