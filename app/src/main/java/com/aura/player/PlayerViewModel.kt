package com.aura.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
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
import kotlinx.coroutines.withContext

data class PlayerUi(
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0L,
    val queueIndex: Int = 0,
    /** Real stream details for the current track, e.g. "256 kbps · 44.1 kHz". Empty until the player reports tracks. */
    val audioInfo: String = "",
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
    val hideRec = MutableStateFlow(store.hideRec)
    val recCount = MutableStateFlow(0)
    val xfade = MutableStateFlow(store.xfade)
    val loud = MutableStateFlow(store.loud)
    val shake = MutableStateFlow(store.shake)
    val hpResume = MutableStateFlow(store.hpResume)
    val tod = MutableStateFlow(store.tod())
    val abStart = MutableStateFlow(-1L)
    val abEnd = MutableStateFlow(-1L)
    val dynColors = MutableStateFlow(store.dyn)
    val hidden = MutableStateFlow(store.hidden())

    // Sound profile per output: speaker and every pair of headphones keep their own EQ + loudness.
    val outName = MutableStateFlow("This Phone")
    private var outKey = "speaker"
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val devCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refreshOut()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refreshOut()
    }
    private fun refreshOut() {
        val types = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26 /* BLE headset */
        )
        val d = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in types }
        val key = if (d == null) "speaker" else "${d.type}:${d.productName}"
        outName.value = if (d == null) "This Phone" else (d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Headphones")
        if (key == outKey) return
        store.saveProfile(outKey, preset.value, bass.value, loud.value)
        outKey = key
        store.profile(key)?.let { (p, b, l) ->
            preset.value = p; bass.value = b; loud.value = l
            store.preset = p; store.bass = b; store.loud = l
            sendFx(); sendPrefs()
        }
    }

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
        audio.registerAudioDeviceCallback(devCb, Handler(Looper.getMainLooper()))
        refreshOut()
        Accent = AccentChoices[store.accentIdx.coerceIn(0, AccentChoices.lastIndex)]
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        f.addListener({
            val c = f.get()
            controller = c
            c.addListener(listener)
            syncQueue(c); sync(c); sendFx(); sendPrefs(); c.setPlaybackSpeed(speed.value); restoreSession()
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
            val b = timeBucket()
            val cur = tod.value[id] ?: List(4) { 0 }
            tod.value = tod.value + (id to cur.mapIndexed { i, v -> if (i == b) v + 1 else v })
            store.saveTod(tod.value)
            abStart.value = -1L; abEnd.value = -1L
        }
        if (id != null && (!p.isPlaying || id != _ui.value.currentId)) saveSession(p)
        _ui.value = PlayerUi(
            id, p.isPlaying, p.shuffleModeEnabled, p.repeatMode,
            p.duration.coerceAtLeast(0L), p.currentMediaItemIndex,
            audioInfoOf(p)
        )
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun audioInfoOf(p: Player): String {
        val fmt = p.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected && it.length > 0 }
            ?.getTrackFormat(0) ?: return ""
        val parts = ArrayList<String>(2)
        val br = if (fmt.averageBitrate != Format.NO_VALUE) fmt.averageBitrate else fmt.bitrate
        if (br != Format.NO_VALUE && br > 0) parts += "${(br + 500) / 1000} kbps"
        if (fmt.sampleRate != Format.NO_VALUE && fmt.sampleRate > 0) {
            val khz = fmt.sampleRate / 1000.0
            parts += (if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else "%.1f kHz".format(khz))
        }
        return parts.joinToString(" · ")
    }

    fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = MusicRepository.load(getApplication<Application>().contentResolver)
            val hid = hidden.value
            val list = all.filter { it.id !in hid && !(hideRec.value && it.isRecording) }
            byId = all.associateBy { it.id }
            recCount.value = all.size - all.count { !it.isRecording }
            _songs.value = list
            _loaded.value = true
            ArtCache.clearMisses()
            withContext(Dispatchers.Main) { restoreSession() }
        }
    }

    fun hideSong(id: Long) { hidden.value = hidden.value + id; store.saveHidden(hidden.value); loadSongs() }
    fun unhideAll() { hidden.value = emptySet(); store.saveHidden(emptySet()); loadSongs() }
    fun setDyn(on: Boolean) { dynColors.value = on; store.dyn = on }

    /** Songs that look like the same track twice (same title + artist, ignoring "(Official Video)", case, punctuation). */
    fun duplicates(list: List<Song>): List<Song> {
        fun norm(t: String) = t.lowercase().replace(Regex("[(\\[].*?[)\\]]"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
        return list.groupBy { s ->
            val a = if (s.artist == "Unknown Artist") "?${s.durationMs / 2000}" else norm(s.artist)
            norm(s.title) + "|" + a
        }.values.filter { it.size > 1 }.flatMap { g -> g.sortedBy { it.dateAdded } }
    }

    fun setHideRec(on: Boolean) { hideRec.value = on; store.hideRec = on; loadSongs() }

    /** Bring back the last queue (paused, at the saved position) when the app starts with an empty player. */
    private fun restoreSession() {
        val c = controller ?: return
        if (!_loaded.value || c.mediaItemCount > 0) return
        val ids = store.sessionIds().filter { it in byId }
        if (ids.isEmpty()) return
        val start = ids.indexOf(store.sessionCur()).coerceAtLeast(0)
        lastId = ids[start]   // restoring is not a new play: don't count it
        c.setMediaItems(ids.map { item(byId.getValue(it)) }, start, store.sessionPos())
        c.prepare()
    }

    private fun saveSession(p: Player) {
        val cur = p.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        store.saveSession(queue.value.filter { it >= 0 }, cur, p.currentPosition)
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
    fun moveItem(from: Int, to: Int) {
        controller?.let {
            val cur = it.currentMediaItemIndex
            if (from > cur && to > cur && from != to) it.moveMediaItem(from, to)
        }
    }

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
        store.saveProfile(outKey, p, b, loud.value)
    }
    private fun sendFx() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_FX, Bundle.EMPTY),
            Bundle().apply { putInt("preset", preset.value); putInt("bass", bass.value) }
        )
    }

    // Premium playback options: all of these run inside the service so they work with the screen off.
    private fun sendPrefs() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_PREFS, Bundle.EMPTY),
            Bundle().apply {
                putInt("xfade", xfade.value); putInt("loud", loud.value)
                putBoolean("shake", shake.value); putBoolean("hp", hpResume.value)
            }
        )
    }
    fun setXfade(sec: Int) { xfade.value = sec; store.xfade = sec; sendPrefs() }
    fun setLoud(mb: Int) { loud.value = mb; store.loud = mb; sendPrefs(); store.saveProfile(outKey, preset.value, bass.value, mb) }
    fun setShake(on: Boolean) { shake.value = on; store.shake = on; sendPrefs() }
    fun setHpResume(on: Boolean) { hpResume.value = on; store.hpResume = on; sendPrefs() }

    /** A-B repeat: first call marks A, second marks B (loop starts), third clears it. */
    fun abCycle() {
        val c = controller ?: return
        val pos = c.currentPosition
        when {
            abStart.value < 0 -> abStart.value = pos
            abEnd.value < 0 -> if (pos > abStart.value + 1000) abEnd.value = pos else abStart.value = pos
            else -> { abStart.value = -1L; abEnd.value = -1L }
        }
        c.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_AB, Bundle.EMPTY),
            Bundle().apply { putLong("a", abStart.value); putLong("b", abEnd.value) }
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
        audio.unregisterAudioDeviceCallback(devCb)
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
