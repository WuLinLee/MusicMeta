package com.wulinlee.musicmeta

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import coil.imageLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Snapshot of the user-visible cache restored off the UI thread on launch. */
internal data class RestoredMusicMetaCache(
    val publicPlaylist: PublicPlaylist?,
    val likedPlaylist: PublicPlaylist?,
    val pageCache: Map<String, List<MusicTrack>>,
    val localTracks: List<LocalAudioTrack>,
)

/** Small persistence helpers for data that should survive Activity/process recreation. */
internal object MusicMetaPersistence {
    fun loadCaches(context: Context, preferences: SharedPreferences): RestoredMusicMetaCache =
        RestoredMusicMetaCache(
            publicPlaylist = loadPlaylist(preferences, "public"),
            likedPlaylist = loadPlaylist(preferences, "liked"),
            pageCache = loadPageCache(preferences),
            localTracks = loadLocalTracks(context),
        )

    private const val PUBLIC_PLAYLIST_KEY = "cached_public_playlist_v1"
    private const val LIKED_PLAYLIST_KEY = "cached_liked_playlist_v1"
    private const val PAGE_CACHE_KEY = "cached_track_pages_v1"
    private const val RECENT_TRACK_KEYS = "recently_played_track_keys_v1"
    private const val LOCAL_TRACKS_FILE = "musicmeta_local_tracks_v1.json"
    private const val MAX_PERSISTED_PAGES = 16
    private const val MAX_RECENT_TRACKS = 12

    fun loadPlaylist(preferences: SharedPreferences, mode: String): PublicPlaylist? {
        val key = if (mode == "liked") LIKED_PLAYLIST_KEY else PUBLIC_PLAYLIST_KEY
        return decodePlaylist(preferences.getString(key, null))
    }

    fun savePlaylist(preferences: SharedPreferences, mode: String, playlist: PublicPlaylist) {
        val key = if (mode == "liked") LIKED_PLAYLIST_KEY else PUBLIC_PLAYLIST_KEY
        preferences.edit().putString(key, encodePlaylist(playlist)).apply()
    }

    fun loadPageCache(preferences: SharedPreferences): Map<String, List<MusicTrack>> {
        val raw = preferences.getString(PAGE_CACHE_KEY, null) ?: return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val pages = root.optJSONArray("pages") ?: return emptyMap()
        val result = linkedMapOf<String, List<MusicTrack>>()
        for (index in 0 until pages.length()) {
            val entry = pages.optJSONObject(index) ?: continue
            val key = entry.optString("key").takeIf { it.isNotBlank() } ?: continue
            val tracks = decodeTracks(entry.optJSONArray("tracks"))
            if (tracks.isNotEmpty()) result[key] = tracks
        }
        return result.entries.toList().takeLast(MAX_PERSISTED_PAGES).associate { it.key to it.value }
    }

    fun savePageCache(preferences: SharedPreferences, cache: Map<String, List<MusicTrack>>) {
        val retained = cache.entries.toList().takeLast(MAX_PERSISTED_PAGES)
        val pages = JSONArray()
        retained.forEach { (key, tracks) ->
            pages.put(JSONObject().put("key", key).put("tracks", encodeTracks(tracks)))
        }
        preferences.edit().putString(PAGE_CACHE_KEY, JSONObject().put("version", 1).put("pages", pages).toString()).apply()
    }

    fun loadLocalTracks(context: Context): List<LocalAudioTrack> {
        val file = File(context.filesDir, LOCAL_TRACKS_FILE)
        if (!file.isFile) return emptyList()
        val array = runCatching { JSONArray(file.readText(Charsets.UTF_8)) }.getOrNull() ?: return emptyList()
        val tracks = ArrayList<LocalAudioTrack>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val rawUri = item.optString("uri").takeIf { it.isNotBlank() } ?: continue
            val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: continue
            tracks += LocalAudioTrack(
                uri = uri,
                title = item.optString("title", "未知歌曲"),
                artist = item.optString("artist", "未知艺术家"),
                album = item.optString("album"),
                durationMs = item.optLong("durationMs", 0L),
                coverUri = item.optString("coverUri"),
            )
        }
        return tracks.distinctBy { it.uri.toString() }
    }

    fun saveLocalTracks(context: Context, tracks: List<LocalAudioTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(
                JSONObject()
                    .put("uri", track.uri.toString())
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("album", track.album)
                    .put("durationMs", track.durationMs)
                    .put("coverUri", track.coverUri),
            )
        }
        val target = File(context.filesDir, LOCAL_TRACKS_FILE)
        val temporary = File(context.filesDir, "$LOCAL_TRACKS_FILE.tmp")
        temporary.writeText(array.toString(), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
            temporary.delete()
        }
    }

    fun loadRecentTrackKeys(preferences: SharedPreferences): List<String> {
        val raw = preferences.getString(RECENT_TRACK_KEYS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            .distinct().takeLast(MAX_RECENT_TRACKS)
    }

    fun recordRecentPlay(
        preferences: SharedPreferences,
        previousKeys: List<String>,
        sourceKey: String,
        playlistMode: String?,
        playlistId: Long?,
        trackId: Long?,
        playlistPosition: Int?,
    ): List<String> {
        val additions = buildList {
            if (sourceKey.isNotBlank()) add(sourceKey)
            if (playlistMode != null && playlistId != null && trackId != null && trackId > 0L) {
                add(playlistTrackKey(playlistMode, playlistId, trackId))
            }
        }
        val updated = (previousKeys + additions).filter(String::isNotBlank).distinct().takeLast(MAX_RECENT_TRACKS)
        val editor = preferences.edit().putString(
            RECENT_TRACK_KEYS,
            JSONArray().apply { updated.forEach { put(it) } }.toString(),
        )

        if (playlistMode != null && playlistId != null && playlistPosition != null && playlistPosition >= 0) {
            val positionKey = recentPositionsKey(playlistMode, playlistId)
            val positions = loadRecentPlaylistPositions(preferences, playlistMode, playlistId).toMutableList()
            positions.remove(playlistPosition)
            positions += playlistPosition
            editor.putString(
                positionKey,
                JSONArray().apply { positions.takeLast(MAX_RECENT_TRACKS).forEach { put(it) } }.toString(),
            )
        }
        editor.apply()
        return updated
    }

    fun loadRecentPlaylistPositions(preferences: SharedPreferences, mode: String, playlistId: Long): Set<Int> {
        val raw = preferences.getString(recentPositionsKey(mode, playlistId), null) ?: return emptySet()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptySet()
        return (0 until array.length()).mapNotNull { array.optInt(it, -1).takeIf { value -> value >= 0 } }.toSet()
    }

    fun clearTemporaryCache(context: Context): Long {
        // Ask Coil to clear its own stores first; never delete filesDir or user downloads.
        runCatching { context.imageLoader.memoryCache?.clear() }
        runCatching { context.imageLoader.diskCache?.clear() }

        fun sizeOf(file: File): Long {
            if (!file.exists()) return 0L
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf(::sizeOf) ?: 0L
        }

        fun clearChildren(directory: File?, keepNames: Set<String> = emptySet()): Long {
            if (directory == null) return 0L
            var bytes = 0L
            directory.listFiles()?.forEach { child ->
                if (child.name in keepNames) return@forEach
                bytes += sizeOf(child)
                runCatching { child.deleteRecursively() }
            }
            return bytes
        }

        // Coil's default disk-cache directory is managed by its DiskCache instance above.
        return clearChildren(context.cacheDir, setOf("image_cache")) + clearChildren(context.externalCacheDir)
    }

    private fun recentPositionsKey(mode: String, playlistId: Long) = "recent_playlist_positions_v1:$mode:$playlistId"

    fun playlistTrackKey(mode: String, playlistId: Long, trackId: Long): String =
        "playlist:$mode:$playlistId:$trackId"

    private fun encodePlaylist(playlist: PublicPlaylist): String =
        JSONObject()
            .put("version", 1)
            .put("id", playlist.id)
            .put("name", playlist.name)
            .put("creator", playlist.creator)
            .put("coverUrl", playlist.coverUrl)
            .put("tracks", encodeTracks(playlist.tracks))
            .put("totalTrackCount", playlist.totalTrackCount)
            .put("nextOffset", playlist.nextOffset)
            .toString()

    private fun decodePlaylist(raw: String?): PublicPlaylist? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val id = json.optLong("id", 0L)
            if (id <= 0L) return null
            val tracks = decodeTracks(json.optJSONArray("tracks"))
            PublicPlaylist(
                id = id,
                name = json.optString("name"),
                creator = json.optString("creator"),
                coverUrl = json.optString("coverUrl"),
                tracks = tracks,
                totalTrackCount = json.optInt("totalTrackCount", tracks.size).coerceAtLeast(tracks.size),
                nextOffset = json.optInt("nextOffset", tracks.size).coerceAtLeast(tracks.size),
            )
        }.getOrNull()
    }

    private fun encodeTracks(tracks: List<MusicTrack>): JSONArray = JSONArray().apply {
        tracks.forEach { track ->
            put(
                JSONObject()
                    .put("id", track.id)
                    .put("title", track.title)
                    .put("artists", track.artists)
                    .put("album", track.album)
                    .put("coverUrl", track.coverUrl)
                    .put("durationMs", track.durationMs),
            )
        }
    }

    private fun decodeTracks(array: JSONArray?): List<MusicTrack> {
        if (array == null) return emptyList()
        val tracks = ArrayList<MusicTrack>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optLong("id", 0L)
            if (id <= 0L) continue
            tracks += MusicTrack(
                id = id,
                title = item.optString("title"),
                artists = item.optString("artists"),
                album = item.optString("album"),
                coverUrl = item.optString("coverUrl"),
                durationMs = item.optLong("durationMs", 0L),
            )
        }
        return tracks
    }
}
