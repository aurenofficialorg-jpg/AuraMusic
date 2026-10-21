package com.aura.player

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small in-memory cache (1/12 of the app heap) so scrolling stays smooth and cheap. */
object ArtCache {
    private val cache = object : LruCache<Long, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 12).toInt().coerceAtLeast(4 * 1024 * 1024)
    ) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val misses = HashSet<Long>()

    private fun key(id: Long, px: Int) = id * 2 + if (px > 400) 1 else 0

    fun get(id: Long, px: Int = 256): Bitmap? = cache.get(key(id, px))

    /** Bumped whenever failed lookups are forgotten, so on-screen art retries automatically. */
    var version by mutableIntStateOf(0)

    fun clearMisses() {
        val had = synchronized(misses) { val h = misses.isNotEmpty(); misses.clear(); h }
        if (had) version++
    }

    fun load(ctx: Context, song: Song, px: Int): Bitmap? {
        val k = key(song.id, px)
        cache.get(k)?.let { return it }
        if (synchronized(misses) { k in misses }) return null
        val b = fromThumbnail(ctx, song.uri, px) ?: fromEmbedded(ctx, song.uri, px) ?: fromAlbum(ctx, song.albumId, px)
        if (b != null) cache.put(k, b) else synchronized(misses) { misses += k }
        return b
    }

    private fun fromThumbnail(ctx: Context, uri: Uri, px: Int): Bitmap? =
        try { ctx.contentResolver.loadThumbnail(uri, Size(px, px), null) } catch (_: Exception) { null }

    /** Reads the cover tag straight from the file when MediaStore has not generated a thumbnail yet. */
    private fun fromEmbedded(ctx: Context, uri: Uri, px: Int): Bitmap? = try {
        val r = MediaMetadataRetriever()
        try { r.setDataSource(ctx, uri); r.embeddedPicture?.let { decode(it, px) } } finally { r.release() }
    } catch (_: Exception) { null }

    private fun fromAlbum(ctx: Context, albumId: Long, px: Int): Bitmap? {
        if (albumId <= 0) return null
        return try {
            ctx.contentResolver.loadThumbnail(
                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albums"), albumId), Size(px, px), null
            )
        } catch (_: Exception) { null }
    }

    private fun decode(bytes: ByteArray, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Darkened average colour of the artwork, used for the Now Playing background. */
    fun tone(b: Bitmap): Color {
        val c = Color(Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0))
        return Color(c.red * 0.55f, c.green * 0.55f, c.blue * 0.55f)
    }
}

/**
 * Heavily blurred copy of the artwork for the Now Playing backdrop. Computed once per track on a background thread
 * (3 box-blur passes ≈ gaussian on a 64px copy), so there is no per-frame blur cost at all and it works on every Android version.
 */
fun softBackdrop(src: Bitmap): Bitmap {
    val n = 64
    val small = Bitmap.createScaledBitmap(src, n, n, true)
    var a = IntArray(n * n)
    small.getPixels(a, 0, n, 0, 0, n, n)
    var b = IntArray(n * n)
    repeat(3) {
        boxPass(a, b, n, 5, true); boxPass(b, a, n, 5, false)
    }
    return Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888).also { it.setPixels(a, 0, n, 0, 0, n, n) }
}

private fun boxPass(src: IntArray, dst: IntArray, n: Int, r: Int, horizontal: Boolean) {
    val cnt = 2 * r + 1
    for (line in 0 until n) for (i in 0 until n) {
        var a = 0; var rr = 0; var g = 0; var bl = 0
        for (k in -r..r) {
            val j = (i + k).coerceIn(0, n - 1)
            val c = if (horizontal) src[line * n + j] else src[j * n + line]
            a += c ushr 24; rr += (c shr 16) and 255; g += (c shr 8) and 255; bl += c and 255
        }
        val o = ((a / cnt) shl 24) or ((rr / cnt) shl 16) or ((g / cnt) shl 8) or (bl / cnt)
        if (horizontal) dst[line * n + i] = o else dst[i * n + line] = o
    }
}

@Composable
fun Art(song: Song?, size: Dp, corner: Dp, modifier: Modifier = Modifier, px: Int = 256) {
    val ctx = LocalContext.current
    val ver = ArtCache.version
    // State is re-created whenever the song changes, so a previous song's cover can never linger.
    val state = remember(song?.id, ver, px) { mutableStateOf<Bitmap?>(song?.let { ArtCache.get(it.id, px) }) }
    LaunchedEffect(song?.id, ver, px) {
        if (song != null && state.value == null) state.value = withContext(Dispatchers.IO) { ArtCache.load(ctx, song, px) }
    }
    val bmp = state.value
    val shape = RoundedCornerShape(corner)
    Box(
        modifier.size(size).clip(shape).background(
            run {
                val hue = (((song?.id ?: 0L) * 47L) % 360L).toFloat()
                Brush.linearGradient(listOf(Color.hsv(hue, 0.35f, 0.55f), Color.hsv((hue + 40f) % 360f, 0.45f, 0.25f)))
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(size / 2.2f))
        }
    }
}
