package com.lyricglow.app.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var mediaCache: SimpleCache? = null

    override fun onCreate() {
        super.onCreate()
        val cacheDir = File(cacheDir, "lyricglow_audio_cache").apply { mkdirs() }
        val cache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L), StandaloneDatabaseProvider(this))
        mediaCache = cache
        val cacheDataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("LyricGlow/0.5.0")
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        mediaCache?.release()
        mediaCache = null
        super.onDestroy()
    }
}
