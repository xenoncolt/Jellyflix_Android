package dev.xenoncolt.jellyflix.player

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Reads chapter markers straight out of a Matroska file.
 *
 * Written by hand because nothing else will do it: Media3's `MatroskaExtractor`
 * skips the Chapters element entirely, and `MediaMetadataRetriever` has no
 * chapter API at all. MKV is an EBML document — a tree of (id, size, payload)
 * nodes — so pulling chapters out means walking that tree far enough to find
 * them, and no further.
 *
 * Only the elements on the path to chapter titles are decoded; everything else
 * is skipped by its declared size, so this reads a few kilobytes of a file that
 * may be tens of gigabytes rather than scanning it.
 */
object MatroskaChapters {

    private const val TAG = "MatroskaChapters"

    // EBML ids, as they appear on the wire including their length-descriptor bits.
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_INFO = 0x1549A966L
    private const val ID_TIMECODE_SCALE = 0x2AD7B1L
    private const val ID_CHAPTERS = 0x1043A770L
    private const val ID_EDITION_ENTRY = 0x45B9L
    private const val ID_CHAPTER_ATOM = 0xB6L
    private const val ID_CHAPTER_TIME_START = 0x91L
    private const val ID_CHAPTER_DISPLAY = 0x80L
    private const val ID_CHAP_STRING = 0x85L
    private const val ID_CHAPTER_FLAG_HIDDEN = 0x98L

    /** Nanoseconds per timecode unit when the file doesn't say otherwise. */
    private const val DEFAULT_TIMECODE_SCALE = 1_000_000L

    /** A guard against walking a corrupt file forever. */
    private const val MAX_ELEMENTS = 200_000

    fun read(file: File): List<Chapter> = runCatching {
        if (!file.extension.equals("mkv", ignoreCase = true) &&
            !file.extension.equals("webm", ignoreCase = true)
        ) {
            // Only Matroska carries chapters in a form worth parsing here.
            return emptyList()
        }

        RandomAccessFile(file, "r").use { input ->
            val reader = EbmlReader(input)
            val segment = reader.findChild(ID_SEGMENT, input.length()) ?: return emptyList()

            var scale = DEFAULT_TIMECODE_SCALE
            var chapters = emptyList<Chapter>()

            // Info and Chapters are siblings inside Segment, in either order.
            reader.forEachChild(segment.end) { id, size, payloadStart ->
                when (id) {
                    ID_INFO -> scale = reader.readTimecodeScale(payloadStart + size) ?: scale
                    ID_CHAPTERS -> chapters = reader.readChapters(payloadStart + size)
                }
                chapters.isEmpty() || scale == DEFAULT_TIMECODE_SCALE
            }

            chapters
                .map { it.copy(startMs = it.startMs * scale / 1_000_000) }
                .sortedBy { it.startMs }
        }
    }.onFailure { Log.w(TAG, "Couldn't read chapters from ${file.name}", it) }
        .getOrDefault(emptyList())

    private class Element(val id: Long, val size: Long, val payloadStart: Long) {
        val end: Long get() = payloadStart + size
    }

    private class EbmlReader(private val input: RandomAccessFile) {

        private var elementsSeen = 0

        /** Scans forward at the current level for one id. */
        fun findChild(wanted: Long, until: Long): Element? {
            while (input.filePointer < until) {
                val element = readElement() ?: return null
                if (element.id == wanted) return element
                input.seek(element.end)
            }
            return null
        }

        /**
         * Walks the children of the element ending at [until].
         *
         * @param onChild returns true to keep going, false to stop early.
         */
        fun forEachChild(until: Long, onChild: (Long, Long, Long) -> Boolean) {
            while (input.filePointer < until) {
                val element = readElement() ?: return
                val keepGoing = onChild(element.id, element.size, element.payloadStart)
                input.seek(element.end)
                if (!keepGoing) return
            }
        }

        fun readTimecodeScale(until: Long): Long? {
            var scale: Long? = null
            forEachChild(until) { id, size, start ->
                if (id == ID_TIMECODE_SCALE) {
                    input.seek(start)
                    scale = readUInt(size.toInt())
                }
                scale == null
            }
            return scale
        }

        fun readChapters(until: Long): List<Chapter> {
            val chapters = mutableListOf<Chapter>()
            forEachChild(until) { id, size, start ->
                if (id == ID_EDITION_ENTRY) {
                    input.seek(start)
                    chapters += readEdition(start + size)
                }
                // One edition is enough; files with several are almost always
                // ordered/alternate cuts of the same list.
                chapters.isEmpty()
            }
            return chapters
        }

        private fun readEdition(until: Long): List<Chapter> {
            val chapters = mutableListOf<Chapter>()
            forEachChild(until) { id, size, start ->
                if (id == ID_CHAPTER_ATOM) {
                    input.seek(start)
                    readAtom(start + size)?.let { chapters += it }
                }
                true
            }
            return chapters
        }

        private fun readAtom(until: Long): Chapter? {
            var startTime: Long? = null
            var title: String? = null
            var hidden = false

            forEachChild(until) { id, size, start ->
                when (id) {
                    ID_CHAPTER_TIME_START -> {
                        input.seek(start)
                        startTime = readUInt(size.toInt())
                    }

                    ID_CHAPTER_FLAG_HIDDEN -> {
                        input.seek(start)
                        hidden = readUInt(size.toInt()) == 1L
                    }

                    ID_CHAPTER_DISPLAY -> {
                        input.seek(start)
                        title = readDisplayTitle(start + size)
                    }
                }
                true
            }

            val time = startTime ?: return null
            if (hidden) return null
            return Chapter(startMs = time, title = title.orEmpty())
        }

        private fun readDisplayTitle(until: Long): String? {
            var title: String? = null
            forEachChild(until) { id, size, start ->
                if (id == ID_CHAP_STRING) {
                    input.seek(start)
                    val bytes = ByteArray(size.toInt().coerceIn(0, 512))
                    input.readFully(bytes)
                    title = String(bytes, Charsets.UTF_8).trim()
                }
                title == null
            }
            return title
        }

        /**
         * Reads one element header.
         *
         * EBML encodes both ids and sizes as variable-length integers whose
         * leading zero bits give the length. Ids keep their marker bits (that is
         * how the constants above are written); sizes have theirs stripped.
         */
        private fun readElement(): Element? {
            if (elementsSeen++ > MAX_ELEMENTS) return null
            if (input.filePointer >= input.length()) return null

            val id = readVarInt(keepMarker = true) ?: return null
            val size = readVarInt(keepMarker = false) ?: return null
            val payloadStart = input.filePointer

            // "Unknown size" is legal for Segment; treat it as running to EOF.
            val resolved = if (size == UNKNOWN_SIZE) input.length() - payloadStart else size
            if (resolved < 0 || payloadStart + resolved > input.length()) return null

            return Element(id, resolved, payloadStart)
        }

        private fun readVarInt(keepMarker: Boolean): Long? {
            val first = input.read()
            if (first < 0) return null

            var length = 1
            var mask = 0x80
            while (length <= 8 && (first and mask) == 0) {
                length++
                mask = mask shr 1
            }
            if (length > 8) return null

            var value = if (keepMarker) first.toLong() else (first and (mask - 1)).toLong()
            var allOnes = !keepMarker && (first and (mask - 1)) == (mask - 1)

            repeat(length - 1) {
                val next = input.read()
                if (next < 0) return null
                value = (value shl 8) or next.toLong()
                if (next != 0xFF) allOnes = false
            }

            return if (allOnes) UNKNOWN_SIZE else value
        }

        private fun readUInt(length: Int): Long {
            if (length !in 1..8) return 0
            var value = 0L
            repeat(length) {
                val next = input.read()
                if (next < 0) return value
                value = (value shl 8) or next.toLong()
            }
            return value
        }

        private companion object {
            const val UNKNOWN_SIZE = -2L
        }
    }
}
