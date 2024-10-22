package dev.xenoncolt.jellyflix.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import dev.xenoncolt.jellyflix.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject


object UpdateManager {
    private const val GITHUB_API_URL = "https://api.github.com/repos/xenoncolt/Jellyflix_Android/releases/latest"
//    const val REQUEST_INSTALL_PERMISSION = 1001 // Custom request code

    suspend fun getLatestReleaseTag(): String? {
        return withContext(Dispatchers.IO) {
            try {
//                val author = "xenoncolt"
//                val repo = "Jellyflix_Android"
//                val url = "https://api.github.com/repos/$author/$repo/releases/latest"
                val connection = URL(GITHUB_API_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonObject = JSONObject(response)
                jsonObject.getString("tag_name")
            } catch (e: Exception) {
                null
            }
        }
    }

    // Mapping ABIs to APK names
    private val apkMapping = mapOf(
        "arm64-v8a" to "Jellyflix-android-arm64-v8a.apk",
        "armeabi-v7a" to "Jellyflix-android-armeabi-v7a.apk",
        "x86_64" to "Jellyflix-android-x86_64.apk",
        "x86" to "Jellyflix-android-x86.apk"
    )

    // Method to determine device's ABI
    fun getDeviceAbi(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        return when {
            supportedAbis.contains("arm64-v8a") -> "arm64-v8a"
            supportedAbis.contains("armeabi-v7a") -> "armeabi-v7a"
            supportedAbis.contains("x86_64") -> "x86_64"
            supportedAbis.contains("x86") -> "x86"
            else -> throw IllegalArgumentException("Unsupported ABI")
        }
    }

    suspend fun isUpdateAvailable() : Boolean {
        val latestReleaseTag = getLatestReleaseTag()
        return latestReleaseTag != null && latestReleaseTag != BuildConfig.VERSION_NAME
    }

     suspend fun getApkUrl(abi: String): String? {
        val  latestRelease = fetchLatestRelease()
        return latestRelease?.assets?.find { it.name == apkMapping[abi] }?.browserDownloadUrl
    }

    private suspend fun fetchLatestRelease(): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseGithubRelease(response)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseGithubRelease(response: String): GithubRelease {
        val json = JSONObject(response)
        val assets = json.getJSONArray("assets")
        val releaseTag = json.getString("tag_name")

        val assetList = mutableListOf<GithubReleaseAsset>()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            assetList.add(
                GithubReleaseAsset(
                    name = asset.getString("name"),
                    browserDownloadUrl = asset.getString("browser_download_url")
                )
            )
        }
        return GithubRelease(releaseTag, assetList)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
     fun downloadAndInstallApk(context: Context, apkUrl: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
        request.setTitle("Downloading Update")
        request.setDescription("Downloading the latest version of Jellyflix")
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "Jellyflix_latest.apk")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (downloadId == intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)) {
                    val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
                    installApk(context, apkUri)

                    context?.unregisterReceiver(this)
                }
            }
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED)
    }

    private fun installApk(context: Context?, apkUri: Uri?) {
//        val intent = Intent(Intent.ACTION_VIEW)
//        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
//        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION


        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context?.startActivity(intent)

//        if (context != null) {
//            if (!context.packageManager.canRequestPackageInstalls()) {
//                val installPermissionIntent = Intent(
//                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
//                    Uri.parse("package:${context.packageName}")
//                )
//                installPermissionIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                context.startActivity(installPermissionIntent)
//                requestInstallPermission(context)
//                context.startActivity(intent)
//            } else {
//                context.startActivity(intent)
//            }
//        }
    }

//    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
//    fun requestInstallPermission(context: Context, apkUrl: String) {
//        AlertDialog.Builder(context)
//            .setTitle("Update Permission Required")
//            .setMessage("To install the latest update, Jellyflix needs permission to install apps from this source. Please grant permission in the next screen.")
//            .setPositiveButton("Grant Permission") { _, _ ->
//                val intent = Intent(
//                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
//                    Uri.parse("package:${context.packageName}")
//                )
//                (context as Activity).startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
//                downloadAndInstallApk(context, apkUrl)
//            }
//            .setNegativeButton("Cancel") { _, _ ->
//                Toast.makeText(context, "Update cancelled.\nReason: Permission denied to install the APK", Toast.LENGTH_SHORT).show()
//            }
//            .show()
//    }
}

data class GithubRelease(
    val tagName: String,
    val assets: List<GithubReleaseAsset>
)

data class GithubReleaseAsset(
    val name: String,
    val browserDownloadUrl: String
)