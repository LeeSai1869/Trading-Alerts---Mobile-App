package com.tradingindicator.alerts.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_receipts")
data class NotificationReceipt(@PrimaryKey val signalKey: String, val recordedAt: Long)
