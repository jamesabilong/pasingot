package app.personal.workouttracker.shared.voice

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val PHONE_VOICE_CUE_PATH = "/voice-cue/v1"
const val PHONE_VOICE_TICKET_MILLIS = 1_500L
enum class PhoneVoiceOperation { PROBE, SPEAK, CANCEL }
enum class PhoneVoiceStatus { READY, DECLINED, SPOKEN, OWNED_SILENT }

/** Ephemeral RPC only: never persisted or queued as a DataItem. */
@Serializable
data class PhoneVoiceCueRequest(
    val version: Int = 1,
    val requestId: String,
    val cueKey: String,
    val owner: String,
    val operation: PhoneVoiceOperation,
    val ticket: String? = null,
    val text: String? = null,
)

@Serializable
data class PhoneVoiceCueResponse(val status: PhoneVoiceStatus, val ticket: String? = null)

private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun encodePhoneVoiceRequest(value: PhoneVoiceCueRequest): ByteArray = codec.encodeToString(value).toByteArray()
fun decodePhoneVoiceRequest(bytes: ByteArray): PhoneVoiceCueRequest? = try {
    if (bytes.size > 4_096) null else codec.decodeFromString<PhoneVoiceCueRequest>(bytes.toString(Charsets.UTF_8))
        .takeIf { it.version == 1 && it.requestId.length in 1..100 &&
            it.cueKey.length in 1..256 && it.cueKey.startsWith(it.requestId + "|") &&
            it.owner.length in 1..100 && (it.text == null || it.text.length in 1..400) }
} catch (_: Exception) { null }
fun encodePhoneVoiceResponse(value: PhoneVoiceCueResponse): ByteArray = codec.encodeToString(value).toByteArray()
fun decodePhoneVoiceResponse(bytes: ByteArray): PhoneVoiceCueResponse? = try {
    if (bytes.size > 1_024) null else codec.decodeFromString<PhoneVoiceCueResponse>(bytes.toString(Charsets.UTF_8))
} catch (_: Exception) { null }

/** One phone service lifetime. Watch durable cue reservations prevent retries across restarts. */
class PhoneVoiceCueGate {
    private data class Ticket(val node: String, val request: PhoneVoiceCueRequest, val expires: Long)
    private val bindings = mutableMapOf<String, String>()
    private val tickets = mutableMapOf<String, Ticket>()
    private val canceledOwners = mutableSetOf<String>()
    private val claimedKeys = mutableSetOf<String>()

    @Synchronized fun arm(requestId: String, watchNodeId: String) { bindings[requestId] = watchNodeId }
    @Synchronized fun retire(requestId: String) {
        bindings.remove(requestId)
        tickets.entries.removeAll { it.value.request.requestId == requestId }
    }
    @Synchronized fun probe(node: String, request: PhoneVoiceCueRequest, ticket: String, now: Long): Boolean {
        tickets.entries.removeAll { it.value.expires < now }
        if (bindings[request.requestId] != node || request.owner in canceledOwners ||
            request.cueKey in claimedKeys || tickets.size >= 128) return false
        tickets[ticket] = Ticket(node, request, now + PHONE_VOICE_TICKET_MILLIS)
        return true
    }
    @Synchronized fun claim(node: String, request: PhoneVoiceCueRequest, now: Long): Boolean {
        val ticket = tickets.remove(request.ticket) ?: return false
        if (bindings[request.requestId] != node || ticket.node != node || ticket.expires < now ||
            ticket.request.requestId != request.requestId || ticket.request.cueKey != request.cueKey ||
            ticket.request.owner != request.owner || request.owner in canceledOwners ||
            request.cueKey in claimedKeys || claimedKeys.size >= 512) return false
        claimedKeys.add(request.cueKey)
        return true
    }
    @Synchronized fun cancel(node: String, request: PhoneVoiceCueRequest): Boolean {
        if (bindings[request.requestId] != node || canceledOwners.size >= 512) return false
        canceledOwners.add(request.owner)
        tickets.entries.removeAll { it.value.request.owner == request.owner }
        return true
    }
}
