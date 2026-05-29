package com.example.database

import kotlinx.coroutines.flow.Flow

class PlatformRepository(private val db: PlatformDatabase) {
    val userDao = db.userDao()
    val deviceDao = db.deviceDao()
    val telemetryDao = db.telemetryDao()
    val evidenceDao = db.evidenceDao()
    val recoveryEventDao = db.recoveryEventDao()
    val settingsDao = db.settingsDao()
    val chatMessageDao = db.chatMessageDao()

    // Observable flows
    val currentUser: Flow<UserEntity?> = userDao.getUserFlow()
    val currentDevice: Flow<DeviceEntity?> = deviceDao.getDeviceFlow()
    val allTelemetry: Flow<List<TelemetryEntity>> = telemetryDao.getAllTelemetryFlow()
    val allEvidence: Flow<List<EvidenceEntity>> = evidenceDao.getAllEvidenceFlow()
    val allEvents: Flow<List<RecoveryEventEntity>> = recoveryEventDao.getAllEventsFlow()
    val allMessages: Flow<List<ChatMessageEntity>> = chatMessageDao.getAllMessagesFlow()

    // Users
    suspend fun getUserSync(): UserEntity? = userDao.getUserSync()
    suspend fun saveUser(user: UserEntity) = userDao.insertUser(user)
    suspend fun clearUser() = userDao.clearUser()

    // Device
    suspend fun getDeviceSync(): DeviceEntity? = deviceDao.getDeviceSync()
    suspend fun saveDevice(device: DeviceEntity) = deviceDao.registerDevice(device)
    suspend fun setLostMode(isLost: Boolean) = deviceDao.setLostMode(isLost)
    suspend fun clearDevice() = deviceDao.clearDevice()

    // Telemetry
    suspend fun getPendingTelemetry() = telemetryDao.getPendingTelemetry()
    suspend fun insertTelemetry(telemetry: TelemetryEntity) = telemetryDao.insertTelemetry(telemetry)
    suspend fun markTelemetryUploaded(id: Int) = telemetryDao.markTelemetryUploaded(id)

    // Evidence
    suspend fun getPendingEvidence() = evidenceDao.getPendingEvidence()
    suspend fun insertEvidence(evidence: EvidenceEntity) = evidenceDao.insertEvidence(evidence)
    suspend fun markEvidenceUploaded(id: Int) = evidenceDao.markEvidenceUploaded(id)

    // Events
    suspend fun getPendingEvents() = recoveryEventDao.getPendingEvents()
    suspend fun insertEvent(event: RecoveryEventEntity) = recoveryEventDao.insertEvent(event)
    suspend fun markEventUploaded(id: Int) = recoveryEventDao.markEventUploaded(id)

    // Settings
    suspend fun getSetting(key: String): String? = settingsDao.getSetting(key)?.value
    suspend fun saveSetting(key: String, value: String) = settingsDao.insertSetting(SettingsEntity(key, value))
    suspend fun deleteSetting(key: String) = settingsDao.deleteSetting(key)

    // Helper functions for consent storage
    suspend fun getConsentGranted(consentType: String): Boolean {
        return getSetting("consent_$consentType") == "true"
    }

    suspend fun saveConsentGranted(consentType: String, granted: Boolean) {
        saveSetting("consent_$consentType", granted.toString())
    }

    // Chat Messages
    suspend fun insertChatMessage(message: ChatMessageEntity) = chatMessageDao.insertMessage(message)
    suspend fun clearChatHistory() = chatMessageDao.clearHistory()
}
