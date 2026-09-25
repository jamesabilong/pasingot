package app.personal.workouttracker.wear.quickstart

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.quickStartPackageDataStore by preferencesDataStore(name = "quick_start_package")

class DataStoreQuickStartPackagePersistence(
    private val context: Context,
) : QuickStartPackagePersistence {
    private val key = stringPreferencesKey("quick_start_package_json")

    override suspend fun read(): String? = context.quickStartPackageDataStore.data.first()[key]

    override suspend fun write(raw: String?) {
        context.quickStartPackageDataStore.edit { preferences ->
            if (raw == null) preferences.remove(key) else preferences[key] = raw
        }
    }
}
