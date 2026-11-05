package com.tradingindicator.alerts.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SignalMessage>)

    @Query("SELECT * FROM signal_messages WHERE owner = :owner AND hidden = 0 ORDER BY receivedAt DESC")
    suspend fun forOwner(owner: String): List<SignalMessage>

    @Query("UPDATE signal_messages SET hidden = 1 WHERE owner = :owner")
    suspend fun hideOwner(owner: String)

    @Query("UPDATE signal_messages SET receivedAt = receivedAt * 1000 WHERE receivedAt > 0 AND receivedAt < 100000000000")
    suspend fun normalizeLegacyTimestamps()

    @Query("DELETE FROM signal_messages WHERE receivedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
