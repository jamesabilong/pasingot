package app.personal.workouttracker.wear.ongoing

import app.personal.workouttracker.shared.CURRENT_SCHEMA_VERSION
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import app.personal.workouttracker.wear.R
import app.personal.workouttracker.wear.WearMainActivity
import app.personal.workouttracker.wear.data.WorkoutRepository
import app.personal.workouttracker.wear.quickstart.DataStoreQuickStartRuntimePersistence
import app.personal.workouttracker.wear.quickstart.QuickStartRuntimeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Observes durable changes, including receipt cleanup; never runs a timer or workout engine. */
object WorkoutOngoingActivity {
    const val KIND_EXTRA = "ongoingWorkoutKind"
    const val ID_EXTRA = "ongoingWorkoutId"
    const val NOTIFICATION_ID = 3
    private const val CHANNEL = "ongoing_workout"
    private var started = false
    private var lastWorkout: OngoingWorkout? = null

    fun refresh(context: Context) = publish(context.applicationContext, lastWorkout)

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val persistence = DataStoreQuickStartRuntimePersistence(app)
        val runtime = QuickStartRuntimeStore(persistence)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                combine(WorkoutRepository(app).entries, persistence.changes.map { runtime.current() }) { entries, quick ->
                    val candidates = entries.mapNotNull { entry ->
                        if (entry.schemaVersion != CURRENT_SCHEMA_VERSION ||
                            entry.sessionState?.workoutEntryId != entry.id) null
                        else ongoingWorkout("session", entry.id, entry.label, entry.sessionState)
                    }.toMutableList()
                    quick?.let { state ->
                        ongoingWorkout("quick-start", state.sessionPackage.request.requestId,
                            state.sessionPackage.request.title ?: "Workout", state.session)?.let(candidates::add)
                    }
                    singleOngoingWorkout(candidates)
                }.distinctUntilChanged().collect { publish(app, it) }
            } catch (error: Exception) {
                lastWorkout = null
                NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
                Log.e("WorkoutOngoing", "Unable to observe saved workout", error)
            }
        }
    }

    private fun publish(context: Context, workout: OngoingWorkout?) {
        lastWorkout = workout
        val notifications = NotificationManagerCompat.from(context)
        if (workout == null || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.cancel(NOTIFICATION_ID)
            return
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Ongoing workout", NotificationManager.IMPORTANCE_LOW))
        val intent = Intent(context, WearMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.Builder().scheme("pasingot").authority("ongoing")
                .appendPath(workout.kind).appendPath(workout.id).build()
            putExtra(KIND_EXTRA, workout.kind)
            putExtra(ID_EXTRA, workout.id)
        }
        val touch = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = when (workout.status) {
            "paused" -> "Workout paused"
            "resting" -> "Resting"
            else -> "Workout in progress"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_ongoing_workout)
            .setContentTitle(workout.title).setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(touch).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
        OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_ongoing_workout).setTouchIntent(touch).build().apply(context)
        notifications.notify(NOTIFICATION_ID, builder.build())
    }
}
