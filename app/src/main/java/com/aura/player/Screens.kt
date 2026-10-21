package com.aura.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

val Accent = Color(0xFFFA2D48)

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun LibraryScreen(vm: PlayerViewModel, bottomPad: androidx.compose.ui.unit.Dp) {
    val songs by vm.songs.collectAsState()
    val ui by vm.ui.collectAsState()
    val loaded by vm.loaded.collectAsState()
    var query by remember { mutableStateOf("") }
    val shown = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPad + 16.dp)
    ) {
        item {
            Text(
                "Songs", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
            )
        }
        item {
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Box {
                        if (query.isEmpty()) Text("Search your library", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                            cursorBrush = SolidColor(Accent),
                            keyboardOptions = KeyboardOptions.Default,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        if (query.isBlank() && songs.isNotEmpty()) item {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Play", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { vm.playAll(false) }
                PillButton("Shuffle", Icons.Rounded.Shuffle, Modifier.weight(1f)) { vm.playAll(true) }
            }
        }
        if (loaded && songs.isEmpty()) item {
            Text(
                "No music found on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
        items(shown, key = { it.id }, contentType = { 0 }) { song ->
            val active = ui.currentId == song.id
            Row(
                Modifier.fillMaxWidth().clickable { vm.play(song) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(song, 52.dp, 8.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
                        color = if (active) Accent else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (active && ui.isPlaying) Icon(Icons.Rounded.GraphicEq, null, tint = Accent)
                else Text(fmt(song.durationMs), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val songs by vm.songs.collectAsState()
    val ui by vm.ui.collectAsState()
    val song = remember(songs, ui.currentId) { songs.firstOrNull { it.id == ui.currentId } } ?: return
    Row(
        modifier.padding(horizontal = 10.dp).fillMaxWidth().height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song, 48.dp, 8.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            song.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp, fontWeight = FontWeight.Medium
        )
        IconButton(onClick = vm::togglePlay) {
            Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(32.dp)) }
    }
}

@Composable
fun NowPlaying(vm: PlayerViewModel, onClose: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val ui by vm.ui.collectAsState()
    val song = remember(songs, ui.currentId) { songs.firstOrNull { it.id == ui.currentId } }

    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    // Position polling runs ONLY while this screen is visible and music is playing.
    LaunchedEffect(ui.isPlaying, ui.currentId) {
        pos = vm.position()
        while (isActive && ui.isPlaying) {
            if (!dragging) pos = vm.position()
            delay(400)
        }
    }

    val artScale by animateFloatAsState(if (ui.isPlaying) 1f else 0.84f, label = "art")
    val dur = ui.durationMs.coerceAtLeast(1L)

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF4A2A32), Color(0xFF14090C), Color.Black))
        ).clickable(enabled = false) {}
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.width(40.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(0.35f))
                    .clickable(onClick = onClose)
            )
            Spacer(Modifier.height(10.dp))
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.weight(1f))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = maxWidth
                Art(song, side, 14.dp, Modifier.scale(artScale), px = 720)
            }
            Spacer(Modifier.height(32.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(song?.title ?: "", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artist ?: "", color = Accent.copy(alpha = 0.95f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            Slider(
                value = if (dragging) dragValue else (pos.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { vm.seekTo((dragValue * dur).toLong()); pos = (dragValue * dur).toLong(); dragging = false },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White, activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(0.25f)
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val shownPos = if (dragging) (dragValue * dur).toLong() else pos
                Text(fmt(shownPos), color = Color.White.copy(0.6f), fontSize = 12.sp)
                Text("-" + fmt(dur - shownPos), color = Color.White.copy(0.6f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::previous, Modifier.size(72.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(52.dp))
                }
                IconButton(onClick = vm::togglePlay, Modifier.size(84.dp)) {
                    Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(72.dp))
                }
                IconButton(onClick = vm::next, Modifier.size(72.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(52.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (ui.shuffle) Accent else Color.White.copy(0.6f))
                }
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat",
                        tint = if (ui.repeat == Player.REPEAT_MODE_OFF) Color.White.copy(0.6f) else Accent
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
