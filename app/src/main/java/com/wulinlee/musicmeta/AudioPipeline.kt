package com.wulinlee.musicmeta

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object AudioDownloader {
    private val client = OkHttpClient.Builder().followRedirects(true).callTimeout(10, TimeUnit.MINUTES).build()

    fun download(
        context: Context,
        url: String,
        userAgent: String,
        cookie: String?,
        disposition: String?,
        mimeTypeHint: String?,
        referer: String,
    ): File {
        val uri = Uri.parse(url)
        require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "仅支持标准 HTTP/HTTPS 音频下载链接；网页内部 blob/data 链接暂不支持。"
        }
        val builder = Request.Builder().url(url)
            .header("User-Agent", userAgent.ifBlank { fallbackUserAgent })
            .header("Accept", "audio/*, application/octet-stream, */*")
            .get()
        if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)
        if (referer.startsWith("http://") || referer.startsWith("https://")) builder.header("Referer", referer)

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
            val body = response.body ?: throw IOException("网站没有返回音频文件。")
            val responseType = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            if (responseType == "text/html" || responseType.contains("json")) {
                throw IOException("拿到的是网页或接口响应，不是音频。请在网站内点击正常的下载按钮。")
            }
            if (body.contentLength() > 1_500L * 1024L * 1024L) throw IOException("文件大于 1.5 GB，暂不下载。")
            val mime = responseType.ifBlank { mimeTypeHint.orEmpty() }
            val filename = URLUtil.guessFileName(response.request.url.toString(), response.header("Content-Disposition") ?: disposition, mime)
            val extension = File(filename).extension.lowercase()
            val allowed = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma")
            val inferred = when (mime.substringBefore(';').lowercase()) {
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/flac", "audio/x-flac" -> "flac"
                "audio/mp4", "audio/x-m4a" -> "m4a"
                "audio/aac", "audio/aacp" -> "aac"
                "audio/ogg" -> "ogg"
                "audio/opus" -> "opus"
                "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
                "audio/x-ms-wma" -> "wma"
                else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase().orEmpty()
            }
            val finalExtension = when {
                extension in allowed -> extension
                inferred in allowed -> inferred
                else -> throw IOException("无法识别音频格式（${mime.ifBlank { "未知类型" }}）。")
            }
            val base = (if (extension in allowed) filename.substringBeforeLast('.') else filename)
                .replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.').take(150)
                .ifBlank { "music-${System.currentTimeMillis()}" }
            val folder = File(context.cacheDir, "incoming-audio").apply { mkdirs() }
            val file = File(folder, "$base-${System.currentTimeMillis()}.$finalExtension")
            body.byteStream().use { input -> file.outputStream().buffered().use { output -> input.copyTo(output) } }
            if (!file.isFile || file.length() == 0L) {
                file.delete()
                throw IOException("下载到的音频文件为空。")
            }
            file
        }
    }

    private const val fallbackUserAgent = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
}


object AudioConverter {
    fun toMp3(input: File): File {
        if (input.extension.equals("mp3", ignoreCase = true)) return input
        val output = File(input.parentFile, input.nameWithoutExtension + "-converted.mp3")
        output.delete()
        val command = "-y -i \"${input.absolutePath}\" -vn -map_metadata 0 -codec:a libmp3lame -q:a 2 \"${output.absolutePath}\""
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode) || !output.isFile || output.length() == 0L) {
            output.delete()
            throw IOException("MP3 转码失败。原始下载文件仍保留，可选择仅保存原文件。")
        }
        if (input.absolutePath != output.absolutePath) input.delete()
        return output
    }
}

object AudioTagWriter {
    fun write(file: File, track: MusicTrack, assets: TrackAssets) {
        val audioFile = try {
            AudioFileIO.read(file)
        } catch (e: Exception) {
            throw IOException("音频格式暂不支持写入内嵌标签：${file.extension}", e)
        }
        val tag: Tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }
        setField(tag, FieldKey.TITLE, track.title)
        setField(tag, FieldKey.ARTIST, track.artists)
        if (track.album.isNotBlank()) setField(tag, FieldKey.ALBUM, track.album)
        if (assets.bilingualLyrics.isNotBlank()) {
            tag.setField(tag.createField(FieldKey.LYRICS, assets.bilingualLyrics))
        }
        val cover = assets.coverBytes
        if (cover != null && cover.isNotEmpty()) {
            val artwork = ArtworkFactory.getNew()
            artwork.binaryData = cover
            artwork.mimeType = detectImageMime(cover, assets.coverMimeType)
            artwork.pictureType = 3
            tag.setField(artwork)
        }
        try {
            audioFile.commit()
        } catch (e: Exception) {
            throw IOException("标签保存失败：${e.message ?: "未知错误"}", e)
        }
    }

    private fun setField(tag: Tag, key: FieldKey, value: String) {
        if (value.isBlank()) return
        tag.setField(tag.createField(key, value))
    }

    private fun detectImageMime(data: ByteArray, reported: String?): String = when {
        data.size >= 3 && data[0] == 0xff.toByte() && data[1] == 0xd8.toByte() -> "image/jpeg"
        data.size >= 8 && data.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "image/png"
        else -> reported?.substringBefore(';')?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    }
}

object MediaPublisher {
    fun publish(context: Context, file: File, track: MusicTrack?): Uri {
        val extension = file.extension.lowercase()
        val mime = when (extension) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "wma" -> "audio/x-ms-wma"
            else -> "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/MusicMeta")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (track != null) {
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ARTIST, track.artists)
                put(MediaStore.Audio.Media.ALBUM, track.album)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建手机音乐文件。")
        try {
            val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("无法写入音乐目录。")
            output.use { out -> file.inputStream().buffered().use { input -> input.copyTo(out) } }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}
