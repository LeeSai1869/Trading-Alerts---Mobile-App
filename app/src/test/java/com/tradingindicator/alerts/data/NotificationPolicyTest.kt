package com.tradingindicator.alerts.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    private val now = 1700001000000L
    private fun signal(timestamp: Long) = SignalMessage("1:alice", "alice", "BTCUSDT", "5m", 100.0,
        null, null, null, timestamp, direction = "BUY")

    @Test fun currentAndOneMinuteBoundarySignalsNotify() {
        assertTrue(NotificationPolicy.isFresh(signal(now), now))
        assertTrue(NotificationPolicy.isFresh(signal(now - 60000), now))
    }
    @Test fun staleAndInvalidSignalsDoNotNotify() {
        assertFalse(NotificationPolicy.isFresh(signal(now - 60001), now))
        assertFalse(NotificationPolicy.isFresh(signal(0), now))
        assertFalse(NotificationPolicy.isFresh(signal(now - 240000), now))
    }
    @Test fun newEventForOldSignalDoesNotNotify() {
        val item = SignalPacket.parse(JSONObject("""{"seq":1,"created":$now,"details":{"signal_ms":${now - 3600000}}}"""), "alice")
        assertFalse(NotificationPolicy.isFresh(item, now))
    }
}
