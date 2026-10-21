package com.aura.player

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.runtime.produceState
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

    fun get(key: Long): Bitmap? = cache.get(key)

    fun load(ctx: Context, song: Song, px: Int): Bitmap? {
        cache.get(song.id)?.let { return it }
        if (synchronized(misses) { song.id in misses }) return null
        return try {
            ctx.contentResolver.loadThumbnail(song.uri, Size(px, px), null).also { cache.put(song.id, it) }
        } catch (_: Exception) {
            synchronized(misses) { misses += song.id }
            null
        }
    }
}

@Composable
fun Art(song: Song?, size: Dp, corner: Dp, modifier: Modifier = Modifier, px: Int = 256) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(song?.let { ArtCache.get(it.id) }, song?.id) {
        if (song != null && value == null) value = withContext(Dispatchers.IO) { ArtCache.load(ctx, song, px) }
    }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier.size(size).clip(shape).background(
            Brush.linearGradient(listOf(Color(0xFF5B5B63), Color(0xFF2B2B30)))
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
