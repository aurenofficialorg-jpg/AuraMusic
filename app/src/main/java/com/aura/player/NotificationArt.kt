package com.aura.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.core.graphics.PathParser
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/**
 * Supplies the cover art shown in the notification shade and on the lock screen.
 * Uses the same loader as the in-app art (thumbnail, embedded tag, album art) and falls back
 * to a branded gradient tile when a track has no cover.
 */
class AuraBitmapLoader(private val ctx: Context) : BitmapLoader {
    private val exec = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String) = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = exec.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: placeholder()
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = exec.submit<Bitmap> {
        val id = uri.lastPathSegment?.toLongOrNull()
        val albumId = uri.getQueryParameter("albumId")?.toLongOrNull() ?: 0L
        val art = if (id != null) ArtCache.load(ctx, Song(id, "", "", "", albumId, 0L), SIZE) else null
        art ?: placeholder()
    }

    private fun placeholder(): Bitmap {
        val b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), 0xFFFA2D48.toInt(), 0xFF5E5CE6.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), p)
        val note = PathParser.createPathFromPathData("M46,34 L74,28 L74,66 A8,8 0 1 1 68,58.3 L68,40 L52,43.5 L52,72 A8,8 0 1 1 46,64.3 Z")
        val s = SIZE / 108f
        note.transform(Matrix().apply { setScale(s, s) })
        p.shader = null; p.color = 0xE6FFFFFF.toInt()
        c.drawPath(note, p)
        return b
    }

    private companion object { const val SIZE = 512 }
}
