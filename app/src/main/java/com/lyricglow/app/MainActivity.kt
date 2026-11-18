package com.lyricglow.app

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import java.util.concurrent.Executor
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(
            applicationContext,
            ComponentName(applicationContext, com.lyricglow.app.playback.PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(this, token).buildAsync()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val future = controllerFuture
                    if (future == null) {
                        Text("Unable to initialise playback.")
                    } else {
                        PlaybackHome(future)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun PlaybackHome(controllerFuture: ListenableFuture<MediaController>) {
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var connected by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("Nothing playing") }
    var artist by remember { mutableStateOf("Choose an audio URL to begin") }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableFloatStateOf(0f) }
    var position by remember { mutableFloatStateOf(0f) }
    var seeking by remember { mutableStateOf(false) }
    var queueTitles by remember { mutableStateOf(listOf<String>()) }

    val appContext = LocalContext.current
    DisposableEffect(controllerFuture) {
        val listener = Runnable {
            try {
                controller = controllerFuture.get()
                connected = true
            } catch (_: Exception) {
                connected = false
            }
        }
        val mainExecutor = Executor { command ->
            Handler(Looper.getMainLooper()).post(command)
        }
        controllerFuture.addListener(listener, mainExecutor)
        onDispose { }
    }

    val player = controller
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown title"
                artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown artist"
                queueTitles = (0 until player.mediaItemCount).mapNotNull { index ->
                    player.getMediaItemAt(index).mediaMetadata.title?.toString()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.duration.coerceAtLeast(0L).toFloat()
                isPlaying = player.isPlaying
            }
        }
        player.addListener(playerListener)
        onDispose { player.removeListener(playerListener) }
    }

    LaunchedEffect(player, isPlaying) {
        while (player != null && isPlaying) {
            if (!seeking) {
                position = player.currentPosition.coerceAtLeast(0L).toFloat()
                duration = player.duration.coerceAtLeast(0L).toFloat()
            }
            delay(400)
        }
    }

    fun addAndPlay(stream: String, append: Boolean) {
        val p = player ?: return
        val clean = stream.trim()
        if (!clean.startsWith("https://") && !clean.startsWith("http://")) return
        val media = MediaItem.Builder()
            .setMediaId(clean)
            .setUri(clean)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(clean.substringAfterLast('/').ifBlank { "Audio stream" })
                    .setArtist("Direct stream")
                    .build()
            )
            .build()
        if (append) {
            p.addMediaItem(media)
            queueTitles = queueTitles + (media.mediaMetadata.title?.toString() ?: "Audio stream")
        } else {
            p.setMediaItem(media)
            p.prepare()
            p.play()
        }
        url = ""
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("LyricGlow", style = MaterialTheme.typography.headlineLarge)
            Text("Ad-free music, your way.", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (connected) "Playback connected" else "Connecting to playback…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(artist, style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = if (duration > 0f) position.coerceIn(0f, duration) else 0f,
                    onValueChange = {
                        seeking = true
                        position = it
                    },
                    onValueChangeFinished = {
                        player?.seekTo(position.toLong())
                        seeking = false
                    },
                    valueRange = 0f..(duration.coerceAtLeast(1f))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position.toLong()))
                    Text(formatTime(duration.toLong()))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { player?.seekToPreviousMediaItem() }) { Text("Previous") }
                    Button(onClick = {
                        player?.let { if (it.isPlaying) it.pause() else it.play() }
                    }, enabled = player != null) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }
                    TextButton(onClick = { player?.seekToNextMediaItem() }) { Text("Next") }
                }
            }
        }

        Text("Play a direct audio URL", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("https://…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { addAndPlay(url, false) },
                enabled = player != null && url.startsWith("http")
            ) { Text("Play URL") }
            TextButton(
                onClick = { addAndPlay(url, true) },
                enabled = player != null && url.startsWith("http")
            ) { Text("Add to queue") }
        }

        Text("Queue", style = MaterialTheme.typography.titleMedium)
        if (queueTitles.isEmpty()) {
            Text("Your queue is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(queueTitles) { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}. $item", modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = {
                            player?.removeMediaItem(index)
                            queueTitles = queueTitles.toMutableList().also {
                                if (index in it.indices) it.removeAt(index)
                            }
                        }) { Text("Remove") }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "This build plays direct audio URLs. Music-provider integrations and synced lyrics are the next milestones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds < 0) return "0:00"
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
