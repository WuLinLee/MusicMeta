package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueuePolicyTest {
    @Test
    fun shufflePrefersTracksNotInRecentHistory() {
        val queue = listOf("a", "b", "c", "d")
        assertEquals(
            listOf("d"),
            shuffleCandidates(queue, "c", setOf("a", "b", "c"), { it }),
        )
    }

    @Test
    fun shuffleFallsBackWhenEveryAlternativeWasRecentlyPlayed() {
        val queue = listOf("a", "b", "c")
        assertEquals(
            listOf("a", "b"),
            shuffleCandidates(queue, "c", setOf("a", "b", "c"), { it }),
        )
    }

    @Test
    fun shuffleNeverSelectsTheCurrentTrackWhenAlternativesExist() {
        val queue = listOf("same", "same", "other")
        assertTrue(shuffleCandidates(queue, "same", emptySet(), { it }).all { it != "same" })
    }

    @Test
    fun pagedShuffleAvoidsRecentPositionsAndKnownRecentTrackIds() {
        val candidates = shuffleCandidatePositions(
            totalCount = 6,
            currentIndex = 4,
            recentPositions = setOf(0, 1),
            recentTrackKeys = setOf("playlist:playlist:77:12"),
            trackKeyAt = { position ->
                when (position) {
                    2 -> "playlist:playlist:77:12"
                    else -> "track:$position"
                }
            },
        )
        assertEquals(listOf(3, 5), candidates)
    }

    @Test
    fun pagedShuffleFallsBackWhenAllAlternativePositionsAreRecent() {
        assertEquals(
            listOf(0, 1, 3),
            shuffleCandidatePositions(
                totalCount = 4,
                currentIndex = 2,
                recentPositions = setOf(0, 1, 3),
                recentTrackKeys = emptySet(),
                trackKeyAt = { "track:$it" },
            ),
        )
    }

    @Test
    fun recentHistoryIsBoundedAndMovesRepeatedTracksToNewestPosition() {
        val first = rememberRecentTrackKeys(emptyList(), "source:a", null, null, null, maxEntries = 3)
        val second = rememberRecentTrackKeys(first, "source:b", null, null, null, maxEntries = 3)
        val third = rememberRecentTrackKeys(second, "source:c", null, null, null, maxEntries = 3)
        val fourth = rememberRecentTrackKeys(third, "source:a", null, null, null, maxEntries = 3)
        assertEquals(listOf("source:b", "source:c", "source:a"), fourth)
    }

    @Test
    fun recentHistoryTracksPlaylistIdentityWithoutPersistingIt() {
        assertEquals(
            listOf("source:abc", "playlist:liked:55:99"),
            rememberRecentTrackKeys(emptyList(), "source:abc", "liked", 55L, 99L),
        )
    }


    @Test
    fun previewNavigationKeepsLikedPlaylistWhenUiIsOnDirectSearch() {
        assertEquals(
            "liked",
            previewPlaylistModeForTrack(
                currentPlaylistId = 41L,
                likedPlaylistId = 41L,
                publicPlaylistId = 99L,
                fallbackMode = "search",
            ),
        )
    }

    @Test
    fun previewNavigationKeepsPublicPlaylistWhenUiIsOnDirectSearch() {
        assertEquals(
            "playlist",
            previewPlaylistModeForTrack(
                currentPlaylistId = 99L,
                likedPlaylistId = 41L,
                publicPlaylistId = 99L,
                fallbackMode = "search",
            ),
        )
    }

    @Test
    fun previewNavigationUsesUiModeOnlyWhenSongHasNoKnownPlaylist() {
        assertEquals(
            "search",
            previewPlaylistModeForTrack(
                currentPlaylistId = null,
                likedPlaylistId = 41L,
                publicPlaylistId = 99L,
                fallbackMode = "search",
            ),
        )
    }

}