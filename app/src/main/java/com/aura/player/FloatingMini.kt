package com.aura.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Small draggable player shown over other apps while you are outside Aura Music.
 * Needs Android's "Display over other apps" permission and the switch in Settings. Hosted by [PlaybackService],
 * so it lives exactly as long as playback does. Plain Views (no Compose) keep it light inside a Service.
 */
class FloatingMini(private val ctx: Context, private val player: Player) {
    companion object {
        const val KEY = "floatMini"
        /** Set by MainActivity.onStart / onStop. The overlay only shows while the app itself is not on screen. */
        @Volatile var appVisible = false
        var instance: FloatingMini? = null
        private var lastX = -1
        private var lastY = -1
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private var root: View? = null
    private var lp: WindowManager.LayoutParams? = null
    private var artV: ImageView? = null
    private var titleV: TextView? = null
    private var artistV: TextView? = null
    private var playV: ImageView? = null
    private var shownArtId = -1L
    private var dismissedFor: String? = null

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** Re-evaluate everything: show, hide or just update the text and buttons. */
    fun refresh() {
        val on = ctx.getSharedPreferences("aura", Context.MODE_PRIVATE).getBoolean(KEY, false)
        val want = on && Settings.canDrawOverlays(ctx) && !appVisible &&
            player.mediaItemCount > 0 && dismissedFor != player.currentMediaItem?.mediaId
        if (!want) { hide(); return }
        show()
        bind()
    }

    /** Coming back into the app forgets a previous "close" so the pill returns next time you leave. */
    fun onAppVisible() { dismissedFor = null; refresh() }

    fun release() { hide(); io.shutdown(); if (instance === this) instance = null }

    private fun hide() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        root = null; lp = null; shownArtId = -1L
    }

    private fun show() {
        if (root != null) return
        val dm = ctx.resources.displayMetrics
        val v = build()
        val p = WindowManager.LayoutParams(
            minOf(dp(320), dm.widthPixels - dp(24)), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (lastX >= 0) lastX else dp(12)
            y = if (lastY >= 0) lastY else (dm.heightPixels * 0.70f).toInt()
        }
        try { wm.addView(v, p); root = v; lp = p } catch (_: Exception) { root = null; lp = null }
    }

    private fun label(sizeSp: Float, color: Int, bold: Boolean) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp); setTextColor(color)
        setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(res: Int, onClick: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res); setColorFilter(Color.WHITE)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { onClick() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build(): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat(); setColor(0xF01C1C1F.toInt()); setStroke(dp(1), 0x33FFFFFF)
            }
            elevation = dp(8).toFloat()
        }
        val art = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF2A2A2E.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat()) }
            }
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10); marginEnd = dp(4) }
        }
        val title = label(14f, Color.WHITE, true)
        val artist = label(12f, 0xFF9A9AA0.toInt(), false)
        col.addView(title); col.addView(artist)

        val play = button(R.drawable.ic_w_play) { if (player.isPlaying) player.pause() else player.play(); bind() }
        val next = button(R.drawable.ic_w_next) { player.seekToNextMediaItem() }
        val close = TextView(ctx).apply {
            text = "\u2715"; setTextColor(0xFF9A9AA0.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(40))
            setOnClickListener { dismissedFor = player.currentMediaItem?.mediaId; hide() }
        }
        card.addView(art); card.addView(col); card.addView(play); card.addView(next); card.addView(close)

        // Drag from anywhere that isn't a button; a plain tap opens the app.
        card.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                val p = lp ?: return false
                val dm = ctx.resources.displayMetrics
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx; val dy = e.rawY - sy
                        if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                        if (moved) {
                            p.x = (ox + dx).toInt().coerceIn(0, (dm.widthPixels - v.width).coerceAtLeast(0))
                            p.y = (oy + dy).toInt().coerceIn(0, (dm.heightPixels - v.height).coerceAtLeast(0))
                            lastX = p.x; lastY = p.y
                            try { wm.updateViewLayout(v, p) } catch (_: Exception) { }
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) open()
                }
                return true
            }
        })
        artV = art; titleV = title; artistV = artist; playV = play
        return card
    }

    private fun open() {
        try {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (_: Exception) { }
    }

    private fun bind() {
        if (root == null) return
        val item = player.currentMediaItem
        val md = item?.mediaMetadata
        titleV?.text = md?.title ?: ""
        artistV?.text = md?.artist ?: ""
        playV?.setImageResource(if (player.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play)

        val id = item?.mediaId?.toLongOrNull() ?: return
        if (id == shownArtId) return
        shownArtId = id
        val cached = ArtCache.get(id, 128)
        if (cached != null) { artV?.setImageBitmap(cached); return }
        artV?.setImageDrawable(null)
        val albumId = md?.artworkUri?.getQueryParameter("albumId")?.toLongOrNull() ?: 0L
        val song = Song(id, md?.title?.toString() ?: "", md?.artist?.toString() ?: "", md?.albumTitle?.toString() ?: "", albumId, 0L)
        io.execute {
            val bmp = try { ArtCache.load(ctx, song, 128) } catch (_: Exception) { null }
            if (bmp != null) main.post { if (shownArtId == id) artV?.setImageBitmap(bmp) }
        }
    }
}
