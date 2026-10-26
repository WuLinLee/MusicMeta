package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSourceConfigTest {
    @Test
    fun defaultLimitMatchesGdStudioDocumentation() {
        // GdStudioRequestLimiter exposes its configured ceiling through a testable helper.
        assertEquals(50, GdStudioRequestLimiter.maxRequestsForTests())
    }

    @Test
    fun defaultsToGdStudioAndNetEase() {
        assertEquals(MusicSourceConfig.BACKEND_GD_STUDIO, MusicSourceConfig.DEFAULT_BACKEND)
        assertEquals("netease", MusicSourceConfig.DEFAULT_PLATFORM)
        assertEquals("netease", MusicSourceConfig.normalizePlatform("wyy"))
    }

    @Test
    fun gdStudioOptionsMatchCurrentlyDocumentedStableSources() {
        val ids = MusicSourceConfig.platformsFor(MusicSourceConfig.BACKEND_GD_STUDIO).map { it.id }
        assertEquals(listOf("netease", "joox", "bilibili"), ids)
        assertEquals("netease", MusicSourceConfig.platformForBackend(MusicSourceConfig.BACKEND_GD_STUDIO, "tencent"))
    }

    @Test
    fun flacPlatformRemainsAvailableEvenThoughGdStudioDoesNotListIt() {
        assertEquals("kuwo", MusicSourceConfig.normalizePlatform("kuwo"))
        assertEquals("kuwo", MusicSourceConfig.platformForBackend(MusicSourceConfig.BACKEND_FLAC_SITE, "kuwo"))
        assertEquals("netease", MusicSourceConfig.platformForBackend(MusicSourceConfig.BACKEND_GD_STUDIO, "kuwo"))
    }

    @Test
    fun gdStudioHttpErrorShowsSanitizedServerReason() {
        val message = formatGdStudioHttpErrorMessage(
            400,
            "source unavailable at https://private.example/path?token=secret",
        )
        assertTrue(message.contains("HTTP 400"))
        assertTrue(message.contains("source unavailable"))
        assertFalse(message.contains("https://"))
        assertFalse(message.contains("token=secret"))
    }

    @Test
    fun flacSiteSongNotFoundErrorGivesRecoverySteps() {
        val message = formatFlacSiteApiError(
            -1,
            "获取下载地址失败，Song not found or invalid response",
            "下载地址解析",
        )
        assertTrue(message.orEmpty().contains("重新搜索"))
        assertTrue(message.orEmpty().contains("切换 API 服务"))
    }

    @Test
    fun expandedLyricsPanelChoosesOppositeSideFromCover() {
        val coverWidth = 64
        val lyricsWidth = 260
        val gap = 14
        assertTrue(FloatingOverlayPlacement.lyricsPanelOnRight(8, coverWidth, 1080))
        assertEquals(8, FloatingOverlayPlacement.windowOriginX(true, 8, lyricsWidth, gap, true))
        assertFalse(FloatingOverlayPlacement.lyricsPanelOnRight(900, coverWidth, 1080))
        assertEquals(626, FloatingOverlayPlacement.windowOriginX(true, 900, lyricsWidth, gap, false))
    }

    @Test
    fun flacAdapterOnlyOffersImplementedPlatforms() {
        val ids = MusicSourceConfig.platformsFor(MusicSourceConfig.BACKEND_FLAC_SITE).map { it.id }
        assertEquals(listOf("netease", "kuwo"), ids)
        assertEquals("wyy", MusicSourceConfig.toFlacSitePlatform("netease"))
        assertEquals("kuwo", MusicSourceConfig.toFlacSitePlatform("kuwo"))
    }

    @Test
    fun expandedOverlayBoundsStayWithinScreenAfterDisplayResize() {
        val coverWidth = 64
        val gap = 14
        val margin = 8
        for (displayWidth in listOf(360, 720, 1080)) {
            val groupWidth = minOf(356, (displayWidth - 16).coerceAtLeast(120))
            val lyricsWidth = (groupWidth - 78).coerceAtLeast(48)

            val rightMin = FloatingOverlayPlacement.minimumCoverX(
                true, true, lyricsWidth, gap, margin,
            )
            val rightMax = FloatingOverlayPlacement.maximumCoverX(
                true, true, displayWidth, groupWidth, coverWidth, margin,
            ).coerceAtLeast(rightMin)
            assertEquals(margin, rightMin)
            assertTrue(rightMax + groupWidth <= displayWidth - margin)

            val leftMin = FloatingOverlayPlacement.minimumCoverX(
                true, false, lyricsWidth, gap, margin,
            )
            val leftMax = FloatingOverlayPlacement.maximumCoverX(
                true, false, displayWidth, groupWidth, coverWidth, margin,
            ).coerceAtLeast(leftMin)
            val leftOrigin = FloatingOverlayPlacement.windowOriginX(
                true, leftMin, lyricsWidth, gap, false,
            )
            assertTrue(leftOrigin >= margin)
            assertTrue(leftOrigin + groupWidth <= displayWidth - margin)
            assertTrue(leftMax + coverWidth <= displayWidth - margin)
        }
    }

    @Test
    fun rateLimitExceptionProvidesRecoverableDelay() {
        val error = GdStudioRateLimitException(22_001L)
        assertEquals(23L, error.retryAfterSeconds)
        assertTrue(error.message.orEmpty().contains("GD Studio 请求较多"))
    }

    @Test
    fun pageSizesReuseSameHundredTrackBatch() {
        val firstBatch = "playlist:123:100:1"
        assertEquals(firstBatch, playlistBatchCacheKey("playlist", "123", 25, 1))
        assertEquals(firstBatch, playlistBatchCacheKey("playlist", "123", 25, 4))
        assertEquals(firstBatch, playlistBatchCacheKey("playlist", "123", 50, 2))
        assertEquals(firstBatch, playlistBatchCacheKey("playlist", "123", 100, 1))
        assertEquals("playlist:123:100:2", playlistBatchCacheKey("playlist", "123", 25, 5))
        assertEquals("playlist:123:100:2", playlistBatchCacheKey("playlist", "123", 50, 3))
    }
}
