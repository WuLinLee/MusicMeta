package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcMergerTest {
    @Test fun convertsNetEaseWordByWordLyricsToStandardLrc() {
        assertEquals(
            "[00:01.23]Hello world\n[00:02.00]Next",
            LrcMerger.normalizeWordByWordToLrc(
                "[1234,800](1234,200,0)Hello(1434,200,0) world\n[2000,400](2000,400,0)Next"
            )
        )
    }

    @Test fun keepsStandardLrcWhenInputIsAlreadyLrc() {
        assertEquals(
            "[00:01.00]Hello\n[00:02.00]World",
            LrcMerger.normalizeWordByWordToLrc("[00:01.00]Hello\n[00:02.00]World")
        )
    }

    @Test fun translationCannotReplaceMissingOriginalLyrics() {
        assertEquals("", LrcMerger.merge("", "[00:01.00]你好"))
    }

    @Test fun mergesTranslationAtTheSameTimestamp() {
        assertEquals(
            "[00:01.00]Hello\n[00:01.00]你好\n[00:02.50]World\n[00:02.50]世界",
            LrcMerger.merge("[00:01.00]Hello\n[00:02.50]World", "[00:01.00]你好\n[00:02.50]世界")
        )
    }
    @Test fun keepsOriginalWhenTranslationMissing() {
        assertEquals("[00:01.00]Hello", LrcMerger.merge("[00:01.00]Hello", ""))
    }
    @Test fun mergesEachTimestampWhenTranslationIsAvailable() {
        assertEquals(
            "[00:01.00]Hi\n[00:01.00]你好\n[00:02.00]Hi\n[00:02.00]世界",
            LrcMerger.merge("[00:01.00][00:02.00]Hi", "[00:01.00]你好\n[00:02.00]世界")
        )
    }
}
