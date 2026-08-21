package dev.xenoncolt.jellyflix.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The episode list reports what is left to watch, so the boundaries matter:
 * the hour rollover and the "no minutes remaining" case are exactly where a
 * clock-style formatter reads wrong.
 */
class FormatRemainingTest {

    private fun minutes(n: Long) = n * 60_000L

    @Test
    fun `under a minute avoids saying zero`() {
        assertEquals("Less than a minute left", Format.remaining(0))
        assertEquals("Less than a minute left", Format.remaining(59_000))
    }

    @Test
    fun `minutes below an hour`() {
        assertEquals("1 min left", Format.remaining(minutes(1)))
        assertEquals("15 min left", Format.remaining(minutes(15)))
        assertEquals("59 min left", Format.remaining(minutes(59)))
    }

    @Test
    fun `hours drop the minutes when there are none`() {
        assertEquals("1h left", Format.remaining(minutes(60)))
        assertEquals("2h left", Format.remaining(minutes(120)))
    }

    @Test
    fun `hours and minutes together`() {
        assertEquals("1h 5m left", Format.remaining(minutes(65)))
        assertEquals("2h 17m left", Format.remaining(minutes(137)))
    }

    @Test
    fun `negative input cannot produce a negative reading`() {
        // Position past duration is possible if a file's length is re-reported.
        assertEquals("Less than a minute left", Format.remaining(-5_000))
    }
}
