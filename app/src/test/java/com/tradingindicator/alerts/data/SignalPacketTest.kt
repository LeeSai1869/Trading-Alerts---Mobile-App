package com.tradingindicator.alerts.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SignalPacketTest {
    @Test fun senderPacketUsesOuterOpportunityEdges() {
        val p = JSONObject("""{"seq":7,"created":1700000000,"symbol":"BTCUSDT","interval":"5m","details":{"price":100,"upper":{"low":110,"high":120},"lower":{"low":80,"high":90}}}""")
        val item = SignalPacket.parse(p, "alice")
        assertEquals("7:alice", item.id)
        assertEquals(120.0, item.upperEdge!!, 0.0)
        assertEquals(80.0, item.lowerEdge!!, 0.0)
        assertEquals(1700000000000L, item.receivedAt)
    }
    @Test fun directionSupportsSenderNameAndMessageAndStructuredSide() {
        assertEquals("BUY", SignalPacket.direction(JSONObject("""{"name":"BUY"}"""), JSONObject(), emptyMap()))
        assertEquals("SELL", SignalPacket.direction(JSONObject("""{"message":"BTCUSDT 5m SELL · 100"}"""), JSONObject(), emptyMap()))
        assertEquals("BUY", SignalPacket.direction(JSONObject("""{"name":"SELL"}"""), JSONObject("""{"side":"BUY"}"""), emptyMap()))
    }
    @Test fun signalTimeTakesPrecedenceOverSendTime() {
        val item = SignalPacket.parse(JSONObject("""{"created":1700000005000,"details":{"signal_ms":1700000000000}}"""), "alice")
        assertEquals(1700000000000L, item.receivedAt)
    }
    @Test fun missingDataStaysUnknown() {
        val p = JSONObject("""{"seq":8}""")
        val item = SignalPacket.parse(p, "alice")
        assertNull(item.confidence)
        assertNull(item.price)
        assertNull(SignalPacket.direction(p, JSONObject(), emptyMap()))
    }
}
