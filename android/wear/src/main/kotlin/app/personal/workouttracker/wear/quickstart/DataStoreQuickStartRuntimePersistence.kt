package app.personal.workouttracker.wear.quickstart

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.quickStartRuntimeDataStore by preferencesDataStore(name = "quick_start_runtime")

class DataStoreQuickStartRuntimePersistence internal constructor(
    private val dataStore: DataStore<Preferences>,
) : QuickStartRuntimePersistence {
    constructor(context: Context) : this(context.applicationContext.quickStartRuntimeDataStore)

    private val key = stringPreferencesKey("quick_start_runtime_json")

    override suspend fun read(): String? = dataStore.data.first()[key]

    override suspend fun write(raw: String) {
        dataStore.edit { it[key] = raw }
    }
}
