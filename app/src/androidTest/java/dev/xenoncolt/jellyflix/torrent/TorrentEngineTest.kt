package dev.xenoncolt.jellyflix.torrent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Exercises the real libtorrent4j session on-device.
 *
 * Uses Big Buck Bunny — Blender Foundation, CC-BY, freely distributable — so
 * the engine can be verified against an actual swarm without touching anything
 * copyrighted.
 *
 * **One engine for the whole class, deliberately.** libtorrent is not happy
 * being stood up and torn down repeatedly inside a single process, and the app
 * never does that either: [TorrentEngine] is a Hilt `@Singleton` with exactly
 * one session for the process lifetime. A per-test engine produced native
 * crashes in session teardown that no user could ever hit.
 *
 * These tests exist because two of the worst bugs here — a null
 * `torrent_flags_t` and a use-after-free on an already-freed alert — compiled
 * perfectly and only failed at runtime, in native code, where nothing is
 * catchable.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TorrentEngineTest {

    companion object {
        /** Big Buck Bunny (CC-BY, Blender Foundation). */
        private const val BBB_MAGNET =
            "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c" +
                "&dn=Big+Buck+Bunny" +
                "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce" +
                "&tr=udp%3A%2F%2Fexplodie.org%3A6969" +
                "&tr=udp%3A%2F%2Ftracker.empire-js.us%3A1337"

        private const val BBB_INFO_HASH = "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c"

        private lateinit var engine: TorrentEngine
        private lateinit var saveDir: File

        @JvmStatic
        @BeforeClass
        fun startEngine() = runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            engine = TorrentEngine(context)
            saveDir = File(context.cacheDir, "torrent-test").apply { mkdirs() }
            engine.start()
        }

        @JvmStatic
        @AfterClass
        fun stopEngine() = runBlocking {
            runCatching { engine.remove(BBB_INFO_HASH) }
            engine.stop()
            saveDir.deleteRecursively()
            Unit
        }
    }

    /**
     * The regression test for the null-flags crash. A non-sequential add is the
     * default path — the one every download takes unless the user turns
     * sequential mode on — and it used to die with
     * "Attempt to dereference null libtorrent::flags::bitfield_flag".
     */
    @Test
    fun a_addMagnet_nonSequential_doesNotThrow() = runBlocking {
        val result = engine.addMagnet(BBB_MAGNET, saveDir, sequential = false)

        assertTrue(
            "Non-sequential add failed: ${result.exceptionOrNull()?.message}",
            result.isSuccess,
        )
        assertEquals(BBB_INFO_HASH, result.getOrNull())
    }

    /**
     * Adding is not the same as working. The engine only reports progress once
     * libtorrent has delivered an ADD_TORRENT alert and a handle is registered;
     * if that never arrives, every download sits at 0% with no error — which is
     * exactly what users saw.
     */
    @Test
    fun b_engineRegistersHandleAndReportsProgress() = runBlocking {
        val progress = withTimeoutOrNull(60_000) {
            var seen: TorrentProgress? = null
            while (seen == null) {
                seen = engine.progress.value[BBB_INFO_HASH]
                if (seen == null) delay(500)
            }
            seen
        }

        assertNotNull(
            "Engine never reported status within 60s — the ADD_TORRENT alert " +
                "never registered a handle.",
            progress,
        )
    }

    /** Sequential mode is the one path that always had non-null flags. */
    @Test
    fun c_setSequential_doesNotThrow() {
        engine.setSequential(BBB_INFO_HASH, sequential = true)
        engine.setSequential(BBB_INFO_HASH, sequential = false)
    }

    /**
     * Pause asks libtorrent to persist resume data, which is where the
     * use-after-free used to land: the resulting alert was serialised from a
     * coroutine after libtorrent had already freed it.
     */
    @Test
    fun d_pauseAndResume_doNotCrash() = runBlocking {
        engine.pause(BBB_INFO_HASH)
        delay(2000)
        engine.resume(BBB_INFO_HASH)
        delay(1000)
    }
}
