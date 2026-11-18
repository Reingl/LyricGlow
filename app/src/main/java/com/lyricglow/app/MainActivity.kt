package com.lyricglow.app

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executor

private enum class HomeLayout(val label: String) { TOP_PICKS("Top Picks"), IMMERSIVE("Immersive") }
private enum class VisualTheme(val label: String) { CRYSTAL("Crystal"), NOCTURNE("Nocturne"), ATELIER("Atelier") }
private enum class AppPage { HOME, EXPLORE, LIBRARY, SEARCH, PLAYER, APPEARANCE }

private data class Palette(
    val bg: Color, val surface: Color, val raised: Color, val text: Color,
    val muted: Color, val accent: Color, val heroA: Color, val heroB: Color
)

private fun palette(theme: VisualTheme) = when (theme) {
    VisualTheme.CRYSTAL -> Palette(Color(0xFF071426), Color(0xE51B304B), Color(0xFF294766),
        Color(0xFFF4F8FF), Color(0xFFB6C7DC), Color(0xFFB8DCFF), Color(0xFF416E9B), Color(0xFF14253D))
    VisualTheme.NOCTURNE -> Palette(Color(0xFF08080A), Color(0xF01B1A20), Color(0xFF302D38),
        Color(0xFFF8F6FA), Color(0xFFA8A4AE), Color(0xFFC6A3FF), Color(0xFF58347A), Color(0xFF171321))
    VisualTheme.ATELIER -> Palette(Color(0xFFF1ECF8), Color(0xFDFEFCFF), Color(0xFFE3D8F0),
        Color(0xFF241C30), Color(0xFF74677F), Color(0xFF7650A8), Color(0xFFC8B1E7), Color(0xFFEDE4F7))
}

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(applicationContext,
            ComponentName(applicationContext, com.lyricglow.app.playback.PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        setContent {
            val future = controllerFuture
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF08080A)) {
                    if (future == null) Text("Unable to initialise playback.")
                    else LyricGlowRoot(future)
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

@Composable
private fun LyricGlowRoot(future: ListenableFuture<MediaController>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lyricglow_appearance", 0) }
    var layout by remember {
        mutableStateOf(runCatching { HomeLayout.valueOf(prefs.getString("home_layout", "TOP_PICKS")!!) }.getOrDefault(HomeLayout.TOP_PICKS))
    }
    var theme by remember {
        mutableStateOf(runCatching { VisualTheme.valueOf(prefs.getString("visual_theme", "NOCTURNE")!!) }.getOrDefault(VisualTheme.NOCTURNE))
    }
    var glass by remember { mutableStateOf(prefs.getBoolean("glass_enabled", true)) }
    var topPicks by remember { mutableStateOf(prefs.getBoolean("show_top_picks", true)) }
    var recents by remember { mutableStateOf(prefs.getBoolean("show_recents", true)) }
    var moods by remember { mutableStateOf(prefs.getBoolean("show_moods", true)) }
    var page by remember { mutableStateOf(AppPage.HOME) }

    LaunchedEffect(layout, theme, glass, topPicks, recents, moods) {
        prefs.edit().putString("home_layout", layout.name).putString("visual_theme", theme.name)
            .putBoolean("glass_enabled", glass).putBoolean("show_top_picks", topPicks)
            .putBoolean("show_recents", recents).putBoolean("show_moods", moods).apply()
    }

    val colors = palette(theme)
    when (page) {
        AppPage.APPEARANCE -> AppearancePage(layout, { layout = it }, theme, { theme = it },
            glass, { glass = it }, topPicks, { topPicks = it }, recents, { recents = it },
            moods, { moods = it }, colors) { page = AppPage.HOME }
        AppPage.EXPLORE -> SimplePage("Explore", "Discover music, mixes and new releases.", colors) { page = AppPage.HOME }
        AppPage.LIBRARY -> SimplePage("Your Library", "Your saved music will appear here.", colors) { page = AppPage.HOME }
        AppPage.SEARCH -> SearchPage(colors) { page = AppPage.HOME }
        AppPage.PLAYER -> PlayerPage(future, colors) { page = AppPage.HOME }
        AppPage.HOME -> HomePage(layout, theme, glass, topPicks, recents, moods, colors, future,
            onAppearance = { page = AppPage.APPEARANCE }, onNavigate = { page = it })
    }
}

@Composable
private fun HomePage(
    layout: HomeLayout, theme: VisualTheme, glass: Boolean, showPicks: Boolean, showRecents: Boolean,
    showMoods: Boolean, c: Palette, future: ListenableFuture<MediaController>,
    onAppearance: () -> Unit, onNavigate: (AppPage) -> Unit
) {
    var player by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf("Nothing playing") }
    var artist by remember { mutableStateOf("Add an audio URL to start listening") }
    var playing by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var showUrl by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(listOf<String>()) }
    var connected by remember { mutableStateOf(false) }

    DisposableEffect(future) {
        val listener = Runnable { runCatching { player = future.get(); connected = true }.onFailure { connected = false } }
        future.addListener(listener, Executor { Handler(Looper.getMainLooper()).post(it) })
        onDispose { }
    }
    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = mediaItem?.mediaMetadata?.title?.toString() ?: "Nothing playing"
                artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown artist"
                queue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaMetadata.title?.toString() ?: "Audio" }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        p.addListener(listener)
        if (p.currentMediaItem != null) {
            title = p.currentMediaItem?.mediaMetadata?.title?.toString() ?: title
            artist = p.currentMediaItem?.mediaMetadata?.artist?.toString() ?: artist
            playing = p.isPlaying
        }
        onDispose { p.removeListener(listener) }
    }

    fun playUrl(append: Boolean) {
        val clean = url.trim()
        val p = player ?: return
        if (!clean.startsWith("https://") && !clean.startsWith("http://")) return
        val item = MediaItem.Builder().setUri(clean).setMediaMetadata(
            MediaMetadata.Builder().setTitle(clean.substringAfterLast('/').ifBlank { "Audio stream" })
                .setArtist("Direct stream").build()
        ).build()
        if (append) p.addMediaItem(item) else { p.setMediaItem(item); p.prepare(); p.play() }
        queue = if (append) queue + item.mediaMetadata.title.toString() else listOf(item.mediaMetadata.title.toString())
        url = ""
        showUrl = false
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("≋", color = c.text, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Row(Modifier.clip(CircleShape).background(c.surface).clickable { onAppearance() }
                .padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↑", color = c.text, fontSize = 17.sp)
                Spacer(Modifier.width(12.dp))
                Text("Appearance", color = c.text, fontSize = 12.sp)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (layout == HomeLayout.TOP_PICKS) {
                if (showPicks) {
                    SectionHeader("Top Picks for You", c)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PromoCard("WASTEDYOUTH", "New from Red Leather", "EP · 2026",
                            Color(0xFF315E59), Color(0xFF172C31), 300.dp, c)
                        PromoCard("Discovery Station", "Made for You", "Personalized mix",
                            Color(0xFF482A9C), Color(0xFF244DA0), 300.dp, c)
                    }
                }
                if (showRecents) {
                    SectionHeader("Recents", c)
                    RecentItem("times is changing", "earfzilla", "✿", Color(0xFF6C557B), c) { }
                    RecentItem("Structure (Demo)", "Odd Sweetheart", "S", Color(0xFF8D7770), c) { }
                    RecentItem("GLITCH IN YOUR HEART", "Unknown artist", "✳", Color(0xFF174B32), c) { }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(320.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(c.heroA, c.heroB)))) {
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        Text("CONTINUE LISTENING", color = Color.White.copy(alpha = .7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Your soundtrack", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Picked up right where you left off", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    }
                }
                if (showRecents) {
                    SectionHeader("Recently Played", c)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AlbumTile("After Hours", "The Weeknd", Color(0xFF573D61), c)
                        AlbumTile("Blonde", "Frank Ocean", Color(0xFF8B7257), c)
                        AlbumTile("Gemini Rights", "Steve Lacy", Color(0xFF416C55), c)
                    }
                }
                if (showPicks) {
                    SectionHeader("Top Picks for You", c)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PromoCard("Daily Mix", "Made for You", "Personalized", c.heroA, c.heroB, 245.dp, c)
                        PromoCard("New Releases", "Fresh music", "Updated today", Color(0xFF7C3D55), Color(0xFF322039), 245.dp, c)
                    }
                }
            }
            if (showMoods) {
                SectionHeader("Find Your Mood", c)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("Late night", "Focus", "Dreamy", "Energy").forEach { MoodChip(it, c) }
                }
            }

            if (showUrl) {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Play a direct audio URL", color = c.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(),
                        label = { Text("https://…") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { playUrl(false) }, enabled = player != null && url.startsWith("http")) { Text("Play URL") }
                        TextButton(onClick = { playUrl(true) }, enabled = player != null && url.startsWith("http")) { Text("Add to queue") }
                    }
                }
            }
            if (queue.isNotEmpty()) {
                SectionHeader("Queue · ${queue.size}", c)
                queue.forEachIndexed { index, name ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. $name", color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = {
                            player?.removeMediaItem(index)
                            queue = queue.toMutableList().also { if (index in it.indices) it.removeAt(index) }
                        }) { Text("Remove") }
                    }
                }
            }
        }

        if (player?.currentMediaItem != null || title != "Nothing playing") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(if (glass) c.surface.copy(alpha = .92f) else c.surface)
                .clickable { onNavigate(AppPage.PLAYER) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.accent.copy(alpha = .28f)),
                    contentAlignment = Alignment.Center) { Text("♫", color = c.text, fontSize = 21.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
                    Text(artist, color = c.muted, maxLines = 1, fontSize = 12.sp)
                }
                Text(if (playing) "Ⅱ" else "▶", color = c.text, fontSize = 22.sp,
                    modifier = Modifier.clickable { player?.let { if (it.isPlaying) it.pause() else it.play() } }.padding(horizontal = 10.dp))
                Text("▶▶", color = c.text, fontSize = 16.sp,
                    modifier = Modifier.clickable { player?.seekToNextMediaItem() })
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(34.dp)).background(c.surface).padding(5.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            NavItem("⌂", "Home", true, c) { onNavigate(AppPage.HOME) }
            NavItem("▦", "Explore", false, c) { onNavigate(AppPage.EXPLORE) }
            NavItem("▤", "Library", false, c) { onNavigate(AppPage.LIBRARY) }
            NavItem("⌕", "Search", false, c) { onNavigate(AppPage.SEARCH) }
        }
        TextButton(onClick = { showUrl = !showUrl }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (showUrl) "Close audio URL" else "Add audio URL", color = c.muted)
        }
    }
}

@Composable
private fun AppearancePage(
    layout: HomeLayout, onLayout: (HomeLayout) -> Unit, theme: VisualTheme, onTheme: (VisualTheme) -> Unit,
    glass: Boolean, onGlass: (Boolean) -> Unit, picks: Boolean, onPicks: (Boolean) -> Unit,
    recents: Boolean, onRecents: (Boolean) -> Unit, moods: Boolean, onMoods: (Boolean) -> Unit,
    c: Palette, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = c.text, fontSize = 32.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Appearance", color = c.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Make LyricGlow yours", color = c.muted, fontSize = 13.sp)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Home Screen", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("Choose a layout. You can change it anytime.", color = c.muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LayoutPreview(HomeLayout.TOP_PICKS, layout == HomeLayout.TOP_PICKS, c, Modifier.weight(1f)) { onLayout(HomeLayout.TOP_PICKS) }
                LayoutPreview(HomeLayout.IMMERSIVE, layout == HomeLayout.IMMERSIVE, c, Modifier.weight(1f)) { onLayout(HomeLayout.IMMERSIVE) }
            }
            Text("Visual Theme", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("Lightweight skeletal previews; no album artwork is loaded.", color = c.muted, fontSize = 12.sp)
            VisualTheme.values().forEach { item -> ThemePreview(item, theme == item, c) { onTheme(item) } }
            Text("Home Sections", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            ToggleRow("Top Picks for You", "Recommendation cards", picks, onPicks, c)
            ToggleRow("Recents / Recently Played", "Listening history section", recents, onRecents, c)
            ToggleRow("Find Your Mood", "Mood and discovery shortcuts", moods, onMoods, c)
            Text("Effects", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            ToggleRow("Liquid Glass appearance", "Uses translucent surfaces; advanced real-time blur is a later renderer step.", glass, onGlass, c)
            Spacer(Modifier.height(20.dp))
        }
        Text("Changes save automatically", color = c.muted, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
    }
}

@Composable
private fun LayoutPreview(layout: HomeLayout, selected: Boolean, c: Palette, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(if (selected) c.accent.copy(alpha = .15f) else c.surface)
        .clickable { onClick() }.padding(9.dp)) {
        Box(Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF15151B)).padding(7.dp)) {
            if (layout == HomeLayout.TOP_PICKS) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(5.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF426B63), Color(0xFF263A3D)))))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) { Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF41414A))) }
                    }
                    repeat(2) { Box(Modifier.fillMaxWidth(.8f).height(5.dp).clip(CircleShape).background(Color(0xFF55555E))) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(5.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF674A8C), Color(0xFF273C62)))))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF454550))) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(layout.label, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(if (selected) "Selected" else "Tap to use", color = c.muted, fontSize = 11.sp)
    }
}

@Composable
private fun ThemePreview(theme: VisualTheme, selected: Boolean, c: Palette, onClick: () -> Unit) {
    val p = palette(theme)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
        .background(if (selected) c.accent.copy(alpha = .14f) else c.surface)
        .clickable { onClick() }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(82.dp).height(88.dp).clip(RoundedCornerShape(9.dp)).background(p.bg).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(5.dp))
                .background(Brush.linearGradient(listOf(p.heroA, p.heroB))))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(17.dp).clip(RoundedCornerShape(4.dp)).background(p.raised)) }
            }
            Box(Modifier.fillMaxWidth(.72f).height(4.dp).clip(CircleShape).background(p.muted))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(p.surface))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(theme.label, color = c.text, fontWeight = FontWeight.SemiBold)
            Text(when (theme) {
                VisualTheme.CRYSTAL -> "Cool glass and layered depth"
                VisualTheme.NOCTURNE -> "Deep dark with vivid accents"
                VisualTheme.ATELIER -> "Light editorial surfaces"
            }, color = c.muted, fontSize = 11.sp)
        }
        Text(if (selected) "✓" else "○", color = c.accent, fontSize = 21.sp)
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, c: Palette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontWeight = FontWeight.Medium)
            Text(detail, color = c.muted, fontSize = 11.sp)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun PlayerPage(future: ListenableFuture<MediaController>, c: Palette, onBack: () -> Unit) {
    var player by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf("Nothing playing") }
    var artist by remember { mutableStateOf("Unknown artist") }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableFloatStateOf(0f) }
    var position by remember { mutableFloatStateOf(0f) }
    var seeking by remember { mutableStateOf(false) }

    DisposableEffect(future) {
        future.addListener({ runCatching { player = future.get() } }, Executor { Handler(Looper.getMainLooper()).post(it) })
        onDispose { }
    }
    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = mediaItem?.mediaMetadata?.title?.toString() ?: "Nothing playing"
                artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown artist"
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }
    LaunchedEffect(player, playing) {
        while (player != null && playing) {
            if (!seeking) {
                position = player!!.currentPosition.coerceAtLeast(0L).toFloat()
                duration = player!!.duration.coerceAtLeast(0L).toFloat()
            }
            delay(400)
        }
    }
    Column(Modifier.fillMaxSize().background(c.bg).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("‹  Now Playing", color = c.text, fontSize = 23.sp, modifier = Modifier.clickable { onBack() })
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(c.heroA, c.heroB))), contentAlignment = Alignment.Center) {
            Text("♫", color = Color.White, fontSize = 100.sp)
        }
        Text(title, color = c.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(artist, color = c.muted, fontSize = 16.sp)
        Slider(value = if (duration > 0) position.coerceIn(0f, duration) else 0f,
            onValueChange = { seeking = true; position = it },
            onValueChangeFinished = { player?.seekTo(position.toLong()); seeking = false },
            valueRange = 0f..duration.coerceAtLeast(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position.toLong()), color = c.muted)
            Text(formatTime(duration.toLong()), color = c.muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { player?.seekToPreviousMediaItem() }) { Text("Previous") }
            Button(onClick = { player?.let { if (it.isPlaying) it.pause() else it.play() } }) { Text(if (playing) "Pause" else "Play") }
            TextButton(onClick = { player?.seekToNextMediaItem() }) { Text("Next") }
        }
    }
}

@Composable
private fun SimplePage(title: String, subtitle: String, c: Palette, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(c.bg).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("‹  $title", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() })
        Text(subtitle, color = c.muted)
        listOf("New Releases", "Made for You", "Popular right now", "Genres & moods").forEach {
            Card(colors = CardDefaults.cardColors(containerColor = c.surface)) {
                Text(it, color = c.text, modifier = Modifier.fillMaxWidth().padding(18.dp))
            }
        }
    }
}

@Composable
private fun SearchPage(c: Palette, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(c.bg).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("‹  Search", color = c.text, fontSize = 26.sp, modifier = Modifier.clickable { onBack() })
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Songs, artists, albums") })
        Text("Provider search integration is a future milestone.", color = c.muted)
    }
}

@Composable
private fun SectionHeader(title: String, c: Palette) {
    Text(title, color = c.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun PromoCard(title: String, subtitle: String, detail: String, a: Color, b: Color, width: androidx.compose.ui.unit.Dp, c: Palette) {
    Box(Modifier.width(width).height(300.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(a, b)))) {
        Column(Modifier.fillMaxSize().padding(17.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 2)
            Column {
                Text(subtitle, color = Color.White.copy(alpha = .75f), fontSize = 14.sp)
                Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RecentItem(title: String, artist: String, glyph: String, color: Color, c: Palette, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(glyph, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(artist, color = c.muted, fontSize = 14.sp, maxLines = 1)
        }
        Text("⋮", color = c.muted, fontSize = 23.sp)
    }
}

@Composable
private fun AlbumTile(title: String, artist: String, color: Color, c: Palette) {
    Column(Modifier.width(126.dp)) {
        Box(Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(14.dp)).background(color),
            contentAlignment = Alignment.Center) { Text("♫", color = Color.White, fontSize = 38.sp) }
        Spacer(Modifier.height(7.dp))
        Text(title, color = c.text, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(artist, color = c.muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun MoodChip(label: String, c: Palette) {
    Box(Modifier.clip(CircleShape).background(c.raised).padding(horizontal = 17.dp, vertical = 10.dp)) {
        Text(label, color = c.text, fontSize = 13.sp)
    }
}

@Composable
private fun NavItem(symbol: String, label: String, selected: Boolean, c: Palette, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(28.dp)).background(if (selected) c.raised else Color.Transparent)
        .clickable { onClick() }.padding(horizontal = 15.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, color = if (selected) c.text else c.muted, fontSize = 23.sp)
        Text(label, color = if (selected) c.text else c.muted, fontSize = 11.sp)
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds < 0) return "0:00"
    val seconds = milliseconds / 1000
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
