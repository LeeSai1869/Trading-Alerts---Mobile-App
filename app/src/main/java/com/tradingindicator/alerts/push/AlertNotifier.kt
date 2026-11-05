package com.tradingindicator.alerts.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.tradingindicator.alerts.data.SignalMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlertNotifier {
    fun show(context: Context, item: SignalMessage, direction: String?, sound: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = if (sound) "alerts_sound_v2" else "alerts_silent"
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                if (sound) "Alerts (sound)" else "Alerts (silent)",
                NotificationManager.IMPORTANCE_HIGH
            )
            if (sound) {
                channel.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                channel.setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
            .format(Date(item.receivedAt))
        fun value(number: Double?): String =
            number?.let { String.format(Locale.getDefault(), "%.4f", it) } ?: "—"
        val title = listOfNotNull(
            direction,
            item.symbol.takeIf { it.isNotBlank() },
            item.timeframe.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { "Trading alert" }
        val lines = listOf(
            "Signal: ${direction ?: "—"}",
            "Symbol: ${item.symbol.ifBlank { "—" }}",
            "Timeframe: ${item.timeframe.ifBlank { "—" }}",
            "Price: ${value(item.price)}",
            "Confidence: ${item.confidence?.let { "${it.toInt()}%" } ?: "—"}",
            "Upper level: ${value(item.upperEdge)}",
            "Lower level: ${value(item.lowerEdge)}",
            "Time: $timestamp"
        )
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(timestamp)
        lines.forEach(style::addLine)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setWhen(item.receivedAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .build()
        manager.notify(item.id.hashCode(), notification)
    }
}
