package app.personal.workouttracker.health

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

@CapacitorPlugin(name = "HealthConnectBridge")
class HealthConnectBridgePlugin : Plugin() {

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val permissions = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

    @PluginMethod
    fun getStatus(call: PluginCall) {
        pluginScope.launch {
            call.resolve(buildStatus())
        }
    }

    @PluginMethod
    fun requestHealthConnectPermissions(call: PluginCall) {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            openHealthConnectInstaller()
            call.resolve(JSObject().apply {
                put("opened", true)
                put("availability", "provider_update_required")
            })
            return
        }
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            call.resolve(JSObject().apply {
                put("opened", false)
                put("availability", availabilityLabel(status))
            })
            return
        }

        try {
            val intent = PermissionController.createRequestPermissionResultContract().createIntent(context, permissions)
            activity.startActivity(intent)
            call.resolve(JSObject().apply {
                put("opened", true)
                put("availability", "available")
            })
        } catch (e: ActivityNotFoundException) {
            call.reject("Health Connect permission screen is unavailable.", e)
        } catch (e: Exception) {
            call.reject("Failed to open Health Connect permissions: ${e.message}", e)
        }
    }

    @PluginMethod
    fun writeWorkoutSession(call: PluginCall) {
        val startTimeText = call.getString("startTime")
        val endTimeText = call.getString("endTime")
        if (startTimeText.isNullOrBlank() || endTimeText.isNullOrBlank()) {
            call.reject("Missing required startTime or endTime")
            return
        }

        pluginScope.launch {
            try {
                val status = HealthConnectClient.getSdkStatus(context)
                if (status != HealthConnectClient.SDK_AVAILABLE) {
                    call.resolve(JSObject().apply {
                        put("written", false)
                        put("availability", availabilityLabel(status))
                        put("permissionGranted", false)
                    })
                    return@launch
                }

                val client = HealthConnectClient.getOrCreate(context)
                val granted = withContext(Dispatchers.IO) {
                    client.permissionController.getGrantedPermissions().containsAll(permissions)
                }
                if (!granted) {
                    call.resolve(JSObject().apply {
                        put("written", false)
                        put("availability", "available")
                        put("permissionGranted", false)
                    })
                    return@launch
                }

                val startTime = Instant.parse(startTimeText)
                val endTime = Instant.parse(endTimeText)
                if (!endTime.isAfter(startTime)) {
                    call.reject("Workout endTime must be after startTime")
                    return@launch
                }

                val zoneRules = ZoneId.systemDefault().rules
                val clientRecordId = call.getString("clientRecordId") ?: "pasingot:${startTimeText}:${endTimeText}"
                val title = call.getString("title") ?: "Workout"
                val notes = call.getString("notes")
                val record = ExerciseSessionRecord(
                    startTime = startTime,
                    startZoneOffset = zoneRules.getOffset(startTime),
                    endTime = endTime,
                    endZoneOffset = zoneRules.getOffset(endTime),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    title = title,
                    notes = notes,
                    metadata = Metadata.activelyRecorded(
                        clientRecordId = clientRecordId,
                        clientRecordVersion = call.getLong("clientRecordVersion") ?: 1L,
                        device = Device(type = Device.TYPE_PHONE)
                    )
                )

                withContext(Dispatchers.IO) {
                    client.insertRecords(listOf(record))
                }
                call.resolve(JSObject().apply {
                    put("written", true)
                    put("availability", "available")
                    put("permissionGranted", true)
                })
            } catch (e: Exception) {
                call.reject("Failed to write Health Connect workout: ${e.message}", e)
            }
        }
    }

    private suspend fun buildStatus(): JSObject {
        val status = HealthConnectClient.getSdkStatus(context)
        val permissionGranted = if (status == HealthConnectClient.SDK_AVAILABLE) {
            try {
                withContext(Dispatchers.IO) {
                    HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
                }
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        return JSObject().apply {
            put("availability", availabilityLabel(status))
            put("permissionGranted", permissionGranted)
        }
    }

    private fun openHealthConnectInstaller() {
        val providerPackageName = HEALTH_CONNECT_PROVIDER_PACKAGE_NAME
        val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse(uriString)
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
        activity.startActivity(intent)
    }

    private fun availabilityLabel(status: Int): String = when (status) {
        HealthConnectClient.SDK_AVAILABLE -> "available"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "provider_update_required"
        else -> "unavailable"
    }

    companion object {
        private const val HEALTH_CONNECT_PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
    }
}
