package com.aura.player

import android.app.Application
import android.content.ComponentName
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUi(
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0L,
    val queueIndex: Int = 0,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val favs = MutableStateFlow(store.favs())
    val recents = MutableStateFlow(store.recents())
    val playlists = MutableStateFlow(store.playlists())
    val queue = MutableStateFlow<List<Long>>(emptyList())
    val sleepMin = MutableStateFlow(0)
    val preset = MutableStateFlow(store.preset)
    val bass = MutableStateFlow(store.bass)
    val counts = MutableStateFlow(store.counts())
    val theme = MutableStateFlow(store.theme)
    val accentIdx = MutableStateFlow(store.accentIdx)
    val speed = MutableStateFlow(store.speed)
    val source = MutableStateFlow("Library")

    private var byId: Map<Long, Song> = emptyMap()
    private val itemCache = HashMap<Long, MediaItem>()
    private var controller: MediaController? = null
    private var lastId: Long? = null

    fun songById(id: Long?): Song? = id?.let { byId[it] }
    private fun item(s: Song) = itemCache.getOrPut(s.id) { s.toMediaItem() }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                player.mediaItemCount != queue.value.size) syncQueue(player)
            sync(player)
        }
    }

    // Live library: any change in MediaStore (new download, deleted file, re-tag) refreshes the lists automatically.
    private var reloadJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    /** Debounced reload; safe to call often (e.g. from onResume). */
    fun refresh() {
        if (!_loaded.value) return
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch { delay(700); loadSongs() }
    }

    init {
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        Accent = AccentChoices[store.accentIdx.coerceIn(0, AccentChoices.lastIndex)]
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        f.addListener({
            val c = f.get()
            controller = c
            c.addListener(listener)
            syncQueue(c); sync(c); sendFx(); c.setPlaybackSpeed(speed.value)
        }, MoreExecutors.directExecutor())
    }

    private fun syncQueue(p: Player) {
        queue.value = List(p.mediaItemCount) { p.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
    }

    private fun sync(p: Player) {
        val id = p.currentMediaItem?.mediaId?.toLongOrNull()
        if (id != null && id != lastId) {
            lastId = id
            recents.value = (listOf(id) + recents.value.filter { it != id }).take(30)
            store.saveRecents(recents.value)
            counts.value = counts.value + (id to ((counts.value[id] ?: 0) + 1))
            store.saveCounts(counts.value)
        }
        _ui.value = PlayerUi(
            id, p.isPlaying, p.shuffleModeEnabled, p.repeatMode,
            p.duration.coerceAtLeast(0L), p.currentMediaItemIndex
        )
    }

    fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = MusicRepository.load(getApplication<Application>().contentResolver)
            byId = list.associateBy { it.id }
            _songs.value = list
            _loaded.value = true
            ArtCache.clearMisses()
        }
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    fun play(song: Song, list: List<Song>, src: String = "Library") {
        val c = controller ?: return
        if (_ui.value.currentId == song.id) { togglePlay(); return }
        source.value = src
        c.setMediaItems(list.map { item(it) }, list.indexOf(song).coerceAtLeast(0), 0L)
        c.prepare(); c.play()
    }

    fun playAll(list: List<Song>, shuffle: Boolean, src: String = "Library") {
        val c = controller ?: return
        if (list.isEmpty()) return
        source.value = src
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(list.map { item(it) }, if (shuffle) list.indices.random() else 0, 0L)
        c.prepare(); c.play()
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playAll(listOf(song), false); return }
        c.addMediaItem(c.currentMediaItemIndex + 1, item(song))
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNext() }
    fun previous() { controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPrevious() } }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // Queue
    fun jumpTo(i: Int) { controller?.let { it.seekToDefaultPosition(i); it.play() } }
    fun moveUp(i: Int) { controller?.let { if (i > it.currentMediaItemIndex + 1) it.moveMediaItem(i, i - 1) } }
    fun removeAt(i: Int) { controller?.removeMediaItem(i) }

    // Favourites & playlists
    fun toggleFav(id: Long) {
        favs.value = favs.value.let { if (id in it) it - id else it + id }
        store.saveFavs(favs.value)
    }
    private fun savePl(m: Map<String, List<Long>>) { playlists.value = m; store.savePlaylists(m) }
    fun createPlaylist(name: String) {
        val n = name.trim(); if (n.isNotEmpty() && n !in playlists.value) savePl(playlists.value + (n to emptyList()))
    }
    fun deletePlaylist(name: String) = savePl(playlists.value - name)
    fun addToPlaylist(name: String, id: Long) {
        val l = playlists.value[name] ?: return
        if (id !in l) savePl(playlists.value + (name to (l + id)))
    }
    fun removeFromPlaylist(name: String, id: Long) {
        val l = playlists.value[name] ?: return
        savePl(playlists.value + (name to l.filter { it != id }))
    }

    // Sleep timer & equalizer (run inside the service so they work with the screen off)
    fun setSleep(min: Int) {
        sleepMin.value = min
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY), Bundle().apply { putInt("min", min) }
        )
    }
    fun setFx(p: Int, b: Int) {
        preset.value = p; bass.value = b; store.preset = p; store.bass = b; sendFx()
    }
    private fun sendFx() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_FX, Bundle.EMPTY),
            Bundle().apply { putInt("preset", preset.value); putInt("bass", bass.value) }
        )
    }

    fun setTheme(t: Int) { theme.value = t; store.theme = t }
    fun setAccent(i: Int) { accentIdx.value = i; store.accentIdx = i; Accent = AccentChoices[i] }
    fun setSpeed(s: Float) { speed.value = s; store.speed = s; controller?.setPlaybackSpeed(s) }

    // Lyrics
    fun lyrics(id: Long): String? = store.lyrics(id)
    fun saveLyrics(id: Long, text: String) = store.saveLyrics(id, text)

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
