package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun getUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUserSync(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clearUser()
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices LIMIT 1")
    fun getDeviceFlow(): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices LIMIT 1")
    suspend fun getDeviceSync(): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registerDevice(device: DeviceEntity)

    @Query("UPDATE devices SET isLostMode = :isLost")
    suspend fun setLostMode(isLost: Boolean)

    @Query("DELETE FROM devices")
    suspend fun clearDevice()
}

@Dao
interface TelemetryDao {
    @Query("SELECT * FROM telemetry ORDER BY timestamp DESC")
    fun getAllTelemetryFlow(): Flow<List<TelemetryEntity>>

    @Query("SELECT * FROM telemetry WHERE isUploaded = 0")
    suspend fun getPendingTelemetry(): List<TelemetryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetry(telemetry: TelemetryEntity)

    @Query("UPDATE telemetry SET isUploaded = 1 WHERE id = :id")
    suspend fun markTelemetryUploaded(id: Int)

    @Query("DELETE FROM telemetry")
    suspend fun clearAllTelemetry()
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence ORDER BY timestamp DESC")
    fun getAllEvidenceFlow(): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence WHERE isUploaded = 0")
    suspend fun getPendingEvidence(): List<EvidenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceEntity)

    @Query("UPDATE evidence SET isUploaded = 1 WHERE id = :id")
    suspend fun markEvidenceUploaded(id: Int)

    @Query("DELETE FROM evidence")
    suspend fun clearAllEvidence()
}

@Dao
interface RecoveryEventDao {
    @Query("SELECT * FROM recovery_events ORDER BY timestamp DESC")
    fun getAllEventsFlow(): Flow<List<RecoveryEventEntity>>

    @Query("SELECT * FROM recovery_events WHERE isUploaded = 0")
    suspend fun getPendingEvents(): List<RecoveryEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: RecoveryEventEntity)

    @Query("UPDATE recovery_events SET isUploaded = 1 WHERE id = :id")
    suspend fun markEventUploaded(id: Int)

    @Query("DELETE FROM recovery_events")
    suspend fun clearAllEvents()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingsEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

