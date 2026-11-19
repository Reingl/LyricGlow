package com.lyricglow.app.data

import android.content.Context
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Public YouTube/YouTube Music catalogue adapter. Search metadata is cached locally;
 * signed audio URLs are deliberately never cached because they expire.
 */
class YoutubeMusicRepository(context: Context) {
    private val cache = MusicMetadataCache(context.applicationContext)

    fun saveTrack(track: MusicTrack) = cache.save(track)
    fun savedTracks(): List<MusicTrack> = cache.saved()

    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val clean = query.trim()
        require(clean.length >= 2) { "Enter at least two characters." }
        val cached = cache.get(clean)
        runCatching {
            ensureExtractor()
            val handler = ServiceList.YouTube.searchQHFactory.fromQuery(clean, listOf("music_songs"), "")
            val info = SearchInfo.getInfo(ServiceList.YouTube, handler)
            val tracks = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
                val id = item.url.substringAfter("v=", item.url.substringAfterLast('/')).substringBefore('&')
                if (id.isBlank()) null else MusicTrack(
                    id = id,
                    title = item.name,
                    artist = item.uploaderName ?: "Unknown artist",
                    durationSeconds = item.duration.coerceAtLeast(0),
                    artworkUrl = item.thumbnails.firstOrNull()?.url,
                    pageUrl = item.url
                )
            }
            cache.put(clean, tracks)
            SearchResults(tracks, emptyList())
        }.getOrElse { error ->
            if (cached.isNotEmpty()) SearchResults(cached, emptyList())
            else throw IllegalStateException(friendlyError(error), error)
        }
    }

    suspend fun searchAlbums(query: String): List<MusicAlbum> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        require(clean.length >= 2) { "Enter at least two characters." }
        ensureExtractor()
        val handler = ServiceList.YouTube.searchQHFactory.fromQuery(clean, listOf("music_albums"), "")
        val info = SearchInfo.getInfo(ServiceList.YouTube, handler)
        info.relatedItems.filterIsInstance<PlaylistInfoItem>().map { item ->
            MusicAlbum(
                id = item.url.substringAfterLast("list="), title = item.name,
                artist = item.uploaderName ?: "Unknown artist",
                artworkUrl = item.thumbnails.firstOrNull()?.url,
                pageUrl = item.url
            )
        }
    }

    suspend fun resolveAudio(pageUrl: String): ResolvedAudio = withContext(Dispatchers.IO) {
        ensureExtractor()
        val info = StreamInfo.getInfo(ServiceList.YouTube, pageUrl)
        val audio = info.audioStreams
            .filter { it.isUrl }
            .maxByOrNull(AudioStream::getAverageBitrate)
            ?: throw IllegalStateException("No compatible audio stream is available for this track.")
        ResolvedAudio(audio.content, audio.format?.mimeType, audio.averageBitrate)
    }

    suspend fun loadAlbum(pageUrl: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        ensureExtractor()
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, pageUrl)
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { item ->
            MusicTrack(
                id = item.url.substringAfter("v=", item.url.substringAfterLast('/')).substringBefore('&'),
                title = item.name, artist = item.uploaderName ?: "Unknown artist",
                durationSeconds = item.duration.coerceAtLeast(0),
                artworkUrl = item.thumbnails.firstOrNull()?.url, pageUrl = item.url
            )
        }
    }

    private fun ensureExtractor() {
        if (INITIALIZED.compareAndSet(false, true)) {
            NewPipe.init(NewPipeHttpDownloader())
        }
    }

    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("captcha", true) || message.contains("recaptcha", true) ->
                "YouTube temporarily rate-limited this connection. Try again later."
            message.contains("age", true) || message.contains("sign in", true) ->
                "This result requires YouTube sign-in or age verification and is unavailable to LyricGlow."
            else -> "Couldn't reach YouTube Music right now. Check your connection and try again."
        }
    }

    companion object { private val INITIALIZED = AtomicBoolean(false) }
}
