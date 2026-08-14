package dev.xenoncolt.jellyflix.player

import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Frames for the scrub bar, pulled from the file as the user drags.
 *
 * Jellyfin calls this trickplay and generates sprite sheets on the server.
 * There is no server doing that here, so frames are extracted on the device
 * instead — fine for a local file, but it has to be handled carefully: decoding
 * a 4K HEVC keyframe is not free, so requests are snapped to a coarse grid,
 * cached, and always taken off the main thread.
 */
class SeekPreview(private val file: File) {

    companion object {
        /** Preview frames are 16:9; callers size the surface with this. */
        const val ASPECT = 16f / 9f

        /**
         * Frames are only fetched at multiples of this. Without snapping, a drag
         * would request a different timestamp every few pixels and spend the
         * whole gesture decoding frames it is about to discard.
         */
        private const val STEP_MS = 10_000L

        /** ~40 frames. Small enough to keep in memory at this size. */
        private const val CACHE_ENTRIES = 40

        private const val THUMB_WIDTH = 320
        private const val THUMB_HEIGHT = 180
    }

    private val cache = LruCache<Long, ImageBitmap>(CACHE_ENTRIES)

    /**
     * Shared across calls: opening a retriever per frame is most of the cost,
     * and it holds an extractor and codec open until released.
     */
    private var retriever: MediaMetadataRetriever? = null

    @Volatile private var closed = false

    /** @return a frame near [positionMs], or null if one can't be produced. */
    suspend fun frameAt(positionMs: Long): ImageBitmap? {
        if (closed) return null

        val key = (positionMs / STEP_MS) * STEP_MS
        cache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            if (closed) return@withContext null

            val bitmap = runCatching {
                val source = retriever ?: MediaMetadataRetriever().also {
                    it.setDataSource(file.absolutePath)
                    retriever = it
                }

                // OPTION_CLOSEST_SYNC takes the nearest keyframe rather than
                // decoding forward to an exact frame — far cheaper, and a
                // preview does not need to be frame-accurate.
                source.getScaledFrameAtTime(
                    key * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMB_WIDTH,
                    THUMB_HEIGHT,
                )
            }.getOrNull() ?: return@withContext null

            bitmap.asImageBitmap().also { cache.put(key, it) }
        }
    }

    fun close() {
        closed = true
        runCatching { retriever?.release() }
        retriever = null
        cache.evictAll()
    }
}
