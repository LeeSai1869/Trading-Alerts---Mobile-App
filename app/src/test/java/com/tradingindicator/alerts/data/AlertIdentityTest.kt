package com.tradingindicator.alerts.data

import org.junit.Assert.*
import org.junit.Test

class AlertIdentityTest {
    private val item = SignalMessage("1:alice", "alice", "BTCUSDT", "5m", 100.0, 90.0, 110.0, 80.0, 1700000000000L, direction = "BUY")
    @Test fun duplicateDeliveriesSharePersistentIdentity() {
        assertEquals(AlertIdentity.notificationKey(item), AlertIdentity.notificationKey(item.copy(id = "2:alice")))
        assertEquals(64, AlertIdentity.notificationKey(item).length)
    }
    @Test fun changedSignalOrAccountUsesSeparateIdentity() {
        for (other in listOf(item.copy(owner = "bob"), item.copy(receivedAt = item.receivedAt + 300000),
            item.copy(direction = "SELL"), item.copy(timeframe = "15m"), item.copy(price = 101.0))) {
            assertNotEquals(AlertIdentity.notificationKey(item), AlertIdentity.notificationKey(other))
        }
    }
    @Test fun unknownDirectionUsesEventIdentity() {
        val unknown = item.copy(direction = null)
        assertNotEquals(AlertIdentity.notificationKey(unknown), AlertIdentity.notificationKey(unknown.copy(id = "2:alice")))
    }
}
