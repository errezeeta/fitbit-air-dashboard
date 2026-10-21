package com.javier.fitbitair.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.javier.fitbitair.BuildConfig
import com.javier.fitbitair.MainActivity
import com.javier.fitbitair.R
import com.javier.fitbitair.domain.BackendApiClient
import com.javier.fitbitair.domain.DashboardState
import com.javier.fitbitair.domain.MorningBriefPolicy
import com.javier.fitbitair.domain.TrendRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One morning notification with the day's brief.
 *
 * Android gives a periodic worker no tighter than fifteen minutes and Doze
 * stretches it further, so "twenty minutes after waking" is really "the first
 * window after that, once the phone is awake". [MorningBriefPolicy] holds the
 * window rules; this class only fetches, asks and posts.
 */
class DailyBriefWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trim()
        if (baseUrl.isBlank()) return Result.success()

        val sessionStore = SessionTokenStore(applicationContext)
        if (!sessionStore.hasSession()) return Result.success()

        val client = BackendApiClient(
            baseUrl = baseUrl,
            credentials = sessionStore,
            transport = UrlConnectionBackendTransport(baseUrl),
        )
        val zone = ZoneId.systemDefault()

        // The dashboard call is free; the agent is not. Decide first, then pay.
        val data = when (val state = client.loadDashboard(TrendRange.WEEK, zone.id)) {
            is DashboardState.Ready -> state.data
            else -> return Result.success()
        }

        val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val lastNotified = preferences.getString(KEY_LAST_NOTIFIED, null)
        val allowed = MorningBriefPolicy.shouldNotify(
            endTimeIso = data.sleep.endTimeIso,
            now = Instant.now(),
            lastNotifiedDay = lastNotified,
            zone = zone,
        )
        if (!allowed) return Result.success()

        val text = runCatching {
            client.askAssistant(BRIEF_QUESTION, TrendRange.WEEK, zone.id).text
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return Result.success()

        if (!postNotification(text)) return Result.success()
        preferences.edit().putString(KEY_LAST_NOTIFIED, LocalDate.now(zone).toString()).apply()
        return Result.success()
    }

    private fun postNotification(body: String): Boolean {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Resumen de la mañana",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Qué ha cambiado en tus datos y qué hacer hoy" },
        )
        val open = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Resumen de hoy")
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily-brief"
        private const val CHANNEL_ID = "daily_brief"
        private const val NOTIFICATION_ID = 1_001
        private const val PREFERENCES = "daily_brief_state"
        private const val KEY_LAST_NOTIFIED = "last_notified_day"

        private const val BRIEF_QUESTION =
            "Dame el informe de esta mañana para una notificación: en 2 o 3 frases, qué ha " +
                "cambiado respecto a los días anteriores y una sola recomendación práctica " +
                "para hoy. Sin listas, sin markdown y sin llamarme por mi nombre."
    }
}
