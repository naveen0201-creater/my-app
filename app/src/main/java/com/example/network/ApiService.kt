package com.example.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.GET
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

interface ApiService {

    @POST("register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>

    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest
    ): Response<DeviceRegisterResponse>

    @POST("telemetry")
    suspend fun postTelemetry(
        @Body request: TelemetryRequest
    ): Response<TelemetryResponse>

    @POST("evidence")
    suspend fun postEvidence(
        @Body request: EvidenceRequest
    ): Response<EvidenceResponse>

    @POST("lostmode/enable")
    suspend fun enableLostMode(
        @Body request: LostModeRequest
    ): Response<LostModeResponse>

    @POST("lostmode/disable")
    suspend fun disableLostMode(
        @Body request: LostModeRequest
    ): Response<LostModeResponse>
    
    @GET("device/{id}")
    suspend fun getDeviceStatus(
        @Path("id") deviceId: String
    ): Response<DeviceRegisterResponse>
    
    @GET("telemetry/{device_id}")
    suspend fun getTelemetryHistory(
        @Path("device_id") deviceId: String
    ): Response<List<TelemetryRequest>>
}

object NetworkClient {
    private var currentBaseUrl = "http://10.0.2.2:8000/api/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var retrofit = buildRetrofit()

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    fun updateBaseUrl(newUrl: String) {
        val formatted = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        if (formatted != currentBaseUrl) {
            currentBaseUrl = formatted
            retrofit = buildRetrofit()
        }
    }

    fun getBaseUrl(): String = currentBaseUrl

    val apiService: ApiService
        get() = retrofit.create(ApiService::class.java)
}
