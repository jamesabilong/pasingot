package app.personal.workouttracker.wear.cues

import android.content.Context
import app.personal.workouttracker.shared.voice.*
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** Quick Start only. The watch always owns haptics and durable cue admission. */
class PhoneFirstWatchCueOutput(context: Context, requestId: String) : WatchCueOutput by
    RoutedWatchCueOutput(requestId, AndroidTtsCueOutput(context), AndroidPhoneVoiceCueTransport(context))

private class AndroidPhoneVoiceCueTransport(context: Context) : PhoneVoiceCueTransport {
    private val nodes = Wearable.getNodeClient(context)
    private val messages = Wearable.getMessageClient(context)
    override suspend fun node(): String? = nodes.connectedNodes.await().singleOrNull()?.id
    override suspend fun exchange(node: String, request: PhoneVoiceCueRequest): PhoneVoiceCueResponse? =
        decodePhoneVoiceResponse(messages.sendRequest(node, PHONE_VOICE_CUE_PATH,
            encodePhoneVoiceRequest(request)).await())
    override fun cancel(node: String, request: PhoneVoiceCueRequest) {
        // Stop local output even if the paired transport has gone away.
        runCatching { messages.sendRequest(node, PHONE_VOICE_CUE_PATH,
            encodePhoneVoiceRequest(request.copy(operation = PhoneVoiceOperation.CANCEL))) }
    }
}
