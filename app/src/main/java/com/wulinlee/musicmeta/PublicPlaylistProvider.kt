package com.wulinlee.musicmeta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class PublicPlaylistProvider {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    suspend fun load(input: String): PublicPlaylist = withContext(Dispatchers.IO) {
        val playlistId = parsePlaylistId(input)
        val detailUrl = HttpUrl.Builder()
            .scheme("https")
            .host("music.163.com")
            .addPathSegments("api/v6/playlist/detail")
            .addQueryParameter("id", playlistId.toString())
            .addQueryParameter("n", "1000")
            .build()
        val json = JSONObject(get(detailUrl.toString()))
        if (json.optInt("code") != 200) {
            throw IOException("网易云公开歌单接口暂不可用（代码 ${json.optInt("code")}）。请确认歌单公开，稍后重试。")
        }
        val p = json.optJSONObject("playlist") ?: throw IOException("没有读取到歌单信息。")
        val idsArray = p.optJSONArray("trackIds")
        val ids = if (idsArray != null) {
            (0 until idsArray.length()).mapNotNull { idsArray.optJSONObject(it)?.optLong("id")?.takeIf { id -> id > 0L } }
        } else emptyList()

        val tracks = mutableListOf<MusicTrack>()
        if (ids.isNotEmpty()) {
            ids.chunked(80).forEach { batch -> tracks += fetchTrackBatch(batch) }
        } else {
            val firstBatch = p.optJSONArray("tracks")
            if (firstBatch != null) {
                for (i in 0 until firstBatch.length()) {
                    firstBatch.optJSONObject(i)?.let { parseTrack(it)?.let(tracks::add) }
                }
            }
        }
        if (tracks.isEmpty()) {
            throw IOException("歌单是空的，或网易云没有返回公开歌曲详情。")
        }
        val expected = p.optInt("trackCount", ids.size)
        if (ids.isNotEmpty() && tracks.size < expected) {
            throw IOException("歌单应有 ${expected} 首，但公开接口只返回 ${tracks.size} 首。为避免错误同步，请稍后重试。")
        }

        PublicPlaylist(
            id = playlistId,
            name = p.optString("name").ifBlank { "网易云歌单 ${playlistId}" },
            creator = p.optJSONObject("creator")?.optString("nickname").orEmpty(),
            coverUrl = p.optString("coverImgUrl"),
            tracks = tracks.distinctBy { it.id },
        )
    }

    private fun fetchTrackBatch(ids: List<Long>): List<MusicTrack> {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("music.163.com")
            .addPathSegments("api/song/detail/")
            .addQueryParameter("id", ids.first().toString())
            .addQueryParameter("ids", JSONArray(ids).toString())
            .build()
        val json = JSONObject(get(url.toString()))
        val songs = json.optJSONArray("songs") ?: return emptyList()
        return (0 until songs.length()).mapNotNull { parseTrack(songs.optJSONObject(it) ?: return@mapNotNull null) }
    }

    private fun parseTrack(song: JSONObject): MusicTrack? {
        val id = song.optLong("id")
        if (id <= 0L) return null
        val album = song.optJSONObject("al") ?: song.optJSONObject("album") ?: JSONObject()
        val artists = song.optJSONArray("ar") ?: song.optJSONArray("artists")
        val artistNames = if (artists == null) "" else {
            (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.optString("name") }
                .filter { it.isNotBlank() }.joinToString(" / ")
        }
        return MusicTrack(
            id = id,
            title = song.optString("name").ifBlank { "未知歌曲" },
            artists = artistNames.ifBlank { "未知歌手" },
            album = album.optString("name"),
            coverUrl = album.optString("picUrl"),
            durationMs = song.optLong("dt", song.optLong("duration", 0L)),
        )
    }

    private fun parsePlaylistId(input: String): Long {
        val value = input.trim()
        if (value.matches(Regex("\\d{4,20}"))) return value.toLong()
        val id = Regex("(?:[?&]|/)(?:id=)?(\\d{4,20})(?:[&#/]|$)").findAll(value).lastOrNull()
            ?.groupValues?.getOrNull(1)?.toLongOrNull()
        return id ?: throw IOException("请输入网易云歌单 ID（纯数字）或完整歌单链接。")
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("请求失败：HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("服务器返回空内容。")
        }
    }
}
