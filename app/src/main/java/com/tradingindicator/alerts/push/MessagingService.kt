package com.tradingindicator.alerts.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tradingindicator.alerts.data.AppDatabase
import com.tradingindicator.alerts.data.Prefs
import com.tradingindicator.alerts.data.SignalMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.Locale

class MessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        val owner = d["owner"] ?: Prefs(this).username ?: return
        val eventJson = d["event"] ?: return
        val p = runCatching { JSONObject(eventJson) }.getOrNull() ?: return
        val details = p.optJSONObject("details") ?: JSONObject()
        val direction = extractDirection(p, details, d)
        fun num(o: JSONObject, k: String): Double? =
            if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeIf { !it.isNaN() } else null
        val item = SignalMessage(
            id = p.optLong("seq", System.currentTimeMillis()).toString() + ":" + owner,
            owner = owner,
            symbol = p.optString("symbol", ""),
            timeframe = p.optString("interval", ""),
            price = num(details, "price"),
            confidence = num(details, "confidence"),
            upperEdge = details.optJSONObject("upper")?.let { num(it, "low") },
            lowerEdge = details.optJSONObject("lower")?.let { num(it, "high") },
            receivedAt = SignalMessage.normalizeTimestamp(p.optLong("created", System.currentTimeMillis()))
        )
        runBlocking { AppDatabase.get(this@MessagingService).signalDao().insertAll(listOf(item)) }
        val prefs = Prefs(this)
        val sound = d["sound"] != "false" && prefs.soundOn
        if (prefs.notificationsOn) AlertNotifier.show(this, item, direction, sound)
    }

    override fun onNewToken(token: String) {
        val p = Prefs(this)
        if (p.baseUrl.isBlank() || p.cookie == null) return
        try {
            val api = com.tradingindicator.alerts.net.ApiClient(p.baseUrl, p.cookie)
            api.registerToken(token, "com.tradingindicator.alerts", p.notificationsOn, p.soundOn)
        } catch (_: Exception) {}
    }

    private fun extractDirection(event: JSONObject, details: JSONObject, data: Map<String, String>): String? {
        val keys = listOf(
            "direction", "side", "signal", "signal_type", "signalType",
            "trade_type", "tradeType", "order_side", "action", "type", "bias", "trend"
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
