package com.example.evidence

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.crypto.CryptoHelper
import com.example.database.PlatformDatabase
import com.example.database.PlatformRepository
import com.example.database.EvidenceEntity
import com.example.network.NetworkClient
import com.example.network.EvidenceRequest
import java.io.File
import java.io.FileInputStream

class EvidenceUploader(private val context: Context) {

    companion object {
        private const val TAG = "EvidenceUploader"
    }

    private val repository = PlatformRepository(PlatformDatabase.getDatabase(context))

    suspend fun uploadPendingEvidence() {
        val pendingEvidence = repository.getPendingEvidence()
        if (pendingEvidence.isEmpty()) {
            Log.d(TAG, "No pending offline evidence items found.")
            return
        }

        val device = repository.getDeviceSync()
        val deviceHash = device?.deviceHash ?: "UNKNOWN_DEVICE_HASH"

        // Dynamically create or retrieve localized RSA keys for signatures
        val keyPair = CryptoHelper.generateRSAKeyPair()
        val privateKey = keyPair.private
        val publicKey = keyPair.public

        var uploadedCount = 0

        for (evidence in pendingEvidence) {
            try {
                val file = File(evidence.photoPath)
                if (!file.exists()) {
                    Log.e(TAG, "Evidence image missing on disk: ${evidence.photoPath}")
                    continue
                }

                // 1. Read binary image file to byte array
                val imageBytes = ByteArray(file.length().toInt())
                val fis = FileInputStream(file)
                fis.read(imageBytes)
                fis.close()

                // Convert original to Base64 representation
                val rawBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                // 2. Generate a symmetric AES key to encrypt the Base64 image
                val aesKey = CryptoHelper.generateAESKey()
                val encryptedImageBase64 = CryptoHelper.encryptAES(rawBase64.toByteArray(), aesKey)

                // 3. Wrap the AES secret key using RSA Public Key so the server can unlock it
                val wrappedAesKey = CryptoHelper.wrapAESKey(aesKey, publicKey)

                // 4. Digitally sign the encrypted evidence package using the Device's Private RSA Key
                val signature = CryptoHelper.signData(encryptedImageBase64.toByteArray(), privateKey)

                Log.d(TAG, "Secured evidence payload: AES-GCM Encrypted and RSA-2048 Signed.")

                // 5. Retrofit POST
                val response = NetworkClient.apiService.postEvidence(
                    EvidenceRequest(
                        deviceHash = deviceHash,
                        imageBase64 = encryptedImageBase64,
                        timestamp = evidence.timestamp,
                        signature = signature,
                        aesKeyWrapped = wrappedAesKey
                    )
                )

                if (response.isSuccessful) {
                    repository.markEvidenceUploaded(evidence.id)
                    uploadedCount++
                    Log.d(TAG, "Evidence ID: ${evidence.id} uploaded successfully.")
                } else {
                    Log.e(TAG, "Server rejected evidence upload. HTTP Code: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Incurred fatal error during evidence compilation or transport: ${e.message}", e)
            }
        }

        Log.d(TAG, "Uploaded $uploadedCount evidence items.")
    }
}
