package app.personal.workouttracker.quickstart

import android.util.Log
import app.personal.workouttracker.shared.quickstart.QuickStartRequest
import app.personal.workouttracker.voice.PhoneVoiceCueService
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@CapacitorPlugin(name = "WatchQuickStart")
class WatchQuickStartPlugin : Plugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val store by lazy { QuickStartPhoneStore(DataStoreQuickStartPhonePersistence(context)) }
    private val client by lazy { WatchQuickStartClient(context) }
    private val recoveryMutex = Mutex()

    override fun load() {
        super.load()
        QuickStartPhoneEvents.onChange = { requestId ->
            scope.launch {
                val acknowledgement = store.current(requestId)?.acknowledgement ?: return@launch
                notifyListeners("quickStartStatus", JSObject(json.encodeToString(acknowledgement)))
            }
        }
        // Retry durable decisions against actual retained transport items. Historical
        // receipts alone must not recreate already-consumed Data Items.
        recoverTransport()
    }

    override fun handleOnResume() {
        super.handleOnResume()
        recoverTransport()
    }

    private fun recoverTransport() {
        scope.launch {
            recoveryMutex.withLock {
                try { client.publishCapability() }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { Log.w("QuickStartRecovery", "Could not publish capability", error) }
                try {
                    QuickStartPhoneTransportRecovery(store, client,
                        onFailure = { requestId, error ->
                            Log.w("QuickStartRecovery", "Could not reconcile request $requestId", error)
                        },
                        onImported = QuickStartPhoneEvents::publish,
                    ).reconcile()
                }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { Log.w("QuickStartRecovery", "Could not read transport state", error) }
            }
        }
    }

    @PluginMethod fun getAvailability(call: PluginCall) {
        scope.launch {
            try {
                val result = client.availability()
                call.resolve(JSObject().apply {
                    when (result) {
                        is WatchQuickStartAvailability.Available -> {
                            put("available", true); put("watchNodeId", result.watchNodeId)
                        }
                        is WatchQuickStartAvailability.Unavailable -> {
                            put("available", false); put("reason", result.reason)
                        }
                    }
                })
            } catch (error: Exception) { call.reject(error.message ?: "Could not check watch", error) }
        }
    }

    @PluginMethod fun sendQuickStart(call: PluginCall) {
        val raw = call.getObject("request")?.toString() ?: run {
            call.reject("Missing Quick Start request"); return
        }
        val request = try { json.decodeFromString<QuickStartRequest>(raw) }
        catch (error: SerializationException) { call.reject("Invalid Quick Start request", error); return }
        catch (error: IllegalArgumentException) { call.reject("Invalid Quick Start request", error); return }
        scope.launch {
            try {
                // Validate capability before storing an offer the watch cannot receive.
                val available = client.availability() as? WatchQuickStartAvailability.Available
                    ?: throw IllegalStateException("Compatible watch not available")
                require(available.watchNodeId == request.targetNodeId) { "Watch selection changed" }
                store.saveRequest(request, System.currentTimeMillis())
                PhoneVoiceCueService.arm(context, request.requestId, request.targetNodeId, request.expiresAtMillis)
                client.send(request)
                val accepted = store.markTransportAccepted(request.requestId, System.currentTimeMillis())
                call.resolve(JSObject().apply {
                    put("requestId", request.requestId)
                    put("transportAcceptedAtMillis", accepted.transportAcceptedAtMillis)
                })
            } catch (error: Exception) { call.reject(error.message ?: "Could not send to watch", error) }
        }
    }

    @PluginMethod fun getQuickStartStatus(call: PluginCall) {
        val requestId = call.getString("requestId") ?: run { call.reject("Missing request ID"); return }
        scope.launch {
            try {
                val record = store.current(requestId) ?: run { call.reject("Unknown request ID"); return@launch }
                call.resolve(JSObject().apply {
                    put("requestId", requestId)
                    put("transportAcceptedAtMillis", record.transportAcceptedAtMillis)
                    put("acknowledgement", record.acknowledgement?.let { JSObject(json.encodeToString(it)) })
                    put("cancellation", record.cancellation?.let { JSObject(json.encodeToString(it)) })
                    put("finalResult", record.finalResult?.let { JSObject(json.encodeToString(it)) })
                    put("resultReceipt", record.resultReceipt?.let { JSObject(json.encodeToString(it)) })
                })
            } catch (error: Exception) { call.reject(error.message ?: "Could not load status", error) }
        }
    }

    @PluginMethod fun cancelQuickStart(call: PluginCall) {
        val requestId = call.getString("requestId") ?: run { call.reject("Missing request ID"); return }
        scope.launch {
            try {
                val localNodeId = com.google.android.gms.wearable.Wearable
                    .getNodeClient(context).localNode.await().id
                val cancellation = store.prepareCancellation(
                    requestId, localNodeId, System.currentTimeMillis(),
                )
                client.sendCancellation(cancellation)
                PhoneVoiceCueService.terminal(requestId)
                call.resolve(JSObject(json.encodeToString(cancellation)))
            } catch (error: Exception) {
                call.reject(error.message ?: "Could not cancel Quick Start", error)
            }
        }
    }

    @PluginMethod fun getLatestQuickStart(call: PluginCall) {
        scope.launch {
            try {
                call.resolve(JSObject().apply {
                    put("record", store.latest()?.let { JSObject(json.encodeToString(it)) })
                })
            } catch (error: Exception) { call.reject(error.message ?: "Could not load latest offer", error) }
        }
    }

    override fun handleOnDestroy() {
        QuickStartPhoneEvents.onChange = null
        scope.cancel()
        super.handleOnDestroy()
    }
}
