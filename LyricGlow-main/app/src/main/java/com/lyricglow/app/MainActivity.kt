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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.blur
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
private enum class VisualTheme(val label: String) { MONO_GREEN("Mono Green"), CARBON("Carbon"), PAPER("Paper") }
private enum class AppPage { HOME, SEARCH, LIBRARY, PROFILE, PLAYER, APPEARANCE }

private data class Palette(
    val bg: Color, val surface: Color, val raised: Color, val text: Color,
    val muted: Color, val accent: Color, val heroA: Color, val heroB: Color
)

private fun palette(theme: VisualTheme) = when (theme) {
    VisualTheme.MONO_GREEN -> Palette(Color(0xFF080A08), Color(0xFF171B17), Color(0xFF252C25), Color(0xFFF4F7F2), Color(0xFF9AA49A), Color(0xFF8BE28B), Color(0xFF263D2A), Color(0xFF101A12))
    VisualTheme.CARBON -> Palette(Color(0xFF090A0B), Color(0xFF181A1D), Color(0xFF292C30), Color(0xFFF5F6F7), Color(0xFFA0A4AA), Color(0xFFBFC7C2), Color(0xFF343B38), Color(0xFF111514))
    VisualTheme.PAPER -> Palette(Color(0xFFF1F2EF), Color(0xFFFFFFFF), Color(0xFFE2E6DF), Color(0xFF171B17), Color(0xFF687168), Color(0xFF347A43), Color(0xFFC9DCCB), Color(0xFFE7EEE6))
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
        mutableStateOf(runCatching { VisualTheme.valueOf(prefs.getString("visual_theme", "MONO_GREEN")!!) }.getOrDefault(VisualTheme.MONO_GREEN))
    }
    var glass by remember { mutableStateOf(prefs.getBoolean("glass_enabled", true)) }
    var forceBlur by remember { mutableStateOf(prefs.getBoolean("force_blur", false)) }
    var recents by remember { mutableStateOf(prefs.getBoolean("show_recents", true)) }
    var page by remember { mutableStateOf(AppPage.HOME) }

    LaunchedEffect(layout, theme, glass, forceBlur, recents) {
        prefs.edit().putString("home_layout", layout.name).putString("visual_theme", theme.name)
            .putBoolean("glass_enabled", glass).putBoolean("force_blur", forceBlur)
            .putBoolean("show_recents", recents).apply()
    }

    val colors = palette(theme)
    val mainTab = page == AppPage.HOME || page == AppPage.SEARCH || page == AppPage.LIBRARY || page == AppPage.PROFILE
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                AppPage.APPEARANCE -> AppearancePage(layout, { layout = it }, theme, { theme = it },
                    glass, { glass = it }, forceBlur, { forceBlur = it }, recents, { recents = it }, colors) { page = AppPage.PROFILE }
                AppPage.LIBRARY -> SimplePage("Your Library", "Your saved music and playlists will appear here.", colors) { page = AppPage.HOME }
                AppPage.SEARCH -> SearchPage(colors) { page = AppPage.HOME }
                AppPage.PROFILE -> ProfilePage(colors, onAppearance = { page = AppPage.APPEARANCE })
                AppPage.PLAYER -> PlayerPage(future, colors) { page = AppPage.HOME }
                AppPage.HOME -> HomePage(layout, theme, glass, forceBlur, recents, colors, future,
                    onAppearance = { page = AppPage.PROFILE }, onNavigate = { page = it })
            }
        }
        if (mainTab) MainTabBar(page, colors) { page = it }
    }
}

@Composable
private fun HomePage(
    layout: HomeLayout, theme: VisualTheme, glass: Boolean, forceBlur: Boolean, showRecents: Boolean,
    c: Palette, future: ListenableFuture<MediaController>,
    onAppearance: () -> Unit, onNavigate: (AppPage) -> Unit
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf("Nothing playing") }
    var artist by remember { mutableStateOf("Add an audio URL to start listening") }
    var playing by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var showUrl by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(listOf<String>()) }
    var connected by remember { mutableStateOf(false) }
    val historyPrefs = remember { context.getSharedPreferences("lyricglow_history", 0) }
    var history by remember {
        mutableStateOf(historyPrefs.getString("items", "")!!.split("\n").filter { it.contains("|") }
            .mapNotNull { row ->
                val parts = row.split("|", limit = 3)
                if (parts.size == 3) Triple(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L) else null
            })
    }
    fun rememberTrack() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val t = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return
        val a = item.mediaMetadata.artist?.toString() ?: "Unknown artist"
        val entry = Triple(t.replace("|", " "), a.replace("|", " "), System.currentTimeMillis())
        history = (listOf(entry) + history.filterNot { it.first == entry.first && it.second == entry.second }).take(30)
        historyPrefs.edit().putString("items", history.joinToString("\n") { "${it.first}|${it.second}|${it.third}" }).apply()
    }

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
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        p.addListener(listener)
        if (p.currentMediaItem != null) {
            title = p.currentMediaItem?.mediaMetadata?.title?.toString() ?: title
            artist = p.currentMediaItem?.mediaMetadata?.artist?.toString() ?: artist
            playing = p.isPlaying
        }
        onDispose { p.removeListener(listener) }
    }
    LaunchedEffect(player, playing, title) {
        if (player != null && playing && title != "Nothing playing") {
            delay(10_000)
            if (player?.isPlaying == true) rememberTrack()
        }
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
                Text("Profile", color = c.text, fontSize = 12.sp)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(if (layout == HomeLayout.TOP_PICKS) "Your music" else "Listening space",
                color = c.text, fontSize = 27.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp))
            if (showRecents && history.isNotEmpty()) {
                SectionHeader("Recently Played", c)
                history.forEach { item ->
                    RecentItem(item.first, item.second, "♫", c.raised, c) { }
                }
            } else if (showRecents) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(18.dp)).background(c.surface).padding(18.dp)) {
                    Text("Nothing here yet", color = c.text, fontWeight = FontWeight.SemiBold)
                    Text("Your recently played music will appear after you start listening.",
                        color = c.muted, fontSize = 13.sp)
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
                .then(if (forceBlur) Modifier.blur(10.dp) else Modifier)
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

        TextButton(onClick = { showUrl = !showUrl }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (showUrl) "Close audio URL" else "Add audio URL", color = c.muted)
        }
    }
}

@Composable
private fun ProfilePage(c: Palette, onAppearance: () -> Unit) {
    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Profile", color = c.text, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(c.raised), contentAlignment = Alignment.Center) {
                Text("♫", color = c.text, fontSize = 27.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Your LyricGlow profile", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("Account and listening preferences", color = c.muted, fontSize = 12.sp)
            }
        }
        Text("Settings", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        ProfileSettingRow("Appearance", "Themes, home screen and visual effects", "◐", c, onAppearance)
        ProfileSettingRow("Playback", "Playback behaviour and audio quality", "♫", c) { }
        ProfileSettingRow("Privacy", "Your profile and listening activity", "⌑", c) { }
        ProfileSettingRow("About LyricGlow", "Version, licences and help", "ⓘ", c) { }
    }
}

@Composable
private fun ProfileSettingRow(title: String, subtitle: String, icon: String, c: Palette, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
        .clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = c.accent, fontSize = 23.sp, modifier = Modifier.width(38.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontWeight = FontWeight.Medium)
            Text(subtitle, color = c.muted, fontSize = 11.sp)
        }
        Text("›", color = c.muted, fontSize = 24.sp)
    }
}

@Composable
private fun MainTabBar(selected: AppPage, c: Palette, onSelect: (AppPage) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(34.dp)).background(c.surface).padding(5.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        NavItem("⌂", "Home", selected == AppPage.HOME, c) { onSelect(AppPage.HOME) }
        NavItem("⌕", "Search", selected == AppPage.SEARCH, c) { onSelect(AppPage.SEARCH) }
        NavItem("▤", "Library", selected == AppPage.LIBRARY, c) { onSelect(AppPage.LIBRARY) }
        NavItem("●", "Profile", selected == AppPage.PROFILE, c) { onSelect(AppPage.PROFILE) }
    }
}

@Composable
private fun AppearancePage(
    layout: HomeLayout, onLayout: (HomeLayout) -> Unit, theme: VisualTheme, onTheme: (VisualTheme) -> Unit,
    glass: Boolean, onGlass: (Boolean) -> Unit, forceBlur: Boolean, onForceBlur: (Boolean) -> Unit,
    recents: Boolean, onRecents: (Boolean) -> Unit, c: Palette, onBack: () -> Unit
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
            Text("Listening history", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            ToggleRow("Recently Played", "Show tracks you have actually listened to.", recents, onRecents, c)
            Text("Effects", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            ToggleRow("Glass surfaces", "Translucent player and panel surfaces.", glass, onGlass, c)
            ToggleRow("Force blur (experimental)", "Try blur even when the device does not report support.", forceBlur, onForceBlur, c)
            if (forceBlur) {
                Text("⚠ Forced blur may cause lag, battery drain, or rendering glitches on unsupported devices.",
                    color = Color(0xFFFFC857), fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFC857)).padding(12.dp))
            }
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
                VisualTheme.MONO_GREEN -> "Monochrome surfaces with green highlights"
                VisualTheme.CARBON -> "Neutral graphite with soft highlights"
                VisualTheme.PAPER -> "Light surfaces with green highlights"
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
        Text("This section is waiting for a music provider or library source.",
            color = c.muted, fontSize = 13.sp)
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
