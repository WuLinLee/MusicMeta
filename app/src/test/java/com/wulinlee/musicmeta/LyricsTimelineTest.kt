package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTimelineTest {
    @Test
    fun followsTranslationTimestampInsteadOfStickingToFirstLine() {
        val translated = "[00:01.00]第一句\n[00:05.00]第二句"
        assertEquals("第一句", currentLyricLine(translated, "", 2_000, isTranslation = true))
        assertEquals("第二句", currentLyricLine(translated, "", 6_000, isTranslation = true))
    }

    @Test
    fun borrowsOriginalTimelineOnlyWhenUntimedTranslationHasMatchingLineCount() {
        val original = "[00:01.00]First\n[00:05.00]Second\n[00:09.00]Third"
        val translated = "第一句\n第二句\n第三句"
        assertEquals("第一句", currentLyricLine(translated, original, 2_000, isTranslation = true))
        assertEquals("第二句", currentLyricLine(translated, original, 6_000, isTranslation = true))
        assertEquals("第三句", currentLyricLine(translated, original, 10_000, isTranslation = true))
    }

    @Test
    fun explainsUntimedTranslationsThatCannotBePairedWithoutDeletingTheirSourceText() {
        val original = "[00:01.00]作曲：张三\n[00:05.00]Second"
        val translated = "译文第一行\n译文第二行\n译文第三行"
        assertEquals(UNTITLED_TRANSLATION_SYNC_HINT, currentLyricLine(translated, original, 6_000, isTranslation = true))
        assertEquals("作曲：张三", currentLyricLine(original, original, 2_000))
        assertEquals("第二行", currentLyricLine("[00:01.00]第一行\n[00:05.00]第二行", "", 6_000))
    }

    @Test
    fun retainsCreditLinesAsNormalTimedLyrics() {
        val parsed = parseTimedLyricLines("[00:01.00]作曲：张三\n[00:05.00]编曲：李四")
        assertEquals(listOf("作曲：张三", "编曲：李四"), parsed.map { it.text })
    }
}
