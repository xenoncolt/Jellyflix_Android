package dev.xenoncolt.jellyflix.player

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps playback alive when the player screen isn't on top.
 *
 * A [MediaSessionService] is what buys the lock-screen and notification
 * controls, headset buttons and Bluetooth transport — Android routes all of
 * those through the session rather than the activity. Without it, audio stops
 * the moment the app goes to the background.
 *
 * The session is handed the *existing* player rather than building its own:
 * two players would mean two decoders on the same file.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = PlaybackHolder.player?.let { player ->
            MediaSession.Builder(this, player).build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Stops the service when the user swipes the app away *and* nothing is
     * playing. Leaving it running would keep a notification for a player that
     * no longer exists; stopping it unconditionally would kill audio the user
     * deliberately backgrounded.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            // The player is owned by the ViewModel, so only the session is
            // released here.
            release()
        }
        session = null
        super.onDestroy()
    }
}

/**
 * Bridges the player into the service.
 *
 * A `MediaSessionService` is constructed by the system, so it cannot be given
 * the player through Hilt or an Intent. The engine parks the instance here on
 * creation and clears it on release, and the service picks it up.
 */
object PlaybackHolder {
    @Volatile
    var player: androidx.media3.common.Player? = null
}
