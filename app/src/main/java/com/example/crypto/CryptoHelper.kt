package com.example.crypto

import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128 // bits

    // Generates a random AES-256 secret key
    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE)
        return keyGen.generateKey()
    }

    // Encrypts plaintext using AES-GCM. Returns (IV + Ciphertext) in Base64
    fun encryptAES(plainText: ByteArray, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText)
        
        // Combine IV and Ciphertext
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    // Decrypts Base64 combined (IV + Ciphertext) using AES-GCM
    fun decryptAES(encryptedBase64: String, secretKey: SecretKey): ByteArray {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, iv.size)
        
        val cipherTextSize = combined.size - iv.size
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(combined, iv.size, cipherText, 0, cipherTextSize)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(cipherText)
    }

    // Generates an RSA-2048 Public/Private KeyPair
    fun generateRSAKeyPair(): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        return keyPairGen.generateKeyPair()
    }

    // Wraps an AES secret key using an RSA Public Key
    fun wrapAESKey(aesKey: SecretKey, rsaPublicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.WRAP_MODE, rsaPublicKey)
        val wrappedBytes = cipher.wrap(aesKey)
        return Base64.encodeToString(wrappedBytes, Base64.NO_WRAP)
    }

    // Unwraps an AES secret key using an RSA Private Key
    fun unwrapAESKey(wrappedKeyBase64: String, rsaPrivateKey: PrivateKey): SecretKey {
        val wrappedBytes = Base64.decode(wrappedKeyBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.UNWRAP_MODE, rsaPrivateKey)
        return cipher.unwrap(wrappedBytes, "AES", Cipher.SECRET_KEY) as SecretKey
    }

    // Generates an RSA Signature (SHA256withRSA)
    fun signData(data: ByteArray, privateKey: PrivateKey): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data)
        val sigBytes = signature.sign()
        return Base64.encodeToString(sigBytes, Base64.NO_WRAP)
    }

    // Verifies an RSA Signature
    fun verifySignature(data: ByteArray, signatureBase64: String, publicKey: PublicKey): Boolean {
        return try {
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(data)
            val sigBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            signature.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }
}
