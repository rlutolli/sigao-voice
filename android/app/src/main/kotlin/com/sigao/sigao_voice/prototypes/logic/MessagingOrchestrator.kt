package com.sigao.sigao_voice.prototypes.logic

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.sigao.sigao_voice.prototypes.messaging.RcsTunnelHandler
import com.sigao.sigao_voice.prototypes.security.IdentityManager
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Central Orchestrator for all Outgoing Signaling & Messaging.
 * Routes traffic to:
 * - RcsTunnelHandler (High Speed / MLS)
 * - SmsManager (Fallback / Double Ratchet / Fan-out)
 * - GroupOrchestrator (Virtual Groups)
 */
class MessagingOrchestrator(
    private val context: Context,
    private val identityManager: IdentityManager,
    private val rcsHandler: RcsTunnelHandler,
    private val groupOrchestrator: GroupOrchestrator,
    private val smsQueue: com.sigao.sigao_voice.prototypes.messaging.AsyncSmsQueue
) {

    /**
     * Sends a message to a single contact or a group.
     * @param targetId Phone Number OR Group UUID
     * @param isGroup True if targeting a group
     */
    fun sendMessage(targetId: String, content: String, isGroup: Boolean) {
        if (isGroup) {
            groupOrchestrator.broadcastMessage(targetId, content)
        } else {
            sendOneToOne(targetId, content)
        }
    }

    private fun sendOneToOne(phoneNumber: String, content: String) {
        // 1. Check Capabilities (Cached or Live)
        rcsHandler.discoverCapabilities(phoneNumber) { hasRcs ->
            
            // 2. Encrypt Payload (Double Ratchet)
            val sessionKey = identityManager.getEncryptionKey(phoneNumber)
            val encryptedBytes = encrypt(content, sessionKey)
            
            if (hasRcs) {
                // RCS Tunnel Path
                Log.d("MessagingOrchestrator", "Routing via RCS Tunnel to $phoneNumber")
                rcsHandler.sendTunnelMessage(phoneNumber, encryptedBytes)
            } else {
                // SMS Fallback Path
                Log.d("MessagingOrchestrator", "Routing via SMS Fallback to $phoneNumber")
                sendBinarySms(phoneNumber, encryptedBytes)
            }
        }
    }

    private fun sendBinarySms(phoneNumber: String, payload: ByteArray) {
        // Use Async Queue to avoid rate limits
        // If payload > 133 bytes, we'd loop and send chunks here (omitted for brevity)
        
        // Using Text Mode wrapper for prototype (easier to read logs)
        val base64Body = Base64.encodeToString(payload, Base64.NO_WRAP)
        smsQueue.enqueueSms(phoneNumber, "SIGAO_ENC:$base64Body")
    }

    // --- Crypto Helper (Stub) ---
    private fun encrypt(plaintext: String, key: SecretKeySpec): ByteArray {
        // Simple AES mock for prototype
        // In Prod: Use true Double Ratchet implementation
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding") // ECB for prototype only!
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(plaintext.toByteArray())
    }
}
