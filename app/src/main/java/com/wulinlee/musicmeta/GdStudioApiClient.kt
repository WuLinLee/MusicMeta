package com.wulinlee.musicmeta

import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import okhttp3.FormBody
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
    const val API_VERSION = "2026.08.01"
    const val PRIMARY_HOST = "music.gdstudio.xyz"
    internal val API_HOSTS = listOf(PRIMARY_HOST, "music.gdstudio.org")
    internal const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

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
                val version = API_VERSION
                val serverTime = serverTimePrefix(client, host)
                try {
                    return executeSignedRequest(client, host, params, ttlMillis, serverTime, version)
                } catch (error: IOException) {
                    if (!isSignatureRejection(error)) throw error

                    // Refresh both signing inputs after a rejection. If neither changed, sending
                    // the same signature again cannot help and may worsen upstream rate limiting.
                    val refreshedVersion = API_VERSION
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
        val signedParams = buildSignedParams(host, params, serverTime, version)
        val formBody = FormBody.Builder().apply {
            signedParams.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(apiUrl(host))
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Origin", "https://$host")
            .header("Referer", "https://$host/")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .post(formBody)
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
                ", signatureAlgorithm=standard-md5"
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

    /** Build the GD Studio API endpoint; signed parameters are sent as a form-encoded POST body. */
    internal fun apiUrl(host: String): HttpUrl {
        require(host in API_HOSTS) { "Unsupported GD Studio API host." }
        return HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .addPathSegment("api.php")
            .build()
    }

    /**
     * Sign the URI-encoded search name or lookup id and return the parameters for a form POST.
     * The field values sent to the API remain unencoded here; FormBody performs the encoding.
     */
    internal fun buildSignedParams(
        host: String,
        params: Map<String, String>,
        serverTime: String,
        version: String = API_VERSION,
    ): Map<String, String> {
        require(host in API_HOSTS) { "Unsupported GD Studio API host." }
        val type = params["types"].orEmpty()
        val rawPayload = if (type == "search") params["name"] else params["id"]
        val payload = rawPayload?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("GD Studio signature payload is missing.")
        val encodedPayload = encodeURIComponent(payload)
        val timePrefix = serverTime.take(9)
        require(timePrefix.length == 9) { "GD Studio server time must contain at least 9 digits." }
        val signatureInput = "${timePrefix}|${host}|${normalizeVersion(version)}|${encodedPayload}"
        val digest = MessageDigest.getInstance("MD5")
            .digest(signatureInput.toByteArray(Charsets.UTF_8))
        val signature = digest.joinToString("") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xff)
        }.takeLast(8).uppercase(Locale.US)

        return linkedMapOf<String, String>().apply {
            params.forEach { (key, value) ->
                if (key != "s") put(key, value)
            }
            put("s", signature)
        }
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

}
