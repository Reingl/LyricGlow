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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lyricglow.app.data.YoutubeMusicRepository
import com.lyricglow.app.data.MusicTrack
import com.lyricglow.app.auth.SupabaseAuthRepository
import com.lyricglow.app.auth.SupabaseLibrarySync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executor

private enum class HomeLayout(val label: String) { TOP_PICKS("Standard"), IMMERSIVE("Immersive") }
private enum class VisualTheme(val label: String) { MONO_GREEN("Mono Green"), CARBON("Carbon"), PAPER("Paper") }
private enum class AppPage { HOME, SEARCH, LIBRARY, PROFILE, PLAYER, APPEARANCE, ACCOUNT }

private data class ListeningEntry(val title: String, val artist: String, val timestamp: Long)

private data class Palette(
    val bg: Color, val surface: Color, val raised: Color, val text: Color,
    val muted: Color, val accent: Color, val heroA: Color, val heroB: Color
)

private fun palette(theme: VisualTheme) = when (theme) {
    VisualTheme.MONO_GREEN -> Palette(Color(0xFF080A09), Color(0xFF171B18), Color(0xFF252D28),
        Color(0xFFF4F7F4), Color(0xFFA1ADA4), Color(0xFF69D391), Color(0xFF244B35), Color(0xFF101B14))
    VisualTheme.CARBON -> Palette(Color(0xFF090A0B), Color(0xFF191B1E), Color(0xFF292D31),
        Color(0xFFF4F5F6), Color(0xFFA5A9AE), Color(0xFFB8C2BC), Color(0xFF343B38), Color(0xFF111513))
    VisualTheme.PAPER -> Palette(Color(0xFFF2F3EF), Color(0xFFFFFFFF), Color(0xFFE2E7DF),
        Color(0xFF172019), Color(0xFF657168), Color(0xFF24834C), Color(0xFFB7D8C1), Color(0xFFE6EEE7))
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
    var glass by remember { mutableStateOf(prefs.getBoolean("glass_enabled", false)) }
    var forceBlur by remember { mutableStateOf(prefs.getBoolean("force_blur", false)) }
    var accentIndex by remember { mutableIntStateOf(prefs.getInt("accent_index", 0)) }
    val accentOptions = listOf(Color(0xFF69D391), Color(0xFF69B7E8), Color(0xFFE7B86A), Color(0xFFD58BE8), Color(0xFFE77F8B))
    val historyPrefs = remember { context.getSharedPreferences("lyricglow_history", 0) }
    var history by remember { mutableStateOf(historyPrefs.getString("entries", "")!!.split("\n").filter { it.contains("\u001f") }.mapNotNull { row -> val a = row.split("\u001f"); if (a.size == 3) ListeningEntry(a[0], a[1], a[2].toLongOrNull() ?: 0L) else null }) }
    var topPicks by remember { mutableStateOf(prefs.getBoolean("show_top_picks", true)) }
    var recents by remember { mutableStateOf(prefs.getBoolean("show_recents", true)) }
    var moods by remember { mutableStateOf(prefs.getBoolean("show_moods", true)) }
    var page by remember { mutableStateOf(AppPage.HOME) }

    LaunchedEffect(layout, theme, glass, forceBlur, accentIndex, topPicks, recents, moods) {
        prefs.edit().putString("home_layout", layout.name).putString("visual_theme", theme.name)
             .putBoolean("glass_enabled", glass).putBoolean("force_blur", forceBlur).putInt("accent_index", accentIndex).putBoolean("show_top_picks", topPicks)
            .putBoolean("show_recents", recents).putBoolean("show_moods", moods).apply()
    }

    val colors = palette(theme).copy(accent = accentOptions[accentIndex.coerceIn(accentOptions.indices)])
    fun recordHistory(entry: ListeningEntry) {
        val now = System.currentTimeMillis()
        val updated = (listOf(entry.copy(timestamp = now)) + history.filterNot { it.title == entry.title && it.artist == entry.artist && now - it.timestamp < 30 * 60 * 1000L }).take(30)
        history = updated
        historyPrefs.edit().putString("entries", updated.joinToString("\n") { "${it.title}\u001f${it.artist}\u001f${it.timestamp}" }).apply()
    }
    val mainTab = page == AppPage.HOME || page == AppPage.SEARCH || page == AppPage.LIBRARY || page == AppPage.PROFILE
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                AppPage.APPEARANCE -> AppearancePage(layout, { layout = it }, theme, { theme = it },
                    glass, { glass = it }, forceBlur, { forceBlur = it }, accentIndex, { accentIndex = it }, topPicks, { topPicks = it }, recents, { recents = it },
                    moods, { moods = it }, colors) { page = AppPage.PROFILE }
                AppPage.LIBRARY -> LibraryPage(future, colors) { page = AppPage.HOME }
                AppPage.SEARCH -> SearchPage(future, colors) { page = AppPage.HOME }
                AppPage.PROFILE -> ProfilePage(colors, onAppearance = { page = AppPage.APPEARANCE }, onAccount = { page = AppPage.ACCOUNT })
                AppPage.ACCOUNT -> AccountPage(colors) { page = AppPage.PROFILE }
                AppPage.PLAYER -> PlayerPage(future, colors) { page = AppPage.HOME }
                AppPage.HOME -> HomePage(layout, theme, glass, forceBlur, topPicks, recents, moods, history, ::recordHistory, colors, future,
                    onAppearance = { page = AppPage.PROFILE }, onNavigate = { page = it })
            }
        }
        if (mainTab) MainTabBar(page, colors) { page = it }
    }
}

@Composable
private fun HomePage(
    layout: HomeLayout, theme: VisualTheme, glass: Boolean, forceBlur: Boolean, showPicks: Boolean, showRecents: Boolean,
    showMoods: Boolean, history: List<ListeningEntry>, onHistory: (ListeningEntry) -> Unit, c: Palette, future: ListenableFuture<MediaController>,
    onAppearance: () -> Unit, onNavigate: (AppPage) -> Unit
) {
    var player by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf("Nothing playing") }
    var artist by remember { mutableStateOf("Search YouTube Music to start listening") }
    var playing by remember { mutableStateOf(false) }
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

    LaunchedEffect(player, playing, title, artist) {
        val activePlayer = player ?: return@LaunchedEffect
        if (!playing || title == "Nothing playing") return@LaunchedEffect
        delay(10_000)
        if (activePlayer.isPlaying && activePlayer.currentMediaItem != null &&
            activePlayer.currentMediaItem?.mediaMetadata?.title?.toString() == title) {
            onHistory(ListeningEntry(title, artist, System.currentTimeMillis()))
        }
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
            if (layout == HomeLayout.IMMERSIVE) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(c.heroA, c.heroB)))) {
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        Text("NOW PLAYING", color = Color.White.copy(alpha = .72f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(artist, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    }
                }
            } else {
                SectionHeader("Your music", c)
                Text(if (player?.currentMediaItem == null) "Search YouTube Music to find something to play." else "Continue listening to $title", color = c.muted,
                    modifier = Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            }
            if (showRecents && history.isNotEmpty()) {
                SectionHeader("Recently Played", c)
                history.forEach { entry ->
                    RecentItem(entry.title, entry.artist, "♫", c.raised, c) { }
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
                .background(if (glass || forceBlur) c.surface.copy(alpha = .72f) else c.surface)
                .then(if (forceBlur) Modifier.blur(18.dp) else Modifier)
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


    }
}

@Composable
private fun ProfilePage(c: Palette, onAppearance: () -> Unit, onAccount: () -> Unit) {
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
        ProfileSettingRow("Account", "Sign in to sync your library and preferences", "◎", c, onAccount)
        ProfileSettingRow("Appearance", "Themes, home screen and visual effects", "◐", c, onAppearance)
        ProfileSettingRow("Playback", "Playback behaviour and audio quality", "♫", c) { }
        ProfileSettingRow("Privacy", "Your profile and listening activity", "⌑", c) { }
        ProfileSettingRow("About LyricGlow", "Version, licences and help", "ⓘ", c) { }
    }
}

@Composable
private fun AccountPage(c: Palette, onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { SupabaseAuthRepository(context) }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(auth.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(auth.isSignedIn) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("‹  Account", color = c.text, fontSize = 26.sp, modifier = Modifier.clickable { onBack() })
        Text("LyricGlow account", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("This account syncs your LyricGlow library and settings. It is not a Google or YouTube sign-in.", color = c.muted)
        if (signedIn) {
            Text("Signed in as ${auth.email ?: email}", color = c.accent)
            Button(onClick = {
                busy = true; message = "Backing up saved tracks…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) { SupabaseLibrarySync(context).syncSavedTracks() }
                    result.onSuccess { message = "Cloud backup complete · $it saved tracks" }.onFailure { message = it.message }
                    busy = false
                }
            }, enabled = !busy) { Text("Sync saved tracks") }
            OutlinedButton(onClick = { auth.signOut(); signedIn = false; message = "Signed out." }, enabled = !busy) { Text("Sign out") }
        } else {
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (email.isBlank() || password.length < 6) { message = "Enter a valid email and a password with at least 6 characters."; return@Button }
                    busy = true; message = null
                    val secret = password.toCharArray(); password = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { auth.signIn(email, secret) }
                        result.onSuccess { signedIn = true; message = "Signed in." }.onFailure { message = it.message }
                        busy = false
                    }
                }, enabled = !busy) { Text(if (busy) "Please wait…" else "Sign in") }
                OutlinedButton(onClick = {
                    if (email.isBlank() || password.length < 6) { message = "Enter a valid email and a password with at least 6 characters."; return@OutlinedButton }
                    busy = true; message = null
                    val secret = password.toCharArray(); password = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { auth.signUp(email, secret) }
                        result.onSuccess { created -> message = if (created) { signedIn = true; "Account created and signed in." } else "Account created. Check your email to confirm, then sign in." }
                            .onFailure { message = it.message }
                        busy = false
                    }
                }, enabled = !busy) { Text("Create account") }
            }
            Text("If email confirmation is enabled in Supabase, verify your address before signing in.", color = c.muted, fontSize = 12.sp)
        }
        message?.let { Text(it, color = if (it.contains("fail", true) || it.contains("enter", true)) Color(0xFFFFB45C) else c.accent) }
        if (com.lyricglow.app.BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            Text("Supabase publishable key is missing. Add SUPABASE_PUBLISHABLE_KEY to local.properties or the GitHub Actions secret.", color = Color(0xFFFFB45C), fontSize = 12.sp)
        }
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
    glass: Boolean, onGlass: (Boolean) -> Unit, forceBlur: Boolean, onForceBlur: (Boolean) -> Unit, accentIndex: Int, onAccent: (Int) -> Unit, picks: Boolean, onPicks: (Boolean) -> Unit,
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
            Text("Minimal surfaces with an adjustable accent.", color = c.muted, fontSize = 12.sp)
            VisualTheme.values().forEach { item -> ThemePreview(item, theme == item, c) { onTheme(item) } }
            Text("Accent Colour", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Color(0xFF69D391), Color(0xFF69B7E8), Color(0xFFE7B86A), Color(0xFFD58BE8), Color(0xFFE77F8B)).forEachIndexed { index, color ->
                    Box(Modifier.size(38.dp).clip(CircleShape).background(color).clickable { onAccent(index) }, contentAlignment = Alignment.Center) {
                        if (accentIndex == index) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Home Sections", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            ToggleRow("Recently Played", "Show real listening history only", recents, onRecents, c)
            Text("Effects", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            ToggleRow("Translucent surfaces", "Use semi-transparent player surfaces", glass, onGlass, c)
            ToggleRow("Force blur (experimental)", "Apply blur even when device support is uncertain", forceBlur, onForceBlur, c)
            if (forceBlur) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFE08A)).padding(12.dp), verticalAlignment = Alignment.Top) {
                    Text("⚠", color = Color(0xFF5A3A00), fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Forced blur may cause lag, extra battery use, or rendering issues. Disable it if the interface stutters. Android versions and GPUs may still limit blur support.", color = Color(0xFF5A3A00), fontSize = 12.sp)
                }
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
                VisualTheme.MONO_GREEN -> "Monochrome with a green accent"
                VisualTheme.CARBON -> "Neutral dark surfaces"
                VisualTheme.PAPER -> "Clean light surfaces"
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
        Text("Your library is empty", color = c.text, fontWeight = FontWeight.SemiBold)
        Text("Saved tracks and playlists will appear here when library support is connected.", color = c.muted)
    }
}

@Composable
private fun LibraryPage(future: ListenableFuture<MediaController>, c: Palette, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { YoutubeMusicRepository(context) }
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaController?>(null) }
    var saved by remember { mutableStateOf(repository.savedTracks()) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(future) {
        future.addListener({ runCatching { player = future.get() } }, Executor { Handler(Looper.getMainLooper()).post(it) })
        onDispose { }
    }
    Column(Modifier.fillMaxSize().background(c.bg).padding(20.dp)) {
        Text("‹  Your Library", color = c.text, fontSize = 26.sp, modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(12.dp))
        Text("Saved songs", color = c.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        message?.let { Text(it, color = c.muted) }
        if (saved.isEmpty()) Text("Nothing saved yet. Search for music and tap Save to build your library.", color = c.muted, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(saved, key = { _, track -> track.id }) { _, track ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).clickable {
                    val controller = player
                    if (controller == null) { message = "Player is starting…"; return@clickable }
                    message = "Preparing ${track.title}…"
                    scope.launch {
                        try {
                            val stream = repository.resolveAudio(track.pageUrl)
                            val item = MediaItem.Builder().setMediaId(track.id).setUri(stream.url).setMediaMetadata(
                                MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()).build()
                            controller.setMediaItem(item); controller.prepare(); controller.play(); message = "Now playing"
                        } catch (e: Exception) { message = e.message ?: "Playback failed." }
                    }
                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("♫", color = c.accent, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) { Text(track.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(track.artist, color = c.muted, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun SearchPage(future: ListenableFuture<MediaController>, c: Palette, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { YoutubeMusicRepository(context) }
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaController?>(null) }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var albums by remember { mutableStateOf(emptyList<com.lyricglow.app.data.MusicAlbum>()) }
    var albumMode by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    DisposableEffect(future) {
        future.addListener({ runCatching { player = future.get() } }, Executor { Handler(Looper.getMainLooper()).post(it) })
        onDispose { }
    }
    fun search() {
        if (query.trim().length < 2 || loading) return
        loading = true; error = null; status = null
        scope.launch {
            try {
                if (albumMode) albums = repository.searchAlbums(query)
                else tracks = repository.search(query).tracks
                if (albumMode && albums.isEmpty()) status = "No albums found. Try another search."
                if (!albumMode && tracks.isEmpty()) status = "No songs found. Try another search."
            } catch (e: Exception) { error = e.message ?: "Search failed." }
            finally { loading = false }
        }
    }
    fun play(track: MusicTrack, append: Boolean = false) {
        val controller = player
        if (controller == null) { error = "Player is still starting. Try again in a moment."; return }
        status = "Preparing ${track.title}…"; error = null
        scope.launch {
            try {
                val stream = repository.resolveAudio(track.pageUrl)
                val metadata = MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist)
                    .apply { track.artworkUrl?.let { setArtworkUri(android.net.Uri.parse(it)) } }.build()
                val item = MediaItem.Builder().setMediaId(track.id).setUri(stream.url).setMediaMetadata(metadata).build()
                if (append) controller.addMediaItem(item) else controller.setMediaItem(item)
                if (!append) controller.prepare()
                controller.play()
                status = if (append) "Added to queue" else "Now playing"
            } catch (e: Exception) { error = e.message ?: "Couldn't prepare this track."; status = null }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("‹  Search", color = c.text, fontSize = 26.sp, modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Songs, artists, albums") }, singleLine = true,
            trailingIcon = { TextButton(onClick = ::search, enabled = !loading && query.trim().length >= 2) { Text("Search") } })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
            FilterChip(selected = !albumMode, onClick = { albumMode = false }, label = { Text("Songs") })
            FilterChip(selected = albumMode, onClick = { albumMode = true }, label = { Text("Albums") })
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent)
        status?.let { Text(it, color = c.muted, modifier = Modifier.padding(vertical = 8.dp)) }
        error?.let { Text(it, color = Color(0xFFFFB45C), modifier = Modifier.padding(vertical = 8.dp)) }
        if (!albumMode) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(tracks, key = { _, item -> item.id }) { _, track ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).clickable { play(track) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(c.raised), contentAlignment = Alignment.Center) { Text("♫", color = c.accent, fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(track.artist + if (track.durationSeconds > 0) " · ${formatTime(track.durationSeconds)}" else "", color = c.muted, fontSize = 12.sp, maxLines = 1)
                        }
                        TextButton(onClick = { repository.saveTrack(track); status = "Saved to your library" }) { Text("Save") }
                        TextButton(onClick = { play(track, true) }) { Text("+ Queue") }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(albums, key = { _, item -> item.id }) { _, album ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(c.raised), contentAlignment = Alignment.Center) { Text("▦", color = c.accent, fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(album.title, color = c.text, fontWeight = FontWeight.SemiBold); Text(album.artist, color = c.muted, fontSize = 12.sp) }
                        TextButton(onClick = {
                            scope.launch {
                                loading = true; error = null; status = "Loading album…"
                                try {
                                    val list = repository.loadAlbum(album.pageUrl)
                                    val controller = player ?: throw IllegalStateException("Player is still starting.")
                                    val mediaItems = list.map { track ->
                                        val stream = repository.resolveAudio(track.pageUrl)
                                        MediaItem.Builder().setMediaId(track.id).setUri(stream.url).setMediaMetadata(
                                            MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist)
                                                .apply { track.artworkUrl?.let { setArtworkUri(android.net.Uri.parse(it)) } }.build()
                                        ).build()
                                    }
                                    if (mediaItems.isEmpty()) throw IllegalStateException("This album has no playable tracks.")
                                    controller.setMediaItems(mediaItems); controller.prepare(); controller.play()
                                    status = "Playing album · ${mediaItems.size} tracks"
                                } catch (e: Exception) { error = e.message ?: "Couldn't load album."; status = null }
                                finally { loading = false }
                            }
                        }) { Text("Play album") }
                    }
                }
            }
        }
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
