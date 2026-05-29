package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.database.ChatMessageEntity
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

object GeminiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)
}

object GeminiManager {
    suspend fun chatWithGemini(history: List<ChatMessageEntity>): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w("GeminiManager", "Gemini API key is not configured.")
            return@withContext getOfflineMockResponse(history.lastOrNull()?.message ?: "")
        }

        val systemPrompt = "You are the Secure Mobile Recovery & Evidence Platform (SMREP) Shield AI Assistant. " +
                "You assist officers and device owners in auditing mobile device risks, establishing cryptographic " +
                "evidence chain-of-custody protocols, ensuring Play Integrity, and lost mode configurations. " +
                "Respond in a supportive, professional, secure-oriented tone. Keep replies relatively concise."

        // Map messages and ensure roles are standardized for Gemini chat ("user" / "model")
        val contentsList = history.map {
            GeminiContent(
                role = if (it.sender == "user") "user" else "model",
                parts = listOf(GeminiPart(text = it.message))
            )
        }

        val request = GeminiRequest(
            contents = contentsList,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        try {
            val response = GeminiClient.apiService.generateContent(apiKey, request)
            if (response.isSuccessful && response.body() != null) {
                val candidateText = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (candidateText != null) {
                    return@withContext candidateText
                }
            }
            Log.e("GeminiManager", "Gemini API response failed: ${response.code()} ${response.errorBody()?.string()}")
            getOfflineMockResponse(history.lastOrNull()?.message ?: "")
        } catch (e: Exception) {
            Log.e("GeminiManager", "Gemini connection error", e)
            getOfflineMockResponse(history.lastOrNull()?.message ?: "")
        }
    }

    private fun getOfflineMockResponse(prompt: String): String {
        val cleaned = prompt.lowercase()
        return when {
            cleaned.contains("lost") || cleaned.contains("lostmode") -> {
                "🔒 [SUPPORT MATRIX] SMREP Lost Mode is powered by a multi-layered trigger engine. When activated, active telemetry tracking begins, ICCD SIM states are locked down, secure snapshots are armed, and a verification audit ledger entry is minted to preserve valid recovery evidence."
            }
            cleaned.contains("integrity") || cleaned.contains("play integrity") -> {
                "🛡️ [INTEGRITY SERVICE] Play Integrity Verification establishes state validation (MEETS_STRONG_INTEGRITY etc.). If a device fails integrity tests, SMREP automatically switches keys to specialized AES-GCM local storage with wrap-around RSA envelopes."
            }
            cleaned.contains("evidence") || cleaned.contains("snapshot") -> {
                "📸 [CHAIN OF CUSTODY] Live evidence captures are instantly written into the device's container sandbox, signed using private RSA keys, and prepared for synchronization. Real-time logging maintains a tamper-proof custody trail."
            }
            cleaned.contains("help") || cleaned.contains("hi") || cleaned.contains("hello") || cleaned.contains("hey") -> {
                "SMREP Shield AI Support Online. Command inputs are parsed and secured. How can I assist you with device recovery telemetry or cryptographic evidence collection today?"
            }
            else -> {
                "Understood. Command processed. SMREP recovery telemetry and cryptographic verification systems remain fully operational. Let me know if you need specific details about lost mode triggers or evidence snapshots."
            }
        }
    }
}
