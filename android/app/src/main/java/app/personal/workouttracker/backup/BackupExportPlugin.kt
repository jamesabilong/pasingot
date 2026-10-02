package app.personal.workouttracker.backup

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Writes only to the document explicitly chosen by the user; no storage permission. */
@CapacitorPlugin(name = "BackupExport")
class BackupExportPlugin : Plugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = AtomicBoolean(false)

    @PluginMethod
    fun saveBackup(call: PluginCall) {
        val content = call.getString("content")
        val fileName = call.getString("fileName")
        if (content == null || fileName == null || !fileName.matches(Regex("[A-Za-z0-9._-]{1,100}\\.json"))) {
            call.reject("Invalid backup file.")
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            call.reject("A backup save is already open.")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            startActivityForResult(call, intent, "backupDestination")
        } catch (error: Exception) {
            inFlight.set(false)
            call.reject("Could not open the save dialog. Try again.", error)
        }
    }

    @ActivityCallback
    private fun backupDestination(call: PluginCall?, result: ActivityResult) {
        if (call == null) { inFlight.set(false); return }
        if (result.resultCode != Activity.RESULT_OK) {
            inFlight.set(false)
            call.resolve(JSObject().put("saved", false))
            return
        }
        val uri = result.data?.data
        val content = call.getString("content")
        if (uri == null || content == null) {
            inFlight.set(false)
            call.reject("No backup destination was returned. Try again.")
            return
        }
        scope.launch {
            try {
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("No document output stream")
                output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                call.resolve(JSObject().put("saved", true))
            } catch (error: Exception) {
                call.reject("Could not save backup. Choose another location and try again.", error)
            } finally {
                inFlight.set(false)
            }
        }
    }

    override fun handleOnDestroy() {
        scope.cancel()
        super.handleOnDestroy()
    }
}
