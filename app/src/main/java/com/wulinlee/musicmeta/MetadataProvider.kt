package com.wulinlee.musicmeta

import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MetadataProvider {
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

    private data class LyricsPayload(
        val original: String = "",
        val translated: String = "",
        val romanization: String = "",
        val wordByWord: String = "",
        val wordByWordTranslated: String = "",
    )

    suspend fun fetchAssets(track: MusicTrack): TrackAssets = withContext(Dispatchers.IO) {
        val cover = downloadCover(track.coverUrl)
            ?: resolveCoverUrlFromSongDetail(track.id)?.let(::downloadCover)
            ?: resolveCoverUrlFromGdStudio(track)?.let(::downloadCover)
            ?: resolveCoverUrlFromItunes(track)?.let(::downloadCover)
        var lyrics = if (track.id > 0L) getLyrics(track.id) else LyricsPayload()
        if (track.id > 0L && (lyrics.original.isBlank() || lyrics.translated.isBlank())) {
            val gdFallback = getGdStudioLyrics(track.id)
            lyrics = lyrics.copy(
                original = lyrics.original.ifBlank { gdFallback.original },
                translated = lyrics.translated.ifBlank { gdFallback.translated },
            )
        }
        if (lyrics.original.isBlank()) {
            val fallback = getLrclibLyrics(track)
            lyrics = lyrics.copy(
                original = lyrics.original.ifBlank { fallback.first },
                translated = lyrics.translated.ifBlank { fallback.second },
            )
        }
        TrackAssets(
            coverBytes = cover?.first,
            coverMimeType = cover?.second,
            originalLyrics = lyrics.original,
            translatedLyrics = lyrics.translated,
            bilingualLyrics = LrcMerger.merge(lyrics.original, lyrics.translated),
            romanizationLyrics = lyrics.romanization,
            wordByWordLyrics = lyrics.wordByWord,
            wordByWordTranslatedLyrics = lyrics.wordByWordTranslated,
        )
    }

    suspend fun resolveSourceCoverUrl(song: FlacSiteSong): String = withContext(Dispatchers.IO) {
        val existing = song.coverUrl.trim()
        if (existing.startsWith("https://") || existing.startsWith("http://")) {
            return@withContext existing.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        }
        val track = MusicTrack(
            id = 0L,
            title = song.title,
            artists = song.artist,
            album = song.album,
            coverUrl = "",
        )
        (resolveCoverUrlFromGdStudio(track) ?: resolveCoverUrlFromItunes(track))
            .orEmpty()
            .replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
    }

    suspend fun resolveSourceLyrics(song: FlacSiteSong): String = withContext(Dispatchers.IO) {
        val track = MusicTrack(
            id = 0L,
            title = song.title,
            artists = song.artist,
            album = song.album,
            coverUrl = song.coverUrl,
        )
        getLrclibLyrics(track).first.trim()
    }

    private fun getLyrics(id: Long): LyricsPayload {
        val url = HttpUrl.Builder()
            .scheme("https").host("music.163.com").addPathSegments("api/song/lyric")
            .addQueryParameter("id", id.toString())
            .addQueryParameter("lv", "-1")
            .addQueryParameter("kv", "-1")
            .addQueryParameter("tv", "-1")
            .addQueryParameter("yv", "-1")
            .build()
        return try {
            val json = JSONObject(get(url.toString(), "https://music.163.com/"))
            if (json.optInt("code") != 200) return LyricsPayload()
            val translated = json.optJSONObject("tlyric")?.optString("lyric").orEmpty()
            val wordByWordTranslated = json.optJSONObject("ytlrc")?.optString("lyric").orEmpty()
            val romanization = json.optJSONObject("yromalrc")?.optString("lyric").orEmpty()
                .ifBlank { json.optJSONObject("romalrc")?.optString("lyric").orEmpty() }
            LyricsPayload(
                original = json.optJSONObject("lrc")?.optString("lyric").orEmpty(),
                translated = translated,
                romanization = romanization,
                wordByWord = json.optJSONObject("yrc")?.optString("lyric").orEmpty(),
                wordByWordTranslated = wordByWordTranslated,
            )
        } catch (_: Exception) {
            LyricsPayload()
        }
    }

    private fun getGdStudioLyrics(id: Long): LyricsPayload {
        if (id <= 0L) return LyricsPayload()
        return try {
            val url = HttpUrl.Builder()
                .scheme("https").host("music-api.gdstudio.xyz").addPathSegments("api.php")
                .addQueryParameter("types", "lyric")
                .addQueryParameter("source", "netease")
                .addQueryParameter("id", id.toString())
                .build()
            val body = get(url.toString(), "https://music.gdstudio.xyz/")
            val root = JSONObject(body)
            val data = root.optJSONObject("data")
            LyricsPayload(
                original = root.optString("lyric").ifBlank { data?.optString("lyric").orEmpty() },
                translated = root.optString("tlyric").ifBlank { data?.optString("tlyric").orEmpty() },
            )
        } catch (_: Exception) {
            LyricsPayload()
        }
    }

    private fun getLrclibLyrics(track: MusicTrack): Pair<String, String> {
        fun normalize(value: String): String = value.trim().lowercase().filter { it.isLetterOrDigit() }
        val wantedTitle = normalize(track.title)
        val wantedArtist = normalize(track.artists.substringBefore(" / "))

        // LRCLIB's exact-signature endpoint uses duration and is generally safer than a loose search.
        val durationSeconds = (track.durationMs / 1000L).toInt()
        if (durationSeconds in 1..3600) {
            try {
                val exactUrl = HttpUrl.Builder()
                    .scheme("https").host("lrclib.net").addPathSegments("api/get")
                    .addQueryParameter("track_name", track.title)
                    .addQueryParameter("artist_name", track.artists.substringBefore(" / "))
                    .addQueryParameter("album_name", track.album)
                    .addQueryParameter("duration", durationSeconds.toString())
                    .build()
                val match = JSONObject(get(exactUrl.toString()))
                val title = normalize(match.optString("trackName").ifBlank { match.optString("name") })
                val artist = normalize(match.optString("artistName"))
                if (title == wantedTitle && (wantedArtist.isBlank() || artist.contains(wantedArtist) || wantedArtist.contains(artist))) {
                    val synced = match.optString("syncedLyrics")
                    val plain = match.optString("plainLyrics")
                    if (synced.isNotBlank() || plain.isNotBlank()) return (synced.ifBlank { plain }) to ""
                }
            } catch (_: Exception) {
                // Continue to strict title/artist search below.
            }
        }

        return try {
            val url = HttpUrl.Builder()
                .scheme("https").host("lrclib.net").addPathSegments("api/search")
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", track.artists.substringBefore(" / "))
                .addQueryParameter("album_name", track.album)
                .build()
            val result = JSONArray(get(url.toString()))
            val list = (0 until result.length()).mapNotNull { result.optJSONObject(it) }
            val match = list.firstOrNull {
                val title = normalize(it.optString("trackName").ifBlank { it.optString("name") })
                val artist = normalize(it.optString("artistName"))
                title == wantedTitle &&
                    (wantedArtist.isBlank() || artist.contains(wantedArtist) || wantedArtist.contains(artist))
            } ?: return "" to ""
            val synced = match.optString("syncedLyrics")
            val plain = match.optString("plainLyrics")
            (synced.ifBlank { plain }) to ""
        } catch (_: Exception) {
            "" to ""
        }
    }

    private fun resolveCoverUrlFromSongDetail(id: Long): String? {
        if (id <= 0L) return null
        return try {
            val url = HttpUrl.Builder().scheme("https").host("music.163.com")
                .addPathSegments("api/v3/song/detail").build()
            val payload = JSONArray().put(JSONObject().put("id", id)).toString()
            val request = Request.Builder().url(url)
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", userAgent)
                .post(FormBody.Builder().add("c", payload).build()).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JSONObject(response.body?.string().orEmpty())
                val song = root.optJSONArray("songs")?.optJSONObject(0) ?: return@use null
                val album = song.optJSONObject("al") ?: song.optJSONObject("album")
                listOf(
                    album?.optString("picUrl"), album?.optString("pic_url"),
                    album?.optString("coverUrl"), album?.optString("cover"),
                    song.optString("picUrl"), song.optString("pic_url"),
                    song.optString("coverUrl"), song.optString("cover")
                ).mapNotNull { it?.trim() }
                    .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            }
        } catch (_: Exception) { null }
    }

    private fun resolveCoverUrlFromGdStudio(track: MusicTrack): String? {
        return try {
            val searchUrl = HttpUrl.Builder().scheme("https").host("music-api.gdstudio.xyz")
                .addPathSegments("api.php")
                .addQueryParameter("types", "search")
                .addQueryParameter("source", "netease")
                .addQueryParameter("name", track.title + " " + track.artists.substringBefore(" / "))
                .addQueryParameter("count", "10").addQueryParameter("pages", "1").build()
            val searchRequest = Request.Builder().url(searchUrl)
                .header("Referer", "https://music.gdstudio.xyz/")
                .header("User-Agent", userAgent).get().build()
            val body = client.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            } ?: return null
            val root = runCatching { JSONObject(body) }.getOrNull()
            val results = if (body.trimStart().startsWith("[")) {
                runCatching { JSONArray(body) }.getOrNull()
            } else {
                root?.optJSONArray("data") ?: root?.optJSONArray("result")
                    ?: root?.optJSONArray("list") ?: root?.optJSONArray("songs")
            } ?: return null
            val normalize: (String) -> String = { value ->
                value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
            }
            val title = normalize(track.title)
            val artist = normalize(track.artists.substringBefore(" / "))
            val match = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
                .firstOrNull { item ->
                    val name = normalize(item.optString("name").ifBlank { item.optString("title") })
                    val artistValue = item.opt("artist")
                    val artists = when (artistValue) {
                        is JSONArray -> (0 until artistValue.length()).joinToString(" ") { artistValue.optString(it) }
                        is JSONObject -> artistValue.optString("name")
                        else -> artistValue?.toString().orEmpty()
                    }.let(normalize)
                    name == title && (artist.isBlank() || artists.contains(artist) || artist.contains(artists))
                } ?: return null
            val picId = match.optString("pic_id").ifBlank { match.optString("picId") }
                .ifBlank { match.optString("pic") }
            if (picId.isBlank() || picId == "0") return null
            val picUrl = HttpUrl.Builder().scheme("https").host("music-api.gdstudio.xyz")
                .addPathSegments("api.php").addQueryParameter("types", "pic")
                .addQueryParameter("source", "netease").addQueryParameter("id", picId)
                .addQueryParameter("size", "500").build()
            val picRequest = Request.Builder().url(picUrl)
                .header("Referer", "https://music.gdstudio.xyz/")
                .header("User-Agent", userAgent).get().build()
            client.newCall(picRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val value = response.body?.string().orEmpty().trim()
                if (value.startsWith("http://") || value.startsWith("https://")) return@use value
                val json = runCatching { JSONObject(value) }.getOrNull() ?: return@use null
                json.optString("url").ifBlank { json.optJSONObject("data")?.optString("url").orEmpty() }
                    .takeIf { it.startsWith("http://") || it.startsWith("https://") }
            }
        } catch (_: Exception) { null }
    }

    private fun resolveCoverUrlFromItunes(track: MusicTrack): String? {
        return try {
            val url = HttpUrl.Builder().scheme("https").host("itunes.apple.com")
                .addPathSegments("search")
                .addQueryParameter("term", track.title + " " + track.artists.substringBefore(" / "))
                .addQueryParameter("entity", "song").addQueryParameter("limit", "10").build()
            val request = Request.Builder().url(url).header("User-Agent", userAgent).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val results = JSONObject(response.body?.string().orEmpty()).optJSONArray("results") ?: return@use null
                val norm: (String) -> String = { it.trim().lowercase().replace(Regex("\\s+"), " ") }
                val title = norm(track.title)
                val artist = norm(track.artists.substringBefore(" / "))
                val match = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
                    .firstOrNull { item ->
                        norm(item.optString("trackName")) == title &&
                            (artist.isBlank() || norm(item.optString("artistName")).contains(artist))
                    } ?: return@use null
                match.optString("artworkUrl100").replace("100x100bb", "600x600bb")
                    .takeIf { it.startsWith("http://") || it.startsWith("https://") }
            }
        } catch (_: Exception) { null }
    }

    private fun downloadCover(url: String): Pair<ByteArray, String>? {
        if (url.isBlank()) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val requestUrl = if (uri.scheme.equals("http", true) &&
            uri.host.orEmpty().endsWith(".music.126.net", true)) {
            url.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        } else url
        return try {
            val request = Request.Builder().url(requestUrl)
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", userAgent).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > 12_000_000L) return@use null
                val reportedMime = body.contentType()?.toString()?.substringBefore(';').orEmpty()
                if (reportedMime.isNotBlank() && !reportedMime.startsWith("image/") &&
                    reportedMime != "application/octet-stream") return@use null
                val bytes = readLimited(body.byteStream(), 12_000_000) ?: return@use null
                val isJpeg = bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()
                val isPng = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
                    byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a))
                val isWebp = bytes.size >= 12 && String(bytes.copyOfRange(0,4), Charsets.US_ASCII) == "RIFF" &&
                    String(bytes.copyOfRange(8,12), Charsets.US_ASCII) == "WEBP"
                val isGif = bytes.size >= 6 && String(bytes.copyOfRange(0,3), Charsets.US_ASCII) == "GIF"
                if (!isJpeg && !isPng && !isWebp && !isGif) return@use null
                val mime = reportedMime.ifBlank {
                    when { isJpeg -> "image/jpeg"; isWebp -> "image/webp"; isGif -> "image/gif"; else -> "image/png" }
                }
                bytes to mime
            }
        } catch (_: Exception) { null }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray? {
        return input.use { stream ->
            val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) return@use null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun get(url: String, referer: String? = null): String {
        val builder = Request.Builder().url(url).header("User-Agent", userAgent).get()
        if (referer != null) builder.header("Referer", referer)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val userAgent = "MusicMeta/0.1.0 Android metadata lookup"
    }
}
