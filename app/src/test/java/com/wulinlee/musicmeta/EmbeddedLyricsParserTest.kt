package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedLyricsParserTest {
    @Test
    fun splitsInterleavedOriginalAndTranslationFromSameTimestampLrc() {
        val result = EmbeddedLyricsParser.split(
            "[00:01.00]Hello\n[00:01.00]你好\n[00:02.50]World\n[00:02.50]世界"
        )
        assertEquals("[00:01.00]Hello\n[00:02.50]World", result.original)
        assertEquals("[00:01.00]你好\n[00:02.50]世界", result.translated)
    }

    @Test
    fun leavesSingleLanguageLrcAlone() {
        val raw = "[00:01.00]Hello\n[00:02.50]World"
        val result = EmbeddedLyricsParser.split(raw)
        assertEquals(raw, result.original)
        assertEquals("", result.translated)
    }

    @Test
    fun supportsPlainTextEmbeddedLyrics() {
        val raw = "First lyric line\nSecond lyric line"
        val result = EmbeddedLyricsParser.split(raw)
        assertEquals(raw, result.original)
        assertEquals("", result.translated)
    }
}
