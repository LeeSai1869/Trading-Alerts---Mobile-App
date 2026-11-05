package com.tradingindicator.alerts.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SignalMessage>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claimNotification(receipt: NotificationReceipt): Long

    @Query("DELETE FROM notification_receipts WHERE recordedAt < :timestamp")
    suspend fun deleteOldReceipts(timestamp: Long)

    @Query("SELECT * FROM signal_messages WHERE owner = :owner AND hidden = 0 ORDER BY receivedAt DESC")
    suspend fun forOwner(owner: String): List<SignalMessage>

    @Query("SELECT * FROM signal_messages WHERE owner = :owner AND hidden = 0 ORDER BY receivedAt DESC")
    fun observeForOwner(owner: String): Flow<List<SignalMessage>>

    @Query("UPDATE signal_messages SET hidden = 1 WHERE owner = :owner")
    suspend fun hideOwner(owner: String)

    @Query("UPDATE signal_messages SET receivedAt = receivedAt * 1000 WHERE receivedAt > 0 AND receivedAt < 100000000000")
    suspend fun normalizeLegacyTimestamps()

    @Query("DELETE FROM signal_messages WHERE receivedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
