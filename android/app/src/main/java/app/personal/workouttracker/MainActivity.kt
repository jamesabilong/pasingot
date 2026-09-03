package app.personal.workouttracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.personal.workouttracker.health.HealthConnectBridgePlugin
import app.personal.workouttracker.weardata.ScheduleSyncPlugin
import app.personal.workouttracker.weardata.WorkoutLogBridgePlugin
import com.getcapacitor.BridgeActivity

/**
 * Prompt 3 req 4: request POST_NOTIFICATIONS at runtime on first launch
 * (Android 13+ requirement — the permission is declared in the manifest,
 * but must also be requested at runtime or notifications silently no-op).
 *
 * Also registers the custom JS<->native bridge plugins from Prompt 6/8 —
 * unlike npm-installed Capacitor plugins, project-local plugins must be
 * registered explicitly, and it must happen before super.onCreate() since
 * that's where Bridge initialization completes.
 */
class MainActivity : BridgeActivity() {

    private val prefsName = "workout_tracker_prefs"
    private val firstLaunchKey = "notif_permission_requested"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        registerPlugin(ScheduleSyncPlugin::class.java)
        registerPlugin(WorkoutLogBridgePlugin::class.java)
        registerPlugin(HealthConnectBridgePlugin::class.java)
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return // < Android 13, no runtime prompt needed

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        if (prefs.getBoolean(firstLaunchKey, false)) return // already asked once

        val alreadyGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            prefs.edit().putBoolean(firstLaunchKey, true).apply()
            return
        }

        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE
        )
        prefs.edit().putBoolean(firstLaunchKey, true).apply()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
