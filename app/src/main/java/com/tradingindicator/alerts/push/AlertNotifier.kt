package com.tradingindicator.alerts.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import com.tradingindicator.alerts.MainActivity
import com.tradingindicator.alerts.data.Prefs
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.tradingindicator.alerts.data.SignalMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlertNotifier {
    private fun customSound(context: Context): String? = Prefs(context).customSoundUri?.takeIf { value ->
            runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(value), "r")?.use { true } ?: false
            }.getOrDefault(false)
        }

    fun soundChannelId(context: Context): String {
        val custom = customSound(context) ?: return "alerts_sound_v2"
        return "alerts_custom_${java.security.MessageDigest.getInstance("SHA-256").digest(custom.toByteArray()).take(8).joinToString("") { "%02x".format(it) }}"
    }

    fun show(context: Context, item: SignalMessage, direction: String?, sound: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        val soundUri = customSound(context)?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channelId = if (sound) soundChannelId(context) else "alerts_silent"
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                if (sound) "Alerts (sound)" else "Alerts (silent)",
                NotificationManager.IMPORTANCE_HIGH
            )
            if (sound) {
                channel.setSound(
                    soundUri,
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
            "Sell OZ upper: ${value(item.upperEdge)}",
            "Buy OZ lower: ${value(item.lowerEdge)}",
            "Time: $timestamp"
        )
        val style = NotificationCompat.BigTextStyle()
            .setBigContentTitle(title)
            .bigText(lines.joinToString("\n"))

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Price: ${value(item.price)} · Confidence: ${item.confidence?.let { "${it.toInt()}%" } ?: "—"}")
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setStyle(style)
            .setWhen(item.receivedAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .build()
        manager.notify(item.id.hashCode(), notification)
    }
}
