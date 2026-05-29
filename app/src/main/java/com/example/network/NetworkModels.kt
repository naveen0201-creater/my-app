package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val phone: String,
    @Json(name = "device_hash") val deviceHash: String,
    val consent: String
)

@JsonClass(generateAdapter = true)
data class RegisterResponse(
    val status: String,
    val message: String,
    @Json(name = "owner_id") val ownerId: String,
    val token: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val code: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val token: String,
    @Json(name = "owner_id") val ownerId: String
)

@JsonClass(generateAdapter = true)
data class DeviceRegisterRequest(
    @Json(name = "device_hash") val deviceHash: String,
    @Json(name = "registered_at") val registeredAt: Long,
    @Json(name = "owner_id") val ownerId: String,
    val consent: String
)

@JsonClass(generateAdapter = true)
data class DeviceRegisterResponse(
    val status: String,
    @Json(name = "device_hash") val deviceHash: String
)

@JsonClass(generateAdapter = true)
data class TelemetryRequest(
    @Json(name = "device_hash") val deviceHash: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val battery: Int,
    val network: String,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class TelemetryResponse(
    val status: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class EvidenceRequest(
    @Json(name = "device_hash") val deviceHash: String,
    @Json(name = "image_base64") val imageBase64: String,
    val timestamp: Long,
    val signature: String,
    @Json(name = "aes_key_wrapped") val aesKeyWrapped: String
)

@JsonClass(generateAdapter = true)
data class EvidenceResponse(
    val status: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class LostModeRequest(
    @Json(name = "device_hash") val deviceHash: String,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class LostModeResponse(
    val status: String,
    val message: String
)
