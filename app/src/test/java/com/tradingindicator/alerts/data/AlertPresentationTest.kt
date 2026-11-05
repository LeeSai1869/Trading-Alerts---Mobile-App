package com.tradingindicator.alerts.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AlertPresentationTest {
    @Test fun inboxAndNotificationUseFullSenderDetails() {
        val item = SignalPacket.parse(JSONObject("""{"seq":1,"symbol":"BTCUSDT","interval":"5m","created":1700000000000,"details":{"side":"BUY","price":100,"confidence":92,"upper":{"low":110,"high":120},"lower":{"low":80,"high":90}}}"""), "alice")
        assertEquals("BUY", item.direction)
        assertEquals("BUY · BTCUSDT · 5m", AlertPresentation.title(item))
        val lines = AlertPresentation.lines(item)
        assertEquals(8, lines.size)
        assertEquals("Signal: BUY", lines[0])
        assertEquals("Symbol: BTCUSDT", lines[1])
        assertEquals("Timeframe: 5m", lines[2])
        assertEquals("Confidence: 92%", lines[4])
        assertTrue(lines[5].startsWith("Sell OZ upper: 120"))
        assertTrue(lines[6].startsWith("Buy OZ lower: 80"))
        assertTrue(lines[7].startsWith("Time: "))
    }
    @Test fun legacyOrMissingFieldsRemainUnknown() {
        val item = SignalPacket.parse(JSONObject("""{"seq":2}"""), "alice")
        assertEquals("Trading alert", AlertPresentation.title(item))
        assertTrue(AlertPresentation.lines(item).contains("Signal: —"))
        assertTrue(AlertPresentation.lines(item).contains("Confidence: —"))
        assertTrue(AlertPresentation.lines(item).contains("Price: —"))
    }
}
