package dev.xenoncolt.jellyflix.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * Just enough of the GitHub releases API to find the newest APK.
 *
 * Unauthenticated, which is rate limited to 60 requests an hour per IP. We
 * check once on launch and only when a day has passed, so that's plenty.
 */
interface GithubApi {

    @GET("repos/xenoncolt/Jellyflix_Android/releases/latest")
    suspend fun latestRelease(): GithubRelease
}

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
) {
    /**
     * Picks the APK to install on this device.
     *
     * Releases carry a universal APK plus one per ABI, e.g.
     * Jellyflix-android.apk alongside Jellyflix-android-arm64-v8a.apk. Just
     * taking the first .apk grabs whichever GitHub happens to list first, which
     * is an ABI build that won't install on half the devices out there.
     *
     * So: match the device's own ABIs first (a third of the size), and only
     * fall back to the universal build — the one with no ABI in its name — when
     * there's no specific match.
     */
    fun apkFor(supportedAbis: List<String>): GithubAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        // Device ABIs come back best-first, so the first hit is the right one.
        supportedAbis.forEach { abi ->
            apks.firstOrNull { it.name.contains("-$abi.", ignoreCase = true) }
                ?.let { return it }
        }

        // Longest-first, or "x86" would match inside "x86_64".
        val abiTokens = listOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86")
        return apks.firstOrNull { asset ->
            abiTokens.none { asset.name.contains(it, ignoreCase = true) }
        } ?: apks.maxByOrNull { it.size }
    }

    /** Tags are published as "1.0.0"; tolerate a stray "v" either way. */
    val version: String get() = tagName.trimStart('v', 'V').trim()
}

@Serializable
data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)
