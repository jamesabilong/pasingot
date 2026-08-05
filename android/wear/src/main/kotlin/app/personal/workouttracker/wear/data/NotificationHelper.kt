package app.personal.workouttracker.wear.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Prompt 5 req 3 / resolved decision: when a download (manual or scheduled)
 * is [AddResult.Blocked] by the storage cap, post a system notification
 * immediately rather than a quiet in-app banner — a 6:30 AM scheduled job
 * has no one watching in real time, so silence isn't an option.
 *
 * Posted unconditionally on Blocked regardless of which trigger caused it
 * (manual or scheduled) — simpler than threading foreground/background
 * context into the listener service, and harmless to also see it after a
 * manual tap.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "workout_downloads"

    fun notifyBlockedDownload(context: Context) {
        ensureChannel(context)

        val hasPermission = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return // nothing we can do without the runtime grant

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Auto-download skipped")
            .setContentText("Delete or finish a workout to free space.")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(BLOCKED_DOWNLOAD_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout downloads",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    private const val BLOCKED_DOWNLOAD_NOTIFICATION_ID = 1
}
