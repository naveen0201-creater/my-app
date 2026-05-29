package com.example.lostmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.database.PlatformDatabase
import com.example.database.PlatformRepository
import com.example.database.RecoveryEventEntity
import com.example.telemetry.TelemetryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SIMChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SIMChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Intercepted Action: $action")

        val db = PlatformDatabase.getDatabase(context)
        val repository = PlatformRepository(db)

        CoroutineScope(Dispatchers.IO).launch {
            val device = repository.getDeviceSync()
            // Only perform active checking if device is configured / registered
            if (device != null) {
                // If action is SIM_STATE_CHANGED
                if (action == "android.intent.action.SIM_STATE_CHANGED" || action == Intent.ACTION_BOOT_COMPLETED) {
                    val simState = getSimStateString(context)
                    Log.d(TAG, "Current SIM Hardware State: $simState")

                    // Inspect cached SIM state
                    val cachedSimState = repository.getSetting("cached_sim_state") ?: ""
                    
                    if (cachedSimState.isNotEmpty() && cachedSimState != simState) {
                        Log.w(TAG, "SIM state configuration mismatch detected! Original: $cachedSimState, New: $simState")
                        
                        // Save Recovery Event
                        val event = RecoveryEventEntity(
                            eventType = "SIM_CHANGED",
                            timestamp = System.currentTimeMillis(),
                            description = "SIM transition recorded: $simState (Pre-loaded SIM: $cachedSimState)"
                        )
                        repository.insertEvent(event)

                        // If device is in Lost Mode, trigger snapshot captures and extra tracking
                        if (device.isLostMode) {
                            Log.e(TAG, "Device is in Lost Mode. Escalating telemetry gathering.")
                            TelemetryService.start(context)
                        }
                    }

                    // Update cached SIM state
                    repository.saveSetting("cached_sim_state", simState)
                }
            }
        }
    }

    private fun getSimStateString(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return when (tm.simState) {
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_UNKNOWN -> "UNKNOWN"
            else -> "OTHER"
        }
    }
}
