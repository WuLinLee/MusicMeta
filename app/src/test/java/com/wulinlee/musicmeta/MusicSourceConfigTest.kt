package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSourceConfigTest {
    @Test
    fun mediaCardPrefersCurrentLyricForTitleAndFallsBackToSongTitle() {
        assertEquals("翻译歌词", mediaDisplayTitle("  翻译歌词  ", "歌曲名"))
        assertEquals("歌曲名", mediaDisplayTitle("   ", "歌曲名"))
        assertEquals("歌曲名", mediaDisplayTitle("", "歌曲名"))
        val longLyric = "这是一句很长很长的歌词，即使远远超过媒体卡片的宽度，也应当完整保存在媒体标题元数据中。"
        assertEquals(longLyric, mediaDisplayTitle(longLyric, "歌曲名"))
        assertEquals("关闭悬浮窗", overlayToggleLabel(true))
        assertEquals("开启悬浮窗", overlayToggleLabel(false))
    }

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
    fun gdStudioServerErrorsCanUseFallbackButRateLimitIsNotMisclassified() {
        assertTrue(
            isGdStudioTransientServiceError(
                java.io.IOException("GD Studio API 请求失败：HTTP 503"),
            ),
        )
        assertTrue(
            isGdStudioTransientServiceError(
                java.io.IOException("请求失败", java.io.IOException("HTTP 502")),
            ),
        )
        assertFalse(
            isGdStudioTransientServiceError(GdStudioRateLimitException(22_001L)),
        )
        assertFalse(
            isGdStudioTransientServiceError(java.io.IOException("HTTP 429")),
        )
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
        assertTrue(FloatingOverlayPlacement.lyricsPanelOnRight(8, 64, 1080))
        assertFalse(FloatingOverlayPlacement.lyricsPanelOnRight(900, 64, 1080))
    }

    @Test
    fun lyricsPanelPreferredWidthIsScaledByThirtyFivePercent() {
        assertEquals(375, FloatingOverlayPlacement.scaledPreferredLyricsWidthPx(356, 78, 1.35f))
        assertEquals(0, FloatingOverlayPlacement.scaledPreferredLyricsWidthPx(64, 78, 1.35f))
        assertEquals(88, FLOATING_OVERLAY_GROUP_HEIGHT_DP)
        assertEquals(64 + 2 + 22, FLOATING_OVERLAY_GROUP_HEIGHT_DP)
    }

    @Test
    fun expandedOverlayBoundsKeepCoverAnchoredAndStayWithinScreenAfterResize() {
        val coverWidth = 64
        val preferredGap = 14
        val margin = 4
        for (displayWidth in listOf(360, 720, 1080)) {
            val desiredLyricsWidth = FloatingOverlayPlacement.scaledPreferredLyricsWidthPx(356, 78, 1.35f)
            val coverPositions = listOf(
                margin,
                ((displayWidth - coverWidth) / 2).coerceAtLeast(margin),
                (displayWidth - margin - coverWidth).coerceAtLeast(margin),
            )
            for (coverX in coverPositions) {
                val onRight = FloatingOverlayPlacement.lyricsPanelOnRight(coverX, coverWidth, displayWidth)
                val gap = FloatingOverlayPlacement.panelGapPx(displayWidth, coverX, coverWidth, preferredGap, margin, onRight)
                val lyricsWidth = FloatingOverlayPlacement.lyricsWidthPx(
                    displayWidth, coverX, coverWidth, desiredLyricsWidth, gap, margin, onRight,
                )
                val collapsedWidth = FloatingOverlayPlacement.windowWidthPx(coverWidth, lyricsWidth, gap, false)
                val collapsedX = FloatingOverlayPlacement.windowOriginX(coverX, lyricsWidth, gap, onRight, false)
                assertEquals(coverWidth, collapsedWidth)
                assertEquals(coverX, collapsedX)
                val expandedWidth = FloatingOverlayPlacement.windowWidthPx(coverWidth, lyricsWidth, gap, true)
                val expandedX = FloatingOverlayPlacement.windowOriginX(coverX, lyricsWidth, gap, onRight, true)
                assertTrue("expanded left edge must remain visible", expandedX >= margin)
                assertTrue("expanded right edge must remain visible", expandedX + expandedWidth <= displayWidth - margin)
                val coverScreenX = if (onRight) expandedX else expandedX + expandedWidth - coverWidth
                assertEquals("cover must not jump when expanding", coverX, coverScreenX)
            }
        }
    }


    @Test
    fun lyricFontSliderKeepsDefaultAtCenterAndProvidesHalfToDoubleRange() {
        assertEquals(0.5f, floatingLyricsFontSliderPosition(DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP), 0.001f)
        assertEquals(MIN_FLOATING_LYRICS_FONT_SIZE_SP, floatingLyricsFontSizeFromSlider(0f), 0.01f)
        assertEquals(DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP, floatingLyricsFontSizeFromSlider(0.5f), 0.01f)
        assertEquals(MAX_FLOATING_LYRICS_FONT_SIZE_SP, floatingLyricsFontSizeFromSlider(1f), 0.01f)
        assertEquals(0f, floatingLyricsFontSliderPosition(MIN_FLOATING_LYRICS_FONT_SIZE_SP), 0.001f)
        assertEquals(1f, floatingLyricsFontSliderPosition(MAX_FLOATING_LYRICS_FONT_SIZE_SP), 0.001f)
    }

    @Test
    fun flacAdapterOnlyOffersImplementedPlatforms() {
        val ids = MusicSourceConfig.platformsFor(MusicSourceConfig.BACKEND_FLAC_SITE).map { it.id }
        assertEquals(listOf("netease", "kuwo"), ids)
        assertEquals("wyy", MusicSourceConfig.toFlacSitePlatform("netease"))
        assertEquals("kuwo", MusicSourceConfig.toFlacSitePlatform("kuwo"))
    }

    @Test
    fun gdStudioSearchUsesTitleFallbackOnlyWhenDistinct() {
        assertEquals(
            listOf("ray (超かぐや姫！ Version) Aimer", "ray (超かぐや姫！ Version)"),
            gdStudioSearchKeywords("ray (超かぐや姫！ Version) Aimer", "ray (超かぐや姫！ Version)"),
        )
        assertEquals(listOf("Same title"), gdStudioSearchKeywords("Same title", "Same title"))
        assertTrue(isGdStudioNoCandidatesError(java.io.IOException(formatGdStudioNoCandidatesMessage())))
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
