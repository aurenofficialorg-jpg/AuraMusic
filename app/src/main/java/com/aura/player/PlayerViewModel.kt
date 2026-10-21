package com.aura.player

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUi(
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0L,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var items: List<MediaItem> = emptyList()
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            sync(c)
        }, MoreExecutors.directExecutor())
    }

    private fun sync(p: Player) {
        _ui.value = PlayerUi(
            currentId = p.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = p.isPlaying,
            shuffle = p.shuffleModeEnabled,
            repeat = p.repeatMode,
            durationMs = p.duration.coerceAtLeast(0L),
        )
    }

    fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = MusicRepository.load(getApplication<Application>().contentResolver)
            items = list.map { it.toMediaItem() }
            _songs.value = list
            _loaded.value = true
        }
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    fun play(song: Song) {
        val c = controller ?: return
        if (_ui.value.currentId == song.id) { togglePlay(); return }
        val idx = _songs.value.indexOfFirst { it.id == song.id }
        if (idx < 0) return
        c.setMediaItems(items, idx, 0L)
        c.prepare(); c.play()
    }

    fun playAll(shuffle: Boolean) {
        val c = controller ?: return
        if (items.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(items, if (shuffle) items.indices.random() else 0, 0L)
        c.prepare(); c.play()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNext() }
    fun previous() {
        controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPrevious() }
    }
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

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
