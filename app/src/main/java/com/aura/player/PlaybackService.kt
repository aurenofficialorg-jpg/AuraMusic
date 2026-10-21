package com.aura.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * Foreground media service: keeps music playing with the screen off or the app closed.
 * Owns everything that must keep working in the background: equalizer, bass boost, loudness boost,
 * sleep timer (with fade-out), fade between songs, A-B repeat, shake-to-skip, headphone auto-resume
 * and the home-screen widget.
 *
 * Battery notes: the 80 ms ticker only runs while music plays AND fade/A-B is on; the accelerometer is
 * only registered while music plays AND shake-to-skip is on.
 */
class PlaybackService : MediaSessionService() {
    companion object {
        const val CMD_FX = "aura.fx"
        const val CMD_SLEEP = "aura.sleep"
        const val CMD_PREFS = "aura.prefs"
        const val CMD_AB = "aura.ab"
        // Shape of each preset across 5 frequency zones, -1..1
        val PRESETS = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f),          // Flat
            floatArrayOf(1f, .6f, 0f, 0f, 0f),         // Bass
            floatArrayOf(0f, 0f, 0f, .6f, 1f),         // Treble
            floatArrayOf(-.3f, 0f, .6f, .6f, 0f),      // Vocal
            floatArrayOf(.7f, .3f, -.3f, .3f, .7f),    // Rock
        )
        private val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            26 /* TYPE_BLE_HEADSET (API 31) */
        )
    }

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var eq: Equalizer? = null
    private var bassFx: BassBoost? = null
    private var loudFx: LoudnessEnhancer? = null
    private var preset = 0
    private var bass = 0
    private var loud = 0
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    // ---- volume = sleep fade × song fade -------------------------------------------------
    private var sleepGain = 1f
    private var xGain = 1f
    private fun applyVolume() { player.volume = (sleepGain * xGain).coerceIn(0f, 1f) }

    private var fadeStep = 0
    private val fadeRun = object : Runnable {
        override fun run() {
            if (fadeStep >= 30) { player.pause(); sleepGain = 1f; applyVolume(); return }
            sleepGain = 1f - fadeStep / 30f; applyVolume()
            fadeStep++
            handler.postDelayed(this, 200)
        }
    }
    private val sleepRun = Runnable { fadeStep = 0; fadeRun.run() }

    // ---- fade between songs + A-B repeat ------------------------------------------------
    private var xfadeMs = 0L
    private var abStart = -1L
    private var abEnd = -1L
    private fun tickNeeded() = player.isPlaying && (xfadeMs > 0 || (abStart >= 0 && abEnd > abStart))
    private val ticker = object : Runnable {
        override fun run() {
            val pos = player.currentPosition
            val dur = player.duration
            if (abStart >= 0 && abEnd > abStart && pos >= abEnd) player.seekTo(abStart)
            xGain = if (xfadeMs > 0 && dur != C.TIME_UNSET && dur > xfadeMs * 3) {
                val g = minOf(pos.toFloat() / xfadeMs, (dur - pos).toFloat() / xfadeMs).coerceIn(0f, 1f)
                sqrt(g)                         // equal-power curve sounds smoother than linear
            } else 1f
            applyVolume()
            if (tickNeeded()) handler.postDelayed(this, 80)
        }
    }
    private fun startTicker() {
        handler.removeCallbacks(ticker)
        if (xfadeMs <= 0) { xGain = 1f; applyVolume() }
        if (tickNeeded()) handler.post(ticker)
    }

    // ---- shake to skip ------------------------------------------------------------------
    private var shakeOn = false
    private val shakeL = object : SensorEventListener {
        private var last = 0L
        override fun onSensorChanged(e: SensorEvent) {
            val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
            if (g > 2.6f) {
                val t = SystemClock.elapsedRealtime()
                if (t - last > 1200) { last = t; player.seekToNextMediaItem() }
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    private fun updateShake() {
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.unregisterListener(shakeL)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (shakeOn && player.isPlaying && acc != null) sm.registerListener(shakeL, acc, SensorManager.SENSOR_DELAY_UI)
    }

    // ---- headphone auto-resume ----------------------------------------------------------
    private var hpResume = false
    private var resumeOnDevice = false
    private var resumeStamp = 0L
    private val deviceCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            if (!hpResume || !resumeOnDevice || player.isPlaying) return
            if (SystemClock.elapsedRealtime() - resumeStamp > 10 * 60_000L) { resumeOnDevice = false; return }
            if (added.any { it.isSink && it.type in HEADSET_TYPES }) { resumeOnDevice = false; player.play() }
        }
    }

    // ---- widget -------------------------------------------------------------------------
    private fun refreshWidget() {
        if (!AuraWidget.hasInstances(this)) return
        val item = player.currentMediaItem
        val md = item?.mediaMetadata
        val playing = player.isPlaying
        val id = item?.mediaId?.toLongOrNull()
        val album = md?.artworkUri?.getQueryParameter("albumId")?.toLongOrNull() ?: 0L
        val title = md?.title?.toString(); val artist = md?.artist?.toString()
        val ctx = applicationContext
        io.execute {
            val art = id?.let { ArtCache.load(ctx, Song(it, "", "", "", album, 0L), 160) }
            AuraWidget.push(ctx, title, artist, playing, art)
        }
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession, controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_FX, Bundle.EMPTY))
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                .add(SessionCommand(CMD_PREFS, Bundle.EMPTY))
                .add(SessionCommand(CMD_AB, Bundle.EMPTY)).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds).build()
        }

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand, args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_FX -> { preset = args.getInt("preset"); bass = args.getInt("bass"); applyFx() }
                CMD_SLEEP -> {
                    handler.removeCallbacks(sleepRun); handler.removeCallbacks(fadeRun)
                    sleepGain = 1f; applyVolume()
                    val min = args.getInt("min")
                    if (min > 0) handler.postDelayed(sleepRun, min * 60_000L)
                }
                CMD_PREFS -> {
                    xfadeMs = args.getInt("xfade") * 1000L
                    loud = args.getInt("loud")
                    shakeOn = args.getBoolean("shake")
                    hpResume = args.getBoolean("hp")
                    applyLoud(); startTicker(); updateShake()
                }
                CMD_AB -> { abStart = args.getLong("a", -1L); abEnd = args.getLong("b", -1L); startTicker() }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            // Local files: a small buffer is plenty and keeps memory + wake-ups down.
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 30_000, 500, 1_000).build())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = initFx(audioSessionId)
            override fun onIsPlayingChanged(isPlaying: Boolean) { startTicker(); updateShake(); refreshWidget() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) { abStart = -1L; abEnd = -1L }
                refreshWidget()
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refreshWidget()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                    resumeOnDevice = true; resumeStamp = SystemClock.elapsedRealtime()
                } else if (playWhenReady) resumeOnDevice = false
            }
        })
        initFx(player.audioSessionId)
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).registerAudioDeviceCallback(deviceCb, handler)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(open)               // tapping the notification / lock-screen card opens the app
            .setBitmapLoader(AuraBitmapLoader(this)) // cover art for notification + lock screen
            .build()
        // Rich media notification: cover art, title/artist, previous / play-pause / next, own channel + icon.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("aura_playback")
                .setChannelName(R.string.notif_channel)
                .setNotificationId(1001)
                .build().apply { setSmallIcon(R.drawable.ic_stat_music) }
        )
    }

    private fun initFx(id: Int) {
        if (id == C.AUDIO_SESSION_ID_UNSET) return
        try {
            eq?.release(); bassFx?.release()
            eq = Equalizer(0, id).apply { enabled = true }
            bassFx = BassBoost(0, id).apply { enabled = true }
            applyFx()
        } catch (_: Exception) { }
        try {
            loudFx?.release()
            loudFx = LoudnessEnhancer(id)
            applyLoud()
        } catch (_: Exception) { }
    }

    private fun applyLoud() {
        try { loudFx?.let { it.setTargetGain(loud); it.enabled = loud > 0 } } catch (_: Exception) { }
    }

    private fun applyFx() {
        try {
            eq?.let { e ->
                val n = e.numberOfBands.toInt()
                val min = e.bandLevelRange[0].toInt()
                val max = e.bandLevelRange[1].toInt()
                val shape = PRESETS[preset.coerceIn(0, PRESETS.lastIndex)]
                for (i in 0 until n) {
                    val z = if (n == 1) 0 else Math.round(i * 4f / (n - 1))
                    val level = (shape[z] * max * 0.6f).toInt().coerceIn(min, max)
                    e.setBandLevel(i.toShort(), level.toShort())
                }
            }
            bassFx?.takeIf { it.strengthSupported }?.setStrength(bass.coerceIn(0, 1000).toShort())
        } catch (_: Exception) { }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(shakeL)
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(deviceCb)
        io.shutdown()
        eq?.release(); bassFx?.release(); loudFx?.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
