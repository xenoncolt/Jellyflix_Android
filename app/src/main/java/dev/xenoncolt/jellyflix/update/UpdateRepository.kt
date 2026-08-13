package dev.xenoncolt.jellyflix.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xenoncolt.jellyflix.BuildConfig
import dev.xenoncolt.jellyflix.data.remote.GithubApi
import dev.xenoncolt.jellyflix.data.remote.GithubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the UI needs to know about an available update. */
data class AvailableUpdate(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GithubApi,
    private val okHttpClient: OkHttpClient,
) {
    private companion object {
        const val TAG = "UpdateRepository"
    }

    /** Returns an update only when GitHub is actually ahead of what's installed. */
    suspend fun check(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val release = runCatching { api.latestRelease() }
            .onFailure { Log.w(TAG, "Update check failed", it) }
            .getOrNull()
            ?: return@withContext null

        if (release.draft || release.prerelease) return@withContext null

        val asset = release.apkFor(android.os.Build.SUPPORTED_ABIS?.toList().orEmpty())
            ?: return@withContext null
        if (!isNewer(release.version, BuildConfig.VERSION_NAME)) return@withContext null

        AvailableUpdate(
            version = release.version,
            notes = release.body.trim(),
            downloadUrl = asset.downloadUrl,
            sizeBytes = asset.size,
        )
    }

    /**
     * Pulls the APK down ourselves and reports progress, so the user never
     * leaves the app for a browser.
     *
     * Written to the cache dir: it's a throwaway that the system can reclaim,
     * and FileProvider is already pointed at it.
     */
    suspend fun download(
        update: AvailableUpdate,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Clear old attempts so a half-written APK can't be handed to the
            // installer, which fails with a useless "parse error".
            dir.listFiles()?.forEach { it.delete() }

            val target = File(dir, "Jellyflix-${update.version}.apk")
            val request = Request.Builder().url(update.downloadUrl).build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed (${response.code})")
                val body = response.body ?: error("Empty download")
                val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            if (target.length() <= 0) error("Downloaded file was empty")
            target
        }
    }

    /**
     * Hands the APK to the system installer.
     *
     * This is as automatic as Android allows: a sideloaded app can't install
     * silently, so the user still taps through the system prompt. What we've
     * avoided is sending them to a browser to find and download the file
     * themselves.
     */
    fun install(apk: File): Result<Unit> = runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** True if the user has allowed us to install APKs. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Settings page where that permission is granted. */
    fun installPermissionIntent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Compares dotted versions numerically, so 1.10.0 correctly beats 1.9.0 —
     * a plain string compare gets that backwards.
     */
    private fun isNewer(remote: String, installed: String): Boolean {
        fun parts(value: String) = value
            .substringBefore('-')
            .split('.')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

        val remoteParts = parts(remote)
        val installedParts = parts(installed)
        if (remoteParts.isEmpty()) return false

        for (i in 0 until maxOf(remoteParts.size, installedParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = installedParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
