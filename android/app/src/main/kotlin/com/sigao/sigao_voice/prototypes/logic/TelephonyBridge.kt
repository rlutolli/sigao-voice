package com.sigao.sigao_voice.prototypes.logic

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

/**
 * Interface for cross-version Telephony Monitoring.
 */
interface TelephonyMonitor {
    fun startMonitoring(onCallActive: (String) -> Unit)
    fun stopMonitoring()
}

/**
 * Factory to create the correct monitor based on Android Version.
 */
object TelephonyBridge {
    fun create(context: Context): TelephonyMonitor {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ModernTelephonyMonitor(context, tm)
        } else {
            LegacyTelephonyMonitor(tm)
        }
    }
}

/**
 * Legacy Implementation (Android 10 - 11).
 * Uses deprecated PhoneStateListener.
 */
@Suppress("DEPRECATION")
private class LegacyTelephonyMonitor(
    private val telephonyManager: TelephonyManager
) : TelephonyMonitor {

    private var listener: PhoneStateListener? = null

    override fun startMonitoring(onCallActive: (String) -> Unit) {
        listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    Log.d("TelephonyBridge", "Legacy: Call OFFHOOK. Number=${phoneNumber?.take(4)}***")
                    // Note: phoneNumber might be null depending on permissions
                    onCallActive(phoneNumber ?: "Unknown")
                }
            }
        }
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        Log.d("TelephonyBridge", "Legacy Monitor Started")
    }

    override fun stopMonitoring() {
        listener?.let {
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        listener = null
    }
}

/**
 * Modern Implementation (Android 12+).
 * Uses TelephonyCallback.
 */
@RequiresApi(Build.VERSION_CODES.S)
private class ModernTelephonyMonitor(
    private val context: Context,
    private val telephonyManager: TelephonyManager
) : TelephonyMonitor {

    private var callback: TelephonyCallback? = null

    override fun startMonitoring(onCallActive: (String) -> Unit) {
        callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    Log.d("TelephonyBridge", "Modern: Call OFFHOOK")
                    // Note: Modern callback doesn't give number directly here usually,
                    // but for this prototype we trigger the flow.
                    // Ideally we'd query CallLog or use ConnectionService state.
                    onCallActive("Unknown") 
                }
            }
        }
        
        // Register with Main Executor
        telephonyManager.registerTelephonyCallback(
            context.mainExecutor,
            callback!!
        )
        Log.d("TelephonyBridge", "Modern Monitor Started")
    }

    override fun stopMonitoring() {
        callback?.let {
            telephonyManager.unregisterTelephonyCallback(it)
        }
        callback = null
    }
}
