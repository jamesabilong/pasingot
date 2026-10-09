package app.personal.workouttracker.shared.voice

import org.junit.Assert.*
import org.junit.Test

class PhoneVoiceCueGateTest {
    private fun request(owner: String = "owner", key: String = "session|0|REST") = PhoneVoiceCueRequest(
        requestId = "session", cueKey = key, owner = owner, operation = PhoneVoiceOperation.PROBE)
    @Test fun boundPeerAndSingleClaim() {
        val gate = PhoneVoiceCueGate().apply { arm("session", "watch") }
        val probe = request()
        assertFalse(gate.probe("other", probe, "wrong", 0))
        assertTrue(gate.probe("watch", probe, "ticket", 0))
        val speech = probe.copy(operation = PhoneVoiceOperation.SPEAK, ticket = "ticket", text = "Rest")
        assertTrue(gate.claim("watch", speech, 200))
        assertFalse(gate.claim("watch", speech, 300))
        assertFalse(gate.probe("watch", request("new-owner"), "retry", 400))
    }
    @Test fun cancellationBeforeDelayedSpeechAndExpiredTicketStaySilent() {
        val gate = PhoneVoiceCueGate().apply { arm("session", "watch") }
        val probe = request()
        assertTrue(gate.probe("watch", probe, "ticket", 0))
        assertTrue(gate.cancel("watch", probe))
        assertFalse(gate.claim("watch", probe.copy(ticket = "ticket"), 100))
        assertFalse(gate.probe("watch", probe, "late", 100))
        val fresh = request("fresh", "session|1|GO")
        assertTrue(gate.probe("watch", fresh, "new", 100))
        assertFalse(gate.claim("watch", fresh.copy(ticket = "new"), 100 + PHONE_VOICE_TICKET_MILLIS + 1))
    }
    @Test fun retiredSessionAndForeignOwnerCannotUseTicket() {
        val gate = PhoneVoiceCueGate().apply { arm("session", "watch") }
        val probe = request()
        assertTrue(gate.probe("watch", probe, "ticket", 0))
        assertFalse(gate.claim("watch", probe.copy(owner = "other", ticket = "ticket"), 1))
        assertTrue(gate.probe("watch", probe, "second", 10))
        gate.retire("session")
        assertFalse(gate.claim("watch", probe.copy(ticket = "second"), 11))
    }
    @Test fun malformedOrUnboundCueIsRejected() {
        assertNull(decodePhoneVoiceRequest(encodePhoneVoiceRequest(request().copy(cueKey = "another|0"))))
        assertNull(decodePhoneVoiceRequest(encodePhoneVoiceRequest(request().copy(version = 2))))
        assertNull(decodePhoneVoiceRequest(ByteArray(4_097)))
        assertEquals(request(), decodePhoneVoiceRequest(encodePhoneVoiceRequest(request())))
    }
}
