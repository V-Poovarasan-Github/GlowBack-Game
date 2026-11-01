package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background worker executing the daily Glowback reminder.
 * Calculates time delta, posts the notification, and reschedules for tomorrow at 18:00.
 */
class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val isTest = tags.contains("glowback_test_reminder")

        NotificationScheduler.postGlowbackNotification(applicationContext, isTest = isTest)

        if (!isTest) {
            // Automatically schedule tomorrow's 18:00 reminder
            NotificationScheduler.scheduleDailyReminder(applicationContext)
        }

        return Result.success()
    }
}
