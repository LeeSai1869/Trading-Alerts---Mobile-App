package com.tradingindicator.alerts.data

import org.json.JSONObject
import java.util.Locale

object SignalPacket {
    fun parse(p: JSONObject, owner: String): SignalMessage {
        val details = p.optJSONObject("details") ?: JSONObject()
        fun num(o: JSONObject, k: String): Double? =
            if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeIf { it.isFinite() } else null
        return SignalMessage(
            id = p.optLong("seq", System.currentTimeMillis()).toString() + ":" + owner,
            owner = owner,
            direction = direction(p, details, emptyMap()),
            symbol = p.optString("symbol", ""),
            timeframe = p.optString("interval", ""),
            price = num(details, "price"),
            confidence = num(details, "confidence"),
            upperEdge = details.optJSONObject("upper")?.let { num(it, "high") },
            lowerEdge = details.optJSONObject("lower")?.let { num(it, "low") },
            receivedAt = SignalMessage.normalizeTimestamp(details.optLong("signal_ms", p.optLong("created", System.currentTimeMillis())))
        )
    }
    fun direction(event: JSONObject, details: JSONObject, data: Map<String, String>): String? {
        val keys = listOf(
            "direction", "side", "signal", "signal_type", "signalType",
            "trade_type", "tradeType", "order_side", "action", "type", "bias", "trend", "name", "message"
        )
        val candidates = buildList {
            keys.forEach { key ->
                if (event.has(key) && !event.isNull(key)) add(event.optString(key))
                if (details.has(key) && !details.isNull(key)) add(details.optString(key))
                data[key]?.let(::add)
            }
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            candidate.uppercase(Locale.ROOT)
                .split(Regex("[^A-Z]+"))
                .firstNotNullOfOrNull { token ->
                    when (token) {
                        "BUY", "LONG", "BULLISH" -> "BUY"
                        "SELL", "SHORT", "BEARISH" -> "SELL"
                        else -> null
                    }
                }
            }
    }
}
