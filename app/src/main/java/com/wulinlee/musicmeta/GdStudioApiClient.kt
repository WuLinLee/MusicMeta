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

    private data class VersionEntry(val version: String, val expiresAtElapsedMs: Long)

    private const val VERSION_CACHE_TTL_MILLIS = 10 * 60 * 1000L
    private val versionCache = mutableMapOf<String, VersionEntry>()
    private val versionPattern = Regex(
        """\bversion\s*[:=]\s*["']?(20\d{2}\.\d{1,2}\.\d{1,2})""",
        RegexOption.IGNORE_CASE,
    )

    /** Host failover stays within GD Studio; cross-provider fallback is handled by FlacMusicProvider. */
    fun getText(
        client: OkHttpClient,
        params: Map<String, String>,
        ttlMillis: Long = 0L,
    ): String {
        var lastError: IOException? = null
        val errors = mutableListOf<IOException>()
        for (host in API_HOSTS) {
            try {
                val version = currentApiVersion(client, host)
                val serverTime = serverTimePrefix(client, host)
                try {
                    return executeSignedRequest(client, host, params, ttlMillis, serverTime, version)
                } catch (error: IOException) {
                    if (!isSignatureRejection(error)) throw error

                    // Refresh both signing inputs after a rejection. If neither changed, sending
                    // the same signature again cannot help and may worsen upstream rate limiting.
                    val refreshedVersion = currentApiVersion(client, host, forceRefresh = true)
                    val refreshedTime = serverTimePrefix(client, host)
                    if (!shouldRetryAfterSignatureRejection(
                            version,
                            serverTime,
                            refreshedVersion,
                            refreshedTime,
                        )
                    ) {
                        error.addSuppressed(
                            IOException(
                                "401 后签名版本与服务器时间桶均未变化；为避免重复相同签名请求，已跳过本主机的重复重试。",
                            ),
                        )
                        throw error
                    }

                    try {
                        return executeSignedRequest(
                            client, host, params, ttlMillis, refreshedTime, refreshedVersion,
                        )
                    } catch (retryError: IOException) {
                        retryError.addSuppressed(error)
                        throw retryError
                    }
                }
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

    private fun executeSignedRequest(
        client: OkHttpClient,
        host: String,
        params: Map<String, String>,
        ttlMillis: Long,
        serverTime: String,
        version: String,
    ): String {
        val url = buildSignedUrl(host, params, serverTime, version)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Origin", "https://$host")
            .header("Referer", "https://$host/")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .get()
            .build()
        val body = try {
            GdStudioApiSupport.getText(client, request, ttlMillis).trim()
        } catch (error: IOException) {
            val payloadKey = if (params["types"] == "search") "name" else "id"
            val payloadLength = encodeURIComponent(params[payloadKey].orEmpty()).length
            val context = "GD Studio 请求诊断：host=" + host +
                ", types=" + params["types"].orEmpty() +
                ", source=" + params["source"].orEmpty() +
                ", signatureVersion=" + version +
                ", signatureTimePrefix=" + serverTime.take(9) +
                ", signaturePayloadLength=" + payloadLength +
                ", signatureAlgorithm=custom-md5-k29-fcfea3f8"
            throw IOException(context + "；" + error.message.orEmpty(), error)
        }
        apiPayloadError(body)?.let { detail ->
            throw IOException(
                "GD Studio 请求诊断：host=" + host +
                    ", types=" + params["types"].orEmpty() +
                    ", source=" + params["source"].orEmpty() +
                    ", signatureVersion=" + version + "；" + detail,
            )
        }
        return body
    }

    private fun isSignatureRejection(error: IOException): Boolean {
        val text = buildString {
            append(error.message.orEmpty())
            error.cause?.message?.let { append(' ').append(it) }
            error.suppressed.forEach { append(' ').append(it.message.orEmpty()) }
        }
        return text.contains("HTTP 401", ignoreCase = true) ||
            text.contains("Invalid request", ignoreCase = true)
    }

    @Synchronized
    private fun currentApiVersion(
        client: OkHttpClient,
        host: String,
        forceRefresh: Boolean = false,
    ): String {
        val now = System.nanoTime() / 1_000_000L
        val cached = versionCache[host]
        if (!forceRefresh && cached != null && now < cached.expiresAtElapsedMs) {
            return cached.version
        }

        // The site's player.js version is part of the signature. It can change without an
        // app release; a pinned fallback alone leaves every API request invalid after rotation.
        // On a 401, forceRefresh bypasses this cache and reads player.js again before retrying.
        val selected = readApiVersion(client, host)
            ?: cached?.version
            ?: API_VERSION
        versionCache[host] = VersionEntry(
            version = selected,
            expiresAtElapsedMs = now + VERSION_CACHE_TTL_MILLIS,
        )
        return selected
    }
    private fun readApiVersion(client: OkHttpClient, host: String): String? {
        val request = Request.Builder()
            .url("https://$host/js/player.js")
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Origin", "https://$host")
            .header("Referer", "https://$host/")
            .header("Accept", "text/javascript, application/javascript, */*; q=0.1")
            .get()
            .build()
        val script = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return null
        }
        return parseVersionFromPlayerScript(script)
    }

    internal fun parseVersionFromPlayerScript(script: String): String? =
        versionPattern.find(script)?.groupValues?.getOrNull(1)

    /** Build the GD Studio API endpoint. The maintained client uses signed GET query parameters. */
    internal fun apiUrl(host: String): HttpUrl {
        require(host in API_HOSTS) { "Unsupported GD Studio API host." }
        return HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .addPathSegment("api.php")
            .build()
    }

    /**
     * GD Studio signs the URI-encoded search name or lookup id, then sends the original
     * parameter values plus the signature as GET query parameters.
     */
    internal fun buildSignedUrl(
        host: String,
        params: Map<String, String>,
        serverTime: String,
        version: String = API_VERSION,
    ): HttpUrl {
        require(host in API_HOSTS) { "Unsupported GD Studio API host." }
        val type = params["types"].orEmpty()
        val rawPayload = if (type == "search") params["name"] else params["id"]
        val payload = rawPayload?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("GD Studio signature payload is missing.")
        val encodedPayload = encodeURIComponent(payload)
        val timePrefix = serverTime.take(9)
        require(timePrefix.length == 9) { "GD Studio server time must contain at least 9 digits." }
        val signatureInput = "${timePrefix}|${host}|${normalizeVersion(version)}|${encodedPayload}"
        val signature = modifiedMd5(signatureInput).takeLast(8).uppercase(Locale.US)

        return apiUrl(host).newBuilder().apply {
            params.forEach { (key, value) ->
                if (key != "s") addQueryParameter(key, value)
            }
            addQueryParameter("s", signature)
        }.build()
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

    internal fun normalizeVersion(version: String): String =
        version.split('.').joinToString("") { part ->
            if (part.length == 1) "0$part" else part
        }

    internal fun shouldRetryAfterSignatureRejection(
        previousVersion: String,
        previousTimePrefix: String,
        refreshedVersion: String,
        refreshedTimePrefix: String,
    ): Boolean =
        normalizeVersion(previousVersion) != normalizeVersion(refreshedVersion) ||
            previousTimePrefix != refreshedTimePrefix

    /**
     * Fetch the signature time from the service for each request.
     *
     * The maintained musicdl implementation does not cache this value. The first nine
     * digits are a short-lived signing bucket, and treating the response as either seconds
     * or milliseconds when calculating a local expiry can keep a stale bucket alive. A fresh
     * read is cheap and avoids a class of intermittent HTTP 401 errors around bucket rollover.
     */
    private fun serverTimePrefix(
        client: OkHttpClient,
        host: String,
    ): String {
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
        return serverTime.take(9)
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

    /**
     * GD Studio's signature digest is intentionally not standard MD5: round constant K[29]
     * is 0xFCFEA3F8 (rather than the standard 0xFCEFA3F8). Using MessageDigest("MD5")
     * produces signatures that the live API rejects with HTTP 401 Invalid request.
     */
    private fun modifiedMd5(message: String): String {
        val input = message.toByteArray(Charsets.UTF_8)
        val paddedLength = ((input.size + 8) / 64 + 1) * 64
        val data = ByteArray(paddedLength)
        input.copyInto(data)
        data[input.size] = 0x80.toByte()
        val bitLength = input.size.toLong() * 8L
        for (i in 0 until 8) {
            data[paddedLength - 8 + i] = (bitLength ushr (8 * i)).toByte()
        }

        val shifts = intArrayOf(
            7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
            5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
            4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
            6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
        )
        val constants = longArrayOf(
            0xD76AA478L, 0xE8C7B756L, 0x242070DBL, 0xC1BDCEEEL,
            0xF57C0FAFL, 0x4787C62AL, 0xA8304613L, 0xFD469501L,
            0x698098D8L, 0x8B44F7AFL, 0xFFFF5BB1L, 0x895CD7BEL,
            0x6B901122L, 0xFD987193L, 0xA679438EL, 0x49B40821L,
            0xF61E2562L, 0xC040B340L, 0x265E5A51L, 0xE9B6C7AAL,
            0xD62F105DL, 0x02441453L, 0xD8A1E681L, 0xE7D3FBC8L,
            0x21E1CDE6L, 0xC33707D6L, 0xF4D50D87L, 0x455A14EDL,
            0xA9E3E905L, 0xFCFEA3F8L, 0x676F02D9L, 0x8D2A4C8AL,
            0xFFFA3942L, 0x8771F681L, 0x6D9D6122L, 0xFDE5380CL,
            0xA4BEEA44L, 0x4BDECFA9L, 0xF6BB4B60L, 0xBEBFBC70L,
            0x289B7EC6L, 0xEAA127FAL, 0xD4EF3085L, 0x04881D05L,
            0xD9D4D039L, 0xE6DB99E5L, 0x1FA27CF8L, 0xC4AC5665L,
            0xF4292244L, 0x432AFF97L, 0xAB9423A7L, 0xFC93A039L,
            0x655B59C3L, 0x8F0CCC92L, 0xFFEFF47DL, 0x85845DD1L,
            0x6FA87E4FL, 0xFE2CE6E0L, 0xA3014314L, 0x4E0811A1L,
            0xF7537E82L, 0xBD3AF235L, 0x2AD7D2BBL, 0xEB86D391L,
        )

        var a0 = 0x67452301
        var b0 = 0xEFCDAB89.toInt()
        var c0 = 0x98BADCFE.toInt()
        var d0 = 0x10325476

        var offset = 0
        while (offset < data.size) {
            val words = IntArray(16) { wordIndex ->
                val p = offset + wordIndex * 4
                (data[p].toInt() and 0xff) or
                    ((data[p + 1].toInt() and 0xff) shl 8) or
                    ((data[p + 2].toInt() and 0xff) shl 16) or
                    ((data[p + 3].toInt() and 0xff) shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0

            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when {
                    i < 16 -> {
                        f = (b and c) or (b.inv() and d)
                        g = i
                    }
                    i < 32 -> {
                        f = (d and b) or (c and d.inv())
                        g = (5 * i + 1) % 16
                    }
                    i < 48 -> {
                        f = b xor c xor d
                        g = (3 * i + 5) % 16
                    }
                    else -> {
                        f = c xor (b or d.inv())
                        g = (7 * i) % 16
                    }
                }
                val sum = a + f + constants[i].toInt() + words[g]
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

        val state = intArrayOf(a0, b0, c0, d0)
        val hex = "0123456789abcdef"
        return buildString(32) {
            for (word in state) {
                for (i in 0 until 4) {
                    val value = (word ushr (8 * i)) and 0xff
                    append(hex[value ushr 4])
                    append(hex[value and 0x0f])
                }
            }
        }
    }
}
