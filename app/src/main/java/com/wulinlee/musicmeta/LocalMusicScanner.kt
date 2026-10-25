package com.wulinlee.musicmeta

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                    result += LocalAudioTrack(Uri.withAppendedPath(collection, key.toString()), cursor.getString(title).orEmpty().ifBlank { "未知歌曲" }, cursor.getString(artist).orEmpty().ifBlank { "未知艺术家" }, cursor.getString(album).orEmpty(), cursor.getLong(duration), "content://media/external/audio/albumart/${cursor.getLong(albumId)}")
                }
            }
            result
        }
    }
}
