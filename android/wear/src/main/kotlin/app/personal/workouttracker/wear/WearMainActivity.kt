package app.personal.workouttracker.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.personal.workouttracker.wear.data.LogSyncManager
import app.personal.workouttracker.wear.data.SettingsRepository
import app.personal.workouttracker.wear.data.WorkoutRepository
import app.personal.workouttracker.wear.download.LogFlushWorker
import app.personal.workouttracker.wear.download.ScheduleDownloadWorker
import app.personal.workouttracker.wear.download.SettingsScreen
import app.personal.workouttracker.wear.download.SettingsViewModel
import app.personal.workouttracker.wear.download.WorkoutListScreen
import app.personal.workouttracker.wear.download.WorkoutListViewModel
import app.personal.workouttracker.wear.session.SessionScreen
import app.personal.workouttracker.wear.session.SessionViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single-activity implementation (Prompt 4 req 5) — the Manage Downloads
 * list (Prompt 5), its settings screen, and the active session screen
 * (Prompt 4) are all Compose Navigation destinations hosted here.
 */
class WearMainActivity : ComponentActivity() {

    private val prefsName = "workout_tracker_wear_prefs"
    private val notificationPermissionAskedKey = "notification_permission_requested"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        val repository = WorkoutRepository(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)
        val logSyncManager = LogSyncManager(applicationContext)

        // (Re)arm the scheduled-download worker on every app start — cheap
        // and idempotent (see ScheduleDownloadWorker.enqueueNext), and it's
        // how the very first schedule gets set up with no explicit user
        // action required.
        lifecycleScope.launch {
            ScheduleDownloadWorker.enqueueNext(applicationContext, settingsRepository.scheduledTime.first())
        }

        // Prompt 8 req 5: retry the offline log queue on app start, plus a
        // periodic WorkManager flush for while the app isn't open.
        lifecycleScope.launch { logSyncManager.flushQueue() }
        LogFlushWorker.schedulePeriodic(applicationContext)

        setContent {
            MaterialTheme {
                val navController = rememberSwipeDismissableNavController()

                SwipeDismissableNavHost(navController = navController, startDestination = "list") {
                    composable("list") {
                        val viewModel: WorkoutListViewModel = viewModel(
                            factory = WorkoutListViewModel.Factory(applicationContext, repository)
                        )
                        WorkoutListScreen(
                            viewModel = viewModel,
                            onOpenEntry = { entryId -> navController.navigate("session/$entryId") },
                            onOpenSettings = { navController.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        val viewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(applicationContext, settingsRepository)
                        )
                        SettingsScreen(viewModel = viewModel)
                    }
                    composable(
                        route = "session/{entryId}",
                        arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val entryId = backStackEntry.arguments?.getString("entryId") ?: return@composable
                        val viewModel: SessionViewModel = viewModel(
                            factory = SessionViewModel.Factory(entryId, repository, logSyncManager)
                        )
                        SessionScreen(
                            viewModel = viewModel,
                            onCancel = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        if (prefs.getBoolean(notificationPermissionAskedKey, false)) return

        val alreadyGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            prefs.edit().putBoolean(notificationPermissionAskedKey, true).apply()
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE,
        )
        prefs.edit().putBoolean(notificationPermissionAskedKey, true).apply()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2001
    }
}
