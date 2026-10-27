package com.wulinlee.musicmeta

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.io.IOException
import java.io.File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.audio.AudioFileIO
import java.util.Locale

data class LocalAudioTrack(
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUri: String = "",
)

object LocalMusicScanner {
    private val extensions = setOf("mp3", "flac", "wav", "wave", "m4a", "aac", "ogg", "opus", "wma")

    suspend fun scan(context: Context, selectedTree: Uri?): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        if (selectedTree != null) {
            val root = DocumentFile.fromTreeUri(context, selectedTree) ?: error("无法读取所选文件夹")
            val tracks = mutableListOf<LocalAudioTrack>()
            fun visit(folder: DocumentFile) {
                folder.listFiles().forEach { file ->
                    if (file.isDirectory) visit(file)
                    else if (file.isFile && file.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in extensions) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, file.uri)
                            tracks += LocalAudioTrack(
                                file.uri, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: file.name?.substringBeforeLast('.') ?: "未知歌曲",
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().ifBlank { "未知艺术家" },
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                                coverUri = runCatching {
                                    saveEmbeddedArtwork(context, file.uri, retriever.embeddedPicture)
                                }.getOrDefault(""),
                            )
                        } catch (_: Exception) {
                        } finally { runCatching { retriever.release() } }
                    }
                }
            }
            visit(root)
            tracks.distinctBy { it.uri.toString() }.sortedBy { it.title.lowercase(Locale.ROOT) }
        } else {
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.ALBUM_ID)
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)"
            val result = mutableListOf<LocalAudioTrack>()
            context.contentResolver.query(collection, projection, selection, arrayOf("Download/-07-MusicMeta/%", "Music/%"), "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                while (cursor.moveToNext()) {
                    val key = cursor.getLong(id)
                    val audioUri = Uri.withAppendedPath(collection, key.toString())
                    val embeddedCover = readEmbeddedArtwork(context, audioUri)
                    val albumArtId = cursor.getLong(albumId)
                    val coverUri = embeddedCover.ifBlank {
                        if (albumArtId > 0L) "content://media/external/audio/albumart/$albumArtId" else ""
                    }
                    result += LocalAudioTrack(
                        audioUri,
                        cursor.getString(title).orEmpty().ifBlank { "未知歌曲" },
                        cursor.getString(artist).orEmpty().ifBlank { "未知艺术家" },
                        cursor.getString(album).orEmpty(),
                        cursor.getLong(duration),
                        coverUri,
                    )
                }
            }
            result
        }
    }

    private const val maxEmbeddedArtworkBytes = 12 * 1024 * 1024
    private const val maxLyricsTagSourceBytes = 256L * 1024L * 1024L

    private fun readEmbeddedArtwork(context: Context, uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            saveEmbeddedArtwork(context, uri, retriever.embeddedPicture)
        } catch (_: Exception) {
            ""
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun saveEmbeddedArtwork(context: Context, sourceUri: Uri, bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty() || bytes.size > maxEmbeddedArtworkBytes) return ""
        val digest = sha256(sourceUri.toString())
        val extension = when {
            bytes.size >= 8 && bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            bytes.size >= 6 && String(bytes, 0, 3, Charsets.US_ASCII) == "GIF" -> "gif"
            else -> "jpg"
        }
        val directory = File(context.filesDir, "local-artwork")
        if (!directory.exists() && !directory.mkdirs()) return ""
        val target = File(directory, "$digest.$extension")
        return try {
            if (!target.isFile || target.length() != bytes.size.toLong()) target.outputStream().use { it.write(bytes) }
            Uri.fromFile(target).toString()
        } catch (_: Exception) {
            ""
        }
    }

    /** Reads embedded LYRICS tag from local files; no network calls are made for local items. */
    suspend fun readEmbeddedLyrics(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val cacheKey = sha256(uri.toString())
        val cacheDirectory = File(context.filesDir, "local-lyrics-cache")
        val cacheFile = File(cacheDirectory, "$cacheKey.lrc")
        if (cacheFile.isFile && cacheFile.length() <= 4L * 1024L * 1024L) {
            runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull()
                ?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        val directFile = findDirectAudioFile(context, uri)
        if (directFile != null && directFile.length() > maxLyricsTagSourceBytes) return@withContext ""
        var temporaryFile: File? = null
        val audioFile = directFile ?: runCatching { copyUriToTemporaryAudioFile(context, uri) }
            .getOrNull().also { temporaryFile = it }
        if (audioFile == null) return@withContext ""
        try {
            val lyrics = runCatching { AudioFileIO.read(audioFile).tag?.getFirst(FieldKey.LYRICS).orEmpty().trim() }
                .getOrDefault("")
            if (lyrics.isNotBlank()) runCatching {
                if (!cacheDirectory.exists()) cacheDirectory.mkdirs()
                cacheFile.writeText(lyrics, Charsets.UTF_8)
            }
            lyrics
        } finally {
            temporaryFile?.delete()
        }
    }

    private fun findDirectAudioFile(context: Context, uri: Uri): File? {
        val path = when (uri.scheme?.lowercase(Locale.ROOT)) {
            "file" -> uri.path
            "content" -> runCatching {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()
            else -> null
        }
        return path?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile && it.canRead() }
    }

    private fun copyUriToTemporaryAudioFile(context: Context, uri: Uri): File? {
        val length = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (length > maxLyricsTagSourceBytes) return null
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
        val candidate = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val extension = candidate.takeIf {
            it in setOf("mp3", "flac", "wav", "wave", "m4a", "aac", "ogg", "opus", "wma")
        } ?: when (context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase(Locale.ROOT)) {
            "audio/mpeg" -> "mp3"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/ogg" -> "ogg"
            else -> "mp3"
        }
        val tempDir = File(context.cacheDir, "local-lyrics-read").apply { mkdirs() }
        val tempFile = File.createTempFile("embedded-lyrics-", ".$extension", tempDir)
        try {
            val source = context.contentResolver.openInputStream(uri) ?: run { tempFile.delete(); return null }
            source.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxLyricsTagSourceBytes) throw IOException("本地音频文件过大，跳过内嵌歌词读取。")
                        output.write(buffer, 0, count)
                    }
                }
            }
            tempFile
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

}
