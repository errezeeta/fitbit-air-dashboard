package com.javier.fitbitair.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.javier.fitbitair.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Self-update straight from the GitHub releases of this project, so updating
 * never requires visiting the repository.
 *
 * Installing an APK on Android needs two things that are easy to forget: the
 * app must declare REQUEST_INSTALL_PACKAGES, and the user must have allowed
 * this app to install unknown apps. Without either, the install intent is
 * resolved to nothing and the tap appears to do nothing at all — so both are
 * checked here and surfaced to the UI instead of silently failing.
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
        // Both branches keep the same relative path so the FileProvider paths
        // in res/xml/update_paths.xml cover the file either way.
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val directory = File(root, "updates/apk").apply { mkdirs() }
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

    /** True once the user has allowed this app to install packages. */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Settings screen where the user grants "install unknown apps" to this app. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Hands the downloaded APK to the system installer. Returns false when no
     * installer accepted the request, so the caller can explain it instead of
     * leaving the user staring at a button that does nothing.
     */
    fun startInstall(prep: InstallPreparation): Boolean {
        if (!prep.file.exists()) return false
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.updatefileprovider",
                prep.file,
            )
        }.getOrNull() ?: return false

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }
        if (launch(viewIntent)) return true

        // Older/odd installers only answer the legacy action.
        val legacyIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }
        return launch(legacyIntent)
    }

    private fun launch(intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun parseMetadata(release: org.json.JSONObject): AvailableUpdate? {
        val raw = release.optString("name").ifBlank { release.optString("tag_name") }
        val versionName = raw.trimStart('v', 'V').trim()
        // El nombre de la release lleva ambos campos, p.ej. "Fitbit Air v0.6.1 · código 7".
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
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
