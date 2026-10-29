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

                    // Refresh server time before one retry while retaining the upstream-pinned version.
                    val refreshedVersion = currentApiVersion(client, host, forceRefresh = true)
                    val refreshedTime = serverTimePrefix(client, host)
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
            val context = "GD Studio 请求诊断：host=" + host +
                ", types=" + params["types"].orEmpty() +
                ", source=" + params["source"].orEmpty() +
                ", signatureVersion=" + version
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

    private fun currentApiVersion(
        client: OkHttpClient,
        host: String,
        forceRefresh: Boolean = false,
    ): String {
        // Match the maintained musicdl client: use its pinned, published signature version.
        // Parsing an arbitrary version token from player.js can select an unrelated value
        // and cause repeatable HTTP 401 Invalid request responses.
        return API_VERSION
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

    /** Custom MD5 round function copied from the maintained reference client. */
    private fun modifiedMd5(message: String): String {
        // Android's standard MD5 implementation is the reference algorithm used by GD Studio.
        // The previous hand-written implementation drifted from MD5 and generated signatures
        // rejected by the live API with HTTP 401 Invalid request.
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest(message.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xff)
        }
    }
}
