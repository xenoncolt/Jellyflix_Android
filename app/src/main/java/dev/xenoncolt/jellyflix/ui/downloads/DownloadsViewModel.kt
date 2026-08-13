package dev.xenoncolt.jellyflix.ui.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xenoncolt.jellyflix.data.local.SettingsStore
import dev.xenoncolt.jellyflix.torrent.DownloadUi
import dev.xenoncolt.jellyflix.torrent.StorageLocations
import dev.xenoncolt.jellyflix.torrent.TorrentRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TorrentRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadUi>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wifiOnly: StateFlow<Boolean> = settings.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val sequential: StateFlow<Boolean> = settings.sequentialDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val downloadLocation: StateFlow<String> = settings.downloadTreeUri
        .map { StorageLocations.describe(context, it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StorageLocations.describe(context, null),
        )

    /**
     * Takes the folder the picker returned. We hold onto read/write permission
     * across reboots, then check we can actually write there — the picker will
     * happily hand back somewhere libtorrent can't touch, and finding that out
     * when a download fails is far too late.
     */
    fun setDownloadLocation(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }

            val resolved = StorageLocations.resolve(context, uri.toString())
            if (resolved == null) {
                // Distinguish the two failures. Without All files access *every*
                // folder fails, so telling the user to pick a different one — as
                // this used to — sends them round a loop they cannot win.
                _message.value = if (!StorageLocations.hasFullStorageAccess(context)) {
                    "Downloads need \"All files access\" to write outside the app's " +
                        "own folder. Grant it in Settings, then pick the folder again."
                } else {
                    "Can't write downloads to that folder. " +
                        "Pick one on internal storage or an SD card, or leave it on the default."
                }
                return@launch
            }
            settings.setDownloadTreeUri(uri.toString())
        }
    }

    fun resetDownloadLocation() {
        viewModelScope.launch { settings.setDownloadTreeUri(null) }
    }

    fun pause(infoHash: String) = viewModelScope.launch { repository.pause(infoHash) }

    fun resume(infoHash: String) = viewModelScope.launch { repository.resume(infoHash) }

    fun remove(infoHash: String, deleteFiles: Boolean) = viewModelScope.launch {
        repository.remove(infoHash, deleteFiles)
    }

    fun setWifiOnly(value: Boolean) = viewModelScope.launch { settings.setWifiOnly(value) }

    fun setSequential(value: Boolean) = viewModelScope.launch {
        settings.setSequentialDownload(value)
    }

    fun consumeMessage() { _message.value = null }

    /**
     * Hands the largest file off to whatever video player the user has, per the
     * handoff's "open with" note. A content:// URI via FileProvider is required —
     * a file:// URI throws FileUriExposedException on anything modern.
     */
    fun openInPlayer(infoHash: String) {
        viewModelScope.launch {
            val file = pickPlayableFile(infoHash)
            if (file == null) {
                _message.value = "Couldn't find the downloaded file."
                return@launch
            }

            val uri = runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }.getOrNull()

            if (uri == null) {
                _message.value = "Couldn't share that file with a player."
                return@launch
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeTypeFor(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            runCatching {
                context.startActivity(Intent.createChooser(intent, "Open with").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                _message.value = "No app on this device can open that file."
            }
        }
    }

    private suspend fun pickPlayableFile(infoHash: String): File? {
        val videoExtensions = setOf("mkv", "mp4", "avi", "m4v", "mov", "webm", "ts")
        val files = repository.filesOnDisk(infoHash)
        return files.firstOrNull { it.extension.lowercase() in videoExtensions }
            ?: files.firstOrNull()
    }

    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        else -> "video/*"
    }
}
