package app.personal.workouttracker.health

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.kilograms
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
    private val exercisePermission = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    private val bodyWeightPermission = HealthPermission.getWritePermission(WeightRecord::class)
    private val permissions = setOf(exercisePermission, bodyWeightPermission)

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
            val opened = openHealthConnectInstaller()
            call.resolve(JSObject().apply {
                put("opened", opened)
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
                val client = availablePermittedClient(call, exercisePermission) ?: return@launch

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
                call.resolve(healthMutationResult("written", true))
            } catch (e: Exception) {
                call.reject("Failed to write Health Connect workout: ${e.message}", e)
            }
        }
    }

    @PluginMethod
    fun writeBodyWeight(call: PluginCall) {
        val timeText = call.getString("time")
        val weightKilograms = call.getDouble("kilograms")
        if (timeText.isNullOrBlank() || weightKilograms == null) {
            call.reject("Missing required time or kilograms")
            return
        }
        if (!weightKilograms.isFinite() || weightKilograms <= 0.0 || weightKilograms > 700.0) {
            call.reject("Body weight must be between 0 and 700 kilograms")
            return
        }

        pluginScope.launch {
            try {
                val client = availablePermittedClient(call, bodyWeightPermission) ?: return@launch
                val time = Instant.parse(timeText)
                val clientRecordId = call.getString("clientRecordId") ?: "pasingot:body-weight:$timeText"
                val record = WeightRecord(
                    time = time,
                    zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
                    weight = weightKilograms.kilograms,
                    metadata = Metadata.activelyRecorded(
                        clientRecordId = clientRecordId,
                        clientRecordVersion = call.getLong("clientRecordVersion") ?: 1L,
                        device = Device(type = Device.TYPE_PHONE)
                    )
                )

                withContext(Dispatchers.IO) {
                    client.insertRecords(listOf(record))
                }
                call.resolve(healthMutationResult("written", true))
            } catch (e: Exception) {
                call.reject("Failed to write Health Connect body weight: ${e.message}", e)
            }
        }
    }

    @PluginMethod
    fun deleteBodyWeight(call: PluginCall) {
        val clientRecordId = call.getString("clientRecordId")
        if (clientRecordId.isNullOrBlank()) {
            call.reject("Missing required clientRecordId")
            return
        }

        pluginScope.launch {
            try {
                val client = availablePermittedClient(call, bodyWeightPermission, resultKey = "deleted") ?: return@launch
                withContext(Dispatchers.IO) {
                    client.deleteRecords(
                        recordType = WeightRecord::class,
                        recordIdsList = emptyList(),
                        clientRecordIdsList = listOf(clientRecordId)
                    )
                }
                call.resolve(healthMutationResult("deleted", true))
            } catch (e: Exception) {
                call.reject("Failed to delete Health Connect body weight: ${e.message}", e)
            }
        }
    }

    private suspend fun availablePermittedClient(
        call: PluginCall,
        requiredPermission: String,
        resultKey: String = "written"
    ): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            call.resolve(healthMutationResult(resultKey, false, availabilityLabel(status), false))
            return null
        }

        val client = HealthConnectClient.getOrCreate(context)
        val granted = withContext(Dispatchers.IO) {
            client.permissionController.getGrantedPermissions().contains(requiredPermission)
        }
        if (!granted) {
            call.resolve(healthMutationResult(resultKey, false, "available", false))
            return null
        }
        return client
    }

    private fun healthMutationResult(
        resultKey: String,
        completed: Boolean,
        availability: String = "available",
        permissionGranted: Boolean = true
    ) = JSObject().apply {
        put(resultKey, completed)
        put("availability", availability)
        put("permissionGranted", permissionGranted)
    }

    private suspend fun buildStatus(): JSObject {
        val status = HealthConnectClient.getSdkStatus(context)
        val grantedPermissions = if (status == HealthConnectClient.SDK_AVAILABLE) {
            try {
                withContext(Dispatchers.IO) {
                    HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
                }
            } catch (_: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }
        val workoutPermissionGranted = grantedPermissions.contains(exercisePermission)
        val bodyWeightPermissionGranted = grantedPermissions.contains(bodyWeightPermission)
        return JSObject().apply {
            put("availability", availabilityLabel(status))
            put("permissionGranted", workoutPermissionGranted && bodyWeightPermissionGranted)
            put("workoutPermissionGranted", workoutPermissionGranted)
            put("bodyWeightPermissionGranted", bodyWeightPermissionGranted)
        }
    }

    private fun openHealthConnectInstaller(): Boolean {
        val providerPackageName = HEALTH_CONNECT_PROVIDER_PACKAGE_NAME
        val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse(uriString)
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
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
