package com.wulinlee.musicmeta

import java.util.LinkedHashMap

internal object MusicSourceSearchCache {
    private const val TTL_MILLIS = 3 * 60 * 1000L
    private const val MAX_ENTRIES = 80

    private data class Entry(val expiresAt: Long, val songs: List<FlacSiteSong>)
    private val cache = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): List<FlacSiteSong>? {
        val entry = cache[key] ?: return null
        if (System.nanoTime() / 1_000_000L >= entry.expiresAt) {
            cache.remove(key)
            return null
        }
        return entry.songs.toList()
    }

    @Synchronized
    fun put(key: String, songs: List<FlacSiteSong>) {
        if (songs.isNotEmpty()) {
            cache[key] = Entry(System.nanoTime() / 1_000_000L + TTL_MILLIS, songs.toList())
        }
    }
}
