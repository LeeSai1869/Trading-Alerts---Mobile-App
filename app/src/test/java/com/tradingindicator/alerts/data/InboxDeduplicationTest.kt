package com.tradingindicator.alerts.data

import org.junit.Assert.*
import org.junit.Test

class InboxDeduplicationTest {
    private val signal = SignalMessage("1:alice", "alice", "BTCUSDT", "5m", 100.0,
        92.0, 120.0, 80.0, 1700000000000L, direction = "BUY")

    @Test fun identicalSignalsWithDifferentServerIdsShowOnce() {
        val input = listOf(signal, signal.copy(id = "2:alice"))
        assertEquals(listOf(signal), AlertPresentation.uniqueItems(input))
        assertEquals(2, input.size) // No deletion or mutation of saved history.
    }

    @Test fun separateCandlesAndDifferentSignalDetailsStaySeparate() {
        val input = listOf(signal,
            signal.copy(id = "2:alice", receivedAt = signal.receivedAt + 300000),
            signal.copy(id = "3:alice", direction = "SELL"),
            signal.copy(id = "4:bob", owner = "bob"),
            signal.copy(id = "5:alice", price = 101.0),
            signal.copy(id = "6:alice", timeframe = "15m"),
            signal.copy(id = "7:alice", confidence = 80.0),
            signal.copy(id = "8:alice", upperEdge = 125.0),
            signal.copy(id = "9:alice", lowerEdge = 75.0),
            signal.copy(id = "10:alice", symbol = "ETHUSDT"))
        assertEquals(input, AlertPresentation.uniqueItems(input))
    }

    @Test fun incompleteEventsAreNotMergedAcrossDifferentIds() {
        for (item in listOf(signal.copy(direction = null), signal.copy(symbol = ""),
            signal.copy(timeframe = ""), signal.copy(receivedAt = 0))) {
            val input = listOf(item, item.copy(id = "2:alice"))
            assertEquals(input, AlertPresentation.uniqueItems(input))
        }
    }
}
