package app.personal.workouttracker.wear.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.workoutOutcomeDataStore by preferencesDataStore(name = "workout_outcome")

class DataStoreWorkoutOutcomePersistence internal constructor(
    private val dataStore: DataStore<Preferences>,
) : WorkoutOutcomePersistence {
    constructor(context: Context) : this(context.applicationContext.workoutOutcomeDataStore)

    private val key = stringPreferencesKey("workout_outcome_json")

    override suspend fun read(): String? = dataStore.data.first()[key]

    override suspend fun write(raw: String?) {
        dataStore.edit { preferences ->
            if (raw == null) preferences.remove(key) else preferences[key] = raw
        }
    }
}
