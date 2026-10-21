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
import androidx.compose.animation.AnimatedVisibility
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
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    val d = detail
                    if (d != null) {
                        if (d.second == "settings") SettingsScreen(vm) { detail = null }
                        else DetailScreen(vm, d.first, d.second) { detail = null }
                    } else when (tab) {
                        Tab.Home -> HomeScreen(vm) { detail = "Settings" to "settings" }
                        Tab.Songs -> SongListScreen(vm, "Songs", songs, null, true)
                        Tab.Albums -> AlbumsScreen(vm) { t, k -> detail = t to k }
                        Tab.Artists -> ArtistsScreen(vm) { t, k -> detail = t to k }
                        Tab.Playlists -> PlaylistsScreen(vm) { t, k -> detail = t to k }
                    }
                }
                if (hasTrack) MiniPlayer(vm) { showPlayer = true }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t; detail = null },
                            icon = { Icon(t.icon, t.label) }, label = { Text(t.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Accent.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
            AnimatedVisibility(showPlayer && hasTrack, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                NowPlaying(vm) { showPlayer = false }
            }
        }
        BackHandler(enabled = showPlayer) { showPlayer = false }
        BackHandler(enabled = !showPlayer && detail != null) { detail = null }
    }
}
