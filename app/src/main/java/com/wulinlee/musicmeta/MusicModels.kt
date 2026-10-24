package com.wulinlee.musicmeta

data class MusicTrack(
    val id: Long,
    val title: String,
    val artists: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long = 0L,
)

data class PublicPlaylist(
    val id: Long,
    val name: String,
    val creator: String,
    val coverUrl: String,
    val tracks: List<MusicTrack>,
)

data class TrackAssets(
    val coverBytes: ByteArray?,
    val coverMimeType: String?,
    val originalLyrics: String,
    val translatedLyrics: String,
    val bilingualLyrics: String,
    val romanizationLyrics: String = "",
    val wordByWordLyrics: String = "",
)
