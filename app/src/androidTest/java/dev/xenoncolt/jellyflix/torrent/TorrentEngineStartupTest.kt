package dev.xenoncolt.jellyflix.torrent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Adds against an engine that was never started.
 *
 * Its own engine on purpose — [TorrentEngineTest] shares one started session
 * across the class, and the whole point here is the *unstarted* case. No session
 * is ever created, so there is no native teardown to go wrong.
 *
 * The bug: [TorrentRepository.startDownload] added the torrent and only then
 * called `TorrentService.start`, which is what starts the engine — and starting
 * a service is asynchronous, so the first download of a process always reached a
 * session that did not exist yet. libtorrent4j keeps its native session in a
 * field that stays null until start(), and `swig()` hands that field straight
 * back, so the magnet path threw
 * "Attempt to invoke virtual method '...session_handle.add_torrent(...)' on a
 * null object reference" into the release picker, while `download()` — which
 * begins with `if (session == null) return` — swallowed .torrent adds without a
 * word and left a row parked at 0%.
 */
@RunWith(AndroidJUnit4::class)
class TorrentEngineStartupTest {

    private companion object {
        const val BBB_MAGNET =
            "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Big+Buck+Bunny"
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun addMagnet_beforeStart_failsWithAMessageInsteadOfNpe() = runBlocking {
        val engine = TorrentEngine(context)
        val saveDir = File(context.cacheDir, "torrent-startup-test").apply { mkdirs() }

        try {
            assertFalse("Engine should not be running", engine.isRunning)

            val result = engine.addMagnet(BBB_MAGNET, saveDir, sequential = false)

            assertTrue("Add against a stopped session should fail", result.isFailure)

            val error = result.exceptionOrNull()
            assertNotNull(error)
            assertFalse(
                "Should report the stopped session, not NPE on a null swig handle: $error",
                error is NullPointerException,
            )
            assertTrue(
                "Message should name the real problem, was: ${error?.message}",
                error?.message?.contains("not running", ignoreCase = true) == true,
            )
        } finally {
            saveDir.deleteRecursively()
        }
    }

    /** The same guard on the path that used to fail silently. */
    @Test
    fun addSaved_beforeStart_fails() = runBlocking {
        val engine = TorrentEngine(context)
        val saveDir = File(context.cacheDir, "torrent-startup-test-saved").apply { mkdirs() }

        try {
            val result = engine.addSaved(
                infoHash = "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c",
                saveDir = saveDir,
                sequential = false,
            )
            assertTrue("Add against a stopped session should fail", result.isFailure)
        } finally {
            saveDir.deleteRecursively()
        }
    }
}
