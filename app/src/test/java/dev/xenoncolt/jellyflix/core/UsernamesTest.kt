package dev.xenoncolt.jellyflix.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernamesTest {

    @Test
    fun `accepts the ordinary shapes`() {
        listOf("xenoncolt", "abc", "a1._-", "User.Name", "0start").forEach {
            assertNull("expected '$it' to be valid", Usernames.formatError(it))
        }
    }

    @Test
    fun `accepts the length boundaries`() {
        assertTrue(Usernames.isValid("a".repeat(Usernames.MIN_LENGTH)))
        assertTrue(Usernames.isValid("a".repeat(Usernames.MAX_LENGTH)))
        assertFalse(Usernames.isValid("a".repeat(Usernames.MIN_LENGTH - 1)))
        assertFalse(Usernames.isValid("a".repeat(Usernames.MAX_LENGTH + 1)))
    }

    @Test
    fun `must start alphanumeric`() {
        // The rule exists so a handle can't masquerade as punctuation.
        listOf("_leading", ".dot", "-dash").forEach {
            assertEquals("Must start with a letter or number.", Usernames.formatError(it))
        }
    }

    @Test
    fun `rejects characters outside the allowed set`() {
        listOf("has space", "emoji🎬", "sla/sh", "at@sign").forEach {
            assertFalse("expected '$it' to be rejected", Usernames.isValid(it))
        }
    }

    @Test
    fun `blank input asks rather than complains about length`() {
        assertEquals("Pick a username.", Usernames.formatError("   "))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertNull(Usernames.formatError("  xenoncolt  "))
    }
}
