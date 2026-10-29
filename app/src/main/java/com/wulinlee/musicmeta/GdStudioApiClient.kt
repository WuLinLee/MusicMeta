package com.wulinlee.musicmeta

import java.io.IOException
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Current GD Studio web-client protocol, kept in one place so search, URL, cover and lyric
 * requests cannot silently drift between hosts or omit the required request signature.
 *
 * Protocol reference: musicdl's maintained GDStudioMusicClient (updated October 2026).
 */
internal object GdStudioApiClient {
    const val API_VERSION = "2026.09.25"
    const val PRIMARY_HOST = "music.gdstudio.xyz"
    internal val API_HOSTS = listOf(PRIMARY_HOST, "music.gdstudio.org")
    internal const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

    private data class TimeEntry(val prefix: String, val expiresAtElapsedMs: Long)
    private val timeCache = mutableMapOf<String, TimeEntry>()

    /** Host failover stays within GD Studio; it never silently changes API providers. */
    fun getText(
        client: OkHttpClient,
        params: Map<String, String>,
        ttlMillis: Long = 0L,
    ): String {
        var lastError: IOException? = null
        val errors = mutableListOf<IOException>()
        for (host in API_HOSTS) {
            try {
                val timePrefix = serverTimePrefix(client, host)
                val url = buildSignedUrl(host, params, timePrefix)
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Origin", "https://$host")
                    .header("Referer", "https://$host/")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .get()
                    .build()
                val body = GdStudioApiSupport.getText(client, request, ttlMillis).trim()
                apiPayloadError(body)?.let { throw IOException(it) }
                return body
            } catch (error: Exception) {
                val io = if (error is IOException) error else IOException(
                    "GD Studio 请求主机 $host 失败。",
                    error,
                )
                errors += io
                lastError = io
            }
        }
        val failure = lastError ?: IOException("GD Studio API 当前不可用。")
        errors.filterNot { it === failure }.forEach(failure::addSuppressed)
        throw failure
    }

    /** Exposed internally for deterministic request-shape tests. */
    internal fun buildSignedUrl(
        host: String,
        params: Map<String, String>,
        serverTime: String,
    ): HttpUrl {
        require(host in API_HOSTS) { "Unsupported GD Studio API host." }
        val type = params["types"].orEmpty()
        val rawPayload = if (type == "search") params["name"] else params["id"]
        val payload = rawPayload?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("GD Studio signature payload is missing.")
        val encodedPayload = encodeURIComponent(payload)
        val timePrefix = serverTime.take(9)
        require(timePrefix.length == 9) { "GD Studio server time must contain at least 9 digits." }
        val signatureInput = "$timePrefix|$host|${normalizeVersion(API_VERSION)}|$encodedPayload"
        val signature = modifiedMd5(signatureInput).takeLast(8).uppercase(Locale.US)

        return HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .addPathSegments("api.php")
            .apply {
                params.forEach { (key, value) ->
                    if (key != "s") addQueryParameter(key, value)
                }
                addQueryParameter("s", signature)
            }
            .build()
    }

    internal fun encodeURIComponent(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(bytes.size * 3)
        val hex = "0123456789ABCDEF"
        for (byte in bytes) {
            val b = byte.toInt() and 0xff
            val safe = (b in 'a'.code..'z'.code) ||
                (b in 'A'.code..'Z'.code) ||
                (b in '0'.code..'9'.code) ||
                b == '-'.code || b == '_'.code || b == '.'.code || b == '~'.code
            if (safe) {
                out.append(b.toChar())
            } else {
                out.append('%').append(hex[b ushr 4]).append(hex[b and 0x0f])
            }
        }
        return out.toString()
            .replace("!", "%21")
            .replace("'", "%27")
            .replace("(", "%28")
            .replace(")", "%29")
            .replace("*", "%2A")
    }

    private fun normalizeVersion(version: String): String =
        version.split('.').joinToString(".") { part ->
            if (part.length == 1) "0$part" else part
        }

    private fun serverTimePrefix(client: OkHttpClient, host: String): String {
        val now = System.nanoTime() / 1_000_000L
        synchronized(timeCache) {
            timeCache[host]?.takeIf { now < it.expiresAtElapsedMs }?.let { return it.prefix }
        }

        val request = Request.Builder()
            .url("https://$host/time")
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Origin", "https://$host")
            .header("Referer", "https://$host/")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "text/plain, */*")
            .get()
            .build()
        val serverTime = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GD Studio 时间接口 HTTP ${response.code}。")
            }
            response.body?.string()?.trim().orEmpty()
        }
        if (serverTime.length < 9 || serverTime.any { !it.isDigit() }) {
            throw IOException("GD Studio 时间接口返回了无效的签名时间。")
        }
        val prefix = serverTime.take(9)
        val epochSeconds = serverTime.toLongOrNull()
        val untilPrefixChangesMs = epochSeconds?.let {
            (((10 - (it % 10)) % 10) * 1_000L - 500L).coerceIn(250L, 8_500L)
        } ?: 1_000L
        synchronized(timeCache) {
            timeCache[host] = TimeEntry(prefix, System.nanoTime() / 1_000_000L + untilPrefixChangesMs)
        }
        return prefix
    }

    private fun apiPayloadError(body: String): String? {
        val json = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return null
        if (!json.has("code")) return null
        val code = json.optInt("code", 0)
        if (code == 0 || code == 200) return null
        val detail = listOf(
            json.optString("msg"),
            json.optString("message"),
            json.optString("error"),
            json.optString("detail"),
        ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        return detail?.let { "GD Studio API 返回错误：$it" }
            ?: "GD Studio API 返回错误代码 $code。"
    }

    /** Custom MD5 round function copied from the maintained reference client. */
    private fun modifiedMd5(message: String): String {
        val input = message.toByteArray(Charsets.UTF_8)
        val paddedLength = ((input.size + 9 + 63) / 64) * 64
        val data = ByteArray(paddedLength)
        input.copyInto(data)
        data[input.size] = 0x80.toByte()
        val bitLength = input.size.toLong() * 8L
        for (i in 0 until 8) data[paddedLength - 8 + i] = (bitLength ushr (i * 8)).toByte()

        val shifts = intArrayOf(
            7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
            5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
            4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
            6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
        )
        val table = intArrayOf(
            0xD76AA478.toInt(), 0xE8C7B756.toInt(), 0x242070DB, 0xC1BDCEEE.toInt(),
            0xF57C0FAF.toInt(), 0x4787C62A, 0xA8304613.toInt(), 0xFD469501.toInt(),
            0x698098D8, 0x8B44F7AF.toInt(), 0xFFFF5BB1.toInt(), 0x895CD7BE.toInt(),
            0x6B901122, 0xFD987193.toInt(), 0xA679438E.toInt(), 0x49B40821,
            0xF61E2562.toInt(), 0xC040B340.toInt(), 0x265E5A51, 0xE9B6C7AA.toInt(),
            0xD62F105D.toInt(), 0x02441453, 0xD8A1E681.toInt(), 0xE7D3FBC8.toInt(),
            0x21E1CDE6, 0xC33707D6.toInt(), 0xF4D50D87.toInt(), 0x455A14ED,
            0xA9E3E905.toInt(), 0xFCFEA3F8.toInt(), 0x676F02D9, 0x8D2A4C8A.toInt(),
            0xFFFA3942.toInt(), 0x8771F681.toInt(), 0x6D9D6122, 0xFDE5380C.toInt(),
            0xA4BEEA44.toInt(), 0x4BDECFA9, 0xF6BB4B60.toInt(), 0xBEBFBC70.toInt(),
            0x289B7EC6, 0xEAA127FA.toInt(), 0xD4EF3085.toInt(), 0x04881D05,
            0xD9D4D039.toInt(), 0xE6DB99E5.toInt(), 0x1FA27CF8, 0xC4AC5665.toInt(),
            0xF4292244.toInt(), 0x432AFF97, 0xAB9423A7.toInt(), 0xFC93A039.toInt(),
            0x655B59C3, 0x8F0CCC92.toInt(), 0xFFEFF47D.toInt(), 0x85845DD1.toInt(),
            0x6FA87E4F, 0xFE2CE6E0.toInt(), 0xA3014314.toInt(), 0x4E0811A1,
            0xF7537E82.toInt(), 0xBD3AF235.toInt(), 0x2AD7D2BB, 0xEB86D391.toInt(),
        )
        var a0 = 0x67452301
        var b0 = 0xEFCDAB89.toInt()
        var c0 = 0x98BADCFE.toInt()
        var d0 = 0x10325476
        var offset = 0
        while (offset < data.size) {
            val words = IntArray(16) { index ->
                val start = offset + index * 4
                (data[start].toInt() and 0xff) or
                    ((data[start + 1].toInt() and 0xff) shl 8) or
                    ((data[start + 2].toInt() and 0xff) shl 16) or
                    ((data[start + 3].toInt() and 0xff) shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0
            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when (i) {
                    in 0..15 -> { f = (b and c) or (b.inv() and d); g = i }
                    in 16..31 -> { f = (d and b) or (c and d.inv()); g = (5 * i + 1) % 16 }
                    in 32..47 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                val sum = f + a + table[i] + words[g]
                val nextB = b + Integer.rotateLeft(sum, shifts[i])
                a = d
                d = c
                c = b
                b = nextB
            }
            a0 += a
            b0 += b
            c0 += c
            d0 += d
            offset += 64
        }
        val output = StringBuilder(32)
        for (word in intArrayOf(a0, b0, c0, d0)) {
            for (i in 0 until 4) {
                val byte = (word ushr (i * 8)) and 0xff
                output.append("0123456789abcdef"[byte ushr 4])
                output.append("0123456789abcdef"[byte and 0x0f])
            }
        }
        return output.toString()
    }
}
