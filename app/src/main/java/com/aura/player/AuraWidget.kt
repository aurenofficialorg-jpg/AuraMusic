package com.aura.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews

/** Home-screen player: cover, title/artist and previous / play-pause / next. Tap the cover area to open the app. */
class AuraWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val rv = build(ctx, last)
        ids.forEach { mgr.updateAppWidget(it, rv) }
    }

    companion object {
        private class State(val title: String?, val artist: String?, val playing: Boolean, val art: Bitmap?)
        @Volatile private var last: State? = null

        private fun ids(ctx: Context) =
            AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, AuraWidget::class.java))

        fun hasInstances(ctx: Context) = ids(ctx).isNotEmpty()

        fun push(ctx: Context, title: String?, artist: String?, playing: Boolean, art: Bitmap?) {
            val st = State(title, artist, playing, art)
            last = st
            val ids = ids(ctx)
            if (ids.isEmpty()) return
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, build(ctx, st))
        }

        private fun key(ctx: Context, code: Int): PendingIntent {
            val i = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(ctx, PlaybackService::class.java)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
            return PendingIntent.getService(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun build(ctx: Context, st: State?): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_player)
            rv.setTextViewText(R.id.w_title, st?.title ?: "Aura Music")
            rv.setTextViewText(R.id.w_artist, if (st?.title != null) (st.artist ?: "") else "Tap to open")
            if (st?.art != null) rv.setImageViewBitmap(R.id.w_art, st.art) else rv.setImageViewResource(R.id.w_art, R.mipmap.ic_launcher)
            rv.setImageViewResource(R.id.w_play, if (st?.playing == true) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            rv.setOnClickPendingIntent(R.id.w_prev, key(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            rv.setOnClickPendingIntent(R.id.w_play, key(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            rv.setOnClickPendingIntent(R.id.w_next, key(ctx, KeyEvent.KEYCODE_MEDIA_NEXT))
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)
            return rv
        }
    }
}
