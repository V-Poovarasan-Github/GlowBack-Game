package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    const val UNIQUE_WORK_NAME = "daily_glowback_reminder"
    const val CHANNEL_ID = "glowback_daily_reminder_channel"
    const val NOTIFICATION_ID = 1001

    private const val PREFS_MAIN = "glowback_prefs"
    const val KEY_LAST_OPENED_TIMESTAMP = "last_opened_timestamp"
    const val KEY_DAILY_NOTIFICATIONS_ENABLED = "daily_notifications_enabled"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Glowback Reminder"
            val descriptionText = "Daily memory training, high score challenges, and brain teasers."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules the next daily notification for 18:00 (6:00 PM) local time.
     * Uses OneTimeWorkRequest with ExistingWorkPolicy.REPLACE and battery constraint disabled.
     */
    fun scheduleDailyReminder(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_DAILY_NOTIFICATIONS_ENABLED, true)
        if (!isEnabled) return

        createNotificationChannel(context)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If 18:00 has already passed today, target tomorrow 18:00
        if (now.after(target)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelayMillis = (target.timeInMillis - now.timeInMillis).coerceAtLeast(1000L)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false) // Fire even in Battery Saver mode
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag("glowback_reminder")
            .build()

        getWorkManager(context)?.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Cancels any pending WorkManager requests when the user toggles notifications OFF.
     */
    fun cancelReminder(context: Context) {
        getWorkManager(context)?.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun getWorkManager(context: Context): WorkManager? {
        return try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            try {
                val config = androidx.work.Configuration.Builder().build()
                WorkManager.initialize(context.applicationContext, config)
                WorkManager.getInstance(context)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Posts the notification to the system tray.
     * Skips if notifications are disabled, if user is in foreground (<2m), or if user played today (Smart Skip).
     * [isTest] bypasses app toggle, foreground suppression, and Smart Skip.
     * Returns true if notification was posted to the notification manager, false if skipped or rejected.
     */
    fun postGlowbackNotification(context: Context, isTest: Boolean = false): Boolean {
        createNotificationChannel(context)

        val managerCompat = NotificationManagerCompat.from(context)
        if (!managerCompat.areNotificationsEnabled()) {
            return false
        }

        val prefs = context.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_DAILY_NOTIFICATIONS_ENABLED, true)
        if (!isTest && !isEnabled) {
            return false
        }

        val lastOpened = prefs.getLong(KEY_LAST_OPENED_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        val deltaMillis = if (lastOpened > 0L) (now - lastOpened).coerceAtLeast(0L) else 0L

        // 1. Foreground suppression: don't post if app was opened in the last 2 minutes, unless testing
        if (!isTest && lastOpened > 0L && deltaMillis < 2 * 60 * 1000L) {
            return false
        }

        // 2. Smart Skip: If worker fires and user already played/opened the app today, skip sending!
        if (!isTest && lastOpened > 0L) {
            val nowCal = Calendar.getInstance().apply { timeInMillis = now }
            val lastOpenedCal = Calendar.getInstance().apply { timeInMillis = lastOpened }
            val isSameDay = nowCal.get(Calendar.YEAR) == lastOpenedCal.get(Calendar.YEAR) &&
                    nowCal.get(Calendar.DAY_OF_YEAR) == lastOpenedCal.get(Calendar.DAY_OF_YEAR)
            if (isSameDay) {
                // User already played today! Smart skip notification.
                return false
            }
        }

        val rawGrid = prefs.getString("selected_grid_size", "TWO_BY_TWO") ?: "TWO_BY_TWO"
        val rawPace = prefs.getString("selected_pace_mode", "CLASSIC") ?: "CLASSIC"
        val rawDiff = prefs.getString("difficulty_mode", "EASY") ?: "EASY"

        val (gridLabel, gridPrefix) = when (rawGrid) {
            "TWO_BY_TWO" -> "2×2" to "2x2"
            "THREE_BY_THREE" -> "3×3" to "3x3"
            "FOUR_BY_FOUR" -> "4×4" to "4x4"
            "FIVE_BY_FOUR" -> "5×4" to "5x4"
            else -> "2×2" to "2x2"
        }

        val paceSuffix = when (rawPace) {
            "PROGRESSIVE" -> "progressive"
            else -> "classic"
        }

        val diffSuffix = when (rawDiff.uppercase()) {
            "HARD" -> "HARD"
            else -> "EASY"
        }

        val scoreKey = "best_${gridPrefix}_${paceSuffix}_${diffSuffix}"
        val bestScore = prefs.getInt(scoreKey, 0)

        val bodyText = NotificationContentProvider.getPhrase(context, deltaMillis, bestScore, gridLabel)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Glowback")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            managerCompat.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // Permission revoked at runtime
            false
        }
    }
}
