package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        DeviceEntity::class,
        TelemetryEntity::class,
        EvidenceEntity::class,
        RecoveryEventEntity::class,
        SettingsEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PlatformDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun deviceDao(): DeviceDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun recoveryEventDao(): RecoveryEventDao
    abstract fun settingsDao(): SettingsDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: PlatformDatabase? = null

        fun getDatabase(context: Context): PlatformDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlatformDatabase::class.java,
                    "smrep_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
