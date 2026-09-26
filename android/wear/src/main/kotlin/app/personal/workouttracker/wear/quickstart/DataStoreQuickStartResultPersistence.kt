package app.personal.workouttracker.wear.quickstart

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.quickStartResultDataStore by preferencesDataStore(name = "quick_start_result")

class DataStoreQuickStartResultPersistence internal constructor(
    private val dataStore: DataStore<Preferences>,
) : QuickStartResultPersistence {
    constructor(context: Context) : this(context.applicationContext.quickStartResultDataStore)

    private val key = stringPreferencesKey("quick_start_result_json")

    override suspend fun read(): String? = dataStore.data.first()[key]

    override suspend fun write(raw: String?) {
        dataStore.edit { preferences ->
            if (raw == null) preferences.remove(key) else preferences[key] = raw
        }
    }
}
