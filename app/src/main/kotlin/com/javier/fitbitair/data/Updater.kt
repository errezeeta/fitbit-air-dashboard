package com.javier.fitbitair.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.javier.fitbitair.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Self-update straight from the GitHub releases of this project, so updating
 * never requires visiting the repository. The app downloads the APK to its own
 * private files dir and hands it to the Android package installer, which is
 * where the user sees the one-time "install unknown apps" prompt that Android
 * itself requires for this flow.
 */
class AppUpdater(private val context: Context) {

    data class AvailableUpdate(
        val tagName: String,
        val versionName: String,
        val versionCode: Int,
        val notes: String,
        val apkUrl: String,
    )

    data class InstallPreparation(val file: File, val versionName: String)

    suspend fun checkForUpdate(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val connection = openConnection(BuildConfig.UPDATE_REPO)
        try {
            if (connection.responseCode != 200) return@withContext null
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val release = org.json.JSONObject(payload)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                return@withContext null
            }
            var apkUrl: String? = null
            val assets = release.optJSONArray("assets")
            for (index in 0 until (assets?.length() ?: 0)) {
                val asset = assets?.optJSONObject(index) ?: continue
                val candidate = asset.optString("browser_download_url")
                if (asset.optString("name").endsWith(".apk") && candidate.startsWith("https://")) {
                    apkUrl = candidate
                    break
                }
            }
            if (apkUrl == null) return@withContext null
            val metadata = parseMetadata(release) ?: return@withContext null
            if (metadata.versionCode <= BuildConfig.VERSION_CODE) return@withContext null
            AvailableUpdate(
                tagName = release.optString("tag_name"),
                versionName = metadata.versionName,
                versionCode = metadata.versionCode,
                notes = release.optString("body").take(600),
                apkUrl = apkUrl,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(update: AvailableUpdate): InstallPreparation = withContext(Dispatchers.IO) {
        val directory = File(context.getExternalFilesDir("updates") ?: context.filesDir, "apk")
            .apply { mkdirs() }
        val target = File(directory, "fitbit-air-${update.versionName}.apk")
        val connection = openConnection(update.apkUrl)
        try {
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        InstallPreparation(file = target, versionName = update.versionName)
    }

    fun startInstall(prep: InstallPreparation) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.updatefileprovider",
            prep.file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
        context.startActivity(intent)
    }

    private fun parseMetadata(release: org.json.JSONObject): AvailableUpdate? {
        val raw = release.optString("name").ifBlank { release.optString("tag_name") }
        val versionName = raw.trimStart('v', 'V').trim()
        // El nombre de la release lleva ambos campos, p.ej. "Fitbit Air v0.4.0 · código 4".
        val versionCode = Regex("(?:código|code|versionCode)\\s*[:=]?\\s*(\\d+)").find(raw)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return AvailableUpdate("", versionName, versionCode, "", "")
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            // Unauthenticated public call: no credentials anywhere in the app.
            setRequestProperty("User-Agent", "fitbit-air-updater")
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
