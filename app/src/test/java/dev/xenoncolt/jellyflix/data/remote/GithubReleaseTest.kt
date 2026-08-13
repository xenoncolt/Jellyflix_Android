package dev.xenoncolt.jellyflix.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks down which APK the updater picks.
 *
 * Worth having as a plain unit test because getting it wrong ships an update
 * that installs on nobody's phone, and you'd only find out from bug reports.
 */
class GithubReleaseTest {

    private fun release(vararg names: String) = GithubRelease(
        tagName = "1.0.0",
        assets = names.map { GithubAsset(name = it, downloadUrl = "https://x/$it", size = 1) },
    )

    private val arm64Device = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
    private val x86Device = listOf("x86_64", "x86", "arm64-v8a")

    /** The shape of releases from 1.0.0 onward: one universal APK. */
    @Test
    fun `single universal apk is chosen on any device`() {
        val only = release("jellyflix_android.apk")

        assertEquals("jellyflix_android.apk", only.apkFor(arm64Device)?.name)
        assertEquals("jellyflix_android.apk", only.apkFor(x86Device)?.name)
        assertEquals("jellyflix_android.apk", only.apkFor(emptyList())?.name)
    }

    /** Older releases (0.5.1) published per-ABI builds alongside the universal one. */
    @Test
    fun `abi specific build wins over the universal one`() {
        val mixed = release(
            "Jellyflix-android-arm64-v8a.apk",
            "Jellyflix-android-armeabi-v7a.apk",
            "Jellyflix-android-x86.apk",
            "Jellyflix-android-x86_64.apk",
            "Jellyflix-android.apk",
        )

        assertEquals("Jellyflix-android-arm64-v8a.apk", mixed.apkFor(arm64Device)?.name)
        assertEquals("Jellyflix-android-x86_64.apk", mixed.apkFor(x86Device)?.name)
    }

    /**
     * "x86" is a substring of "x86_64", so a naive contains() hands an x86_64
     * phone the 32-bit build.
     */
    @Test
    fun `x86 does not match inside x86_64`() {
        val mixed = release("Jellyflix-android-x86.apk", "Jellyflix-android-x86_64.apk")

        assertEquals("Jellyflix-android-x86_64.apk", mixed.apkFor(listOf("x86_64"))?.name)
    }

    /** No ABI match, so it has to fall back to the universal build. */
    @Test
    fun `falls back to universal when no abi matches`() {
        val mixed = release("Jellyflix-android-arm64-v8a.apk", "Jellyflix-android.apk")

        assertEquals("Jellyflix-android.apk", mixed.apkFor(listOf("mips"))?.name)
    }

    @Test
    fun `ignores non apk assets`() {
        val noApk = release("checksums.txt", "mapping.txt")

        assertNull(noApk.apkFor(arm64Device))
    }

    @Test
    fun `strips a leading v from the tag`() {
        assertEquals("1.0.0", GithubRelease(tagName = "v1.0.0").version)
        assertEquals("1.0.0", GithubRelease(tagName = "1.0.0").version)
    }
}
