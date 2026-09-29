package app.personal.workouttracker.wear.quickstart

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.personal.workouttracker.shared.quickstart.WatchSessionPackage
import app.personal.workouttracker.wear.WearMainActivity

enum class QuickStartOfferNotificationResult {
    SHOWN,
    PERMISSION_DENIED,
    NOTIFICATIONS_DISABLED,
    FAILED,
}

interface QuickStartOfferNotifier {
    fun showReady(sessionPackage: WatchSessionPackage): QuickStartOfferNotificationResult
    fun cancel()
}

object NoOpQuickStartOfferNotifier : QuickStartOfferNotifier {
    override fun showReady(sessionPackage: WatchSessionPackage) =
        QuickStartOfferNotificationResult.NOTIFICATIONS_DISABLED
    override fun cancel() = Unit
}

/** Optional system entry point; the durable in-app card remains authoritative. */
class AndroidQuickStartOfferNotifier(context: Context) : QuickStartOfferNotifier {
    private val appContext = context.applicationContext
    private val notifications = NotificationManagerCompat.from(appContext)

    override fun showReady(sessionPackage: WatchSessionPackage): QuickStartOfferNotificationResult {
        ensureChannel()
        if (ActivityCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return QuickStartOfferNotificationResult.PERMISSION_DENIED
        if (!notifications.areNotificationsEnabled() || !channelEnabled()) {
            return QuickStartOfferNotificationResult.NOTIFICATIONS_DISABLED
        }

        val request = sessionPackage.request
        val firstExercise = request.exercises.firstOrNull()?.exerciseName
        val workoutTitle = request.title?.takeIf(String::isNotBlank) ?: "Phone workout"
        val exerciseCount = request.exercises.size
        val content = buildString {
            append(workoutTitle)
            append(" · ")
            append(exerciseCount)
            append(if (exerciseCount == 1) " exercise" else " exercises")
            firstExercise?.takeIf(String::isNotBlank)?.let {
                append(" · ")
                append(it)
            }
        }
        val openApp = Intent(appContext, WearMainActivity::class.java).apply {
            action = ACTION_OPEN_QUICK_START
            putExtra(EXTRA_REQUEST_ID, request.requestId)
            // A Ready offer cannot coexist with an active session. Recreate the
            // single-activity task so a notification tap always lands on the
            // list's durable in-app offer card, even if Settings was open.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val timeout = (sessionPackage.expiresLocallyAtMillis - System.currentTimeMillis())
            .coerceAtLeast(1L)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Quick Start ready")
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setTimeoutAfter(timeout)
            .build()
        return try {
            notifications.notify(NOTIFICATION_ID, notification)
            QuickStartOfferNotificationResult.SHOWN
        } catch (_: SecurityException) {
            QuickStartOfferNotificationResult.PERMISSION_DENIED
        } catch (_: RuntimeException) {
            QuickStartOfferNotificationResult.FAILED
        }
    }

    override fun cancel() {
        notifications.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            "Quick Start offers",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Workout offers sent from your phone"
        })
    }

    private fun channelEnabled(): Boolean {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        return manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    companion object {
        const val ACTION_OPEN_QUICK_START = "app.personal.workouttracker.action.OPEN_QUICK_START"
        const val EXTRA_REQUEST_ID = "quick_start_request_id"
        private const val CHANNEL_ID = "quick_start_offers"
        private const val NOTIFICATION_ID = 30
    }
}
