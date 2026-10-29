package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSourceConfigTest {
    @Test
    fun overlayToggleLabelReflectsGlobalSetting() {
        assertEquals("关闭悬浮窗", overlayToggleLabel(true))
        assertEquals("开启悬浮窗", overlayToggleLabel(false))
    }

    @Test
    fun flacSiteDownloadUrlCacheKeysIncludeSignatureAndQuality() {
        val song = FlacSiteSong(
            id = "123",
            platform = "kuwo",
            title = "测试歌曲",
            artist = "测试歌手",
            album = "测试专辑",
            sign = "sig-a",
            time = "1700000000",
            coverUrl = "",
        )
        val key = flacSiteDownloadUrlCacheKey(song, "flac", "2000")
        assertEquals(key, flacSiteDownloadUrlCacheKey(song, "FLAC", "2000"))
        assertFalse(key == flacSiteDownloadUrlCacheKey(song, "mp3", "320"))
        assertFalse(
            key == flacSiteDownloadUrlCacheKey(song.copy(sign = "sig-b"), "flac", "2000"),
        )
        assertEquals(50 * 60 * 1000L, FlacSiteDownloadUrlCache.TTL_MILLIS)
        FlacSiteDownloadUrlCache.put(key, "https://cdn.example/test.flac")
        assertEquals("https://cdn.example/test.flac", FlacSiteDownloadUrlCache.get(key))
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
        assertEquals(listOf("netease", "kuwo", "joox", "bilibili"), ids)
        assertEquals("netease", MusicSourceConfig.platformForBackend(MusicSourceConfig.BACKEND_GD_STUDIO, "tencent"))
    }



    @Test
    fun flacPlatformRemainsAvailableEvenThoughGdStudioDoesNotListIt() {
        assertEquals("kuwo", MusicSourceConfig.normalizePlatform("kuwo"))
        assertEquals("kuwo", MusicSourceConfig.platformForBackend(MusicSourceConfig.BACKEND_FLAC_SITE, "kuwo"))
        assertEquals("kuwo", MusicSourceConfig.platformForBackend(MusicSourceConfig.BACKEND_GD_STUDIO, "kuwo"))
    }

    @Test
    fun platformLabelsCoverBothBackends() {
        assertEquals("酷我", MusicSourceConfig.labelForPlatform("kuwo"))
        assertEquals("网易云", MusicSourceConfig.labelForPlatform("wyy"))
        assertEquals("JOOX", MusicSourceConfig.labelForPlatform("joox"))
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
    fun flacApiBusinessErrorDistinguishesHttpAndApiLayers() {
        val message = formatFlacSiteApiError(-1, "接口请求失败", "下载地址解析")
        assertTrue(message.orEmpty().contains("HTTP 2xx"))
        assertTrue(message.orEmpty().contains("API code=-1"))
        assertTrue(message.orEmpty().contains("下载地址解析"))
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
    fun lyricsPanelPreferredWidthIsScaledByTwentyPercent() {
        assertEquals(334, FloatingOverlayPlacement.scaledPreferredLyricsWidthPx(356, 78))
        assertEquals(0, FloatingOverlayPlacement.scaledPreferredLyricsWidthPx(64, 78))
    }

    @Test
    fun expandedOverlayBoundsKeepCoverAnchoredAndStayWithinScreenAfterResize() {
        val coverWidth = 64
        val preferredGap = 2
        val margin = 4
        val sideControlsWidth = 28
        for (displayWidth in listOf(360, 720, 1080)) {
            val desiredGroupWidth = minOf(368, (displayWidth - 8).coerceAtLeast(64))
            val desiredLyricsWidth = (desiredGroupWidth - coverWidth - preferredGap - sideControlsWidth).coerceAtLeast(0)
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
                    reservedSideControlsPx = sideControlsWidth,
                )
                val collapsedWidth = FloatingOverlayPlacement.windowWidthPx(coverWidth, lyricsWidth, gap, false)
                val collapsedX = FloatingOverlayPlacement.windowOriginX(coverX, lyricsWidth, gap, onRight, false)
                assertEquals(coverWidth, collapsedWidth)
                assertEquals(coverX, collapsedX)
                val expandedWidth = FloatingOverlayPlacement.windowWidthPx(
                    coverWidth, lyricsWidth, gap, true, sideControlsWidth,
                )
                val expandedX = FloatingOverlayPlacement.windowOriginX(
                    coverX, lyricsWidth, gap, onRight, true, sideControlsWidth,
                )
                assertTrue("expanded left edge must remain visible", expandedX >= margin)
                assertTrue("expanded right edge must remain visible", expandedX + expandedWidth <= displayWidth - margin)
                val coverScreenX = if (onRight) expandedX else expandedX + expandedWidth - coverWidth
                assertEquals("cover must not jump when expanding", coverX, coverScreenX)
            }
        }
    }


    @Test
    fun defaultOverlayCentersTheWholeGroupAndMaximizesLyricsWidth() {
        val coverWidth = 64
        val desiredLyricsWidth = 334
        val gap = 4
        val margin = 4
        val controlsWidth = 28
        for (displayWidth in listOf(360, 390, 720, 1080)) {
            val coverX = FloatingOverlayPlacement.centeredCoverXForExpandedGroup(
                displayWidth = displayWidth,
                coverWidthPx = coverWidth,
                desiredLyricsWidthPx = desiredLyricsWidth,
                preferredGapPx = gap,
                marginPx = margin,
                sideControlsWidthPx = controlsWidth,
            )
            val onRight = FloatingOverlayPlacement.lyricsPanelOnRight(coverX, coverWidth, displayWidth)
            assertFalse("The centered group should place the cover to the right of the lyrics", onRight)
            val actualGap = FloatingOverlayPlacement.panelGapPx(
                displayWidth, coverX, coverWidth, gap, margin, false,
            )
            val lyricsWidth = FloatingOverlayPlacement.lyricsWidthPx(
                displayWidth, coverX, coverWidth, desiredLyricsWidth,
                actualGap, margin, false, reservedSideControlsPx = controlsWidth,
            )
            assertEquals(
                minOf(desiredLyricsWidth, (displayWidth - 2 * margin - coverWidth - gap - controlsWidth).coerceAtLeast(0)),
                lyricsWidth,
            )
            val left = coverX - lyricsWidth - actualGap - controlsWidth
            val right = coverX + coverWidth
            assertTrue("expanded group must fit the left margin", left >= margin)
            assertTrue("expanded group must fit the right margin", right <= displayWidth - margin)
            assertEquals("whole group should be centered", displayWidth, left + right)
        }
    }

    @Test
    fun overlayControlsStayCloseToCoverAndKeepUsableTouchArea() {
        assertEquals(2, FloatingOverlayPlacement.panelGapPx(
            displayWidth = 1080,
            coverX = 200,
            coverWidthPx = 192,
            preferredGapPx = 2,
            marginPx = 12,
            lyricsOnRight = true,
        ))
    }

    @Test
    fun defaultFloatingLyricsFontMigratesTo1728spAndPreservesCustomSizes() {
        assertEquals(17.28f, DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP, 0.001f)
        assertEquals(15.04f, PREVIOUS_DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP, 0.001f)
        assertEquals(
            17.28f,
            floatingLyricsFontSizeAfterDefaultMigration(null, migrationComplete = false),
            0.001f,
        )
        assertEquals(
            17.28f,
            floatingLyricsFontSizeAfterDefaultMigration(
                PREVIOUS_DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP,
                migrationComplete = false,
            ),
            0.001f,
        )
        assertEquals(
            16.5f,
            floatingLyricsFontSizeAfterDefaultMigration(16.5f, migrationComplete = false),
            0.001f,
        )
        assertEquals(
            PREVIOUS_DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP,
            floatingLyricsFontSizeAfterDefaultMigration(
                PREVIOUS_DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP,
                migrationComplete = true,
            ),
            0.001f,
        )
    }

    @Test
    fun defaultFloatingLyricsPanelFitsTwoDefaultLinesAndProgressArea() {
        assertEquals(70, FLOATING_LYRICS_PANEL_HEIGHT_DP)
        assertEquals(48f, floatingLyricsTextAreaHeightDp(), 0.001f)
        assertTrue(
            floatingLyricsTextAreaHeightDp() >=
                DEFAULT_FLOATING_LYRICS_FONT_SIZE_SP * 1.12f * 2f,
        )
    }

    @Test
    fun largerLyricsFontExpandsPanelOnlyAsMuchAsTwoLinesRequire() {
        assertEquals(100, floatingLyricsPanelHeightDp(MAX_FLOATING_LYRICS_FONT_SIZE_SP))
        assertTrue(
            floatingLyricsTextAreaHeightDp(MAX_FLOATING_LYRICS_FONT_SIZE_SP) >=
                MAX_FLOATING_LYRICS_FONT_SIZE_SP * 1.12f * 2f,
        )
    }

    @Test
    fun localOverlayLocateUsesCurrentDisplayedOrderRatherThanScanOrder() {
        val displayedUris = listOf("content://music/b", "content://music/c", "content://music/a")
        assertEquals(2, localTrackIndexForOverlayUri(displayedUris, "content://music/a"))
        assertEquals(-1, localTrackIndexForOverlayUri(displayedUris, "content://music/missing"))
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
