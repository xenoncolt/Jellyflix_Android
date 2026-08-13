package dev.xenoncolt.jellyflix

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import dev.xenoncolt.jellyflix.torrent.TorrentNotifications
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class JellyflixApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * A Provider, not a direct injection: Coil asks for the loader lazily, and
     * building it eagerly in onCreate would put disk-cache setup on the cold
     * start path for no reason.
     */
    @Inject lateinit var imageLoader: Provider<ImageLoader>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Cheap, and the download service may need them before any UI runs.
        TorrentNotifications.ensureChannels(this)
    }

    /**
     * Hands Coil the same OkHttp client and cache configuration the rest of the
     * app uses, so poster loads share connections with API calls.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
