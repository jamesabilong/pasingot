package app.personal.workouttracker.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import app.personal.workouttracker.MainActivity
import app.personal.workouttracker.R
import app.personal.workouttracker.shared.quickstart.QUICK_START_CLOCK_SKEW_MILLIS
import app.personal.workouttracker.shared.quickstart.QUICK_START_TTL_MILLIS
import app.personal.workouttracker.shared.voice.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.util.UUID
import kotlinx.coroutines.*

/** Armed by foreground Send, never by an incoming background message or boot. */
class PhoneVoiceCueService : Service(), MessageClient.RpcService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val gate = PhoneVoiceCueGate()
    private val expiry = mutableMapOf<String, Long>()
    private lateinit var player: PhoneVoiceCuePlayer
    private var speakingOwner: String? = null
    private var speakingRequest: String? = null
    private val expireTask = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            expiry.filterValues { it <= now }.keys.toList().forEach(::retire)
            if (expiry.isNotEmpty()) handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = PhoneVoiceCuePlayer(this)
        live = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Watch voice cues", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(1901, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_workout).setContentTitle("Watch voice cues")
            .setContentText("Workout speech uses phone headphones and briefly pauses media.")
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(0, "Stop voice cues", stop).build())
        Wearable.getMessageClient(this).addRpcService(this, PHONE_VOICE_CUE_PATH)
            .addOnFailureListener { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        val request = intent?.getStringExtra("requestId") ?: run { stopSelf(); return START_NOT_STICKY }
        val node = intent.getStringExtra("watchNodeId") ?: run { stopSelf(); return START_NOT_STICKY }
        gate.arm(request, node)
        expiry.putIfAbsent(request, SystemClock.elapsedRealtime() + intent.getLongExtra("pendingMillis", 1_000))
        handler.removeCallbacks(expireTask)
        handler.postDelayed(expireTask, 5_000)
        return START_NOT_STICKY
    }

    override fun onRequest(nodeId: String, path: String, bytes: ByteArray): Task<ByteArray>? {
        if (path != PHONE_VOICE_CUE_PATH) return null
        val completion = TaskCompletionSource<ByteArray>()
        scope.launch {
            val request = decodePhoneVoiceRequest(bytes)
            var response = PhoneVoiceCueResponse(PhoneVoiceStatus.DECLINED)
            if (request != null) {
                when (request.operation) {
                    PhoneVoiceOperation.PROBE -> {
                        val ticket = UUID.randomUUID().toString()
                        if (player.available() && gate.probe(nodeId, request, ticket, SystemClock.elapsedRealtime()))
                            response = PhoneVoiceCueResponse(PhoneVoiceStatus.READY, ticket)
                    }
                    PhoneVoiceOperation.CANCEL -> {
                        if (gate.cancel(nodeId, request) && speakingOwner == request.owner) player.cancel()
                    }
                    PhoneVoiceOperation.SPEAK -> {
                        val text = request.text
                        if (!text.isNullOrBlank() && player.available() &&
                            gate.claim(nodeId, request, SystemClock.elapsedRealtime())) {
                            // Once accepted, ambiguous transport must never trigger local replay.
                            speakingOwner = request.owner
                            speakingRequest = request.requestId
                            android.util.Log.d("PhoneVoiceCue", "Accepted ${request.cueKey}")
                            val spoken = withTimeoutOrNull(4_500) {
                                player.speak(request.owner, text)
                            } ?: false
                            if (speakingOwner == request.owner) {
                                speakingOwner = null
                                speakingRequest = null
                            }
                            response = PhoneVoiceCueResponse(if (spoken) PhoneVoiceStatus.SPOKEN
                                else PhoneVoiceStatus.OWNED_SILENT)
                            android.util.Log.d("PhoneVoiceCue", "${response.status}: ${request.cueKey}")
                        }
                    }
                }
            }
            completion.trySetResult(encodePhoneVoiceResponse(response))
        }
        return completion.task
    }

    private fun retire(requestId: String) {
        gate.retire(requestId)
        expiry.remove(requestId)
        if (speakingRequest == requestId) player.cancel()
        if (expiry.isEmpty()) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (live === this) live = null
        handler.removeCallbacksAndMessages(null)
        Wearable.getMessageClient(this).removeRpcService(this)
        scope.cancel()
        player.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "watch_voice_cues"
        private const val STOP = "app.personal.workouttracker.STOP_WATCH_VOICE"
        @Volatile private var live: PhoneVoiceCueService? = null

        /** Called only from the user-initiated Send path. Failure keeps watch fallback. */
        fun arm(context: Context, requestId: String, watchNodeId: String, expiresAtMillis: Long) {
            try {
                if (!PhoneVoiceCuePlayer.hasPhoneHeadphones(
                        context.getSystemService(android.media.AudioManager::class.java))) return
                context.startForegroundService(Intent(context, PhoneVoiceCueService::class.java)
                    .putExtra("requestId", requestId).putExtra("watchNodeId", watchNodeId)
                    .putExtra("pendingMillis", (expiresAtMillis - System.currentTimeMillis() +
                        QUICK_START_CLOCK_SKEW_MILLIS).coerceIn(1_000L,
                        QUICK_START_TTL_MILLIS + QUICK_START_CLOCK_SKEW_MILLIS)))
            } catch (error: Exception) {
                android.util.Log.w("PhoneVoiceCue", "Phone speech unavailable; watch keeps local cues", error)
            }
        }
        fun started(requestId: String) { live?.let { service -> service.handler.post {
            if (requestId in service.expiry)
                service.expiry[requestId] = SystemClock.elapsedRealtime() + 2 * 60 * 60_000L
        } } }
        fun terminal(requestId: String, graceMillis: Long = 0) {
            live?.let { service -> service.handler.postDelayed({ service.retire(requestId) }, graceMillis) }
        }
    }
}
