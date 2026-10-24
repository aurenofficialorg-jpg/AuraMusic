package com.aura.player

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long = 0L,
    val mime: String = "",
    val path: String = "",
) {
    /** Voice notes, call/WhatsApp audio and phone recordings that Android files under "music". */
    val isRecording: Boolean get() = RecordingRx.containsMatchIn(title) || RecordingRx.containsMatchIn(path)

    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(Uri.parse("content://media/external/audio/media/$id?albumId=$albumId"))
                .build()
        ).build()
}

private val RecordingRx = Regex(
    "^(aud|ptt|vn|rec|voice|call)[-_ ]?\\d|standard recording|new recording|voice (note|memo|record)|recording \\d|" +
        "whatsapp (audio|voice)|/recordings?/|/voice ?recorder|/call ?recordings?/|/whatsapp/media/whatsapp (voice|audio)",
    RegexOption.IGNORE_CASE
)

/** "Artist - Title.mp3" with no tags -> proper artist and title (only when the artist tag is missing). */
private fun fromFileName(display: String?, title: String, artist: String): Pair<String, String> {
    if (artist != "Unknown Artist" || display == null) return title to artist
    val base = display.substringBeforeLast('.')
    val i = base.indexOf(" - ")
    if (i <= 0) return title to artist
    val left = base.substring(0, i).trim()
    val right = base.substring(i + 3).trim()
    return when {
        right.isEmpty() -> title to artist
        left.all { it.isDigit() } -> right to artist          // "01 - Title"
        left.length > 40 -> title to artist
        else -> right to left
    }
}

object MusicRepository {
    /** Single fast MediaStore query. Run on a background dispatcher. */
    fun load(resolver: ContentResolver): List<Song> {
        val out = ArrayList<Song>(512)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, sel, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val iPath = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val artist = c.getString(iArtist)?.takeUnless { it == "<unknown>" } ?: "Unknown Artist"
                val (title, fixedArtist) = fromFileName(c.getString(iName), c.getString(iTitle) ?: "Unknown", artist)
                out += Song(
                    c.getLong(iId), title, fixedArtist,
                    c.getString(iAlbum) ?: "Unknown Album", c.getLong(iAlbumId), c.getLong(iDur), c.getLong(iAdded), c.getString(iMime) ?: "", "/" + (c.getString(iPath) ?: "")
                )
            }
        }
        return out
    }
}
