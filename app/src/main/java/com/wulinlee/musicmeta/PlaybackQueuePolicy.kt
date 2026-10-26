package com.wulinlee.musicmeta

/** Avoid recently played items in shuffle mode, with a safe fallback for very short queues. */
internal fun <T> shuffleCandidates(
    items: List<T>,
    currentKey: String,
    recentKeys: Set<String>,
    keyOf: (T) -> String,
): List<T> {
    val alternatives = items.filter { keyOf(it) != currentKey }
    if (alternatives.isEmpty()) return emptyList()
    val fresh = alternatives.filterNot { keyOf(it) in recentKeys }
    return fresh.ifEmpty { alternatives }
}

/**
 * For paged playlists, recent positions are remembered too, so unloaded pages can avoid replaying
 * just-played songs even before their track IDs have been loaded into memory.
 */
internal fun shuffleCandidatePositions(
    totalCount: Int,
    currentIndex: Int,
    recentPositions: Set<Int>,
    recentTrackKeys: Set<String>,
    trackKeyAt: (Int) -> String?,
): List<Int> {
    val alternatives = (0 until totalCount.coerceAtLeast(0)).filter { it != currentIndex }
    if (alternatives.isEmpty()) return emptyList()
    val fresh = alternatives.filter { position ->
        position !in recentPositions && trackKeyAt(position)?.let { it !in recentTrackKeys } != false
    }
    return fresh.ifEmpty { alternatives }
}

internal fun findPlaylistTrackPosition(
    trackId: Long,
    playlist: PublicPlaylist,
    pageCache: Map<String, List<MusicTrack>>,
    mode: String,
): Int? {
    val fromBase = playlist.tracks.indexOfFirst { it.id == trackId }
    if (fromBase >= 0) return fromBase
    val prefix = "$mode:${playlist.id}:"
    return pageCache.entries.asSequence()
        .filter { it.key.startsWith(prefix) }
        .mapNotNull { entry ->
            val parts = entry.key.split(":")
            val pageSize = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            val page = parts.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
            val localIndex = entry.value.indexOfFirst { it.id == trackId }
            if (localIndex < 0) null else (page - 1) * pageSize + localIndex
        }
        .minOrNull()
}

internal fun playlistTrackAtPosition(
    position: Int,
    playlist: PublicPlaylist,
    pageCache: Map<String, List<MusicTrack>>,
    mode: String,
): MusicTrack? {
    if (position < 0) return null
    playlist.tracks.getOrNull(position)?.let { return it }
    val prefix = "$mode:${playlist.id}:"
    return pageCache.entries.asSequence()
        .filter { it.key.startsWith(prefix) }
        .mapNotNull { entry ->
            val parts = entry.key.split(":")
            val pageSize = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            val page = parts.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
            val localIndex = position - (page - 1) * pageSize
            entry.value.getOrNull(localIndex)
        }
        .firstOrNull()
}


/** Keep a bounded LRU-style history in memory; callers own the state and never persist it. */
internal fun rememberRecentTrackKeys(
    previousKeys: List<String>,
    sourceKey: String,
    playlistMode: String?,
    playlistId: Long?,
    trackId: Long?,
    maxEntries: Int = 12,
): List<String> {
    val additions = buildList {
        if (sourceKey.isNotBlank()) add(sourceKey)
        if (playlistMode != null && playlistId != null && trackId != null && playlistId > 0L && trackId > 0L) {
            add(MusicMetaPersistence.playlistTrackKey(playlistMode, playlistId, trackId))
        }
    }
    return (previousKeys.filterNot { it in additions } + additions)
        .filter(String::isNotBlank)
        .distinct()
        .takeLast(maxEntries.coerceAtLeast(1))
}
