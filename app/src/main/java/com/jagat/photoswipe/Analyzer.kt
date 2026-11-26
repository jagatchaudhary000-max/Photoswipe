package com.jagat.photoswipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size
import java.io.File
import java.security.MessageDigest

/**
 * Finds duplicate and similar photos.
 *
 * - Duplicates: files with exactly the same bytes (same size, then same MD5).
 * - Similar: photos whose small "fingerprint" (difference hash) is nearly the same,
 *   e.g. burst shots, the same scene taken twice, or a resized copy.
 */
object Analyzer {

    /** 64-bit difference hash of a photo, computed from a tiny thumbnail. */
    fun thumbnailHash(context: Context, item: MediaItem): Long? = try {
        val thumb = context.contentResolver.loadThumbnail(item.uri, Size(96, 96), null)
        val soft = if (thumb.config == Bitmap.Config.HARDWARE) {
            thumb.copy(Bitmap.Config.ARGB_8888, false)
        } else thumb
        dHash(soft)
    } catch (e: Exception) {
        null
    }

    private fun dHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (luma(small.getPixel(x, y)) > luma(small.getPixel(x + 1, y))) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    private fun luma(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

    private fun md5(context: Context, item: MediaItem): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val input = context.contentResolver.openInputStream(item.uri) ?: return null
            input.use {
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = it.read(buffer)
                    if (n <= 0) break
                    digest.update(buffer, 0, n)
                }
            }
            digest.digest().joinToString("") { b -> "%02x".format(b) }
        } catch (e: Exception) {
            null
        }
    }

    /** Groups of exact copies (photos and videos). Oldest copy comes first in each group. */
    fun findDuplicates(context: Context, items: List<MediaItem>): List<List<Long>> =
        items.filter { it.size > 0 }
            .groupBy { it.size }
            .values
            .filter { it.size > 1 }
            .flatMap { sameSize ->
                sameSize.mapNotNull { item -> md5(context, item)?.let { it to item } }
                    .groupBy({ it.first }, { it.second })
                    .values
                    .filter { it.size > 1 }
                    .map { group -> group.sortedBy { it.dateAdded }.map { it.id } }
            }

    /**
     * Groups photos that look alike. Each group is built around one "seed" photo,
     * so unrelated photos don't get chained together.
     */
    fun groupSimilar(entries: List<Pair<Long, Long>>, maxDistance: Int = 8): List<List<Long>> {
        val used = BooleanArray(entries.size)
        val groups = mutableListOf<List<Long>>()
        for (i in entries.indices) {
            if (used[i]) continue
            val seed = entries[i].second
            val group = mutableListOf(entries[i].first)
            for (j in i + 1 until entries.size) {
                if (!used[j] && java.lang.Long.bitCount(seed xor entries[j].second) <= maxDistance) {
                    used[j] = true
                    group += entries[j].first
                }
            }
            if (group.size > 1) {
                used[i] = true
                groups += group
            }
        }
        return groups
    }
}

/** Saves photo fingerprints so the next scan is almost instant. */
class HashCache(context: Context) {
    private val file = File(context.filesDir, "hashes.txt")

    fun load(): MutableMap<String, Long> {
        val map = HashMap<String, Long>()
        if (!file.exists()) return map
        file.forEachLine { line ->
            val parts = line.split('=')
            if (parts.size == 2) parts[1].toLongOrNull()?.let { map[parts[0]] = it }
        }
        return map
    }

    fun save(map: Map<String, Long>) {
        file.bufferedWriter().use { w ->
            map.forEach { (k, v) -> w.write("$k=$v"); w.newLine() }
        }
    }
}
