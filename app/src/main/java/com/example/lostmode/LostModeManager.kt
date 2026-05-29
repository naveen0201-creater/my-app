package com.example.lostmode

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.provider.Settings
import android.util.Log
import com.example.database.PlatformDatabase
import com.example.database.PlatformRepository
import com.example.database.RecoveryEventEntity

class LostModeManager(private val context: Context) {

    companion object {
        private const val TAG = "LostModeManager"
        private const val OFFLINE_THRESHOLD_MS = 5 * 60 * 1000 // 5 minutes
    }

    private val repository = PlatformRepository(PlatformDatabase.getDatabase(context))

    suspend fun auditDeviceRecoveryTriggers() {
        Log.d(TAG, "Running security recovery audit...")
        val device = repository.getDeviceSync() ?: return

        // 1. Check Airplane Mode Status
        val isAirplaneModeOn = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0

        if (isAirplaneModeOn) {
            Log.w(TAG, "Airplane mode enabled detected.")
            repository.insertEvent(
                RecoveryEventEntity(
                    eventType = "AIRPLANE_MODE",
                    timestamp = System.currentTimeMillis(),
                    description = "System Airplane Mode was enabled, suppressing wireless communications."
                )
            )
        }

        // 2. Check Location Services Status
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkLocEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkLocEnabled) {
            Log.w(TAG, "Location services are fully disabled.")
            repository.insertEvent(
                RecoveryEventEntity(
                    eventType = "LOCATION_DISABLED",
                    timestamp = System.currentTimeMillis(),
                    description = "GPS and network location providers have been disabled."
                )
            )
        }

        // 3. Connection Offline Auditing & Reconnections
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val isOnline = activeNetwork != null

        val lastOnlineStr = repository.getSetting("last_online_time") ?: "0"
        val lastOnlineTime = lastOnlineStr.toLong()
        val currentTime = System.currentTimeMillis()

        if (isOnline) {
            // If was offline for > 5 minutes and now reconnected
            if (lastOnlineTime > 0 && (currentTime - lastOnlineTime) > OFFLINE_THRESHOLD_MS) {
                Log.w(TAG, "Device reconnected after long absence (>5 mins).")
                repository.insertEvent(
                    RecoveryEventEntity(
                        eventType = "DEVICE_RECONNECTED",
                        timestamp = currentTime,
                        description = "Device reconnected to network after absent period of ${(currentTime - lastOnlineTime) / 1000} seconds."
                    )
                )
            }
            // Update last online timestamp
            repository.saveSetting("last_online_time", currentTime.toString())
        } else {
            // Currently offline
            if (lastOnlineTime > 0 && (currentTime - lastOnlineTime) > OFFLINE_THRESHOLD_MS) {
                val lastFlaggedOffline = repository.getSetting("last_offline_flagged_time") ?: "0"
                if (currentTime - lastFlaggedOffline.toLong() > OFFLINE_THRESHOLD_MS) {
                    Log.e(TAG, "Device has been offline for over 5 minutes.")
                    repository.insertEvent(
                        RecoveryEventEntity(
                            eventType = "DEVICE_OFFLINE",
                            timestamp = currentTime,
                            description = "Device offline duration exceeds 5-minute safety threshold."
                        )
                    )
                    repository.saveSetting("last_offline_flagged_time", currentTime.toString())
                }
            }
        }
    }
}
