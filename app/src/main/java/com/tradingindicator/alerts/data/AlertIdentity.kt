package com.tradingindicator.alerts.data

import org.json.JSONArray
import java.security.MessageDigest

object AlertIdentity {
    fun messageKey(item: SignalMessage): SignalMessage =
        if (item.direction in setOf("BUY", "SELL") && item.symbol.isNotBlank() &&
            item.timeframe.isNotBlank() && item.receivedAt > 0) item.copy(id = "", hidden = false)
        else item.copy(hidden = false)

    fun notificationKey(item: SignalMessage): String {
        val key = messageKey(item)
        val payload = JSONArray(listOf(key.owner, key.id, key.symbol, key.timeframe,
            key.direction, key.receivedAt, key.price, key.confidence, key.upperEdge, key.lowerEdge)).toString()
        return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
