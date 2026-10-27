package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMusicSortTest {
    private data class Item(val title: String, val modifiedAtMs: Long)

    @Test
    fun cyclesNewestOldestAlphabeticalAndBackToNewest() {
        assertEquals(LocalMusicSortMode.OLDEST_FIRST, LocalMusicSortMode.NEWEST_FIRST.next())
        assertEquals(LocalMusicSortMode.TITLE_A_TO_Z, LocalMusicSortMode.OLDEST_FIRST.next())
        assertEquals(LocalMusicSortMode.NEWEST_FIRST, LocalMusicSortMode.TITLE_A_TO_Z.next())
    }

    @Test
    fun newestAndOldestUseFileModificationTimeAndPlaceUnknownDatesLast() {
        val items = listOf(Item("middle", 200L), Item("newest", 300L), Item("oldest", 100L), Item("unknown date", 0L))
        val newest = sortLocalMusicItems(items, LocalMusicSortMode.NEWEST_FIRST, { it.modifiedAtMs }, { it.title })
        val oldest = sortLocalMusicItems(items, LocalMusicSortMode.OLDEST_FIRST, { it.modifiedAtMs }, { it.title })
        assertEquals(listOf("newest", "middle", "oldest", "unknown date"), newest.map { it.title })
        assertEquals(listOf("oldest", "middle", "newest", "unknown date"), oldest.map { it.title })
    }

    @Test
    fun alphabeticalModeMatchesLegacyTitleOrder() {
        val items = listOf(Item("zebra", 300L), Item("Alpha", 100L), Item("beta", 200L))
        val sorted = sortLocalMusicItems(items, LocalMusicSortMode.TITLE_A_TO_Z, { it.modifiedAtMs }, { it.title })
        assertEquals(listOf("Alpha", "beta", "zebra"), sorted.map { it.title })
    }
}
