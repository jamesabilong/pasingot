package app.personal.workouttracker.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "wear_settings")

data class ScheduledDownloadTime(val hour: Int, val minute: Int) {
    companion object {
        val DEFAULT = ScheduledDownloadTime(hour = 6, minute = 30)
    }
}

/** Backs the "auto-download at HH:MM daily" setting (Prompt 5 req 2). */
class SettingsRepository(private val context: Context) {

    private val hourKey = intPreferencesKey("scheduled_download_hour")
    private val minuteKey = intPreferencesKey("scheduled_download_minute")

    val scheduledTime: Flow<ScheduledDownloadTime> = context.settingsDataStore.data.map { prefs ->
        ScheduledDownloadTime(
            hour = prefs[hourKey] ?: ScheduledDownloadTime.DEFAULT.hour,
            minute = prefs[minuteKey] ?: ScheduledDownloadTime.DEFAULT.minute,
        )
    }

    suspend fun setScheduledTime(time: ScheduledDownloadTime) {
        context.settingsDataStore.edit { prefs ->
            prefs[hourKey] = time.hour
            prefs[minuteKey] = time.minute
        }
    }
}
