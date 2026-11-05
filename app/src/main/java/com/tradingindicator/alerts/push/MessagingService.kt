package com.tradingindicator.alerts.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tradingindicator.alerts.data.AppDatabase
import com.tradingindicator.alerts.data.Prefs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class MessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        val prefs = Prefs(this)
        val owner = prefs.username ?: return
        if (d["owner"] != null && d["owner"] != owner) return
        val eventJson = d["event"] ?: return
        val p = runCatching { JSONObject(eventJson) }.getOrNull() ?: return
        val direction = com.tradingindicator.alerts.data.SignalPacket.direction(p, p.optJSONObject("details") ?: JSONObject(), d)
        val item = com.tradingindicator.alerts.data.SignalPacket.parse(p, owner).copy(direction = direction)
        android.util.Log.i("AlertDelivery", "FCM received: priority=${msg.priority}, transportDelayMs=${if (msg.sentTime > 0) System.currentTimeMillis() - msg.sentTime else -1}, eventAgeMs=${System.currentTimeMillis() - item.receivedAt}")
        runBlocking { AppDatabase.get(this@MessagingService).signalDao().insertAll(listOf(item)) }
        if (!com.tradingindicator.alerts.data.NotificationPolicy.isFresh(item, System.currentTimeMillis())) {
            android.util.Log.i("AlertDelivery", "History saved; notification suppressed because signal is over one minute old")
            return
        }
        if (item.symbol in prefs.mutedSymbols || item.timeframe in prefs.mutedTimeframes) return
        val sound = d["sound"] != "false" && prefs.soundOn
        if (prefs.notificationsOn) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!manager.areNotificationsEnabled()) return
            val key = com.tradingindicator.alerts.data.AlertIdentity.notificationKey(item)
            val firstDelivery = runBlocking {
                val dao = AppDatabase.get(this@MessagingService).signalDao()
                dao.deleteOldReceipts(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
                dao.claimNotification(com.tradingindicator.alerts.data.NotificationReceipt(key, System.currentTimeMillis())) != -1L
            }
            if (firstDelivery) AlertNotifier.show(this, item, direction, sound)
            else android.util.Log.i("AlertDelivery", "Repeated signal saved; duplicate notification suppressed")
        }
    }

    override fun onNewToken(token: String) {
        val p = Prefs(this)
        if (p.baseUrl.isBlank() || p.cookie == null) return
        try {
            val api = com.tradingindicator.alerts.net.ApiClient(p.baseUrl, p.cookie)
            api.registerToken(token, "com.tradingindicator.alerts", p.notificationsOn, p.soundOn)
        } catch (e: Exception) { android.util.Log.w("PushSetup", "Token refresh registration failed", e) }
    }

}
