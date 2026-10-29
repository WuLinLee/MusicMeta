package com.wulinlee.musicmeta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

class GdStudioApiTest {
    @Test
    fun searchRequestUsesMaintainedHostAndSignedGetQuery() {
        val params = linkedMapOf(
            "types" to "search",
            "source" to "netease",
            "name" to "涙が ほお&x",
            "count" to "20",
            "pages" to "1",
        )
        val url = GdStudioApiClient.apiUrl(GdStudioApiClient.PRIMARY_HOST)
        val signedUrl = GdStudioApiClient.buildSignedUrl(
            GdStudioApiClient.PRIMARY_HOST,
            params,
            "1793255432",
        )

        assertEquals("music.gdstudio.xyz", url.host)
        assertEquals("/api.php", url.encodedPath)
        assertEquals("https", signedUrl.scheme)
        assertEquals("music.gdstudio.xyz", signedUrl.host)
        assertEquals("/api.php", signedUrl.encodedPath)
        assertEquals("search", signedUrl.queryParameter("types"))
        assertEquals("netease", signedUrl.queryParameter("source"))
        assertEquals("涙が ほお&x", signedUrl.queryParameter("name"))
        assertEquals("9ACA8505", signedUrl.queryParameter("s"))
        assertTrue(signedUrl.queryParameter("s").orEmpty().matches(Regex("[A-F0-9]{8}")))
    }

    @Test
    fun signaturesChangeWhenSearchPayloadChanges() {
        fun signed(keyword: String): String? = GdStudioApiClient.buildSignedUrl(
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
        assertEquals("2026.09.25", GdStudioApiClient.API_VERSION)
        assertEquals("20260925", GdStudioApiClient.normalizeVersion("2026.09.25"))
        assertEquals(
            "2026.10.29",
            GdStudioApiClient.parseVersionFromPlayerScript(
                """const mkPlayer = { version: "2026.10.29" };""",
            ),
        )
        assertEquals(
            "2026.11.03",
            GdStudioApiClient.parseVersionFromPlayerScript(
                """window.mkPlayer = { name: "player", version: '2026.11.03' };""",
            ),
        )
        assertEquals(null, GdStudioApiClient.parseVersionFromPlayerScript("const player = {};"))
    }

    @Test
    fun hiCnTrackIdPrefersDocumentedIdOverLegacyAliases() {
        val documented = mapOf("id" to "official-id", "rid" to "legacy-id", "songid" to "old-id")
        assertEquals("official-id", flacSiteSongId(documented))

        val legacy = mapOf("rid" to "legacy-id", "songid" to "old-id")
        assertEquals("legacy-id", flacSiteSongId(legacy))
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
