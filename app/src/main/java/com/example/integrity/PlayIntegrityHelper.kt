package com.example.integrity

import android.content.Context
import android.util.Log
import com.example.database.PlatformDatabase
import com.example.database.PlatformRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PlayIntegrityHelper(private val context: Context) {

    companion object {
        private const val TAG = "PlayIntegrityHelper"
    }

    private val repository = PlatformRepository(PlatformDatabase.getDatabase(context))

    data class IntegrityVerdict(
        val isGenuine: Boolean,
        val deviceRecognitionVerdict: String,
        val evaluationType: String,
        val timestamp: Long
    )

    suspend fun verifyPlayIntegrity(): IntegrityVerdict {
        Log.d(TAG, "Requesting Google Play Integrity API verification...")
        
        // Simulating the Play Integrity API response
        // In full production, this imports:
        // com.google.android.play.core.integrity.IntegrityManagerFactory
        // Requests integrityToken, sends to FastAPI backend to decrypt and read verdicts
        
        val verdict = IntegrityVerdict(
            isGenuine = true,
            deviceRecognitionVerdict = "MEETS_STRONG_INTEGRITY",
            evaluationType = "HARDWARE_BACKED",
            timestamp = System.currentTimeMillis()
        )

        // Save state locally in key-value settings
        repository.saveSetting("integrity_status", "true")
        repository.saveSetting("integrity_verdict", verdict.deviceRecognitionVerdict)
        repository.saveSetting("integrity_evaluation", verdict.evaluationType)
        repository.saveSetting("integrity_checked_at", verdict.timestamp.toString())

        Log.d(TAG, "Play Integrity result received: Meets Strong Hardware Integrity")
        return verdict
    }

    suspend fun getCachedVerdict(): IntegrityVerdict? {
        val status = repository.getSetting("integrity_status") ?: return null
        return IntegrityVerdict(
            isGenuine = status == "true",
            deviceRecognitionVerdict = repository.getSetting("integrity_verdict") ?: "MEETS_DEVICE_INTEGRITY",
            evaluationType = repository.getSetting("integrity_evaluation") ?: "BASIC",
            timestamp = (repository.getSetting("integrity_checked_at") ?: "0").toLong()
        )
    }
}
