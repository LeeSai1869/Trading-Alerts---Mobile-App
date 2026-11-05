package com.tradingindicator.alerts.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SignalMessage::class, NotificationReceipt::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalDao(): SignalDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE signal_messages ADD COLUMN direction TEXT")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS notification_receipts (signalKey TEXT NOT NULL, recordedAt INTEGER NOT NULL, PRIMARY KEY(signalKey))")
            }
        }
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                ctx.applicationContext, AppDatabase::class.java, "trading_alerts.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
        }
    }
}
