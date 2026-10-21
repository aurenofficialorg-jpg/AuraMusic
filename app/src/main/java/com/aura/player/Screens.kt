package com.aura.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.lerp
import kotlin.math.roundToInt
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

var Accent by mutableStateOf(Color(0xFFFA2D48))
val AccentChoices = listOf(
    Color(0xFFFA2D48), Color(0xFF0A84FF), Color(0xFF30D158),
    Color(0xFFFF9F0A), Color(0xFFBF5AF2), Color(0xFF64D2FF)
)

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable private fun sub() = MaterialTheme.colorScheme.onSurfaceVariant

/** One horizontal gutter used by every screen so titles, rows, search and buttons share the same left/right edge. */
val Gutter = 20.dp
val ContentMaxWidth = 720.dp

/** Lets a row of IconButtons sit flush with the gutter (their glyphs have 12dp of built-in inset). */
private fun Modifier.bleed(h: Dp) = layout { m, c ->
    val e = h.roundToPx()
    val w = c.maxWidth + 2 * e
    val p = m.measure(c.copy(minWidth = w, maxWidth = w))
    layout(c.maxWidth, p.height) { p.placeRelative(-e, 0) }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(start = if (onBack != null) 8.dp else Gutter, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = Accent) }
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/* ---------------------------------------------------------------- lists */

@Composable
fun ListRow(song: Song?, title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song, 52.dp, 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = sub())
        }
        trailing()
    }
}

@Composable
fun SongListScreen(
    vm: PlayerViewModel, title: String, songs: List<Song>, onBack: (() -> Unit)?,
    searchable: Boolean, inPlaylist: String? = null, headerAction: @Composable () -> Unit = {}
) {
    val ui by vm.ui.collectAsState()
    val favs by vm.favs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var pickFor by remember { mutableStateOf<Song?>(null) }
    var sort by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    val shown = remember(songs, query, sort) {
        val f = if (query.isBlank()) songs else songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
        if (!searchable) f else when (sort) {
            1 -> f.sortedBy { it.artist.lowercase() }
            2 -> f.sortedByDescending { it.dateAdded }
            3 -> f.sortedByDescending { it.durationMs }
            else -> f
        }
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            ScreenHeader(title, onBack) {
                if (searchable) Box {
                    IconButton({ sortMenu = true }) { Icon(Icons.Rounded.SwapVert, "Sort", tint = Accent) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        listOf("Title", "Artist", "Recently Added", "Longest").forEachIndexed { i, n ->
                            DropdownMenuItem({ Text(n, color = if (sort == i) Accent else Color.Unspecified) }, { sort = i; sortMenu = false })
                        }
                    }
                }
                headerAction()
            }
        }
        if (searchable) item {
            Box(
                Modifier.padding(horizontal = Gutter).fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = sub(), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Box {
                        if (query.isEmpty()) Text("Search your library", color = sub(), fontSize = 16.sp)
                        BasicTextField(
                            query, { query = it }, singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                            cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        if (query.isBlank() && songs.isNotEmpty()) item {
            Row(Modifier.padding(horizontal = Gutter, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Play", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { vm.playAll(songs, false, title) }
                PillButton("Shuffle", Icons.Rounded.Shuffle, Modifier.weight(1f)) { vm.playAll(songs, true, title) }
            }
        }
        if (songs.isEmpty()) item { Text("Nothing here yet.", color = sub(), modifier = Modifier.padding(horizontal = Gutter, vertical = 24.dp)) }
        items(shown, key = { it.id }, contentType = { 0 }) { song ->
            val active = ui.currentId == song.id
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { vm.play(song, shown, title) }.padding(start = Gutter, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(song, 52.dp, 8.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, color = if (active) Accent else MaterialTheme.colorScheme.onSurface)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = sub())
                }
                if (active && ui.isPlaying) EqBars()
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "More", tint = sub()) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Play next") }, { vm.playNext(song); menu = false }, leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) })
                        DropdownMenuItem(
                            { Text(if (song.id in favs) "Remove from Favourites" else "Add to Favourites") },
                            { vm.toggleFav(song.id); menu = false },
                            leadingIcon = { Icon(if (song.id in favs) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) }
                        )
                        DropdownMenuItem({ Text("Add to Playlist") }, { pickFor = song; menu = false }, leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) })
                        DropdownMenuItem({ Text("Share") }, {
                            menu = false
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"; putExtra(Intent.EXTRA_STREAM, song.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(i, null))
                        }, leadingIcon = { Icon(Icons.Rounded.Share, null) })
                        if (inPlaylist != null) DropdownMenuItem({ Text("Remove from this Playlist") }, { vm.removeFromPlaylist(inPlaylist, song.id); menu = false }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                    }
                }
            }
        }
    }

    pickFor?.let { s ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            confirmButton = { TextButton({ pickFor = null }) { Text("Cancel") } },
            title = { Text("Add to Playlist") },
            text = {
                if (playlists.isEmpty()) Text("Create a playlist first in the Playlists tab.")
                else Column(Modifier.verticalScroll(rememberScrollState())) { playlists.keys.sorted().forEach { n -> TextButton({ vm.addToPlaylist(n, s.id); pickFor = null }) { Text(n, color = Accent) } } }
            }
        )
    }
}

@Composable
private fun PillButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent); Spacer(Modifier.width(6.dp))
        Text(label, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
private fun TabTitle(t: String) = ScreenHeader(t)

@Composable
fun AlbumsScreen(vm: PlayerViewModel, open: (String, String) -> Unit) {
    val songs by vm.songs.collectAsState()
    val groups = remember(songs) { songs.groupBy { it.albumId }.values.sortedBy { it[0].album.lowercase() } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { TabTitle("Albums") }
        items(groups, key = { it[0].albumId }) { g ->
            ListRow(g[0], g[0].album, "${g[0].artist} · ${g.size} songs", { open(g[0].album, "album:${g[0].albumId}") })
        }
    }
}

@Composable
fun ArtistsScreen(vm: PlayerViewModel, open: (String, String) -> Unit) {
    val songs by vm.songs.collectAsState()
    val groups = remember(songs) { songs.groupBy { it.artist }.values.sortedBy { it[0].artist.lowercase() } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { TabTitle("Artists") }
        items(groups, key = { it[0].artist }) { g ->
            ListRow(g[0], g[0].artist, "${g.size} songs", { open(g[0].artist, "artist:${g[0].artist}") })
        }
    }
}

@Composable
fun PlaylistsScreen(vm: PlayerViewModel, open: (String, String) -> Unit) {
    val favs by vm.favs.collectAsState()
    val recents by vm.recents.collectAsState()
    val playlists by vm.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { TabTitle("Playlists") }
        item { SimpleRow(Icons.Rounded.Favorite, "Favourites", "${favs.size} songs") { open("Favourites", "favs") } }
        item { SimpleRow(Icons.Rounded.History, "Recently Played", "${recents.size} songs") { open("Recently Played", "recents") } }
        item { SimpleRow(Icons.Rounded.Add, "New Playlist", "") { creating = true } }
        items(playlists.keys.sorted(), key = { it }) { n ->
            SimpleRow(Icons.Rounded.QueueMusic, n, "${playlists[n]?.size ?: 0} songs") { open(n, "pl:$n") }
        }
    }
    if (creating) AlertDialog(
        onDismissRequest = { creating = false },
        confirmButton = { TextButton({ vm.createPlaylist(name); name = ""; creating = false }) { Text("Create", color = Accent) } },
        dismissButton = { TextButton({ creating = false }) { Text("Cancel") } },
        title = { Text("New Playlist") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Name") }) }
    )
}

@Composable
private fun SimpleRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 16.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 14.sp, color = sub())
        }
    }
}

@Composable
fun DetailScreen(vm: PlayerViewModel, title: String, key: String, onBack: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val favs by vm.favs.collectAsState()
    val recents by vm.recents.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val list = remember(songs, favs, recents, playlists, key) {
        when {
            key.startsWith("album:") -> songs.filter { it.albumId == key.drop(6).toLong() }
            key.startsWith("artist:") -> songs.filter { it.artist == key.drop(7) }
            key.startsWith("pl:") -> playlists[key.drop(3)].orEmpty().mapNotNull { vm.songById(it) }
            key == "favs" -> songs.filter { it.id in favs }
            else -> recents.mapNotNull { vm.songById(it) }
        }
    }
    val pl = if (key.startsWith("pl:")) key.drop(3) else null
    SongListScreen(vm, title, list, onBack, false, pl) {
        if (pl != null) IconButton({ vm.deletePlaylist(pl); onBack() }) { Icon(Icons.Rounded.Delete, "Delete playlist", tint = Accent) }
    }
}

/* ------------------------------------------------------------ mini player */

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val ui by vm.ui.collectAsState()
    val song = remember(songs, ui.currentId) { vm.songById(ui.currentId) } ?: return
    var dx by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(62.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onOpen)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dx < -90) vm.next() else if (dx > 90) vm.previous(); dx = 0f },
                    onDragCancel = { dx = 0f }
                ) { _, d -> dx += d }
            }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song, 46.dp, 8.dp)
        Spacer(Modifier.width(12.dp))
        Text(song.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        IconButton(onClick = vm::togglePlay) { Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", modifier = Modifier.size(32.dp)) }
        IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(32.dp)) }
    }
}

/* ------------------------------------------------------------ now playing */

private fun formatLabel(mime: String): String {
    val m = mime.lowercase()
    return when {
        "flac" in m || "wav" in m || "alac" in m || "aiff" in m -> "LOSSLESS"
        "mpeg" in m || "mp3" in m -> "MP3"
        "mp4" in m || "m4a" in m || "aac" in m -> "AAC"
        "ogg" in m || "opus" in m -> "OGG"
        else -> "AUDIO"
    }
}

private fun outputName(am: AudioManager): String {
    val d = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val bt = d.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
    return when {
        bt != null -> bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
        d.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET } -> "Headphones"
        else -> "This Phone"
    }
}

@Composable
private fun Badge(label: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, color = Color.White.copy(0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, on: Boolean, desc: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick) { Icon(icon, desc, tint = if (on) Accent else Color.White.copy(0.7f)) }
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) Accent else Color.Transparent))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlaying(vm: PlayerViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val songs by vm.songs.collectAsState()
    val ui by vm.ui.collectAsState()
    val favs by vm.favs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val source by vm.source.collectAsState()
    val sleep by vm.sleepMin.collectAsState()
    val song = remember(songs, ui.currentId) { vm.songById(ui.currentId) }

    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableIntStateOf(0) } // 0 art, 1 lyrics, 2 queue
    var showEq by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }

    // Volume + output device
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    var volDrag by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf("This Phone") }
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!volDrag) vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
            device = outputName(am)
            delay(1000)
        }
    }

    LaunchedEffect(ui.isPlaying, ui.currentId) {
        pos = vm.position()
        while (isActive && ui.isPlaying) { if (!dragging) pos = vm.position(); delay(400) }
    }

    val bmp by produceState<Bitmap?>(null, song?.id) {
        val s = song
        value = if (s == null) null else withContext(Dispatchers.IO) { ArtCache.load(ctx, s, 512) }
    }
    val tint = remember(bmp) { bmp?.let { ArtCache.tone(it) } ?: Color(0xFF3A2A30) }
    val bg by animateColorAsState(tint, tween(600), label = "bg")
    val glow = lerp(bg, Accent, 0.30f)
    val artScale by animateFloatAsState(if (ui.isPlaying) 1f else 0.86f, label = "art")
    val dur = ui.durationMs.coerceAtLeast(1L)
    var dragY by remember { mutableFloatStateOf(0f) }
    val white = Color.White

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { if (dragY > 160) onClose(); dragY = 0f }, onDragCancel = { dragY = 0f }) { _, d -> dragY += d }
            }
    ) {
        // Landscape phones / tablets get a two-pane layout; short screens drop the less important rows.
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        val tight = if (wide) maxHeight < 420.dp else maxHeight < 640.dp
        val playSize = if (tight) 64.dp else 78.dp
        val skipSize = if (tight) 52.dp else 60.dp
        val skipIcon = if (tight) 34.dp else 42.dp

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(glow, Color(0xFF0B0507), Color.Black))))

        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().height(52.dp).bleed(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = white, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", color = white.copy(0.6f), fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Text(source, color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreHoriz, "More", tint = white) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Sleep timer") }, { menu = false; showSleep = true }, leadingIcon = { Icon(Icons.Rounded.Timer, null) })
                        DropdownMenuItem({ Text("Equalizer") }, { menu = false; showEq = true }, leadingIcon = { Icon(Icons.Rounded.Tune, null) })
                        DropdownMenuItem({ Text("Share") }, {
                            menu = false
                            song?.let { s ->
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/*"; putExtra(Intent.EXTRA_STREAM, s.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(Intent.createChooser(i, null))
                            }
                        }, leadingIcon = { Icon(Icons.Rounded.Share, null) })
                    }
                }
            }
        }

        val stage: @Composable (Modifier) -> Unit = { m ->
            Box(m.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                when (mode) {
                    0 -> BoxWithConstraints(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            var dx = 0f
                            detectHorizontalDragGestures(onDragEnd = { if (dx < -120) vm.next() else if (dx > 120) vm.previous(); dx = 0f }) { _, d -> dx += d }
                        }, contentAlignment = Alignment.Center
                    ) {
                        val side = minOf(maxWidth, maxHeight)
                        Art(song, side, 22.dp, Modifier.scale(artScale).shadow(24.dp, RoundedCornerShape(22.dp)), px = 720)
                    }
                    1 -> LyricsPane(vm, song, pos)
                    else -> QueuePane(vm, ui)
                }
            }
        }

        val controls: @Composable ColumnScope.() -> Unit = {
            // Title row
            Row(Modifier.fillMaxWidth().bleed(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(song?.title ?: "", color = white, fontSize = if (tight) 22.sp else 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song?.let { "${it.artist} — ${it.album}" } ?: "", color = Accent, fontSize = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                val fav = song != null && song.id in favs
                IconButton({ song?.let { vm.toggleFav(it.id) } }) {
                    Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (fav) Accent else white.copy(0.8f))
                }
                IconButton({ pick = true }) { Icon(Icons.Rounded.PlaylistAdd, "Add to playlist", tint = white.copy(0.8f)) }
            }
            if (!tight) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(formatLabel(song?.mime ?: "")); Badge("OFFLINE")
            }

            // Progress
            Slider(
                value = if (dragging) dragValue else (pos.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { vm.seekTo((dragValue * dur).toLong()); pos = (dragValue * dur).toLong(); dragging = false },
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = white.copy(0.2f)),
                thumb = { Box(Modifier.size(14.dp).clip(CircleShape).background(Accent)) }
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val p = if (dragging) (dragValue * dur).toLong() else pos
                Text(fmt(p), color = white.copy(0.6f), fontSize = 12.sp)
                Text("-" + fmt(dur - p), color = white.copy(0.6f), fontSize = 12.sp)
            }

            // Transport
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).bleed(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ToggleIcon(Icons.Rounded.Shuffle, ui.shuffle, "Shuffle", vm::toggleShuffle)
                IconButton(vm::previous, Modifier.size(skipSize)) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = white, modifier = Modifier.size(skipIcon)) }
                Box(
                    Modifier.size(playSize).shadow(20.dp, CircleShape, ambientColor = Accent, spotColor = Accent).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent.copy(alpha = 0.85f), Accent))).clickable(onClick = vm::togglePlay),
                    contentAlignment = Alignment.Center
                ) { Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", tint = white, modifier = Modifier.size(skipIcon)) }
                IconButton(vm::next, Modifier.size(skipSize)) { Icon(Icons.Rounded.SkipNext, "Next", tint = white, modifier = Modifier.size(skipIcon)) }
                ToggleIcon(
                    if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    ui.repeat != Player.REPEAT_MODE_OFF, "Repeat", vm::cycleRepeat
                )
            }

            // Volume
            if (!tight) Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeDown, null, tint = white.copy(0.6f), modifier = Modifier.size(20.dp))
                Slider(
                    value = vol,
                    onValueChange = { vol = it; volDrag = true; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVol).roundToInt(), 0) },
                    onValueChangeFinished = { volDrag = false },
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    colors = SliderDefaults.colors(thumbColor = white, activeTrackColor = white, inactiveTrackColor = white.copy(0.2f)),
                    thumb = { Box(Modifier.size(12.dp).clip(CircleShape).background(white)) }
                )
                Icon(Icons.Rounded.VolumeUp, null, tint = white.copy(0.6f), modifier = Modifier.size(20.dp))
            }

            // Bottom row: lyrics, output device, queue
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).bleed(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ mode = if (mode == 1) 0 else 1 }) { Icon(Icons.Rounded.FormatQuote, "Lyrics", tint = if (mode == 1) Accent else white.copy(0.7f)) }
                Row(
                    Modifier.weight(1f, fill = false).clip(RoundedCornerShape(20.dp)).background(white.copy(0.10f)).clickable {
                        try { ctx.startActivity(Intent(Settings.Panel.ACTION_VOLUME)) }
                        catch (_: Exception) { try { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) { } }
                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Headphones, null, tint = white.copy(0.85f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(device, color = white.copy(0.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                }
                IconButton({ mode = if (mode == 2) 0 else 2 }) { Icon(Icons.Rounded.QueueMusic, "Queue", tint = if (mode == 2) Accent else white.copy(0.7f)) }
            }
        }

        if (wide) {
            Row(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                stage(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    header()
                    Spacer(Modifier.weight(1f))
                    controls()
                }
            }
        } else {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.fillMaxHeight().widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    header()
                    stage(Modifier.weight(1f).fillMaxWidth())
                    controls()
                }
            }
        }
    }

    if (pick) AlertDialog(
        onDismissRequest = { pick = false },
        confirmButton = { TextButton({ pick = false }) { Text("Cancel") } },
        title = { Text("Add to Playlist") },
        text = {
            if (playlists.isEmpty()) Text("Create a playlist first in the Library tab.")
            else Column(Modifier.verticalScroll(rememberScrollState())) { playlists.keys.sorted().forEach { n -> TextButton({ song?.let { vm.addToPlaylist(n, it.id) }; pick = false }) { Text(n, color = Accent) } } }
        }
    )
    if (showSleep) AlertDialog(
        onDismissRequest = { showSleep = false },
        confirmButton = { TextButton({ showSleep = false }) { Text("Close") } },
        title = { Text("Sleep timer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour").forEach { (m, l) ->
                    TextButton({ vm.setSleep(m); showSleep = false }) { Text(l, color = if (sleep == m) Accent else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    )
    if (showEq) EqDialog(vm) { showEq = false }
}

@Composable
private fun EqDialog(vm: PlayerViewModel, onClose: () -> Unit) {
    val preset by vm.preset.collectAsState()
    val bass by vm.bass.collectAsState()
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClose) { Text("Done", color = Accent) } },
        title = { Text("Equalizer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf("Flat", "Bass", "Treble", "Vocal", "Rock").forEachIndexed { i, n ->
                    TextButton({ vm.setFx(i, bass) }) { Text(n, color = if (preset == i) Accent else MaterialTheme.colorScheme.onSurface, fontWeight = if (preset == i) FontWeight.Bold else FontWeight.Normal) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Bass boost", fontSize = 14.sp)
                var b by remember { mutableFloatStateOf(bass.toFloat()) }
                Slider(b, { b = it }, onValueChangeFinished = { vm.setFx(preset, b.toInt()) }, valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
            }
        }
    )
}

@Composable
private fun LyricsPane(vm: PlayerViewModel, song: Song?, pos: Long) {
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    val lines = remember(song?.id, ver) { song?.let { s -> vm.lyrics(s.id)?.let { Lrc.parse(it) } } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && song != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
            if (!text.isNullOrBlank()) { vm.saveLyrics(song.id, text); ver++ }
        }
    }
    if (lines.isNullOrEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No lyrics for this song", color = Color.White.copy(0.7f), fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Button({ picker.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Add .lrc file") }
        }
        return
    }
    val synced = lines[0].t >= 0
    val cur = if (synced) lines.indexOfLast { it.t <= pos }.coerceAtLeast(0) else -1
    val state = rememberLazyListState()
    LaunchedEffect(cur) { if (synced) state.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(vertical = 40.dp)) {
        itemsIndexed(lines) { i, l ->
            Text(
                l.text.ifEmpty { "♪" }, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = Color.White.copy(if (!synced || i == cur) 1f else 0.4f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun QueuePane(vm: PlayerViewModel, ui: PlayerUi) {
    val queue by vm.queue.collectAsState()
    val from = ui.queueIndex + 1
    val to = minOf(queue.size, from + 100)
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Up Next", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
        if (from >= queue.size) item { Text("Nothing queued.", color = Color.White.copy(0.7f)) }
        else items((from until to).toList(), key = { it }) { i ->
            val s = vm.songById(queue[i])
            Row(Modifier.fillMaxWidth().clickable { vm.jumpTo(i) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(s, 44.dp, 6.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s?.title ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    Text(s?.artist ?: "", color = Color.White.copy(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                }
                IconButton({ vm.moveUp(i) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = Color.White) }
                IconButton({ vm.removeAt(i) }) { Icon(Icons.Rounded.Close, "Remove", tint = Color.White.copy(0.7f)) }
            }
        }
    }
}


/* ------------------------------------------------------------ extras */

@Composable
fun EqBars(color: Color = Accent) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(Modifier.height(18.dp).padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        listOf(380, 520, 440).forEachIndexed { i, d ->
            val h by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d, easing = LinearEasing), RepeatMode.Reverse), label = "b$i")
            Box(Modifier.width(3.dp).fillMaxHeight(h).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

private fun LazyListScope.shelf(title: String, list: List<Song>, vm: PlayerViewModel) {
    if (list.isEmpty()) return
    item { Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = Gutter, top = 14.dp, bottom = 10.dp)) }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list, key = { it.id }) { s ->
                Column(Modifier.width(140.dp).clickable { vm.play(s, list, title) }) {
                    Art(s, 140.dp, 12.dp, px = 320)
                    Spacer(Modifier.height(6.dp))
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(vm: PlayerViewModel, openSettings: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val recents by vm.recents.collectAsState()
    val favs by vm.favs.collectAsState()
    val counts by vm.counts.collectAsState()
    val recent = remember(recents, songs) { recents.mapNotNull { vm.songById(it) }.take(15) }
    val top = remember(counts, songs) { counts.entries.sortedByDescending { it.value }.mapNotNull { vm.songById(it.key) }.take(15) }
    val fav = remember(favs, songs) { songs.filter { it.id in favs }.take(15) }
    val added = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(15) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            ScreenHeader("Listen Now") {
                IconButton(openSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Accent) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = Gutter, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Play All", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { vm.playAll(songs, false) }
                PillButton("Shuffle All", Icons.Rounded.Shuffle, Modifier.weight(1f)) { vm.playAll(songs, true) }
            }
        }
        shelf("Recently Played", recent, vm)
        shelf("Most Played", top, vm)
        shelf("Favourites", fav, vm)
        shelf("Recently Added", added, vm)
    }
}

private fun speedLabel(v: Float) = if (v % 1f == 0f) "${v.toInt()}×" else "$v×"
private val SpeedChoices = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun SectionLabel(t: String, value: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(t.uppercase(), fontSize = 13.sp, color = sub(), modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold)
    }
}

/** Equal-width segmented control: every option gets the same width/height, so labels never wrap or misalign. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Accent else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val theme by vm.theme.collectAsState()
    val acc by vm.accentIdx.collectAsState()
    val speed by vm.speed.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("Settings", onBack)

        SectionLabel("Appearance")
        Segmented(listOf("System", "Dark", "Light"), theme) { vm.setTheme(it) }

        SectionLabel("Accent colour")
        BoxWithConstraints(Modifier.padding(horizontal = Gutter).fillMaxWidth()) {
            val gap = 10.dp
            val d = minOf(40.dp, (maxWidth - gap * (AccentChoices.size - 1)) / AccentChoices.size)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                AccentChoices.forEachIndexed { i, c ->
                    Box(Modifier.size(d).clip(CircleShape).background(c).clickable { vm.setAccent(i) }, contentAlignment = Alignment.Center) {
                        if (acc == i) Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.size(d / 1.8f))
                    }
                }
            }
        }

        SectionLabel("Playback speed", speedLabel(speed))
        Segmented(SpeedChoices.map(::speedLabel), SpeedChoices.indexOfFirst { kotlin.math.abs(it - speed) < 0.001f }) { vm.setSpeed(SpeedChoices[it]) }
        Text(
            "Applies to every song. Pitch stays natural.", fontSize = 13.sp, color = sub(),
            modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp)
        )

        SectionLabel("About")
        Text(
            "Aura Music 2.0\nOffline player. Your music never leaves your phone.",
            modifier = Modifier.padding(horizontal = Gutter), color = sub()
        )
    }
}
