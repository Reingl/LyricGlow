package com.lyricglow.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** Creates and releases a connection to the process-level PlaybackService. */
object PlaybackConnection {
    fun connect(context: Context): ListenableFuture<MediaController> {
        val appContext = context.applicationContext
        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )
        return MediaController.Builder(appContext, token).buildAsync()
    }

    fun release(future: ListenableFuture<MediaController>) {
        MediaController.releaseFuture(future)
    }
}
