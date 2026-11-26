package com.jagat.photoswipe

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val folder: String,      // e.g. "Camera", "Screenshots", "WhatsApp Images"
    val dateAdded: Long,     // seconds since epoch
    val size: Long,          // bytes
    val durationMs: Long,    // 0 for photos
)

/** Reads every photo and video in the phone's gallery, newest first. */
class MediaRepository(private val context: Context) {

    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val folderCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                result += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    isVideo = isVideo,
                    folder = c.getString(folderCol) ?: "Other",
                    dateAdded = c.getLong(dateCol),
                    size = c.getLong(sizeCol),
                    durationMs = if (c.isNull(durCol)) 0L else c.getLong(durCol),
                )
            }
        }
        result
    }
}

/** Remembers your swipe decisions so kept photos don't come back next time. */
class DecisionStore(context: Context) {
    private val prefs = context.getSharedPreferences("decisions", Context.MODE_PRIVATE)

    fun kept(): Set<Long> = read("kept")
    fun pending(): Set<Long> = read("pending")

    fun save(kept: Set<Long>, pending: Set<Long>) {
        prefs.edit()
            .putStringSet("kept", kept.map { it.toString() }.toSet())
            .putStringSet("pending", pending.map { it.toString() }.toSet())
            .apply()
    }

    private fun read(key: String): Set<Long> =
        prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
}
