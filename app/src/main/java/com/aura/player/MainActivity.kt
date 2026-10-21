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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
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
        var granted by remember { mutableStateOf(false) }
        var showPlayer by remember { mutableStateOf(false) }

        val perms = remember {
            buildList {
                if (Build.VERSION.SDK_INT >= 33) {
                    add(Manifest.permission.READ_MEDIA_AUDIO); add(Manifest.permission.POST_NOTIFICATIONS)
                } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
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
                    Button(onClick = { launcher.launch(perms) }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Allow access")
                    }
                }
            }
            return
        }

        val hasTrack = ui.currentId != null
        Box(Modifier.fillMaxSize()) {
            LibraryScreen(vm, bottomPad = if (hasTrack) 84.dp else 0.dp)
            if (hasTrack) {
                MiniPlayer(
                    vm, onOpen = { showPlayer = true },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp)
                )
            }
            AnimatedVisibility(
                visible = showPlayer && hasTrack,
                enter = slideInVertically { it }, exit = slideOutVertically { it }
            ) {
                NowPlaying(vm, onClose = { showPlayer = false })
            }
        }
        BackHandler(enabled = showPlayer) { showPlayer = false }
    }
}
