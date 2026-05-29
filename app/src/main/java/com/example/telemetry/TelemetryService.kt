package com.example.telemetry

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.crypto.CryptoHelper
import com.example.database.PlatformDatabase
import com.example.database.PlatformRepository
import com.example.database.TelemetryEntity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

class TelemetryService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: PlatformRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    companion object {
        private const val CHANNEL_ID = "telemetry_service_channel"
        private const val NOTIFICATION_ID = 2026
        private const val TAG = "TelemetryService"
        
        fun start(context: Context) {
            val intent = Intent(context, TelemetryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TelemetryService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = PlatformRepository(PlatformDatabase.getDatabase(this))
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        collectAndRecordTelemetry()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun collectAndRecordTelemetry() {
        serviceScope.launch {
            // Check location consent
            val isLocationGranted = repository.getConsentGranted("location")
            if (!isLocationGranted) {
                Log.w(TAG, "Location tracking skipped due to lack of consent")
                return@launch
            }

            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).addOnSuccessListener { location: Location? ->
                    serviceScope.launch {
                        if (location != null) {
                            saveTelemetryData(location)
                        } else {
                            // Fallback to last location
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                                if (lastLoc != null) {
                                    serviceScope.launch {
                                        saveTelemetryData(lastLoc)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture location: ${e.message}")
            }
        }
    }

    private suspend fun saveTelemetryData(location: Location) {
        val batteryLevel = getBatteryLevel()
        val networkType = getNetworkType()
        val timestamp = System.currentTimeMillis()

        val entity = TelemetryEntity(
            lat = location.latitude,
            lon = location.longitude,
            accuracy = location.accuracy,
            battery = batteryLevel,
            network = networkType,
            timestamp = timestamp
        )

        repository.insertTelemetry(entity)
        Log.d(TAG, "Saved telemetry locally: $entity")
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getNetworkType(): String {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return "None"
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "None"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMREP Telemetry Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lawful device telemetry capturing agent"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMREP Protected")
            .setContentText("Active device recovery monitoring is running.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
