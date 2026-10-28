package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingOverlayPlacementTest {
    @Test
    fun defaultLyricsPanelStaysCompactAndLargerFontsGetAdaptiveHeight() {
        assertEquals(70, FLOATING_LYRICS_PANEL_HEIGHT_DP)
        assertEquals(48f, floatingLyricsTextAreaHeightDp(), 0f)
        assertTrue(
            floatingLyricsTextAreaHeightDp(MAX_FLOATING_LYRICS_FONT_SIZE_SP) >=
                MAX_FLOATING_LYRICS_FONT_SIZE_SP * 1.12f * 2f,
        )
    }

    @Test
    fun preferredLyricsWidthIsScaledByAnotherFactorOfOnePointFifteen() {
        assertEquals(441, FloatingOverlayPlacement.scaledPreferredLyricsWidthPx(356, 78, 1.587f))
    }

    @Test
    fun lyricsAndControlsReserveTheSameWidthsOnEitherSideOfTheCover() {
        assertEquals(200, FloatingOverlayPlacement.lyricsWidthPx(400, 100, 64, 334, 4, 4, true, 28))
        assertEquals(200, FloatingOverlayPlacement.lyricsWidthPx(400, 236, 64, 334, 4, 4, false, 28))
    }

    @Test
    fun lyricsGapNeverExceedsAvailableSpace() {
        assertEquals(4, FloatingOverlayPlacement.panelGapPx(400, 100, 64, 4, 4, true))
        assertEquals(0, FloatingOverlayPlacement.panelGapPx(200, 140, 64, 4, 4, true))
        assertEquals(0, FloatingOverlayPlacement.panelGapPx(200, 4, 64, 4, 4, false))
    }

    @Test
    fun expandedWindowIncludesControlsAndGapButCollapsedWindowDoesNot() {
        assertEquals(296, FloatingOverlayPlacement.windowWidthPx(64, 200, 4, true, 28))
        assertEquals(64, FloatingOverlayPlacement.windowWidthPx(64, 200, 4, false, 28))
    }

    @Test
    fun windowOriginMirrorsWithLyricsSide() {
        assertEquals(4, FloatingOverlayPlacement.windowOriginX(236, 200, 4, false, true, 28))
        assertEquals(100, FloatingOverlayPlacement.windowOriginX(100, 200, 4, true, true, 28))
        assertEquals(236, FloatingOverlayPlacement.windowOriginX(236, 200, 4, false, false, 28))
    }

    @Test
    fun centeredExpandedGroupHonorsControlsAndScreenMargins() {
        assertEquals(284, FloatingOverlayPlacement.centeredCoverXForExpandedGroup(400, 64, 200, 4, 4, 28))
        assertEquals(172, FloatingOverlayPlacement.centeredCoverXForExpandedGroup(240, 64, 334, 4, 4, 28))
        assertEquals(4, FloatingOverlayPlacement.windowOriginX(172, 136, 4, false, true, 28))
    }

    @Test
    fun lyricsWidthCanShrinkToZeroAndSideDecisionMirrors() {
        assertEquals(0, FloatingOverlayPlacement.lyricsWidthPx(200, 120, 64, 334, 4, 4, true, 28))
        assertTrue(FloatingOverlayPlacement.lyricsPanelOnRight(50, 64, 400))
        assertFalse(FloatingOverlayPlacement.lyricsPanelOnRight(300, 64, 400))
    }

    @Test
    fun collapsedSideControlsKeepFixedAnchorsAndLyricsUseAnIndependentWindow() {
        assertEquals(164, FloatingOverlayPlacement.collapsedControlsWindowX(100, 64, 28, true))
        assertEquals(208, FloatingOverlayPlacement.collapsedControlsWindowX(236, 64, 28, false))
        assertEquals(196, FloatingOverlayPlacement.lyricsPanelWindowX(100, 64, 28, 4, 200, true))
        assertEquals(4, FloatingOverlayPlacement.lyricsPanelWindowX(236, 64, 28, 4, 200, false))
    }
}
