package com.tradingindicator.alerts.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared by the OS notification and the in-app inbox. */
object AlertPresentation {
    fun title(item: SignalMessage, direction: String? = item.direction): String = listOfNotNull(
        direction,
        item.symbol.takeIf { it.isNotBlank() },
        item.timeframe.takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifBlank { "Trading alert" }

    fun lines(item: SignalMessage, direction: String? = item.direction): List<String> {
        fun value(number: Double?): String =
            number?.let { String.format(Locale.getDefault(), "%.4f", it) } ?: "—"
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date(item.receivedAt))
        return listOf(
            "Signal: ${direction ?: "—"}",
            "Symbol: ${item.symbol.ifBlank { "—" }}",
            "Timeframe: ${item.timeframe.ifBlank { "—" }}",
            "Price: ${value(item.price)}",
            "Confidence: ${item.confidence?.let { "${it.toInt()}%" } ?: "—"}",
            "Sell OZ upper: ${value(item.upperEdge)}",
            "Buy OZ lower: ${value(item.lowerEdge)}",
            "Time: $timestamp"
        )
    }
}
