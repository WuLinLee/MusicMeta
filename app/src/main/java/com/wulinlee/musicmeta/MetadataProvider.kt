package com.wulinlee.musicmeta

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        var lyrics = getLyrics(track.id)
        if (lyrics.original.isBlank()) {
            val fallback = getLrclibLyrics(track)
            lyrics = lyrics.copy(
                original = fallback.first,
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

    private fun getLrclibLyrics(track: MusicTrack): Pair<String, String> {
        return try {
            val url = HttpUrl.Builder()
                .scheme("https").host("lrclib.net").addPathSegments("api/search")
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", track.artists)
                .build()
            val result = JSONArray(get(url.toString()))
            val list = (0 until result.length()).mapNotNull { result.optJSONObject(it) }
            val match = list.firstOrNull {
                it.optString("trackName").equals(track.title, true) &&
                    it.optString("artistName").contains(track.artists.substringBefore(" / "), true)
            } ?: list.firstOrNull()
            val synced = match?.optString("syncedLyrics").orEmpty()
            val plain = match?.optString("plainLyrics").orEmpty()
            (synced.ifBlank { plain }) to ""
        } catch (_: Exception) {
            "" to ""
        }
    }

    private fun downloadCover(url: String): Pair<ByteArray, String>? {
        if (url.isBlank()) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return try {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > 12_000_000L) return@use null
                val mime = body.contentType()?.toString()?.substringBefore(';') ?: "image/jpeg"
                val bytes = body.bytes()
                if (bytes.size < 8) null else bytes to mime
            }
        } catch (_: Exception) {
            null
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
