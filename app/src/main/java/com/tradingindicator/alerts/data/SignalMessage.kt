package com.tradingindicator.alerts.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_messages")
data class SignalMessage(
    @PrimaryKey val id: String,
    val owner: String,
    val symbol: String,
    val timeframe: String,
    val price: Double?,
    val confidence: Double?,
    val upperEdge: Double?,
    val lowerEdge: Double?,
    val receivedAt: Long,
    val hidden: Boolean = false
) {
    companion object {
        fun normalizeTimestamp(timestamp: Long): Long =
            if (timestamp in 1 until 100_000_000_000L) timestamp * 1000 else timestamp
    }
}
