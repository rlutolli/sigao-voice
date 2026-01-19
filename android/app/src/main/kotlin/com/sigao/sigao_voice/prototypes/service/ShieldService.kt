package com.sigao.sigao_voice.prototypes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.Context
import com.sigao.sigao_voice.R

/**
 * "The Shield": A lightweight Foreground Service that keeps the ProtocolOrchestrator alive.
 * Critical for receiving Handshakes in Android 14+ (which kills background receivers).
 */
class ShieldService : Service() {

    companion object {
        const val CHANNEL_ID = "sigao_shield_channel" // Matches config
        const val NOTIFICATION_ID = 999
    }

    private var telephonyMonitor: com.sigao.sigao_voice.prototypes.logic.TelephonyMonitor? = null
    private var ghostManager: com.sigao.sigao_voice.prototypes.logic.GhostHandshakeManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Define Foreground Service Type for Android 14+
        if (Build.VERSION.SDK_INT >= 34) { // Android 14+
             startForeground(
                 NOTIFICATION_ID, 
                 buildNotification(), 
                 android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
             )
        } else {
             startForeground(NOTIFICATION_ID, buildNotification())
        }
        
        initializeSecurityCore()
        startMonitoring()
        registerProfileReceiver()
    }

    private fun initializeSecurityCore() {
        android.util.Log.d("ShieldService", "Initializing Security Core...")
        
        // Dependency Graph construction
        val context = this
        val crypto = com.sigao.sigao_voice.prototypes.security.CryptoEngine()
        val identity = com.sigao.sigao_voice.prototypes.security.IdentityManager(context)
        val msgBuffer = com.sigao.sigao_voice.prototypes.logic.MessageBuffer()
        val orchestrator = com.sigao.sigao_voice.prototypes.logic.ProtocolOrchestrator(context, crypto, identity, msgBuffer)
        
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val transceiver = com.sigao.sigao_voice.prototypes.logic.AudioTransceiver(audioManager)
        
        // Mock ID for prototype
        val myNum = "15551234567"
        
        ghostManager = com.sigao.sigao_voice.prototypes.logic.GhostHandshakeManager(transceiver, orchestrator, myNum)
    }

    private fun startMonitoring() {
        telephonyMonitor = com.sigao.sigao_voice.prototypes.logic.TelephonyBridge.create(this)
        telephonyMonitor?.startMonitoring { remoteNumber ->
            android.util.Log.i("ShieldService", "Call Detected: $remoteNumber. Activating Ghost Protocol.")
            
            // Trigger Handshake
            ghostManager?.onCallEstablished(remoteNumber)
            
            // Update Notification to show "Securing..."
            updateNotification("Securing call with $remoteNumber...")
        }
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: Restart if killed by OS
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sigao Shield Active")
            .setContentText("Monitoring for secure handshakes...")
            .setSmallIcon(R.mipmap.ic_launcher_round) // Ensure icon exists
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimal intrusion
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sigao Shield Active")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
            
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sigao Security Shield",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    // --- Private Space Sync (Android 15/16) ---
    
    private val profileReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE -> {
                    android.util.Log.i("ShieldService", "Private Space UNLOCKED. Waking CryptoEngine...")
                    // Re-init or check keys
                    initializeSecurityCore()
                }
                android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE -> {
                    android.util.Log.w("ShieldService", "Private Space LOCKED. Crypto unavailable.")
                    // Optionally clear keys from RAM here too
                    ghostManager = null
                }
            }
        }
    }

    private fun registerProfileReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
        }
        registerReceiver(profileReceiver, filter)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(profileReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
        telephonyMonitor?.stopMonitoring()
        super.onDestroy()
    }
}
