package app.personal.workouttracker.wear.cues

import app.personal.workouttracker.shared.voice.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

interface PhoneVoiceCueTransport {
    suspend fun node(): String?
    suspend fun exchange(node: String, request: PhoneVoiceCueRequest): PhoneVoiceCueResponse?
    fun cancel(node: String, request: PhoneVoiceCueRequest)
}

/** Live routing with cancellation fencing before every possible local fallback. */
class RoutedWatchCueOutput(
    private val requestId: String,
    private val local: WatchCueOutput,
    private val transport: PhoneVoiceCueTransport,
) : WatchCueOutput {
    private data class Active(val node: String, val request: PhoneVoiceCueRequest)
    private val generation = AtomicLong()
    @Volatile private var active: Active? = null
    override val talkBackEnabled: Boolean get() = local.talkBackEnabled
    override fun haptic(kind: WatchCueKind) = local.haptic(kind)

    override suspend fun speak(utteranceId: String, text: String): Boolean {
        val version = generation.get()
        val node = try { withTimeoutOrNull(500) { transport.node() } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        if (generation.get() != version) return false
        if (node == null) return local.speak(utteranceId, text)
        val probe = PhoneVoiceCueRequest(requestId = requestId, cueKey = utteranceId,
            owner = UUID.randomUUID().toString(), operation = PhoneVoiceOperation.PROBE)
        val lease = Active(node, probe)
        active = lease
        try {
            val available = try { withTimeoutOrNull(650) { transport.exchange(node, probe) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (active !== lease || generation.get() != version) return false
            if (available?.status != PhoneVoiceStatus.READY || available.ticket == null) {
                active = null
                return local.speak(utteranceId, text)
            }
            val speech = probe.copy(operation = PhoneVoiceOperation.SPEAK, ticket = available.ticket, text = text)
            // After dispatch, an unknown result is silent. Local replay could duplicate speech.
            val result = try { transport.exchange(node, speech) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (active !== lease || generation.get() != version) return false
            return when (result?.status) {
                PhoneVoiceStatus.SPOKEN -> true
                PhoneVoiceStatus.DECLINED -> local.speak(utteranceId, text)
                else -> false
            }
        } catch (error: CancellationException) {
            transport.cancel(lease.node, lease.request)
            throw error
        } finally { if (active === lease) active = null }
    }

    override fun cancel(reason: WatchCueCancellation) {
        generation.incrementAndGet()
        val lease = active
        active = null
        if (lease != null) transport.cancel(lease.node, lease.request)
        local.cancel(reason)
    }
    override fun close() { cancel(WatchCueCancellation.NAVIGATION); local.close() }
}
