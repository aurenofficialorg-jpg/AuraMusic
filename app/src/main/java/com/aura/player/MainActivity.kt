package com.aura.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Songs("Songs", Icons.Rounded.MusicNote),
    Albums("Albums", Icons.Rounded.Album),
    Artists("Artists", Icons.Rounded.Person),
    Playlists("Library", Icons.Rounded.LibraryMusic)
}

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by vm.theme.collectAsState()
            val dark = when (mode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
            val scheme = if (dark) darkColorScheme(
                primary = Accent, background = Color.Black, surface = Color.Black,
                surfaceVariant = Color(0xFF1C1C1E), onSurface = Color.White, onSurfaceVariant = Color(0xFF9A9AA0)
            ) else lightColorScheme(
                primary = Accent, background = Color.White, surface = Color.White,
                surfaceVariant = Color(0xFFF0F0F3), onSurface = Color.Black, onSurfaceVariant = Color(0xFF6E6E73)
            )
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalAnimationApi::class)
    @Composable
    private fun App() {
        val ui by vm.ui.collectAsState()
        val songs by vm.songs.collectAsState()
        var granted by remember { mutableStateOf(false) }
        var showPlayer by remember { mutableStateOf(false) }
        var tab by remember { mutableStateOf(Tab.Home) }
        var detail by remember { mutableStateOf<Pair<String, String>?>(null) }

        val perms = remember {
            buildList {
                if (Build.VERSION.SDK_INT >= 33) { add(Manifest.permission.READ_MEDIA_AUDIO); add(Manifest.permission.POST_NOTIFICATIONS) }
                else add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }.toTypedArray()
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            granted = res[audio] == true
            if (granted) vm.loadSongs()
        }
        LaunchedEffect(Unit) { launcher.launch(perms) }

        if (!granted) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aura needs access to the music on your device.")
                    Spacer(Modifier.height(16.dp))
                    Button({ launcher.launch(perms) }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Allow access") }
                }
            }
            return
        }

        val hasTrack = ui.currentId != null
        BoxWithConstraints(Modifier.fillMaxSize()) {
            AuroraBackground()
            // Tablets / landscape: side rail instead of the bottom bar, and content capped to a readable width.
            val rail = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                if (rail) NavigationRail(containerColor = Color.Transparent, modifier = Modifier.glass(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))) {
                    Spacer(Modifier.weight(1f))
                    Tab.values().forEach { t ->
                        NavigationRailItem(
                            selected = tab == t, onClick = { tab = t; detail = null },
                            icon = { Icon(t.icon, t.label) }, label = { Text(t.label, maxLines = 1) }, alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Accent.copy(alpha = 0.15f)
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                            AnimatedContent(
                                targetState = tab to detail, label = "screens",
                                transitionSpec = {
                                    val e = tween<androidx.compose.ui.unit.IntOffset>(420, easing = FastOutSlowInEasing)
                                    val opening = targetState.second != null && initialState.second == null
                                    val closing = targetState.second == null && initialState.second != null
                                    when {
                                        opening -> (slideInHorizontally(e) { it / 3 } + fadeIn(tween(420))) togetherWith
                                            (slideOutHorizontally(e) { -it / 5 } + fadeOut(tween(260)))
                                        closing -> (slideInHorizontally(e) { -it / 5 } + fadeIn(tween(420))) togetherWith
                                            (slideOutHorizontally(e) { it / 3 } + fadeOut(tween(260)))
                                        else -> (fadeIn(tween(380)) + scaleIn(tween(380, easing = FastOutSlowInEasing), 0.94f)) togetherWith fadeOut(tween(220))
                                    }
                                }
                            ) { (t, d) ->
                                MotionBlurBox(10.dp) {
                                    if (d != null) {
                                        if (d.second == "settings") SettingsScreen(vm) { detail = null }
                                        else DetailScreen(vm, d.first, d.second) { detail = null }
                                    } else when (t) {
                                        Tab.Home -> HomeScreen(vm) { detail = "Settings" to "settings" }
                                        Tab.Songs -> SongListScreen(vm, "Songs", songs, null, true)
                                        Tab.Albums -> AlbumsScreen(vm) { tt, k -> detail = tt to k }
                                        Tab.Artists -> ArtistsScreen(vm) { tt, k -> detail = tt to k }
                                        Tab.Playlists -> PlaylistsScreen(vm) { tt, k -> detail = tt to k }
                                    }
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        hasTrack,
                        enter = slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(300)),
                        exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200))
                    ) {
                        Box(
                            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                            contentAlignment = Alignment.TopCenter
                        ) { Box(Modifier.widthIn(max = 720.dp)) { MiniPlayer(vm) { showPlayer = true } } }
                    }
                    if (!rail) Row(
                        Modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .height(64.dp)
                            .glass(RoundedCornerShape(32.dp))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tab.values().forEach { t ->
                            val sel = tab == t
                            val tint = if (sel) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                            Column(
                                Modifier.weight(1f).fillMaxHeight()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(if (sel) Accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { tab = t; detail = null },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(t.icon, t.label, tint = tint, modifier = Modifier.size(24.dp))
                                Text(
                                    t.label, color = tint, maxLines = 1, softWrap = false,
                                    fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                showPlayer && hasTrack,
                enter = slideInVertically(tween(520, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(420, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(300))
            ) {
                MotionBlurBox(18.dp) { NowPlaying(vm) { showPlayer = false } }
            }
        }
        BackHandler(enabled = showPlayer) { showPlayer = false }
        BackHandler(enabled = !showPlayer && detail != null) { detail = null }
    }
}
