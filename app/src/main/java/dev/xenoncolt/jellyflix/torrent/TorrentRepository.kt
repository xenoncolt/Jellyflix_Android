package dev.xenoncolt.jellyflix.torrent

import android.content.Context
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xenoncolt.jellyflix.data.local.DownloadDao
import dev.xenoncolt.jellyflix.data.local.DownloadEntity
import dev.xenoncolt.jellyflix.data.local.DownloadState
import dev.xenoncolt.jellyflix.data.local.SettingsStore
import dev.xenoncolt.jellyflix.data.remote.dto.MediaDetail
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.data.remote.dto.TorrentRelease
import dev.xenoncolt.jellyflix.data.repo.AccountRepository
import dev.xenoncolt.jellyflix.data.repo.DownloadSource
import dev.xenoncolt.jellyflix.data.remote.dto.DownloadLogRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A download row as the UI wants it: durable record joined to live progress. */
@Immutable
data class DownloadUi(
    val entity: DownloadEntity,
    val live: TorrentProgress?,
) {
    val state: DownloadState get() = live?.state ?: entity.state
    val progress: Float get() = live?.progress ?: entity.progress
    val downloadedBytes: Long get() = live?.downloadedBytes ?: entity.downloadedBytes
    val totalBytes: Long
        get() = live?.totalBytes?.takeIf { it > 0 } ?: entity.totalBytes
    val downloadRate: Long get() = live?.downloadRate ?: 0
    val uploadRate: Long get() = live?.uploadRate ?: 0
    val seeds: Int get() = live?.seeds ?: 0
    val peers: Int get() = live?.peers ?: 0
    val etaSeconds: Long? get() = live?.etaSeconds
    val key: String get() = entity.infoHash
}

/**
 * Orchestrates the torrent engine, the durable Room record, and the server-side
 * download history.
 *
 * The engine knows nothing about media; this is where a torrent gains a poster,
 * a season number and a title.
 */
@Singleton
class TorrentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TorrentEngine,
    private val dao: DownloadDao,
    private val settings: SettingsStore,
    private val account: AccountRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Where files land. The user's folder if they picked one and we can still
     * write to it, otherwise app-specific external storage — that needs no
     * runtime permission on any API level, which is why it's the fallback.
     *
     * Re-checked on every download rather than cached: an SD card can be pulled
     * out, and silently failing every write afterwards would be worse than
     * quietly going back to the default.
     */
    private suspend fun downloadRoot(): File {
        val chosen = settings.downloadTreeUri.first()
        return StorageLocations.resolve(context, chosen) ?: StorageLocations.defaultRoot(context)
    }

    val downloads: Flow<List<DownloadUi>> =
        combine(dao.observeAll(), engine.progress) { entities, progressMap ->
            entities.map { entity -> DownloadUi(entity, progressMap[entity.infoHash]) }
        }

    fun observeForMedia(mediaId: Int, mediaType: MediaType): Flow<List<DownloadEntity>> =
        dao.observeForMedia(mediaId, mediaType)

    /**
     * Adds a release to the session and records it.
     *
     * For series, [season] alone means a season pack. [episode] is set when a
     * season is still airing and has no pack yet, so the aired episodes are
     * offered one at a time instead.
     */
    suspend fun startDownload(
        media: MediaDetail,
        release: TorrentRelease,
        source: DownloadSource,
        season: Int?,
        episode: Int? = null,
    ): Result<String> {
        ensureEngineRunning()

        val sequential = settings.sequentialDownload.first()
        val saveDir = File(downloadRoot(), sanitize(media.title)).apply { mkdirs() }

        val added = when (source) {
            is DownloadSource.Magnet ->
                engine.addMagnet(source.uri, saveDir, sequential)

            is DownloadSource.TorrentFile ->
                engine.addTorrentFile(source.bytes, saveDir, sequential)
        }

        val infoHash = added.getOrElse { return Result.failure(it) }

        dao.upsert(
            DownloadEntity(
                infoHash = infoHash,
                mediaType = media.mediaType,
                mediaId = media.id,
                title = media.title,
                posterPath = media.posterPath,
                year = media.year,
                season = season,
                episode = episode,
                releaseTitle = release.title,
                indexer = release.indexer,
                quality = release.quality,
                resolution = release.resolution,
                codec = release.codec,
                totalBytes = release.size,
                savePath = saveDir.absolutePath,
                magnetUri = (source as? DownloadSource.Magnet)?.uri ?: release.magnetUrl,
                sequential = sequential,
                state = DownloadState.Queued,
            )
        )

        // History is a nicety — never let it block or fail the download.
        scope.launch {
            account.logDownload(
                DownloadLogRequest(
                    mediaType = media.mediaType.wire,
                    mediaId = media.id,
                    title = media.title,
                    posterPath = media.posterPath,
                    releaseTitle = release.title,
                    indexer = release.indexer,
                    size = release.size,
                    quality = release.quality,
                    season = season,
                    method = "torrent",
                )
            )
        }

        TorrentService.start(context)
        return Result.success(infoHash)
    }

    suspend fun pause(infoHash: String) {
        engine.pause(infoHash)
        dao.updateState(infoHash, DownloadState.Paused)
    }

    suspend fun resume(infoHash: String) {
        val entity = dao.find(infoHash) ?: return

        val restored = if (engine.isTracked(infoHash)) {
            engine.resume(infoHash)
            true
        } else {
            // Process was killed since this was added — put it back in the session.
            reAdd(entity)
        }

        // Only claim it is running if it actually got into the session. This
        // used to overwrite the Failed state that reAdd had just written, which
        // hid the real error behind a row that looked healthy but never moved.
        if (restored) {
            dao.clearError(infoHash)
            dao.updateState(infoHash, DownloadState.Downloading)
            TorrentService.start(context)
        }
    }

    suspend fun remove(infoHash: String, deleteFiles: Boolean) {
        val entity = dao.find(infoHash)
        engine.remove(infoHash)
        dao.delete(infoHash)
        if (deleteFiles && entity != null) {
            runCatching { File(entity.savePath).deleteRecursively() }
        }
    }

    /** Files of a completed torrent, for the "open with" hand-off to a player. */
    fun playableFiles(infoHash: String): List<File> {
        val fromEngine = engine.filePaths(infoHash)
        if (fromEngine.isNotEmpty()) return fromEngine.filter { it.exists() }
        // Engine restarted without this torrent — fall back to walking the disk.
        return emptyList()
    }

    suspend fun filesOnDisk(infoHash: String): List<File> {
        val entity = dao.find(infoHash) ?: return emptyList()
        val root = File(entity.savePath)
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.length() > 0 }
            .sortedByDescending { it.length() }
            .toList()
    }

    /**
     * Re-adds anything that was mid-flight, called when the service starts.
     *
     * This must not blanket-demote rows to Paused. The service is started by
     * [startDownload] immediately after it inserts the new row, so demoting
     * everything here would park every brand-new download at Paused before it
     * ever reached the engine — which is exactly what it used to do.
     *
     * A row the user explicitly paused stays paused; everything else resumes.
     * Burning mobile data is already prevented by the Wi-Fi-only guard in the
     * service, not by parking downloads.
     */
    suspend fun restorePending() {
        dao.all()
            .filter { !it.isTerminal }
            .forEach { entity ->
                when {
                    // Already live in the session — nothing to do.
                    engine.isTracked(entity.infoHash) -> Unit
                    // Respect a deliberate pause across restarts.
                    entity.state == DownloadState.Paused -> Unit
                    else -> reAdd(entity)
                }
            }
    }

    /**
     * Brings the session up before anything is handed to it.
     *
     * [TorrentService] owns the engine's lifecycle, but it is started *after*
     * the add — and starting a service is asynchronous, so on the first
     * download of a process the add reached a session that did not exist yet.
     * libtorrent4j holds its native session in a field that is null until
     * start(), and the two add paths fail differently on it: a magnet threw
     * "Attempt to invoke virtual method ... session_handle.add_torrent ... on a
     * null object reference" at the user, while a .torrent file was dropped
     * without a word and left a row stuck at 0%.
     *
     * [TorrentEngine.start] is guarded by its own flag and mutex, so calling it
     * here as well as from the service costs nothing when it is already up.
     */
    private suspend fun ensureEngineRunning() {
        if (engine.isRunning) return
        engine.start(
            maxDownloadKbps = settings.maxDownloadKbps.first(),
            maxUploadKbps = settings.maxUploadKbps.first(),
        )
    }

    /** Returns true when the torrent made it back into the session. */
    private suspend fun reAdd(entity: DownloadEntity): Boolean {
        ensureEngineRunning()
        val saveDir = File(entity.savePath).apply { mkdirs() }
        val magnet = entity.magnetUri

        // A torrent added from /api/torrent-file has no magnet, but we persisted
        // its metadata, so it can still come back without a fresh search.
        val result = when {
            !magnet.isNullOrBlank() -> engine.addMagnet(magnet, saveDir, entity.sequential)
            engine.hasSavedMetadata(entity.infoHash) ->
                engine.addSaved(entity.infoHash, saveDir, entity.sequential)
            else -> Result.failure(
                IllegalStateException("Nothing stored to resume from — start this download again.")
            )
        }

        result.onFailure {
            dao.markFailed(entity.infoHash, it.message ?: "Could not restart")
        }
        return result.isSuccess
    }

    /** Called by the service ticker to persist the occasional progress snapshot. */
    suspend fun persistSnapshot(progress: Map<String, TorrentProgress>) {
        progress.forEach { (hash, value) ->
            when (value.state) {
                DownloadState.Completed, DownloadState.Seeding -> {
                    val existing = dao.find(hash)
                    if (existing != null && !existing.isTerminal) {
                        dao.markCompleted(hash)
                        if (value.totalBytes > 0) dao.updateTotalBytes(hash, value.totalBytes)
                    }
                }

                else -> dao.updateProgress(
                    infoHash = hash,
                    progress = value.progress,
                    downloadedBytes = value.downloadedBytes,
                    state = value.state,
                )
            }
            // Metadata gives us the true size; the search result only estimated it.
            if (value.totalBytes > 0) {
                val existing = dao.find(hash)
                if (existing != null && existing.totalBytes != value.totalBytes) {
                    dao.updateTotalBytes(hash, value.totalBytes)
                }
            }
        }
    }

    suspend fun titleFor(infoHash: String): String? = dao.find(infoHash)?.displayTitle

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(80).ifBlank { "download" }
}
