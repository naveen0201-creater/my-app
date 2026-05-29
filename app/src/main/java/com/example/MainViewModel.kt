package com.example

import android.app.Application
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.RecoveryCameraManager
import com.example.crypto.CryptoHelper
import com.example.database.*
import com.example.evidence.EvidenceUploader
import com.example.integrity.PlayIntegrityHelper
import com.example.lostmode.LostModeManager
import com.example.network.*
import com.example.telemetry.TelemetryService
import com.example.telemetry.TelemetryWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = PlatformDatabase.getDatabase(context)
    val repository = PlatformRepository(db)
    
    private val lostModeManager = LostModeManager(context)
    private val playIntegrityHelper = PlayIntegrityHelper(context)
    private val evidenceUploader = EvidenceUploader(context)

    // UI Backing Streams
    val currentUser: StateFlow<UserEntity?> = repository.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val currentDevice: StateFlow<DeviceEntity?> = repository.currentDevice.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val allTelemetry: StateFlow<List<TelemetryEntity>> = repository.allTelemetry.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allEvidence: StateFlow<List<EvidenceEntity>> = repository.allEvidence.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allEvents: StateFlow<List<RecoveryEventEntity>> = repository.allEvents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.allMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isChatGenerating = MutableStateFlow(false)
    val isChatGenerating: StateFlow<Boolean> = _isChatGenerating.asStateFlow()

    // Navigation and screen flows
    private val _currentScreen = MutableStateFlow("splash")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Consents Screen tracking
    private val _locationConsent = MutableStateFlow(false)
    val locationConsent = _locationConsent.asStateFlow()

    private val _evidenceConsent = MutableStateFlow(false)
    val evidenceConsent = _evidenceConsent.asStateFlow()

    private val _lostModeConsent = MutableStateFlow(false)
    val lostModeConsent = _lostModeConsent.asStateFlow()

    private val _privacyConsent = MutableStateFlow(false)
    val privacyConsent = _privacyConsent.asStateFlow()

    // Generic status and alerts
    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs = _terminalLogs.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    init {
        loadServerUrl()
        loadConsents()
        logTrace("System initialized. Model Version: API ${Build.VERSION.SDK_INT}")
        
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    val onboardingCompleted = repository.getSetting("onboarding_completed") == "true"
                    if (onboardingCompleted) {
                        navigateTo("dashboard")
                    } else {
                        navigateTo("onboarding")
                    }
                }
            }
        }
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
        logTrace("Navigated to screen: '$screen'")
    }

    fun logTrace(message: String) {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timestamp = formatter.format(Date())
        _terminalLogs.update { listOf("[$timestamp] $message") + it.take(49) }
        Log.d("MainViewModel", "Audit: $message")
    }

    private fun loadServerUrl() {
        viewModelScope.launch {
            val savedUrl = repository.getSetting("backend_server_url")
            if (savedUrl != null) {
                NetworkClient.updateBaseUrl(savedUrl)
                logTrace("Loaded backend API: $savedUrl")
            }
        }
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            repository.saveSetting("backend_server_url", url)
            NetworkClient.updateBaseUrl(url)
            logTrace("Backend configuration modified to: $url")
        }
    }

    private fun loadConsents() {
        viewModelScope.launch {
            _locationConsent.value = repository.getConsentGranted("location")
            _evidenceConsent.value = repository.getConsentGranted("evidence")
            _lostModeConsent.value = repository.getConsentGranted("lostmode")
            _privacyConsent.value = repository.getConsentGranted("privacy")
        }
    }

    fun saveConsent(type: String, granted: Boolean) {
        viewModelScope.launch {
            repository.saveConsentGranted(type, granted)
            loadConsents()
            logTrace("Consent status: '$type' set to ${if (granted) "GRANTED" else "DENIED"}")
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.saveSetting("onboarding_completed", "true")
            navigateTo("dashboard")
            logTrace("Consent onboarding successfully compiled and locked.")
        }
    }

    // Hash method for identity creation
    private fun hashSHA256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { String.format("%02x", it) }
    }

    // Combined register user & binding device identity to avoid individual steps
    fun performRegisterAndRegisterDevice(email: String, phone: String) {
        if (email.isEmpty() || phone.isEmpty()) {
            logTrace("Validation Error: Email/Phone fields cannot be empty.")
            return
        }
        
        _isProcessing.value = true
        logTrace("Opening lawful secure registration agent...")

        viewModelScope.launch {
            try {
                // Generate secure hashed device ID
                val rawDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "FALLBACK_ID"
                val deviceHash = hashSHA256(rawDeviceId)
                val ownerId = "owner_" + hashSHA256(email).take(12)
                val token = "token_smrep_" + hashSHA256(phone + email).take(20)

                logTrace("Computed Identity: SHA-256 Hash $deviceHash")

                // Locally save user and device
                val user = UserEntity(
                    id = ownerId,
                    email = email,
                    phone = phone,
                    token = token,
                    registeredAt = System.currentTimeMillis()
                )
                repository.saveUser(user)

                val device = DeviceEntity(
                    deviceHash = deviceHash,
                    registeredAt = System.currentTimeMillis(),
                    ownerId = ownerId,
                    isLostMode = false
                )
                repository.saveDevice(device)

                logTrace("Credential record injected locally in Room database.")

                // Call mockup / network service
                val registerRequest = RegisterRequest(
                    email = email,
                    phone = phone,
                    deviceHash = deviceHash,
                    consent = "LOCATION,EVIDENCE,LOSTMODE"
                )
                
                logTrace("Synchronizing authentication with host backend API...")
                val response = NetworkClient.apiService.register(registerRequest)
                if (response.isSuccessful) {
                    logTrace("Server Integration: SUCCESS - Device binder synchronised.")
                } else {
                    logTrace("Server responded: ${response.code()} (Fallback Offline Auth Activated)")
                }

                _isProcessing.value = false
                val onboardingCompleted = repository.getSetting("onboarding_completed") == "true"
                if (onboardingCompleted) {
                    navigateTo("dashboard")
                } else {
                    navigateTo("onboarding")
                }

            } catch (e: Exception) {
                logTrace("Auth transport completed as offline register: ${e.message}")
                _isProcessing.value = false
                navigateTo("onboarding")
            }
        }
    }

    fun performLogin(email: String) {
        if (email.isEmpty()) {
            logTrace("Validation Error: Email must not be empty.")
            return
        }
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val mockResponse = NetworkClient.apiService.login(LoginRequest(email, "7737"))
                if (mockResponse.isSuccessful && mockResponse.body() != null) {
                    val body = mockResponse.body()!!
                    logTrace("Remote session opened. Token: ${body.token}")
                }
                
                // Fallback / standard state search in local database
                val existingUser = repository.getUserSync()
                if (existingUser != null && existingUser.email == email) {
                    logTrace("Login successful for local user record: $email")
                    _isProcessing.value = false
                    navigateTo("dashboard")
                } else {
                    logTrace("No local record matching '$email'. Running registration.")
                    _isProcessing.value = false
                    navigateTo("register")
                }
            } catch (e: Exception) {
                logTrace("Offline Login Override.")
                val existingUser = repository.getUserSync()
                if (existingUser != null) {
                    logTrace("Login successful (Offline cache).")
                    _isProcessing.value = false
                    navigateTo("dashboard")
                } else {
                    _isProcessing.value = false
                    navigateTo("register")
                }
            }
        }
    }

    // Toggle Lost Mode
    fun toggleLostMode(isEnabled: Boolean) {
        viewModelScope.launch {
            repository.setLostMode(isEnabled)
            val device = repository.getDeviceSync()
            if (device != null) {
                logTrace("Lost Mode status changed to: ${if (isEnabled) "ACTIVE" else "INACTIVE"}")

                // Sync with server
                try {
                    val response = if (isEnabled) {
                         NetworkClient.apiService.enableLostMode(LostModeRequest(device.deviceHash, "Requested by owner"))
                    } else {
                         NetworkClient.apiService.disableLostMode(LostModeRequest(device.deviceHash, "Restored by owner"))
                    }
                    if (response.isSuccessful) {
                        logTrace("Server Sync: Saved Lost Mode configuration successfully.")
                    }
                } catch (e: Exception) {
                    logTrace("Network offline. Saved Lost Mode setting locally.")
                }

                // If Lost Mode is turned ON, trigger immediate recovery telemetry service
                if (isEnabled) {
                    TelemetryService.start(context)
                    TelemetryWorker.schedule(context)
                    
                    // Audit trigger engine
                    lostModeManager.auditDeviceRecoveryTriggers()
                } else {
                    TelemetryService.stop(context)
                }
            }
        }
    }

    // Perform audit triggers scan manually
    fun triggerManualAudit() {
        viewModelScope.launch {
            logTrace("Executing user-forced recovery audit...")
            lostModeManager.auditDeviceRecoveryTriggers()
            logTrace("Audit complete. Any irregularities have been committed.")
        }
    }

    // Trigger local simulation of SIM state change
    fun simulateSIMSwap() {
        viewModelScope.launch {
            logTrace("Simulating user SIM alteration events...")
            val currentCached = repository.getSetting("cached_sim_state") ?: "READY"
            val nextSim = if (currentCached == "READY") "ABSENT" else "READY"
            repository.saveSetting("cached_sim_state", nextSim)
            
            // Create event
            val event = RecoveryEventEntity(
                eventType = "SIM_CHANGED",
                timestamp = System.currentTimeMillis(),
                description = "Simulation Trigger: SIM state swapped from $currentCached to $nextSim"
            )
            repository.insertEvent(event)
            logTrace("Recovery Event Generated: SIM_CHANGED.")
            
            // Trigger Camera snapshot if consent granted
            if (repository.getConsentGranted("evidence")) {
                logTrace("Automated Snapshot Authorized. Triggering CameraX...")
                // Snapshot handled in Activity because of lifecycle binding
            } else {
                logTrace("Snapshot skipped: Camera consent was not granted by owner.")
            }
        }
    }

    // Trigger Play Integrity validation
    fun requestIntegrityCheck() {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val verdict = playIntegrityHelper.verifyPlayIntegrity()
                logTrace("Play Integrity: ${verdict.deviceRecognitionVerdict} (${verdict.evaluationType})")
            } catch (e: Exception) {
                logTrace("Play Integrity call failed: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Record custom camera evidence snapshot pathway
    fun saveCameraEvidence(photoFile: File) {
        viewModelScope.launch {
            try {
                // Read / simulate RSA & AES encryption parameters
                val signatureBase = "recovery_photo_${System.currentTimeMillis()}"
                val keyPair = CryptoHelper.generateRSAKeyPair()
                val signature = CryptoHelper.signData(signatureBase.toByteArray(), keyPair.private)
                val aesKey = CryptoHelper.generateAESKey()
                val wrappedKey = CryptoHelper.wrapAESKey(aesKey, keyPair.public)

                val evidence = EvidenceEntity(
                    photoPath = photoFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    isUploaded = false,
                    signature = signature,
                    aesKeyWrapped = wrappedKey
                )
                repository.insertEvidence(evidence)
                logTrace("Evidence saved to database: ${photoFile.name}")

                // Upload Immediately
                evidenceUploader.uploadPendingEvidence()
            } catch (e: Exception) {
                logTrace("Failed to process captured evidence: ${e.message}")
            }
        }
    }

    fun resetAppDatabase() {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                db.clearAllTables()
                repository.clearUser()
                repository.clearDevice()
                repository.telemetryDao.clearAllTelemetry()
                repository.evidenceDao.clearAllEvidence()
                repository.recoveryEventDao.clearAllEvents()
                
                // Clear settings
                repository.deleteSetting("onboarding_completed")
                repository.deleteSetting("consent_location")
                repository.deleteSetting("consent_evidence")
                repository.deleteSetting("consent_lostmode")
                repository.deleteSetting("consent_privacy")
                
                _locationConsent.value = false
                _evidenceConsent.value = false
                _lostModeConsent.value = false
                _privacyConsent.value = false

                logTrace("Offline database entirely wiped.")
                _isProcessing.value = false
                navigateTo("register")
            } catch (e: Exception) {
                logTrace("Database reset failed: ${e.message}")
                _isProcessing.value = false
            }
        }
    }
    
    fun simulateTelemetryUpload() {
        viewModelScope.launch {
            logTrace("Manually forcing periodic telemetry run...")
            val device = repository.getDeviceSync()
            if (device == null) {
                logTrace("Error: No registered device bound.")
                return@launch
            }
            
            // Insert current simulated
            val telemetry = TelemetryEntity(
                lat = 37.7749 + (Math.random() - 0.5) * 0.01,
                lon = -122.4194 + (Math.random() - 0.5) * 0.01,
                accuracy = 12.5f,
                battery = 85,
                network = "WiFi",
                timestamp = System.currentTimeMillis()
            )
            repository.insertTelemetry(telemetry)
            
            // Upload queue
            val pending = repository.getPendingTelemetry()
            var count = 0
            for (tel in pending) {
                try {
                    val response = NetworkClient.apiService.postTelemetry(
                        TelemetryRequest(
                            deviceHash = device.deviceHash,
                            lat = tel.lat,
                            lon = tel.lon,
                            accuracy = tel.accuracy,
                            battery = tel.battery,
                            network = tel.network,
                            timestamp = tel.timestamp
                        )
                    )
                    if (response.isSuccessful) {
                        repository.markTelemetryUploaded(tel.id)
                        count++
                    }
                } catch (e: Exception) {
                    // Fail gracefully
                }
            }
            logTrace("Manual telemetry sync done. Saved: 1 | Uploaded: $count")
        }
    }

    fun sendChatMessage(messageContent: String) {
        if (messageContent.trim().isEmpty()) return
        
        viewModelScope.launch {
            try {
                logTrace("Drafting secure support query packet.")
                
                // 1. Save user message to DB
                val userMsg = ChatMessageEntity(
                    sender = "user",
                    message = messageContent,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertChatMessage(userMsg)
                
                _isChatGenerating.value = true
                
                // 2. Load latest history for Gemini context window
                // Note that chatMessages.value contains all messages from flow.
                val history = chatMessages.value
                
                // 3. Request AI reasoning response
                val aiResponseText = GeminiManager.chatWithGemini(history)
                
                // 4. Save AI response to DB
                val aiMsg = ChatMessageEntity(
                    sender = "agent",
                    message = aiResponseText,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertChatMessage(aiMsg)
                
            } catch (e: Exception) {
                logTrace("Chat error: ${e.message}")
            } finally {
                _isChatGenerating.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
            logTrace("Secure support session history purged.")
        }
    }
}
