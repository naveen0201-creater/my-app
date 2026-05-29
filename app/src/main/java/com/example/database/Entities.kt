package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val phone: String,
    val token: String,
    val registeredAt: Long
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceHash: String,
    val registeredAt: Long,
    val ownerId: String,
    val isLostMode: Boolean = false
)

@Entity(tableName = "telemetry")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val battery: Int,
    val network: String,
    val timestamp: Long,
    val isUploaded: Boolean = false
)

@Entity(tableName = "evidence")
data class EvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val photoPath: String,
    val timestamp: Long,
    val isUploaded: Boolean = false,
    val signature: String,
    val aesKeyWrapped: String = ""
)

@Entity(tableName = "recovery_events")
data class RecoveryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val eventType: String, // SIM_CHANGED, DEVICE_OFFLINE, AIRPLANE_MODE, LOCATION_DISABLED etc.
    val timestamp: Long,
    val description: String,
    val isUploaded: Boolean = false
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "user" or "agent"
    val message: String,
    val timestamp: Long
)

