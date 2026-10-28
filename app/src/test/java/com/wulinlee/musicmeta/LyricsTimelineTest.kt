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
    fun mapsUntimedTranslationAcrossOriginalTimelineEvenWhenRowCountsDiffer() {
        val original = "[00:01.00]作曲：张三\n[00:05.00]原文第二行\n[00:09.00]原文第三行\n[00:13.00]编曲：李四"
        val translated = "译文第一行\n译文第二行"
        assertEquals("译文第一行", currentLyricLine(translated, original, 6_000, isTranslation = true))
        assertEquals("译文第二行", currentLyricLine(translated, original, 10_000, isTranslation = true))
        assertEquals("作曲：张三", currentLyricLine(original, original, 2_000))
        assertEquals("编曲：李四", currentLyricLine(original, original, 14_000))
        assertEquals("译文一", currentLyricLine("译文一\n译文二", "无时间戳原文\n第二行", 6_000, isTranslation = true))
        assertEquals("第二行", currentLyricLine("[00:01.00]第一行\n[00:05.00]第二行", "", 6_000))
    }

    @Test
    fun skipsUntimedCreditsWhenTimestampedLyricRowsAreAvailable() {
        val lyrics = "[00:01.00]歌曲第一句\n作曲：张三\n[00:05.00]歌曲第二句\n编曲：李四"
        assertEquals("歌曲第一句", currentLyricLine(lyrics, lyrics, 3_000))
        assertEquals("歌曲第二句", currentLyricLine(lyrics, lyrics, 7_000))
    }

    @Test
    fun fallsBackToFirstUntimedTranslationLineWhenNoTimelineCanBeUsed() {
        val translated = "译文第一句\n译文第二句"
        assertEquals("译文第一句", currentLyricLine(translated, "没有可用时间轴", 6_000, isTranslation = true))
    }

    @Test
    fun retainsCreditLinesAsNormalTimedLyrics() {
        val parsed = parseTimedLyricLines("[00:01.00]作曲：张三\n[00:05.00]编曲：李四")
        assertEquals(listOf("作曲：张三", "编曲：李四"), parsed.map { it.text })
    }
}
