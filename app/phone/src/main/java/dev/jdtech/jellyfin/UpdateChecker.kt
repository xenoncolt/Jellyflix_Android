package dev.jdtech.jellyfin

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject


object UpdateChecker {
    suspend fun getLatestReleaseTag(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val author = "xenoncolt"
                val repo = "Jellyflix_Android"
                val url = "https://api.github.com/repos/$author/$repo/releases/latest"
                val connection = URL(url).openConnection() as HttpURLConnection
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

    suspend fun isUpdateAvailable() : Boolean {
        val latestReleaseTag = getLatestReleaseTag()
        return latestReleaseTag != null && latestReleaseTag != BuildConfig.VERSION_NAME
    }
}