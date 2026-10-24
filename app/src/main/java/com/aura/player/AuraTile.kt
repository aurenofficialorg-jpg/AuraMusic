package com.aura.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.KeyEvent

/** Quick-settings tile: shows what is playing and toggles play/pause from the notification shade. */
class AuraTile : TileService() {
    companion object {
        @Volatile var playing = false
        @Volatile var title: String? = null
        fun refresh(ctx: Context) {
            try { TileService.requestListeningState(ctx, ComponentName(ctx, AuraTile::class.java)) } catch (_: Exception) { }
        }
    }

    override fun onStartListening() = render()

    override fun onClick() {
        val code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        try {
            val i = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(this, PlaybackService::class.java)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
            PendingIntent.getService(this, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).send()
        } catch (_: Exception) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    private fun render() {
        val t = qsTile ?: return
        t.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Aura Music"
        t.subtitle = title ?: "Tap to play"
        t.icon = Icon.createWithResource(this, R.drawable.ic_stat_music)
        t.updateTile()
    }
}
