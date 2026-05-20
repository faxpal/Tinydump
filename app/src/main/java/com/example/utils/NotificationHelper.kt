package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import android.util.Log

object NotificationHelper {
    private const val CHANNEL_ID = "second_brain_ai_notes"
    private const val CHANNEL_NAME = "Second Brain summaries"
    private const val TAG = "NotificationHelper"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = CHANNEL_NAME
            val descriptionText = "Notifications containing AI parsed screenshot summaries and event actions"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification Channel Registered.")
        }
    }

    fun sendSummaryNotification(context: Context, id: Int, title: String, summary: String) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder) // Use default system icon or adaptive icon fallback
            .setContentTitle("Brain Capture: $title")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                // Since this might fail without runtime permissions on Android 13+,
                // we catch security exception gracefully and log it.
                // The app UI will also guide them to enable toggles.
                notify(id, builder.build())
                Log.d(TAG, "Notification dispatched successfully: ID=$id")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing or denied.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error posting notification.", e)
        }
    }

    fun sendReminderNotification(context: Context, id: Int, title: String, reminderText: String) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🧠 Follow-up Reminder")
            .setContentText("$title: $reminderText")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title: $reminderText"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(id + 10000, builder.build())
                Log.d(TAG, "Reminder notification dispatched successfully: ID=${id + 10000}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing or denied.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error posting alarm notification.", e)
        }
    }
}
