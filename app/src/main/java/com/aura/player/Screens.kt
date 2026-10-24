package com.aura.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.zIndex
import kotlin.math.abs
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, inset: Dp = Gutter, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(start = if (onBack != null) 8.dp else inset, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = Accent) }
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/* ---------------------------------------------------------------- lists */

@Composable
fun ListRow(song: Song?, title: String, subtitle: String, onClick: () -> Unit, round: Boolean = false, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().bouncyClick(0.98f, onClick).padding(horizontal = Gutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song, 52.dp, if (round) 26.dp else Radius.Thumb)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = sub())
        }
        trailing()
    }
}

/* ------------------------------------------------- loading & empty states */

@Composable
private fun skeletonAlpha(): State<Float> {
    val t = rememberInfiniteTransition(label = "skeleton")
    return t.animateFloat(0.06f, 0.15f, infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse), label = "skeletonAlpha")
}

/** Shimmering placeholder rows shown while the library is still being read. */
@Composable
fun SkeletonRows(count: Int = 9) {
    val a = skeletonAlpha()
    val c = MaterialTheme.colorScheme.onSurface
    Column {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(Radius.Thumb)).drawBehind { drawRect(c.copy(alpha = a.value)) })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.6f else 0.45f).height(14.dp).clip(RoundedCornerShape(7.dp)).drawBehind { drawRect(c.copy(alpha = a.value)) })
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(if (i % 3 == 0) 0.3f else 0.38f).height(12.dp).clip(RoundedCornerShape(6.dp)).drawBehind { drawRect(c.copy(alpha = a.value * 0.8f)) })
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(84.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(message, fontSize = 15.sp, color = sub(), textAlign = TextAlign.Center)
        }
    }
}

/* ----------------------------------------------------------- hero header */

/** Big cover + title block for album and artist pages. */
class Hero(val song: Song?, val subtitle: String, val round: Boolean)

@Composable
private fun HeroHeader(h: Hero, title: String, top: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val tint by produceState(Accent, h.song?.id) {
        val sg = h.song
        if (sg != null) {
            val c = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                (ArtCache.get(sg.id, 128) ?: ArtCache.load(ctx, sg, 128))?.let { vibrantColor(it) }
            }
            if (c != null) value = c
        }
    }
    val dark = isDarkTheme()
    Box(
        Modifier.fillMaxWidth().drawBehind {
            drawRect(Brush.verticalGradient(listOf(tint.copy(alpha = if (dark) 0.45f else 0.32f), Color.Transparent)))
        }
    ) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            top()
            val side = 220.dp
            val corner = if (h.round) side / 2 else Radius.Hero
            Art(h.song, side, corner, Modifier.shadow(22.dp, RoundedCornerShape(corner)), px = 600)
            Spacer(Modifier.height(16.dp))
            Text(
                title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = Gutter)
            )
            Text(h.subtitle, fontSize = 15.sp, color = sub(), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListScreen(
    vm: PlayerViewModel, title: String, songs: List<Song>, onBack: (() -> Unit)?,
    searchable: Boolean, inPlaylist: String? = null, hero: Hero? = null, headerAction: @Composable () -> Unit = {}
) {
    val ui by vm.ui.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val favs by vm.favs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var pickFor by remember { mutableStateOf<Song?>(null) }
    var sort by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
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

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().then(if (hero == null) Modifier.statusBarsPadding() else Modifier),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            if (hero == null) {
                ScreenHeader(title, onBack) {
                    if (searchable) IconButton({ sortMenu = true }) { Icon(Icons.Rounded.SwapVert, "Sort", tint = Accent) }
                    headerAction()
                }
            } else {
                HeroHeader(hero, title) { ScreenHeader("", onBack) { headerAction() } }
            }
        }
        if (searchable) item {
            Box(
                Modifier.padding(horizontal = Gutter).fillMaxWidth().heightIn(min = 40.dp).glass(RoundedCornerShape(Radius.Control)), contentAlignment = Alignment.CenterStart
            ) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = sub(), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Box {
                        if (query.isEmpty()) Text("Search your library", color = sub(), fontSize = 16.sp)
                        BasicTextField(
                            query, { query = it }, singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = AuraFont),
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
        if (songs.isEmpty()) {
            if (!loaded && searchable) item { SkeletonRows() }
            else if (searchable) item { EmptyState(Icons.Rounded.MusicNote, "No music found", "Songs on your device will appear here automatically.") }
            else item { EmptyState(Icons.Rounded.QueueMusic, "Nothing here yet", "Songs you add will show up here.") }
        } else if (shown.isEmpty()) {
            item { EmptyState(Icons.Rounded.Search, "No results", "Nothing matches \u201c$query\u201d.") }
        }
        items(shown, key = { it.id }, contentType = { 0 }) { song ->
            val active = ui.currentId == song.id
            Row(
                Modifier.animateItemPlacement().fillMaxWidth().bouncyClick(0.98f) { vm.play(song, shown, title) }.padding(start = Gutter, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(song, 52.dp, Radius.Thumb)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, color = if (active) Accent else MaterialTheme.colorScheme.onSurface)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = sub())
                }
                if (active && ui.isPlaying) EqBars()
                IconButton(onClick = { menuSong = song }) { Icon(Icons.Rounded.MoreHoriz, "More", tint = sub()) }
            }
        }
    }
    val ms = menuSong
    AuraMenu(
        visible = ms != null, onDismiss = { menuSong = null }, title = ms?.title,
        actions = if (ms == null) emptyList() else buildList {
            add(MenuAction("Play next", Icons.Rounded.QueueMusic) { vm.playNext(ms) })
            add(MenuAction(if (ms.id in favs) "Remove from Favourites" else "Add to Favourites",
                if (ms.id in favs) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder) { vm.toggleFav(ms.id) })
            add(MenuAction("Add to Playlist", Icons.Rounded.PlaylistAdd) { pickFor = ms })
            add(MenuAction("Hide from library", Icons.Rounded.VisibilityOff) { vm.hideSong(ms.id) })
            add(MenuAction("Share", Icons.Rounded.Share) {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"; putExtra(Intent.EXTRA_STREAM, ms.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(i, null))
            })
            if (inPlaylist != null) add(MenuAction("Remove from this Playlist", Icons.Rounded.Delete) { vm.removeFromPlaylist(inPlaylist, ms.id) })
        }
    )
    AuraMenu(
        visible = sortMenu, onDismiss = { sortMenu = false }, title = "Sort by",
        actions = listOf("Title", "Artist", "Recently Added", "Longest").mapIndexed { i, n ->
            MenuAction(n, Icons.Rounded.SwapVert, selected = sort == i) { sort = i }
        }
    )
    val pf = pickFor
    AuraMenu(
        visible = pf != null, onDismiss = { pickFor = null }, title = "Add to playlist",
        actions = if (pf == null) emptyList() else if (playlists.isEmpty())
            listOf(MenuAction("Create a playlist first in Library", Icons.Rounded.PlaylistAdd) { })
        else playlists.keys.sorted().map { n -> MenuAction(n, Icons.Rounded.QueueMusic) { vm.addToPlaylist(n, pf.id) } }
    )
    }
}

@Composable
private fun PillButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.bouncyTick(onClick = onClick).heightIn(min = 46.dp).glass(RoundedCornerShape(Radius.Control)),
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
    val loaded by vm.loaded.collectAsState()
    val groups = remember(songs) { songs.groupBy { it.albumId }.values.sortedBy { it[0].album.lowercase() } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ScreenHeader("Albums", inset = 0.dp) }
        if (groups.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (!loaded) SkeletonRows() else EmptyState(Icons.Rounded.Album, "No albums yet", "Albums from your music will appear here.")
            }
        }
        gridItems(groups, key = { it[0].albumId }) { g ->
            Column(Modifier.bouncyClick(0.97f) { open(g[0].album, "album:${g[0].albumId}") }) {
                BoxWithConstraints {
                    Art(g[0], maxWidth, Radius.Cover, Modifier.shadow(10.dp, RoundedCornerShape(Radius.Cover)), px = 400)
                }
                Spacer(Modifier.height(8.dp))
                Text(g[0].album, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(g[0].artist, fontSize = 13.sp, color = sub(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ArtistsScreen(vm: PlayerViewModel, open: (String, String) -> Unit) {
    val songs by vm.songs.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val groups = remember(songs) { songs.groupBy { it.artist }.values.sortedBy { it[0].artist.lowercase() } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { TabTitle("Artists") }
        if (groups.isEmpty()) item {
            if (!loaded) SkeletonRows() else EmptyState(Icons.Rounded.Person, "No artists yet", "Artists from your music will appear here.")
        }
        items(groups, key = { it[0].artist }) { g ->
            ListRow(g[0], g[0].artist, "${g.size} songs", { open(g[0].artist, "artist:${g[0].artist}") }, round = true)
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(creating) {
        if (creating) { kotlinx.coroutines.delay(280); runCatching { focus.requestFocus() } }
    }
    val create = {
        if (name.isNotBlank()) { vm.createPlaylist(name.trim()); name = ""; creating = false }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { TabTitle("Library") }
            item { SimpleRow(Icons.Rounded.Favorite, "Favourites", "${favs.size} songs") { open("Favourites", "favs") } }
            item { SimpleRow(Icons.Rounded.History, "Recently Played", "${recents.size} songs") { open("Recently Played", "recents") } }
            item { SimpleRow(Icons.Rounded.Add, "New Playlist", "") { creating = true } }
            items(playlists.keys.sorted(), key = { it }) { n ->
                SimpleRow(Icons.Rounded.QueueMusic, n, "${playlists[n]?.size ?: 0} songs") { open(n, "pl:$n") }
            }
        }
        AuraSheet(visible = creating, onDismiss = { creating = false; name = "" }, title = "New playlist", ime = true) { fg, sub ->
            Box(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.Control)).background(fg.copy(alpha = 0.07f))
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                if (name.isEmpty()) Text("Name", color = sub, fontSize = 17.sp)
                BasicTextField(
                    name, { name = it }, singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 17.sp, fontFamily = AuraFont),
                    cursorBrush = SolidColor(Accent),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { create() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            Box(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(Radius.Control))
                    .background(if (name.isBlank()) Accent.copy(alpha = 0.35f) else Accent)
                    .bouncyTick { create() },
                contentAlignment = Alignment.Center
            ) { Text("Create", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
        }
    }
}

@Composable
private fun SimpleRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bouncyClick(0.98f, onClick).padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).glass(RoundedCornerShape(Radius.Thumb)), contentAlignment = Alignment.Center) {
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
            key == "dups" -> vm.duplicates(songs)
            else -> recents.mapNotNull { vm.songById(it) }
        }
    }
    val pl = if (key.startsWith("pl:")) key.drop(3) else null
    val hero = when {
        key.startsWith("album:") -> list.firstOrNull()?.let { Hero(it, "${it.artist} \u00b7 ${list.size} songs", false) }
        key.startsWith("artist:") -> list.firstOrNull()?.let { Hero(it, "${list.size} songs", true) }
        else -> null
    }
    SongListScreen(vm, title, list, onBack, false, pl, hero = hero) {
        if (pl != null) IconButton({ vm.deletePlaylist(pl); onBack() }) { Icon(Icons.Rounded.Delete, "Delete playlist", tint = Accent) }
    }
}

/* ------------------------------------------------------------ mini player */

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val ui by vm.ui.collectAsState()
    val song = remember(songs, ui.currentId) { vm.songById(ui.currentId) } ?: return
    val tick = rememberHaptic()
    var dx by remember { mutableFloatStateOf(0f) }
    // Own position state: ticking only redraws the thin progress line, never the rest of the app.
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ui.isPlaying, ui.currentId) {
        pos = vm.position()
        while (isActive && ui.isPlaying) { pos = vm.position(); delay(500) }
    }
    val frac by animateFloatAsState((pos.toFloat() / ui.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f), tween(500, easing = LinearEasing), label = "miniProgress")
    Box(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(62.dp).bouncyClick(0.98f, onOpen)
            .glass(RoundedCornerShape(Radius.Card))
            .drawBehind { drawRect(Accent.copy(alpha = 0.13f)) } // takes on the cover's colour (Accent follows the artwork)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dx < -90) { tick(); vm.next() } else if (dx > 90) { tick(); vm.previous() }; dx = 0f },
                    onDragCancel = { dx = 0f }
                ) { _, d -> dx += d }
            }
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(song, 46.dp, Radius.Thumb)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = sub())
            }
            IconButton(onClick = { tick(); vm.togglePlay() }) { PlayPauseIcon(ui.isPlaying, 32.dp, MaterialTheme.colorScheme.onSurface) }
            IconButton(onClick = { tick(); vm.next() }) { Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(32.dp)) }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 3.dp).fillMaxWidth().height(2.dp)
                .drawBehind {
                    drawRect(Accent.copy(alpha = 0.18f))
                    drawRect(Accent, size = Size(size.width * frac, size.height))
                }
        )
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
    Box(Modifier.glass(RoundedCornerShape(8.dp), true).padding(horizontal = 8.dp, vertical = 3.dp)) {
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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
    val tick = rememberHaptic()

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
    // Follow the hardware volume buttons instantly (the system broadcasts every change), plus a slow poll as backup.
    DisposableEffect(Unit) {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (!volDrag) vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
            }
        }
        ctx.registerReceiver(rx, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        onDispose { runCatching { ctx.unregisterReceiver(rx) } }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!volDrag) vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
            device = outputName(am)
            delay(1000)
        }
    }

    // Only the lyrics pane needs a position in NowPlaying; the progress bar has its own isolated state below.
    LaunchedEffect(ui.isPlaying, ui.currentId, mode) {
        if (mode == 1) {
            pos = vm.position()
            while (isActive && ui.isPlaying) { pos = vm.position(); delay(300) }
        }
    }

    val artVer = ArtCache.version
    val bmp by produceState<Bitmap?>(null, song?.id, artVer) {
        val s = song
        value = if (s == null) null else withContext(Dispatchers.IO) { ArtCache.load(ctx, s, 512) }
    }
    val tint = remember(bmp) { bmp?.let { ArtCache.tone(it) } ?: Color(0xFF3A2A30) }
    val bg by animateColorAsState(tint, tween(600), label = "bg")
    val artScale by animateFloatAsState(if (ui.isPlaying) 1f else 0.86f, label = "art")
    val dur = ui.durationMs.coerceAtLeast(1L)
    val abS by vm.abStart.collectAsState()
    val abE by vm.abEnd.collectAsState()
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

        // Artwork backdrop: blurred once per track off the UI thread (see softBackdrop), then just drawn.
        val bgBmp by produceState<Bitmap?>(null, bmp) { value = bmp?.let { withContext(Dispatchers.Default) { softBackdrop(it) } } }
        Crossfade(bgBmp, animationSpec = tween(700), label = "bgart") { b ->
            if (b != null) {
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.6f, filterQuality = FilterQuality.High)
            }
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                // bg is read here (draw phase) so the colour animation never recomposes the screen
                drawRect(Brush.verticalGradient(listOf(lerp(bg, Accent, 0.30f).copy(0.55f), Color(0xFF0B0507).copy(0.75f), Color.Black.copy(0.92f))))
            }
        )

        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().height(52.dp).bleed(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = white, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", color = white.copy(0.6f), fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Text(source, color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreHoriz, "More", tint = white) }
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
                        AnimatedContent(
                            song?.id, label = "art",
                            transitionSpec = {
                                (fadeIn(tween(450)) + scaleIn(tween(450, easing = FastOutSlowInEasing), 0.85f)) togetherWith
                                    (fadeOut(tween(300)) + scaleOut(tween(300), 1.1f))
                            }
                        ) { id ->
                            val blurR = transition.animateDp(transitionSpec = { tween(400) }, label = "artBlur") { st ->
                                if (st == EnterExitState.Visible) 0.dp else 18.dp
                            }
                            Art(
                                vm.songById(id), side, Radius.Hero,
                                Modifier.motionBlurLazy { blurR.value }.graphicsLayer { scaleX = artScale; scaleY = artScale }
                                    .shadow(24.dp, RoundedCornerShape(Radius.Hero)),
                                px = 720
                            )
                        }
                    }
                    1 -> Column(Modifier.fillMaxSize()) {
                        // Keep the cover and title in view while lyrics are open.
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Art(song, 44.dp, Radius.Thumb)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song?.title ?: "", color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song?.artist ?: "", color = white.copy(0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) { LyricsPane(vm, song, pos) }
                    }
                    else -> QueuePane(vm, ui)
                }
            }
        }

        val controls: @Composable ColumnScope.() -> Unit = {
            // Title row
            Row(Modifier.fillMaxWidth().bleed(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mode == 1) Spacer(Modifier.weight(1f)) else Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(song?.title ?: "", color = white, fontSize = if (tight) 22.sp else 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song?.let { "${it.artist} — ${it.album}" } ?: "", color = Accent, fontSize = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                val fav = song != null && song.id in favs
                IconButton({ tick(); song?.let { vm.toggleFav(it.id) } }) {
                    HeartIcon(fav, white.copy(0.8f))
                }
                IconButton({ pick = true }) { Icon(Icons.Rounded.PlaylistAdd, "Add to playlist", tint = white.copy(0.8f)) }
            }
            if (!tight) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(formatLabel(song?.mime ?: ""))
                if (ui.audioInfo.isNotEmpty()) Badge(ui.audioInfo)
            }

            ProgressSection(vm, ui, white)

            // Transport
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).bleed(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ToggleIcon(Icons.Rounded.Shuffle, ui.shuffle, "Shuffle") { tick(); vm.toggleShuffle() }
                IconButton({ tick(); vm.previous() }, Modifier.size(skipSize)) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = white, modifier = Modifier.size(skipIcon)) }
                Box(
                    Modifier.size(playSize).bouncyTick(0.9f, vm::togglePlay).shadow(20.dp, CircleShape, ambientColor = Accent, spotColor = Accent).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent.copy(alpha = 0.85f), Accent))),
                    contentAlignment = Alignment.Center
                ) { PlayPauseIcon(ui.isPlaying, skipIcon, white) }
                IconButton({ tick(); vm.next() }, Modifier.size(skipSize)) { Icon(Icons.Rounded.SkipNext, "Next", tint = white, modifier = Modifier.size(skipIcon)) }
                ToggleIcon(
                    if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    ui.repeat != Player.REPEAT_MODE_OFF, "Repeat"
                ) { tick(); vm.cycleRepeat() }
            }

            // Volume
            if (!tight) Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeDown, null, tint = white.copy(0.6f), modifier = Modifier.size(20.dp))
                AuraSlider(
                    value = vol,
                    onValueChange = { vol = it; volDrag = true; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVol).roundToInt(), 0) },
                    onValueChangeFinished = {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * maxVol).roundToInt(), 0)
                        vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                        volDrag = false
                    },
                    activeColor = white, inactiveColor = white.copy(0.2f),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp), thumbSize = 12.dp
                )
                Icon(Icons.Rounded.VolumeUp, null, tint = white.copy(0.6f), modifier = Modifier.size(20.dp))
            }

            // Bottom row: lyrics, output device, queue
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).bleed(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ mode = if (mode == 1) 0 else 1 }) { Icon(Icons.Rounded.FormatQuote, "Lyrics", tint = if (mode == 1) Accent else white.copy(0.7f)) }
                Row(
                    Modifier.weight(1f, fill = false).glass(RoundedCornerShape(20.dp), true).clickable {
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
        AuraMenu(
            visible = menu, onDismiss = { menu = false }, dark = true, edgeToEdge = true,
            actions = listOf(
                MenuAction("Sleep timer", Icons.Rounded.Timer) { showSleep = true },
                MenuAction("Equalizer", Icons.Rounded.Tune) { showEq = true },
                MenuAction(
                    when { abS < 0 -> "A-B repeat · mark start (A)"; abE < 0 -> "A-B repeat · mark end (B)"; else -> "Stop A-B repeat" },
                    Icons.Rounded.Repeat, selected = abS >= 0
                ) { vm.abCycle() },
                MenuAction("Share", Icons.Rounded.Share) {
                    song?.let { s ->
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"; putExtra(Intent.EXTRA_STREAM, s.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(i, null))
                    }
                }
            )
        )
        AuraMenu(
            visible = showSleep, onDismiss = { showSleep = false }, title = "Sleep timer", dark = true, edgeToEdge = true,
            actions = listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour").map { (m, l) ->
                MenuAction(l, if (m == 0) Icons.Rounded.TimerOff else Icons.Rounded.Timer, selected = sleep == m) { vm.setSleep(m) }
            }
        )
        AuraMenu(
            visible = pick, onDismiss = { pick = false }, title = "Add to playlist", dark = true, edgeToEdge = true,
            actions = if (playlists.isEmpty())
                listOf(MenuAction("Create a playlist in Library first", Icons.Rounded.PlaylistAdd) { })
            else playlists.keys.sorted().map { n -> MenuAction(n, Icons.Rounded.QueueMusic) { song?.let { vm.addToPlaylist(n, it.id) } } }
        )
        AuraSheet(visible = showEq, onDismiss = { showEq = false }, title = "Equalizer", dark = true, edgeToEdge = true) { fg, sub ->
            EqSheetContent(vm, fg, sub)
        }
    }
}

/** Progress bar + times with their own state, so ticking never recomposes the rest of Now Playing. */
@Composable
private fun ProgressSection(vm: PlayerViewModel, ui: PlayerUi, white: Color) {
    val dur = ui.durationMs.coerceAtLeast(1L)
    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ui.isPlaying, ui.currentId) {
        pos = vm.position()
        while (isActive && ui.isPlaying) { if (!dragging) pos = vm.position(); delay(250) }
    }
    val target = (pos.toFloat() / dur).coerceIn(0f, 1f)
    val smooth by animateFloatAsState(target, tween(260, easing = LinearEasing), label = "progress")
    AuraSlider(
        value = if (dragging) dragValue else smooth,
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = { vm.seekTo((dragValue * dur).toLong()); pos = (dragValue * dur).toLong(); dragging = false },
        activeColor = Accent, inactiveColor = white.copy(0.2f)
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val p = if (dragging) (dragValue * dur).toLong() else pos
        Text(fmt(p), color = white.copy(0.6f), fontSize = 12.sp)
        Text("-" + fmt(dur - p), color = white.copy(0.6f), fontSize = 12.sp)
    }
}

private val EqBandNames = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

@Composable
private fun ColumnScope.EqSheetContent(vm: PlayerViewModel, fg: Color, sub: Color) {
    val preset by vm.preset.collectAsState()
    val bass by vm.bass.collectAsState()
    val tick = rememberHaptic()
    val shape = PlaybackService.PRESETS[preset.coerceIn(0, PlaybackService.PRESETS.lastIndex)]
    val g0 by animateFloatAsState(shape[0], tween(350), label = "eq0")
    val g1 by animateFloatAsState(shape[1], tween(350), label = "eq1")
    val g2 by animateFloatAsState(shape[2], tween(350), label = "eq2")
    val g3 by animateFloatAsState(shape[3], tween(350), label = "eq3")
    val g4 by animateFloatAsState(shape[4], tween(350), label = "eq4")

    // Preset chips
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Flat", "Bass", "Treble", "Vocal", "Rock").forEachIndexed { i, n ->
            val on = preset == i
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(Radius.Control))
                    .background(if (on) Accent else fg.copy(alpha = 0.08f))
                    .bouncyTick(0.94f) { vm.setFx(i, bass) },
                contentAlignment = Alignment.Center
            ) { Text(n, color = if (on) Color.White else fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1) }
        }
    }

    // Live curve of the selected preset
    Canvas(Modifier.padding(horizontal = 28.dp, vertical = 10.dp).fillMaxWidth().height(96.dp)) {
        val w = size.width; val h = size.height; val mid = h / 2f; val amp = h / 2f - 12.dp.toPx()
        val gains = listOf(g0, g1, g2, g3, g4)
        val xs = gains.indices.map { it * w / 4f }
        val ys = gains.map { mid - it.coerceIn(-1f, 1f) * amp }
        drawLine(fg.copy(alpha = 0.14f), Offset(0f, mid), Offset(w, mid), strokeWidth = 1.dp.toPx())
        val line = Path().apply {
            moveTo(xs[0], ys[0])
            for (k in 1 until xs.size) {
                val cx = (xs[k - 1] + xs[k]) / 2f
                cubicTo(cx, ys[k - 1], cx, ys[k], xs[k], ys[k])
            }
        }
        val fill = Path().apply { addPath(line); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(Accent.copy(alpha = 0.32f), Color.Transparent)))
        drawPath(line, Accent, style = Stroke(width = 3.dp.toPx()))
        xs.indices.forEach { k -> drawCircle(Accent, 4.5.dp.toPx(), Offset(xs[k], ys[k])) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        EqBandNames.forEach { Text(it, color = sub, fontSize = 11.sp) }
    }

    // Bass boost
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Bass boost", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("${(bass / 10)}%", color = sub, fontSize = 14.sp)
    }
    var b by remember { mutableFloatStateOf(bass.toFloat()) }
    AuraSlider(
        value = b / 1000f, onValueChange = { b = it * 1000f },
        onValueChangeFinished = { vm.setFx(preset, b.toInt()) },
        activeColor = Accent, inactiveColor = fg.copy(alpha = 0.2f),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
    )
    Spacer(Modifier.height(10.dp))
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
    // Glide so the current line sits in the middle of the pane instead of snapping it to an item boundary.
    LaunchedEffect(cur) {
        if (!synced || cur < 0) return@LaunchedEffect
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == cur }
        if (item != null) {
            val centre = info.viewportSize.height / 2
            state.animateScrollBy((item.offset + item.size / 2 - centre).toFloat(), tween(550, easing = FastOutSlowInEasing))
        } else {
            state.animateScrollToItem(cur, -(info.viewportSize.height / 2 - 40))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(vertical = 140.dp)) {
        itemsIndexed(lines) { i, l ->
            val on = !synced || i == cur
            val a = animateFloatAsState(if (on) 1f else 0.4f, tween(450), label = "la")
            val sc = animateFloatAsState(if (on) 1f else 0.94f, spring(0.8f, 300f), label = "ls")
            val br = animateDpAsState(if (on) 0.dp else 1.5.dp, tween(450), label = "lb")
            Text(
                l.text.ifEmpty { "♪" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).motionBlurLazy { br.value }
                    .graphicsLayer { alpha = a.value; scaleX = sc.value; scaleY = sc.value; transformOrigin = TransformOrigin(0f, 0.5f) },
                textAlign = TextAlign.Start
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuePane(vm: PlayerViewModel, ui: PlayerUi) {
    val queue by vm.queue.collectAsState()
    val tick = rememberHaptic()
    val density = LocalDensity.current
    val from = ui.queueIndex + 1
    val to = minOf(queue.size, from + 100)
    val rowH = 60.dp
    val rowPx = with(density) { rowH.toPx() }
    // Local display order while dragging; the real queue is only changed once, when the finger lifts.
    val order = remember(queue, from) { mutableStateListOf<Int>().apply { addAll((from until to).toList()) } }
    var dragKey by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Up Next", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
        if (order.isEmpty()) item { Text("Nothing queued.", color = Color.White.copy(0.7f)) }
        items(order, key = { it }) { i ->
            val s = vm.songById(queue.getOrNull(i) ?: -1L)
            val scope = rememberCoroutineScope()
            val sx = remember { Animatable(0f) }
            var rowW by remember { mutableIntStateOf(1) }
            val dragging = dragKey == i
            val finishDrag = {
                if (dragKey >= 0) {
                    val target = from + order.indexOf(dragKey)
                    if (target != dragKey) vm.moveItem(dragKey, target)
                    dragKey = -1; dragOffset = 0f
                }
            }
            Box(
                Modifier.fillMaxWidth().height(rowH).onSizeChanged { rowW = it.width }
                    .then(
                        if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset; scaleX = 1.03f; scaleY = 1.03f }
                        else Modifier.animateItemPlacement()
                    )
            ) {
                // Red "remove" strip that grows from the side being swiped
                if (sx.value != 0f) Box(
                    Modifier.align(if (sx.value > 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .width(with(density) { abs(sx.value).toDp() }).fillMaxHeight().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(Radius.Thumb)).background(Color(0xFFFF3B30)),
                    contentAlignment = if (sx.value > 0) Alignment.CenterStart else Alignment.CenterEnd
                ) { Icon(Icons.Rounded.Delete, "Remove", tint = Color.White, modifier = Modifier.padding(horizontal = 16.dp).size(22.dp)) }
                Row(
                    Modifier.fillMaxSize().offset { IntOffset(sx.value.roundToInt(), 0) }
                        .pointerInput(i) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (abs(sx.value) > rowW * 0.35f) {
                                            tick()
                                            sx.animateTo(if (sx.value > 0) rowW * 1.1f else -rowW * 1.1f, tween(160))
                                            vm.removeAt(i)
                                        } else sx.animateTo(0f, spring())
                                    }
                                },
                                onDragCancel = { scope.launch { sx.animateTo(0f, spring()) } }
                            ) { change, d -> change.consume(); scope.launch { sx.snapTo(sx.value + d) } }
                        }
                        .clickable { vm.jumpTo(i) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(s, 44.dp, Radius.Thumb)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s?.title ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                        Text(s?.artist ?: "", color = Color.White.copy(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    }
                    Box(
                        Modifier.size(48.dp).pointerInput(i) {
                            detectVerticalDragGestures(
                                onDragStart = { dragKey = i; dragOffset = 0f; tick() },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() }
                            ) { change, d ->
                                change.consume()
                                dragOffset += d
                                val pos = order.indexOf(i)
                                if (dragOffset > rowPx * 0.55f && pos < order.lastIndex) {
                                    java.util.Collections.swap(order, pos, pos + 1); dragOffset -= rowPx
                                } else if (dragOffset < -rowPx * 0.55f && pos > 0) {
                                    java.util.Collections.swap(order, pos, pos - 1); dragOffset += rowPx
                                }
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.DragHandle, "Reorder", tint = Color.White.copy(0.6f)) }
                }
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
            val h = t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d, easing = LinearEasing), RepeatMode.Reverse), label = "b$i")
            Box(
                Modifier.width(3.dp).fillMaxHeight()
                    .graphicsLayer { scaleY = h.value; transformOrigin = TransformOrigin(0.5f, 1f) }
                    .clip(RoundedCornerShape(2.dp)).background(color)
            )
        }
    }
}

private fun LazyListScope.shelf(title: String, list: List<Song>, vm: PlayerViewModel) {
    if (list.isEmpty()) return
    item { Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = Gutter, top = 14.dp, bottom = 10.dp)) }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(list, key = { _, it -> it.id }) { i, s ->
                Column(Modifier.width(140.dp).fadeUp(minOf(i, 6) * 60).bouncyClick(0.96f) { vm.play(s, list, title) }) {
                    Art(s, 140.dp, Radius.Cover, px = 320)
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
    val loaded by vm.loaded.collectAsState()
    val recents by vm.recents.collectAsState()
    val favs by vm.favs.collectAsState()
    val counts by vm.counts.collectAsState()
    val recent = remember(recents, songs) { recents.mapNotNull { vm.songById(it) }.take(15) }
    val top = remember(counts, songs) { counts.entries.sortedByDescending { it.value }.mapNotNull { vm.songById(it.key) }.take(15) }
    val fav = remember(favs, songs) { songs.filter { it.id in favs }.take(15) }
    val added = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(15) }
    // Smart mixes, built on-device from your own listening habits (no account, no internet).
    val forgotten = remember(favs, recents, songs) {
        val fresh = recents.take(30).toSet()
        songs.filter { it.id in favs && it.id !in fresh }.shuffled().take(15)
    }
    val discover = remember(songs, counts.isEmpty()) { songs.filter { (counts[it.id] ?: 0) == 0 }.shuffled().take(15) }
    val quick = remember(songs, counts) {
        songs.filter { it.durationMs in 1..180_000L }.sortedByDescending { counts[it.id] ?: 0 }.take(15)
    }
    val tod by vm.tod.collectAsState()
    val bucket = remember { timeBucket() }
    val todMix = remember(tod, songs) {
        tod.entries.filter { (it.value.getOrNull(bucket) ?: 0) > 0 }
            .sortedByDescending { it.value[bucket] }.mapNotNull { vm.songById(it.key) }.take(15)
    }
    val plays = remember(counts) { counts.values.sum() }
    val listenedMs = remember(counts, songs) { counts.entries.sumOf { (vm.songById(it.key)?.durationMs ?: 0L) * it.value } }
    val topArtist = remember(counts, songs) {
        counts.entries.mapNotNull { e -> vm.songById(e.key)?.let { it.artist to e.value } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            .filterKeys { it.isNotBlank() && !it.contains("unknown", true) }
            .maxByOrNull { it.value }?.key
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            ScreenHeader("Home") {
                IconButton(openSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Accent) }
            }
        }
        if (songs.isEmpty()) item {
            if (!loaded) SkeletonRows(6)
            else EmptyState(Icons.Rounded.MusicNote, "No music found", "Songs on your device will appear here automatically.")
        }
        if (songs.isNotEmpty()) item {
            Row(Modifier.padding(horizontal = Gutter, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Play All", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { vm.playAll(songs, false) }
                PillButton("Shuffle All", Icons.Rounded.Shuffle, Modifier.weight(1f)) { vm.playAll(songs, true) }
            }
        }
        if (plays > 0) item {
            val mins = listenedMs / 60_000L
            Row(
                Modifier.padding(horizontal = Gutter).fillMaxWidth().glass(RoundedCornerShape(Radius.Card)).padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCell(plays.toString(), "plays")
                StatCell(if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m", "listened")
                if (topArtist != null) StatCell(topArtist, "top artist", Modifier.weight(1f, fill = false))
            }
        }
        shelf("Recently Played", recent, vm)
        shelf("Your ${BucketNames[bucket]} Mix", todMix, vm)
        shelf("Forgotten Favourites", forgotten, vm)
        shelf("Most Played", top, vm)
        shelf("Discover · never played", discover, vm)
        shelf("Quick Hits · under 3 min", quick, vm)
        shelf("Favourites", fav, vm)
        shelf("Recently Added", added, vm)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().glass(RoundedCornerShape(Radius.Card)), content = content)
}

@Composable
private fun RowDivider() {
    Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)))
}

@Composable
private fun IconChip(icon: ImageVector) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SettingHeader(icon: ImageVector, title: String, value: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconChip(icon)
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 14.sp, color = Accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text, fontSize = 13.sp, color = sub(), modifier = Modifier.padding(start = 62.dp, end = 16.dp, top = 6.dp, bottom = 10.dp))
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bouncyClick(0.99f, onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp)
            Text(desc, fontSize = 13.sp, color = sub())
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = sub())
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val tick = rememberHaptic()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconChip(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp)
            Text(desc, fontSize = 13.sp, color = sub())
        }
        Switch(
            on, { tick(); onChange(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 12.sp, color = sub(), maxLines = 1)
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

/** Segmented control with a spring-animated sliding indicator. [inCard] drops the glass look for use inside a settings card. */
@Composable
private fun Segmented(options: List<String>, selected: Int, inCard: Boolean = false, onSelect: (Int) -> Unit) {
    val tick = rememberHaptic()
    val shell = if (inCard)
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.Control))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).padding(3.dp)
    else Modifier.padding(horizontal = Gutter).fillMaxWidth().glass(RoundedCornerShape(Radius.Control)).padding(3.dp)
    BoxWithConstraints(shell) {
        val segW = maxWidth / options.size
        val x = animateDpAsState(segW * selected.coerceAtLeast(0), spring(dampingRatio = 0.75f, stiffness = 380f), label = "seg")
        if (selected >= 0) Box(
            Modifier.offset { IntOffset(x.value.roundToPx(), 0) }.width(segW).height(42.dp).clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(Accent.copy(alpha = 0.9f), Accent)))
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val c by animateColorAsState(if (on) Color.White else MaterialTheme.colorScheme.onSurface, tween(250), label = "segc")
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(11.dp)).clickable { if (!on) tick(); onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, color = c, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, softWrap = false, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: PlayerViewModel, onOpen: (String, String) -> Unit, onBack: () -> Unit) {
    val theme by vm.theme.collectAsState()
    val acc by vm.accentIdx.collectAsState()
    val speed by vm.speed.collectAsState()
    val dyn by vm.dynColors.collectAsState()
    val xfade by vm.xfade.collectAsState()
    val loud by vm.loud.collectAsState()
    val shake by vm.shake.collectAsState()
    val hp by vm.hpResume.collectAsState()
    val hideRec by vm.hideRec.collectAsState()
    val recCount by vm.recCount.collectAsState()
    val hiddenN by vm.hidden.collectAsState()
    val outName by vm.outName.collectAsState()
    val fades = listOf(0, 3, 6, 9)
    var l by remember(loud) { mutableFloatStateOf(loud / 600f) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("Settings", onBack)

        SectionLabel("Appearance")
        SettingsCard {
            SettingHeader(Icons.Rounded.Palette, "Theme")
            Segmented(listOf("System", "Dark", "Light"), theme, inCard = true) { vm.setTheme(it) }
            Spacer(Modifier.height(10.dp))
            RowDivider()
            ToggleRow(Icons.Rounded.AutoAwesome, "Colours follow the cover", "The whole app tints itself to the song that is playing", dyn) { vm.setDyn(it) }
            RowDivider()
            SettingHeader(Icons.Rounded.ColorLens, "Accent colour")
            val tick = rememberHaptic()
            BoxWithConstraints(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth()) {
                val gap = 10.dp
                val d = minOf(40.dp, (maxWidth - gap * (AccentChoices.size - 1)) / AccentChoices.size)
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    AccentChoices.forEachIndexed { i, c ->
                        Box(Modifier.size(d).bouncyClick(0.85f) { tick(); vm.setAccent(i) }.clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(visible = acc == i, enter = scaleIn(spring(0.5f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                                Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.size(d / 1.8f))
                            }
                        }
                    }
                }
            }
        }

        SectionLabel("Playback")
        SettingsCard {
            SettingHeader(Icons.Rounded.Speed, "Playback speed", speedLabel(speed))
            Segmented(SpeedChoices.map(::speedLabel), SpeedChoices.indexOfFirst { kotlin.math.abs(it - speed) < 0.001f }, inCard = true) { vm.setSpeed(SpeedChoices[it]) }
            Footnote("Applies to every song. Pitch stays natural.")
            RowDivider()
            SettingHeader(Icons.Rounded.GraphicEq, "Fade between songs", if (xfade == 0) "Off" else "${xfade}s")
            Segmented(listOf("Off", "3s", "6s", "9s"), fades.indexOf(xfade), inCard = true) { vm.setXfade(fades[it]) }
            Footnote("Each song fades out at the end and the next fades in.")
            RowDivider()
            SettingHeader(Icons.Rounded.VolumeUp, "Loudness boost", if (loud == 0) "Off" else "+${loud / 100} dB")
            AuraSlider(
                value = l, onValueChange = { l = it }, onValueChangeFinished = { vm.setLoud((l * 6).roundToInt() * 100) },
                activeColor = Accent, inactiveColor = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            )
        }

        SectionLabel("Library")
        SettingsCard {
            ToggleRow(
                Icons.Rounded.Mic, "Hide voice recordings",
                if (recCount > 0) "$recCount voice notes / recordings kept out of your songs" else "Keeps voice notes and call recordings out of your songs",
                hideRec
            ) { vm.setHideRec(it) }
            RowDivider()
            ActionRow(Icons.Rounded.ContentCopy, "Find duplicate songs", "Same song saved more than once. Hide the extra copies.") { onOpen("Duplicates", "dups") }
            if (hiddenN.isNotEmpty()) {
                RowDivider()
                ActionRow(Icons.Rounded.Visibility, "Show ${hiddenN.size} hidden song${if (hiddenN.size == 1) "" else "s"}", "Bring back songs you hid from the library") { vm.unhideAll() }
            }
        }

        SectionLabel("Sound profile")
        SettingsCard {
            SettingHeader(Icons.Rounded.Headphones, "Output", outName)
            Footnote("Equalizer and loudness are remembered separately for your phone speaker and each pair of headphones, and switch automatically.")
        }

        SectionLabel("Controls")
        SettingsCard {
            ToggleRow(Icons.Rounded.Vibration, "Shake to skip", "Shake your phone firmly to play the next song", shake) { vm.setShake(it) }
            RowDivider()
            ToggleRow(Icons.Rounded.PlayCircle, "Resume on headphones", "Continue playing when you reconnect headphones after unplugging", hp) { vm.setHpResume(it) }
        }

        SectionLabel("About")
        SettingsCard {
            SettingHeader(Icons.Rounded.Info, "Aura Music 2.0")
            Footnote("Offline player. Your music never leaves your phone.")
        }
    }
}
