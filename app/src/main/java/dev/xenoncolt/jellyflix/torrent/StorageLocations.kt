package dev.xenoncolt.jellyflix.torrent

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Turns the folder the user picked into a path libtorrent can actually write to.
 *
 * The catch with the folder picker is that it hands back a SAF tree URI, and
 * libtorrent writes through plain POSIX calls — it has no idea what a
 * content:// URI is. So we take the tree URI, work out the real path behind it,
 * and check we can write there. If any of that fails we say so and stay on the
 * default folder rather than starting a download that will fail on first write.
 */
object StorageLocations {

    /** Where downloads go when the user hasn't chosen anything. */
    fun defaultRoot(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
            .apply { mkdirs() }

    /**
     * Resolves a persisted tree URI to a writable directory, or null if we
     * can't get a usable path out of it.
     */
    fun resolve(context: Context, treeUri: String?): File? {
        if (treeUri.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return null

        val path = pathFromTreeUri(context, uri) ?: return null
        val dir = File(path)

        if (!dir.exists() && !dir.mkdirs()) return null
        if (!dir.isDirectory || !dir.canWrite()) return null

        // canWrite() lies often enough on Android that it's worth proving it.
        return if (probeWritable(dir)) dir else null
    }

    /** Human-readable label for the settings row. */
    fun describe(context: Context, treeUri: String?): String {
        if (treeUri.isNullOrBlank()) return "App storage (default)"
        val uri = runCatching { Uri.parse(treeUri) }.getOrNull()
            ?: return "App storage (default)"
        val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        return name ?: pathFromTreeUri(context, uri) ?: "Chosen folder"
    }

    /**
     * Tree URIs look like `primary:Movies/Stuff` or `1A2B-3C4D:Movies`. The bit
     * before the colon is the volume, the rest is relative to it.
     */
    private fun pathFromTreeUri(context: Context, uri: Uri): String? {
        val docId = runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        }.getOrNull() ?: return null

        val parts = docId.split(':', limit = 2)
        val volume = parts.getOrNull(0) ?: return null
        val relative = parts.getOrNull(1).orEmpty()

        val root = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            removableVolumePath(context, volume) ?: "/storage/$volume"
        }

        return if (relative.isBlank()) root else "$root/$relative"
    }

    private fun removableVolumePath(context: Context, volumeId: String): String? {
        val manager = context.getSystemService<StorageManager>() ?: return null
        return runCatching {
            manager.storageVolumes
                .firstOrNull { it.uuid.equals(volumeId, ignoreCase = true) }
                ?.directory
                ?.absolutePath
        }.getOrNull()
    }

    private fun probeWritable(dir: File): Boolean = runCatching {
        val probe = File(dir, ".jellyflix-write-test")
        probe.writeText("x")
        probe.delete()
        true
    }.getOrDefault(false)
}
