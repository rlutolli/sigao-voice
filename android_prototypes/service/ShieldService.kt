package com.sigao.prototypes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * "The Shield": A lightweight Foreground Service that keeps the ProtocolOrchestrator alive.
 * Critical for receiving Handshakes in Android 14+ (which kills background receivers).
 */
class ShieldService : Service() {

    companion object {
        const val CHANNEL_ID = "sigao_shield_channel" // Matches config
        const val NOTIFICATION_ID = 999
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        // Initialize Orchestrator here to ensure it's observing SMS
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
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimal intrusion
            .setOngoing(true)
            .build()
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
}
