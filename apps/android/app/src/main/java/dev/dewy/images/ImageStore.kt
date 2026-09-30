package dev.dewy.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Images from the orchestrator (docs/adr/0003-image-attachments.md). They
 * never change, so a file named by id is good forever: the files used
 * longest ago go past [limit] bytes, and decoded thumbnails stay in memory.
 */
class ImageStore(
    context: Context,
    private val fetch: suspend (String) -> ByteArray,
    private val limit: Long = 100L shl 20,
) {
    private val dir = File(context.cacheDir, "images")
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    // One download per image at a time; different images load side by side.
    private val lock = Mutex()
    private val inflight = mutableMapOf<String, CompletableDeferred<File?>>()

    /**
     * The image, decoded for [size] pixels: cropped thumbnails ([fit] false)
     * cover it with their shorter side, a full view ([fit] true) fits its
     * longer side in it. Only thumbnails stay in memory.
     */
    suspend fun bitmap(id: String, size: Int, fit: Boolean): ImageBitmap? {
        val key = "$id@$size"
        if (!fit) memory.get(key)?.let { return it.asImageBitmap() }
        val file = file(id) ?: return null
        val bitmap = withContext(Dispatchers.IO) { decode(file, size, fit) } ?: return null
        if (!fit) memory.put(key, bitmap)
        return bitmap.asImageBitmap()
    }

    /** Keeps an image the app already has, such as one it uploaded. */
    suspend fun put(id: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (!id.matches(IdPattern)) return@withContext
        write(id, bytes)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        memory.evictAll()
        dir.deleteRecursively()
    }

    private suspend fun file(id: String): File? {
        if (!id.matches(IdPattern)) return null
        val f = File(dir, id)
        val cached = withContext(Dispatchers.IO) { f.exists().also { if (it) f.setLastModified(System.currentTimeMillis()) } }
        if (cached) return f
        var mine = false
        val pending = lock.withLock {
            inflight.getOrPut(id) { CompletableDeferred<File?>().also { mine = true } }
        }
        if (!mine) return pending.await()
        val result = runCatching { fetch(id) }.getOrNull()?.let { bytes -> withContext(Dispatchers.IO) { write(id, bytes) } }
        lock.withLock { inflight.remove(id) }
        pending.complete(result)
        return result
    }

    private fun write(id: String, bytes: ByteArray): File {
        dir.mkdirs()
        val f = File(dir, id)
        val part = File.createTempFile(id, ".part", dir)
        part.writeBytes(bytes)
        if (!part.renameTo(f)) part.delete()
        trim()
        return f
    }

    private fun trim() {
        val files = dir.listFiles().orEmpty().filter { !it.name.endsWith(".part") }
        var total = files.sumOf { it.length() }
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= limit) break
            total -= f.length()
            f.delete()
        }
    }

    private fun decode(file: File, size: Int, fit: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        if (fit) {
            // Halving until the longer side fits; the view scales the rest.
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > size * 2) sample *= 2
        } else {
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        }
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        val IdPattern = Regex("[0-9a-f]{64}")
    }
}
