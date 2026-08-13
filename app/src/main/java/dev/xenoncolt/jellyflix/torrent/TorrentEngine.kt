package dev.xenoncolt.jellyflix.torrent

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xenoncolt.jellyflix.data.local.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.Vectors
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.torrent_flags_t
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.alerts.TorrentRemovedAlert
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin, opinionated wrapper over libtorrent4j's [SessionManager].
 *
 * Threading: libtorrent delivers alerts on its own thread. Nothing in the alert
 * callback may block, so callbacks only mutate in-memory maps and hand durable
 * work to [scope].
 *
 * Progress is *polled* on a 1s ticker rather than driven by alerts. Alerts fire
 * far more often than the screen refreshes, and turning every block-finished
 * alert into a state emission would recompose the downloads list hundreds of
 * times a second for no visible gain.
 */
@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "TorrentEngine"
        const val POLL_INTERVAL_MS = 1000L
    }

    private val session = SessionManager(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startStopLock = Mutex()

    /**
     * hex info hash -> the hash object we look torrents up by.
     *
     * Deliberately NOT a map of TorrentHandle. Holding those across ticks kept
     * crashing the process inside torrent_handle::is_valid: the native object
     * behind a handle can go away underneath us, and by the time you ask
     * whether it's valid you've already dereferenced freed memory, so there's
     * no way to check safely. Sha1Hash is a plain value, and find() on the
     * session is cheap, so we re-resolve every time and never hold a handle
     * longer than the call that uses it.
     */
    private val tracked = ConcurrentHashMap<String, Sha1Hash>()

    /**
     * Torrents we have handed to the session but do not yet hold a handle for.
     *
     * We cannot rely on ADD_TORRENT to discover handles. SessionManager keeps a
     * single listener *per alert type* (`AlertListener[]` indexed by type) and
     * registers its own for add_torrent, so an application listener for that
     * type is simply never called — the alert exists, it just never reaches us.
     * Depending on it left `handles` permanently empty, which is why downloads
     * ran in the swarm while the UI showed nothing at all.
     *
     * So the poller resolves handles itself via [SessionManager.find].
     */
    private val pending = ConcurrentHashMap<String, Sha1Hash>()

    private val _progress = MutableStateFlow<Map<String, TorrentProgress>>(emptyMap())
    val progress: StateFlow<Map<String, TorrentProgress>> = _progress.asStateFlow()

    /** Emits an info hash whenever a torrent finishes, so callers can notify. */
    private val _finished = MutableStateFlow<String?>(null)
    val finished: StateFlow<String?> = _finished.asStateFlow()

    private val resumeDir: File by lazy {
        File(context.filesDir, "resume").apply { mkdirs() }
    }
    private val metadataDir: File by lazy {
        File(context.filesDir, "torrents").apply { mkdirs() }
    }

    @Volatile private var started = false

    /**
     * The progress ticker. Held so [stop] can cancel *and join* it before the
     * session is destroyed — the loop dereferences native torrent handles, and
     * those die with the session.
     */
    private var pollJob: Job? = null

    private val listener = object : AlertListener {
        /**
         * null = subscribe to every alert and filter in [alert] below.
         *
         * Returning an explicit type array here is the documented way to filter,
         * but getting no ADD_TORRENT through it leaves the engine with an empty
         * handle map — every download then sits at 0% forever with no way to
         * tell why. Dispatch is a `when` on the alert class anyway, so the only
         * cost of taking them all is a few extra branches on the alert thread.
         */
        override fun types(): IntArray? = null

        override fun alert(alert: Alert<*>) {
            when (alert) {
                is AddTorrentAlert -> onAdded(alert)
                is MetadataReceivedAlert -> onMetadata(alert)
                is TorrentFinishedAlert -> onFinished(alert)
                is TorrentErrorAlert -> onError(alert)
                is SaveResumeDataAlert -> onResumeData(alert)
                is TorrentRemovedAlert -> Unit
                else -> Unit
            }
        }
    }

    // --- Lifecycle ----------------------------------------------------------

    suspend fun start(
        maxDownloadKbps: Int = 0,
        maxUploadKbps: Int = 0,
    ) = startStopLock.withLock {
        if (started) return@withLock
        withContext(Dispatchers.IO) {
            purgeSuspectCaches()
            session.addListener(listener)

            // Start with the library's own defaults, then layer our overrides on
            // top with applySettings.
            //
            // Do NOT start with SessionParams(SettingsPack): that *replaces* the
            // whole settings block rather than merging, which drops libtorrent's
            // default alert_mask. With no alert mask there is no ADD_TORRENT
            // alert, so no handle is ever registered and every download sits at
            // 0% forever.
            session.start()

            session.applySettings(
                SettingsPack()
                    .downloadRateLimit(maxDownloadKbps * 1024)
                    .uploadRateLimit(maxUploadKbps * 1024)
                    // Season packs are large; a handful of parallel torrents with
                    // plenty of connections beats many torrents each starved.
                    .activeDownloads(4)
                    .activeSeeds(2)
                    .activeLimit(6)
                    .connectionsLimit(200)
            )
            started = true
        }
        startPolling()
    }

    suspend fun stop() = startStopLock.withLock {
        if (!started) return@withLock

        // Order matters and this is the whole bug that used to kill the process:
        // flip the flag, then cancel AND JOIN the ticker, and only then tear the
        // session down. Cancelling without joining leaves the loop mid-iteration,
        // where the next handle.isValid() reads a torrent_handle the session has
        // already freed — an uncatchable SIGSEGV.
        started = false
        pollJob?.cancelAndJoin()
        pollJob = null

        withContext(Dispatchers.IO) {
            // Flush resume data before tearing the session down so the next
            // launch does not re-hash gigabytes of already-downloaded pieces.
            tracked.keys.forEach { hash ->
                val handle = handleFor(hash) ?: return@forEach
                if (runCatching { handle.needSaveResumeData() }.getOrDefault(false)) {
                    runCatching { handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT) }
                }
            }
            session.removeListener(listener)
            // Drop our references before the session frees the natives behind
            // them, so nothing can resurrect a dangling handle afterwards.
            tracked.clear()
            runCatching { session.stop() }
        }
    }

    val isRunning: Boolean get() = started

    fun applyRateLimits(maxDownloadKbps: Int, maxUploadKbps: Int) {
        if (!started) return
        runCatching {
            session.downloadRateLimit(maxDownloadKbps * 1024)
            session.uploadRateLimit(maxUploadKbps * 1024)
        }
    }

    /** Pauses the whole session — used when the device drops to a metered network. */
    fun pauseSession() {
        if (started) runCatching { session.pause() }
    }

    fun resumeSession() {
        if (started) runCatching { session.resume() }
    }

    // --- Adding -------------------------------------------------------------

    /**
     * Adds a magnet and returns its info hash, which we know from the URI
     * itself — no need to wait for metadata before we can name the row.
     */
    suspend fun addMagnet(
        magnetUri: String,
        saveDir: File,
        sequential: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            saveDir.mkdirs()
            val infoHash = infoHashFromMagnet(magnetUri)
                ?: error("Magnet link carries no info hash")

            val params = AddTorrentParams.parseMagnetUri(magnetUri)

            // If we already have the metadata on disk, hand it over so the
            // session skips re-fetching it. Corrupt files get dropped by
            // loadMetadata rather than being fed to native code.
            loadMetadata(infoHash)?.let { info ->
                runCatching { params.swig().set_ti(info.swig()) }
            }

            addTorrent(params, saveDir, sequential, infoHash)
            infoHash
        }
    }

    /**
     * The one place a torrent actually enters the session.
     *
     * add_torrent hands back the handle synchronously, so we can record it
     * immediately instead of waiting for an alert that never arrives (see
     * [resolvePendingHandles] for that story). If it somehow comes back
     * invalid we fall back to resolving it on the next poll.
     */
    private fun addTorrent(
        params: AddTorrentParams,
        saveDir: File,
        sequential: Boolean,
        infoHash: String,
    ) {
        params.setSavePath(saveDir.absolutePath)
        params.setFlags(torrentFlags(sequential))

        // Resume data, when we have it, saves re-hashing everything on disk.
        val resumed = File(resumeDir, "$infoHash.resume")
            .takeIf { it.exists() && it.length() > 0 }
            ?.let { file -> readResumeData(file) }
            ?.also {
                it.setSavePath(saveDir.absolutePath)
                it.setFlags(torrentFlags(sequential))
            }

        addResolved(resumed ?: params, infoHash)
    }

    /**
     * There's no wrapper for this on AddTorrentParams, only the swig call.
     * Anything unreadable is deleted — it's just a cache, and a bad resume file
     * would otherwise fail the same way on every retry.
     */
    private fun readResumeData(file: File): AddTorrentParams? {
        val errorCode = error_code()
        val params = runCatching {
            libtorrent.read_resume_data_ex(
                Vectors.bytes2byte_vector(file.readBytes()),
                errorCode,
            )
        }.getOrNull()

        if (params == null || errorCode.value() != 0) {
            Log.w(TAG, "Dropping unreadable resume data ${file.name}")
            file.delete()
            return null
        }
        return AddTorrentParams(params)
    }

    /**
     * The native session, or a readable failure if it isn't up yet.
     *
     * SessionManager keeps its native session in a field that is null until
     * start() assigns it, and swig() just hands that field back. Adding through
     * a null session fails two different ways, both bad: add_torrent throws
     * "Attempt to invoke virtual method ... session_handle.add_torrent ... on a
     * null object reference" straight into the UI, while download() begins with
     * an `if (session == null) return` and no-ops silently — a download row that
     * exists and never moves. Neither says what is actually wrong, so every add
     * goes through here first.
     */
    private fun requireSession() = session.swig()
        ?: error("Torrent session is not running yet")

    private fun addResolved(params: AddTorrentParams, infoHash: String) {
        val errorCode = error_code()
        val swigHandle = requireSession().add_torrent(params.swig(), errorCode)

        if (errorCode.value() != 0) {
            error(errorCode.message())
        }

        // Record the hash, not the handle — see the note on [tracked].
        runCatching {
            tracked[infoHash] = Sha1Hash(params.swig().info_hashes.get_best())
        }

        val handle = TorrentHandle(swigHandle)
        if (runCatching { handle.isValid }.getOrDefault(false)) {
            // Empty flags mean it isn't auto-managed, so give it a nudge.
            runCatching { handle.resume() }
        }
    }

    /** Adds a `.torrent` payload fetched from `/api/torrent-file`. */
    suspend fun addTorrentFile(
        bytes: ByteArray,
        saveDir: File,
        sequential: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            requireSession()
            saveDir.mkdirs()
            val info = TorrentInfo(bytes)
            val infoHash = info.infoHash().toHex().lowercase()
            pending[infoHash] = info.infoHash()

            // Keep the metadata so a later restart can resume without the magnet.
            File(metadataDir, "$infoHash.torrent").writeBytes(bytes)

            val resumeFile = File(resumeDir, "$infoHash.resume").takeIf { it.exists() }
            session.download(info, saveDir, resumeFile, null, null, torrentFlags(sequential))
            infoHash
        }
    }

    /**
     * Flags for an add.
     *
     * Never pass `null` here. libtorrent4j hands the value straight to JNI,
     * which dereferences it — a null produces
     * "Attempt to dereference null libtorrent::flags::bitfield_flag" at add
     * time. The library's own `download(TorrentInfo, File)` overload passes a
     * default-constructed `torrent_flags_t` for the no-flags case, so that is
     * what "no flags" has to mean here too.
     */
    private fun torrentFlags(sequential: Boolean): torrent_flags_t =
        if (sequential) TorrentFlags.SEQUENTIAL_DOWNLOAD else torrent_flags_t()

    /** Fresh handle for a tracked torrent, or null if the session has lost it. */
    private fun handleFor(infoHash: String): TorrentHandle? {
        if (!started) return null
        val sha1 = tracked[infoHash] ?: return null
        val handle = runCatching { session.find(sha1) }.getOrNull() ?: return null
        return handle.takeIf { runCatching { it.isValid }.getOrDefault(false) }
    }

    // --- Per-torrent control ------------------------------------------------

    fun pause(infoHash: String) {
        handleFor(infoHash)?.let {
            it.pause()
            // SAVE_INFO_DICT so the resume file carries the metadata too;
            // without it a resume after a restart has to refetch everything.
            runCatching { it.saveResumeData(TorrentHandle.SAVE_INFO_DICT) }
        }
    }

    fun resume(infoHash: String) {
        handleFor(infoHash)?.let { runCatching { it.resume() } }
    }

    fun setSequential(infoHash: String, sequential: Boolean) {
        handleFor(infoHash)?.let { handle ->
            if (sequential) {
                handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            } else {
                handle.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            }
        }
    }

    /**
     * Removes a torrent from the session. File deletion is done by the caller
     * against the known save path rather than through libtorrent's delete flag,
     * which is asynchronous and reports failure only via an alert.
     */
    fun remove(infoHash: String) {
        pending.remove(infoHash)
        // Resolve before untracking, and only while the session is up: removing
        // through a stopped session walks into freed native state.
        val handle = handleFor(infoHash)
        tracked.remove(infoHash)
        if (started && handle != null) {
            runCatching { session.remove(handle) }
        }
        File(resumeDir, "$infoHash.resume").delete()
        File(metadataDir, "$infoHash.torrent").delete()
        _progress.value = _progress.value - infoHash
    }

    fun isTracked(infoHash: String): Boolean = tracked.containsKey(infoHash)

    /** True when we hold enough on disk to re-add without the original magnet. */
    fun hasSavedMetadata(infoHash: String): Boolean =
        File(metadataDir, "$infoHash.torrent").exists()

    /**
     * Reads persisted metadata, discarding it if libtorrent cannot make sense
     * of it.
     *
     * Parsing a torrent happens in native code, so genuinely malformed bytes
     * can take the whole process down rather than throwing. The obvious size
     * floor plus a guarded parse catches the realistic cases; anything that
     * fails is deleted so it cannot be retried forever.
     */
    private fun loadMetadata(infoHash: String): TorrentInfo? {
        val file = File(metadataDir, "$infoHash.torrent")
        if (!file.exists()) return null

        // A real .torrent is never this small; anything shorter is a truncated
        // or failed write.
        if (file.length() < 64) {
            file.delete()
            return null
        }

        return runCatching { TorrentInfo(file) }
            .onFailure {
                Log.w(TAG, "Discarding unreadable metadata for $infoHash", it)
                file.delete()
                File(resumeDir, "$infoHash.resume").delete()
            }
            .getOrNull()
    }

    /**
     * Drops resume/metadata written by builds up to 1.0.0.
     *
     * Those builds serialised resume data from an alert that libtorrent had
     * already freed, so the files can contain arbitrary heap bytes. Feeding
     * them back in is a native crash on resume, which is precisely what users
     * hit. They are pure cache — losing them costs one re-check, nothing more.
     */
    private fun purgeSuspectCaches() {
        val marker = File(context.filesDir, ".torrent-cache-v2")
        if (marker.exists()) return

        runCatching {
            resumeDir.listFiles()?.forEach { it.delete() }
            metadataDir.listFiles()?.forEach { it.delete() }
            marker.writeText("purged")
            Log.i(TAG, "Cleared torrent resume/metadata cache written by an older build")
        }
    }

    /**
     * Re-adds from the persisted `.torrent` + resume data. Used on restart for
     * torrents that came from `/api/torrent-file` and therefore never had a
     * magnet to remember.
     */
    suspend fun addSaved(
        infoHash: String,
        saveDir: File,
        sequential: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            requireSession()
            val info = loadMetadata(infoHash) ?: error("No usable metadata for $infoHash")
            saveDir.mkdirs()
            session.download(
                info,
                saveDir,
                File(resumeDir, "$infoHash.resume").takeIf { it.exists() },
                null,
                null,
                torrentFlags(sequential),
            )
            pending[infoHash] = info.infoHash()
            infoHash
        }
    }

    /** Absolute paths of the files in a finished torrent, for the "open with" hand-off. */
    fun filePaths(infoHash: String): List<File> {
        val handle = handleFor(infoHash) ?: return emptyList()
        val info = runCatching { handle.torrentFile() }.getOrNull() ?: return emptyList()
        val root = File(handle.savePath())
        val storage = info.files()
        return (0 until info.numFiles()).map { index ->
            File(root, storage.filePath(index))
        }
    }

    // --- Alert handling -----------------------------------------------------

    private fun onAdded(alert: AddTorrentAlert) {
        // In practice this never runs — SessionManager owns the add_torrent
        // listener slot. Kept because it costs nothing and would pick things up
        // if that ever changes. The add path records the hash itself.
        val handle = alert.handle()
        if (!runCatching { handle.isValid }.getOrDefault(false)) return
        runCatching { tracked[handle.infoHash().toHex().lowercase()] = handle.infoHash() }
    }

    private fun onMetadata(alert: MetadataReceivedAlert) {
        val handle = alert.handle()
        if (!runCatching { handle.isValid }.getOrDefault(false)) return
        runCatching { tracked[handle.infoHash().toHex().lowercase()] = handle.infoHash() }
        // Now that metadata exists, snapshot resume data with the info dict in
        // it so a later restart doesn't have to refetch it.
        runCatching { handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT) }
    }

    private fun onFinished(alert: TorrentFinishedAlert) {
        val handle = alert.handle()
        if (!handle.isValid) return
        val hash = handle.infoHash().toHex().lowercase()
        runCatching { handle.saveResumeData() }
        _finished.value = hash
    }

    private fun onError(alert: TorrentErrorAlert) {
        val handle = alert.handle()
        if (!handle.isValid) return
        val hash = handle.infoHash().toHex().lowercase()
        Log.w(TAG, "Torrent error for $hash: ${alert.error().message}")
        _progress.value = _progress.value.toMutableMap().apply {
            val previous = this[hash]
            this[hash] = (previous ?: TorrentProgress.idle(hash, DownloadState.Failed))
                .copy(state = DownloadState.Failed)
        }
    }

    private fun onResumeData(alert: SaveResumeDataAlert) {
        val handle = alert.handle()
        if (!handle.isValid) return
        val hash = handle.infoHash().toHex().lowercase()
        // Serialise on the alert thread, deliberately.
        //
        // The native add_torrent_params behind `alert.params()` lives only for
        // the duration of this callback — libtorrent frees the alert as soon as
        // it returns. Touching it from a coroutine afterwards is a use-after-
        // free that lands as SIGSEGV inside write_resume_data_buf_ex. Both
        // calls below are memory-only and fast; only the disk write is handed
        // off the alert thread.
        val params = alert.params()

        val resumeBytes = runCatching { AddTorrentParams.writeResumeDataBuf(params) }
            .onFailure { Log.w(TAG, "Could not serialise resume data for $hash", it) }
            .getOrNull()

        // I used to also derive a .torrent here via write_torrent_file_buf_ex so
        // restores could skip re-fetching metadata. Dropped it: on params with
        // no info dict — which is every save triggered by pause — it takes the
        // process down with a native crash instead of returning an error. We
        // always store the magnet anyway, so restoring costs a metadata fetch
        // and nothing else.
        if (resumeBytes == null) return

        scope.launch {
            runCatching { File(resumeDir, "$hash.resume").writeBytes(resumeBytes) }
                .onFailure { e -> Log.w(TAG, "Failed to write resume data for $hash", e) }
        }
    }

    // --- Polling ------------------------------------------------------------

    /**
     * Picks up anything the add couldn't resolve immediately.
     *
     * Normally [addResolved] records the hash straight away and this does
     * nothing, but a magnet added before the session has fully settled can take
     * a tick or two to appear.
     */
    private fun resolvePendingHandles() {
        if (pending.isEmpty()) return

        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val (hash, sha1) = iterator.next()
            if (tracked.containsKey(hash)) {
                iterator.remove()
                continue
            }
            val handle = runCatching { session.find(sha1) }.getOrNull()
            if (handle != null && runCatching { handle.isValid }.getOrDefault(false)) {
                tracked[hash] = sha1
                runCatching { handle.resume() }
                iterator.remove()
            }
        }
    }

    private fun startPolling() {
        pollJob = scope.launch {
            while (isActive && started) {
                resolvePendingHandles()

                val snapshot = HashMap<String, TorrentProgress>(tracked.size)
                var anyActive = false

                for (hash in tracked.keys) {
                    val handle = handleFor(hash) ?: continue
                    val status = runCatching { handle.status() }.getOrNull() ?: continue
                    val mapped = status.toProgress(hash)
                    snapshot[hash] = mapped
                    if (mapped.state == DownloadState.Downloading ||
                        mapped.state == DownloadState.FetchingMetadata
                    ) {
                        anyActive = true
                    }
                }

                if (snapshot != _progress.value) {
                    _progress.value = snapshot
                }

                // Idle sessions do not need a 1 Hz wake-up.
                delay(if (anyActive) POLL_INTERVAL_MS else POLL_INTERVAL_MS * 4)
            }
        }
    }

    private fun TorrentStatus.toProgress(hash: String) = TorrentProgress(
        infoHash = hash,
        state = mapState(this),
        progress = progress().coerceIn(0f, 1f),
        downloadedBytes = totalWantedDone(),
        totalBytes = totalWanted(),
        downloadRate = downloadPayloadRate().toLong(),
        uploadRate = uploadPayloadRate().toLong(),
        peers = numPeers(),
        seeds = numSeeds(),
    )

    private fun mapState(status: TorrentStatus): DownloadState = when {
        status.isFinished && status.isSeeding -> DownloadState.Seeding
        status.isFinished -> DownloadState.Completed
        else -> when (status.state()) {
            TorrentStatus.State.CHECKING_FILES,
            TorrentStatus.State.CHECKING_RESUME_DATA -> DownloadState.CheckingFiles
            TorrentStatus.State.DOWNLOADING_METADATA -> DownloadState.FetchingMetadata
            TorrentStatus.State.DOWNLOADING -> DownloadState.Downloading
            TorrentStatus.State.FINISHED -> DownloadState.Completed
            TorrentStatus.State.SEEDING -> DownloadState.Seeding
            else -> DownloadState.Queued
        }
    }

    /**
     * Pulls the v1 info hash out of a magnet's `xt=urn:btih:` parameter.
     * Handles both the 40-char hex and 32-char base32 forms that trackers emit.
     */
    private fun infoHashFromMagnet(magnet: String): String? {
        val raw = magnet.substringAfter("xt=urn:btih:", "")
            .substringBefore('&')
            .takeIf { it.isNotBlank() }
            ?: return null

        return when (raw.length) {
            40 -> raw.lowercase()
            32 -> base32ToHex(raw.uppercase())
            else -> null
        }
    }

    private fun base32ToHex(value: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0L
        var bits = 0
        val out = StringBuilder(40)
        for (char in value) {
            val index = alphabet.indexOf(char)
            if (index < 0) return null
            buffer = (buffer shl 5) or index.toLong()
            bits += 5
            while (bits >= 8) {
                bits -= 8
                val byte = ((buffer shr bits) and 0xFF).toInt()
                out.append("%02x".format(byte))
            }
        }
        return out.toString().take(40).takeIf { it.length == 40 }
    }
}
