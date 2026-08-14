package dev.xenoncolt.jellyflix.player

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@Immutable
data class PlayerUiState(
    val loading: Boolean = true,
    val title: String = "",
    val playlist: List<PlayableItem> = emptyList(),
    val currentIndex: Int = 0,
    val subtitles: List<SubtitleFile> = emptyList(),
    val message: String? = null,
) {
    val current: PlayableItem? get() = playlist.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex < playlist.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playback: PlaybackRepository,
) : ViewModel() {

    private companion object {
        /**
         * How often the position is written to disk. Every second would be a
         * write per second for a whole film; ten loses only a few seconds of
         * progress to a crash.
         */
        const val SAVE_INTERVAL_MS = 10_000L

        /** ExoPlayer has no per-tick position callback, so the UI polls. */
        const val TICK_MS = 500L
    }

    val engine: PlaybackEngine by lazy { PlaybackEngine(context) }

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    val player: StateFlow<PlayerState> get() = engine.state

    private var infoHash: String = ""
    private var saveJob: Job? = null
    private var tickJob: Job? = null
    private var preview: SeekPreview? = null

    fun load(infoHash: String) {
        if (this.infoHash == infoHash && !_ui.value.loading) return
        this.infoHash = infoHash

        viewModelScope.launch {
            val items = playback.playableItems(infoHash)
            val subtitles = playback.sidecarSubtitles(infoHash)

            if (items.isEmpty()) {
                _ui.update {
                    it.copy(
                        loading = false,
                        message = "No playable video was found in this download.",
                    )
                }
                return@launch
            }

            // A season pack resumes at whichever episode was watched last rather
            // than starting the user back at episode one.
            val resumeIndex = items.indexOfLast { it.positionMs > 0 }.takeIf { it >= 0 } ?: 0

            _ui.update {
                it.copy(
                    loading = false,
                    playlist = items,
                    subtitles = subtitles,
                    currentIndex = resumeIndex,
                    title = items[resumeIndex].title,
                )
            }

            startItem(resumeIndex)
            startTicking()
        }
    }

    private fun startItem(index: Int) {
        val item = _ui.value.playlist.getOrNull(index) ?: return

        _ui.update { it.copy(currentIndex = index, title = item.title) }

        engine.play(
            file = item.file,
            startAtMs = item.positionMs,
            subtitles = _ui.value.subtitles,
        )

        // Both of these read the file directly and have nothing to do with
        // playback, so neither blocks it starting.
        preview?.close()
        preview = SeekPreview(item.file)

        viewModelScope.launch {
            val chapters = withContext(Dispatchers.IO) { MatroskaChapters.read(item.file) }
            engine.setChapters(chapters)
        }

        startSavingPosition()
    }

    /** A frame for the scrub bar. Null while none is available. */
    suspend fun previewFrame(positionMs: Long): ImageBitmap? = preview?.frameAt(positionMs)

    fun playIndex(index: Int) {
        if (index !in _ui.value.playlist.indices) return
        savePositionNow()
        startItem(index)
    }

    fun next() {
        if (_ui.value.hasNext) playIndex(_ui.value.currentIndex + 1)
    }

    fun previous() {
        if (_ui.value.hasPrevious) playIndex(_ui.value.currentIndex - 1)
    }

    /** Called when a file ends: roll on through a season pack automatically. */
    fun onEnded() {
        val state = _ui.value
        state.current?.let { finished ->
            // Record it as watched to the end so it doesn't offer to resume.
            val duration = engine.currentDurationMs
            viewModelScope.launch {
                playback.savePosition(infoHash, finished.file, duration, duration)
            }
        }
        if (state.hasNext) next()
    }

    fun addExternalSubtitle(uri: String, label: String) {
        val subtitle = SubtitleFile(uri, label)
        _ui.update { it.copy(subtitles = it.subtitles + subtitle) }

        // Media3 takes subtitles as part of the media item, so the file has to
        // be reloaded — at the current position, so it is not visible as more
        // than a blink.
        val item = _ui.value.current ?: return
        engine.play(
            file = item.file,
            startAtMs = engine.currentPositionMs,
            subtitles = _ui.value.subtitles,
        )
    }

    // --- Position -----------------------------------------------------------

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                engine.syncPosition()
                delay(TICK_MS)
            }
        }
    }

    private fun startSavingPosition() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                savePositionNow()
            }
        }
    }

    fun savePositionNow() {
        val item = _ui.value.current ?: return
        val position = engine.currentPositionMs
        val duration = engine.currentDurationMs
        if (position <= 0) return

        viewModelScope.launch {
            playback.savePosition(infoHash, item.file, position, duration)
        }
    }

    /**
     * Hands the current file to another player app.
     *
     * Kept because no bundled player suits every file — Media3 leaves video
     * decoding to the device, so an unusual codec is better handed on than
     * failed. A content:// URI is required; a file:// one throws
     * FileUriExposedException on anything modern.
     */
    fun openInOtherPlayer() {
        val file = _ui.value.current?.file ?: return
        savePositionNow()

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()

        if (uri == null) {
            _ui.update { it.copy(message = "Couldn't share that file with another app.") }
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(
                Intent.createChooser(intent, "Play with").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            _ui.update { it.copy(message = "No other app on this device can play that file.") }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "ts", "m2ts" -> "video/mp2t"
        else -> "video/*"
    }

    override fun onCleared() {
        val item = _ui.value.current
        val position = engine.currentPositionMs
        val duration = engine.currentDurationMs

        preview?.close()
        engine.release()

        if (item != null && position > 0) {
            // The ViewModel scope is already cancelled here, so this one has to
            // run on a scope that outlives the screen.
            playback.savePositionDetached(infoHash, item.file, position, duration)
        }
        super.onCleared()
    }
}
