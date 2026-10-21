package com.aura.player

import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Foreground media service: keeps music playing with the screen off or the app closed.
 * Also owns the equalizer, bass boost and sleep timer so they keep working in the background.
 */
class PlaybackService : MediaSessionService() {
    companion object {
        const val CMD_FX = "aura.fx"
        const val CMD_SLEEP = "aura.sleep"
        // Shape of each preset across 5 frequency zones, -1..1
        val PRESETS = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f),          // Flat
            floatArrayOf(1f, .6f, 0f, 0f, 0f),         // Bass
            floatArrayOf(0f, 0f, 0f, .6f, 1f),         // Treble
            floatArrayOf(-.3f, 0f, .6f, .6f, 0f),      // Vocal
            floatArrayOf(.7f, .3f, -.3f, .3f, .7f),    // Rock
        )
    }

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var eq: Equalizer? = null
    private var bassFx: BassBoost? = null
    private var preset = 0
    private var bass = 0
    private val handler = Handler(Looper.getMainLooper())
    private val sleepRun = Runnable { player.pause() }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession, controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_FX, Bundle.EMPTY))
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY)).build()
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
                    handler.removeCallbacks(sleepRun)
                    val min = args.getInt("min")
                    if (min > 0) handler.postDelayed(sleepRun, min * 60_000L)
                }
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
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = initFx(audioSessionId)
        })
        initFx(player.audioSessionId)
        session = MediaSession.Builder(this, player).setCallback(callback).build()
    }

    private fun initFx(id: Int) {
        if (id == C.AUDIO_SESSION_ID_UNSET) return
        try {
            eq?.release(); bassFx?.release()
            eq = Equalizer(0, id).apply { enabled = true }
            bassFx = BassBoost(0, id).apply { enabled = true }
            applyFx()
        } catch (_: Exception) { }
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
        handler.removeCallbacks(sleepRun)
        eq?.release(); bassFx?.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
