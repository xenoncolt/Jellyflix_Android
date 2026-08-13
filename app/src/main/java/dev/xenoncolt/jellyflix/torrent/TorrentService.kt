package dev.xenoncolt.jellyflix.torrent

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.xenoncolt.jellyflix.data.local.DownloadDao
import dev.xenoncolt.jellyflix.data.local.DownloadState
import dev.xenoncolt.jellyflix.data.local.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Keeps the libtorrent session alive while downloads are running.
 *
 * A foreground service is the only way an Android app can hold a socket open
 * and write gigabytes while the user is elsewhere. Section 8 of the handoff
 * requires the ongoing notification, the pause/resume/cancel actions, the wake
 * lock, and the metered-network guard — all of which live here.
 */
@AndroidEntryPoint
class TorrentService : LifecycleService() {

    @Inject lateinit var engine: TorrentEngine
    @Inject lateinit var repository: TorrentRepository
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var dao: DownloadDao

    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile private var pausedForMeteredNetwork = false
    @Volatile private var manuallyPaused = false
    private var completionNotificationId = 2000

    companion object {
        const val ACTION_START = "dev.xenoncolt.jellyflix.START"
        const val ACTION_PAUSE_ALL = "dev.xenoncolt.jellyflix.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "dev.xenoncolt.jellyflix.RESUME_ALL"
        const val ACTION_STOP = "dev.xenoncolt.jellyflix.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TorrentService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TorrentService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        TorrentNotifications.ensureChannels(this)

        // Post the notification before any slow work: Android gives a service
        // ~5 seconds to call startForeground or it is killed with an ANR.
        startForegroundCompat()

        lifecycleScope.launch {
            val down = settings.maxDownloadKbps.first()
            val up = settings.maxUploadKbps.first()
            engine.start(maxDownloadKbps = down, maxUploadKbps = up)
            repository.restorePending()
        }

        observeProgress()
        observeCompletions()
        registerConnectivityCallback()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_PAUSE_ALL -> {
                manuallyPaused = true
                engine.pauseSession()
            }

            ACTION_RESUME_ALL -> {
                manuallyPaused = false
                if (!pausedForMeteredNetwork) engine.resumeSession()
            }

            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }

        // START_STICKY: if the OS reclaims us mid-download, come back and
        // restore from Room rather than losing the session silently.
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        releaseWakeLock()
        unregisterConnectivityCallback()
        lifecycleScope.launch { engine.stop() }
        super.onDestroy()
    }

    // --- Progress -> notification + Room ------------------------------------

    private fun observeProgress() {
        lifecycleScope.launch {
            engine.progress.collect { progressMap ->
                val active = progressMap.values.filter {
                    it.state != DownloadState.Completed && it.state != DownloadState.Seeding
                }

                if (progressMap.isEmpty()) {
                    // An empty progress map does NOT mean there is nothing to do.
                    // It is also the normal state for the first seconds after a
                    // magnet is added, before metadata arrives and libtorrent
                    // registers a handle — which routinely takes longer than any
                    // short grace period. Shutting down here used to kill the
                    // engine out from under a download that had just started.
                    //
                    // Room is the authority on whether work exists; the engine's
                    // progress map is only a view of what is live right now.
                    delay(10_000)
                    val hasWork = withContext(Dispatchers.IO) {
                        dao.all().any { it.isActive }
                    }
                    if (engine.progress.value.isEmpty() && !hasWork) stopEverything()
                    return@collect
                }

                val titled = progressMap.entries.mapNotNull { (hash, progress) ->
                    repository.titleFor(hash)?.let { it to progress }
                }
                updateNotification(titled.filter { it.second in active })
            }
        }

        // Persisting on every emission would mean a write per second per
        // torrent; every 5s is enough to survive a process death.
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5000)
                repository.persistSnapshot(engine.progress.value)
            }
        }
    }

    private fun observeCompletions() {
        lifecycleScope.launch {
            engine.finished.collect { infoHash ->
                if (infoHash == null) return@collect
                val title = repository.titleFor(infoHash) ?: return@collect
                repository.persistSnapshot(engine.progress.value)
                getSystemService<NotificationManager>()?.notify(
                    completionNotificationId++,
                    TorrentNotifications.buildComplete(this@TorrentService, title, 0),
                )
            }
        }
    }

    private fun updateNotification(active: List<Pair<String, TorrentProgress>>) {
        val notification = TorrentNotifications.buildProgress(
            context = this,
            active = active,
            paused = manuallyPaused || pausedForMeteredNetwork,
        )
        getSystemService<NotificationManager>()
            ?.notify(TorrentNotifications.FOREGROUND_ID, notification)
    }

    private fun startForegroundCompat() {
        val notification = TorrentNotifications.buildProgress(this, emptyList(), false)
        ServiceCompat.startForeground(
            this,
            TorrentNotifications.FOREGROUND_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun stopEverything() {
        lifecycleScope.launch {
            engine.stop()
            ServiceCompat.stopForeground(this@TorrentService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // --- Metered network guard ----------------------------------------------

    /**
     * Season packs run to tens of gigabytes, so a Wi-Fi-only default is not a
     * nicety. We watch capabilities rather than connection type because a
     * tethered or metered Wi-Fi network should count as mobile data.
     */
    private fun registerConnectivityCallback() {
        val manager = getSystemService<ConnectivityManager>() ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                lifecycleScope.launch {
                    val wifiOnly = settings.wifiOnly.first()
                    val unmetered =
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

                    val shouldPause = wifiOnly && !unmetered
                    if (shouldPause != pausedForMeteredNetwork) {
                        pausedForMeteredNetwork = shouldPause
                        if (shouldPause) {
                            engine.pauseSession()
                        } else if (!manuallyPaused) {
                            engine.resumeSession()
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                // Losing the network is not the same as being on a metered one;
                // libtorrent reconnects on its own once a route reappears.
            }
        }

        runCatching {
            manager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
            connectivityCallback = callback
        }
    }

    private fun unregisterConnectivityCallback() {
        val manager = getSystemService<ConnectivityManager>()
        connectivityCallback?.let { runCatching { manager?.unregisterNetworkCallback(it) } }
        connectivityCallback = null
    }

    // --- Wake lock ----------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService<PowerManager>() ?: return
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "jellyflix:torrent",
        ).apply {
            setReferenceCounted(false)
            // Timeout as a safety net: an OEM battery manager killing us is
            // recoverable, a wake lock leaked forever is not.
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }
}
