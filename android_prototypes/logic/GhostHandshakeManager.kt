package com.sigao.prototypes.logic

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrator for the "Zero-Touch" Silent Handshake during voice calls.
 * Manages timing, collision resolution, and key exchange.
 */
class GhostHandshakeManager(
    private val audioTransceiver: AudioTransceiver,
    private val protocolOrchestrator: ProtocolOrchestrator,
    private val myPhoneNumber: String // "My" phone number for collision logic
) {

    private val isHandshakeComplete = AtomicBoolean(false)

    /**
     * Called when a call is established (OFFHOOK).
     */
    fun onCallEstablished(remotePhoneNumber: String) {
        if (isHandshakeComplete.get()) return

        val amIInitiator = determineInitiator(myPhoneNumber, remotePhoneNumber)

        if (amIInitiator) {
            // Role: Alice (Initiator)
            // ACTION: Send Ping immediately (0.0s)
            audioTransceiver.sendPing()
            
            // Listen for Pong (expected at ~0.5s)
            audioTransceiver.startListening { freq ->
                 if (freq == AudioTransceiver.FREQUENCY_PONG) {
                     // Pong received! Link established.
                     // Send Key Payload
                     sendKeyPayload(remotePhoneNumber)
                 }
            }
        } else {
            // Role: Bob (Responder)
            // ACTION: Listen for Ping (0.0s - 0.5s)
            audioTransceiver.startListening { freq ->
                if (freq == AudioTransceiver.FREQUENCY_PING) {
                    // Ping received! Send Pong.
                    // Note: In real logic, we'd switch frequency to 19kHz for Pong
                    audioTransceiver.sendPing() // Using same for proto simplification
                    
                    // Wait for Key Payload
                }
            }
        }
    }

    /**
     * Collision Resolution: Higher phone number is the Initiator.
     * Prevents both sides from talking at once.
     */
    private fun determineInitiator(myNum: String, theirNum: String): Boolean {
        // Normalize numbers (remove +, spaces) before compare
        val n1 = myNum.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0
        val n2 = theirNum.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0
        return n1 > n2
    }

    private fun sendKeyPayload(remotePhoneNumber: String) {
        // In the Acoustic model, we might send the key via Data-over-Sound (slow)
        // OR, more likely, we use the Acoustic signal just to "Unlock" the SMS channel.
        
        // "I heard you on voice, now I'm triggering the SMS handshake which you should accept implicitly."
        protocolOrchestrator.startSecureSession(remotePhoneNumber)
        
        isHandshakeComplete.set(true)
    }
}
