package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GdStudioApiTest {
    @Test
    fun searchRequestUsesMaintainedHostAndContainsSignature() {
        val params = linkedMapOf(
            "types" to "search",
            "source" to "netease",
            "name" to "涙が ほお&x",
            "count" to "20",
            "pages" to "1",
        )
        val url = GdStudioApiClient.buildSignedUrl(
            GdStudioApiClient.PRIMARY_HOST,
            params,
            "1793255432",
        )
        assertEquals("music.gdstudio.xyz", url.host)
        assertEquals("/api.php", url.encodedPath)
        assertEquals("search", url.queryParameter("types"))
        assertEquals("netease", url.queryParameter("source"))
        assertEquals("涙が ほお&x", url.queryParameter("name"))
        assertEquals("0AB1D53C", url.queryParameter("s"))
        assertTrue(url.queryParameter("s").orEmpty().matches(Regex("[A-F0-9]{8}")))
    }

    @Test
    fun signaturesChangeWhenSearchPayloadChanges() {
        fun signed(keyword: String) = GdStudioApiClient.buildSignedUrl(
            GdStudioApiClient.PRIMARY_HOST,
            linkedMapOf("types" to "search", "source" to "netease", "name" to keyword, "count" to "20", "pages" to "1"),
            "1793255432",
        ).queryParameter("s")
        assertNotEquals(signed("涙がほおを流れても"), signed("あさきゆめみし"))
    }

    @Test
    fun gdstudioAudioLookupUsesUrlIdWhenAvailable() {
        val song = FlacSiteSong(
            id = "track-id",
            platform = "gd:netease",
            title = "测试曲",
            artist = "测试歌手",
            album = "",
            sign = "",
            time = "",
            coverUrl = "",
            urlId = "url-id",
        )
        assertEquals("url-id", gdStudioUrlLookupId(song))
        assertEquals("track-id", gdStudioUrlLookupId(song.copy(urlId = "")))
    }

    @Test
    fun encodeURIComponentMatchesJavascriptEncodingForJapaneseAndPunctuation() {
        assertEquals(
            "%E6%B6%99%E3%81%8C%20%E3%81%BB%E3%81%8A%26x",
            GdStudioApiClient.encodeURIComponent("涙が ほお&x"),
        )
        assertEquals("%21%27%28%29%2A", GdStudioApiClient.encodeURIComponent("!'()*"))
    }

    @Test
    fun flacSongNotFoundIsNotClassifiedAsWebVerification() {
        val message = formatFlacSiteApiError(
            -1,
            "接口请求失败：Song not found or invalid response",
            "下载地址解析",
        )
        assertTrue(message.orEmpty().contains("候选歌曲"))
        assertTrue(isFlacInvalidCandidateError(java.io.IOException(message)))
    }

    @Test
    fun versionNormalizationMatchesTheWebsiteSignatureProtocol() {
        assertEquals("20260925", GdStudioApiClient.normalizeVersion("2026.09.25"))
        assertEquals(
            "2026.10.29",
            GdStudioApiClient.parseVersionFromPlayerScript(
                """const mkPlayer = { version: "2026.10.29" };""",
            ),
        )
        assertEquals(null, GdStudioApiClient.parseVersionFromPlayerScript("const player = {};"))
    }

    @Test
    fun genericHiCnMinusOneErrorsAreRetriedWithoutAssumingWebVerification() {
        assertTrue(
            isFlacRetryableResolutionError(
                java.io.IOException("下载站接口暂时无法处理请求（-1）。请重新搜索并重试。"),
            ),
        )
        assertTrue(
            isFlacRetryableResolutionError(
                java.io.IOException("下载站无法解析该候选歌曲的下载地址。"),
            ),
        )
        assertEquals(
            false,
            isFlacRetryableResolutionError(
                java.io.IOException("下载站访问验证未通过（HTTP 468）。"),
            ),
        )
    }

}
