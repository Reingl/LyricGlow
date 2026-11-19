package com.lyricglow.app.data

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val artworkUrl: String?,
    val pageUrl: String,
    val provider: String = "YouTube Music"
)

data class MusicAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val pageUrl: String,
    val trackCount: Int = 0
)

data class ResolvedAudio(
    val url: String,
    val mimeType: String?,
    val bitrate: Int
)

data class SearchResults(
    val tracks: List<MusicTrack>,
    val albums: List<MusicAlbum>
)
