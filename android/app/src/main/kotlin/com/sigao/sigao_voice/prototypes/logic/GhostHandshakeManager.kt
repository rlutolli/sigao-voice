package com.sigao.sigao_voice.prototypes.logic

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
    private var amIAlice = false

    /**
     * Called when a call is established (OFFHOOK).
     */
    fun onCallEstablished(remotePhoneNumber: String) {
        if (isHandshakeComplete.get()) return

        amIAlice = determineInitiator(myPhoneNumber, remotePhoneNumber)
        log("onCallEstablished (Hybrid): myNum=$myPhoneNumber, remote=$remotePhoneNumber. amIAlice=$amIAlice")

        if (amIAlice) {
            // Role: Alice (Initiator)
            // 1. Send Layered Ping (2.2kHz + 19kHz)
            log("Alice: Sending LAYERED PING (2.2k + 19k)...")
            audioTransceiver.sendCompositePing(
                AudioTransceiver.FREQUENCY_UNIVERSAL, 
                AudioTransceiver.FREQUENCY_GHOST_PING
            )
            
            // 2. Listen for Pong (Either 19.5k Silent or 2.5k Audible)
            log("Alice: Listening for PONG (19.5k OR 2.5k)...")
            audioTransceiver.startDualListening(
                AudioTransceiver.FREQUENCY_UNIVERSAL_PONG, // 2.5k
                AudioTransceiver.FREQUENCY_GHOST_PONG      // 19.5k
            ) { audiblePong, silentPong ->
                 
                 log("Alice: Pong Detected! Silent=$silentPong, Audible=$audiblePong")
                 
                 // If Silent Pong -> Great!
                 // If Audible Pong -> Good, but fallback mode.
                 
                 // 3. Send Key Payload (Simplified: Using 18.5kHz still? Or Audible Key?)
                 // User spec: "Encryption is seamless and invisible."
                 // If we heard Audible Pong, implies 19k failed. So we should send AUDIBLE Key?
                 // But for this prototype, let's assume if Handshake succeeds, we trigger SMS (Data).
                 // So we don't strictly need to send a Key Tone if SMS handles the key.
                 // The user said: "If Bob hears only 2.2k... replies Audible Pong... app shows Securing."
                 // So we proceed to SMS Verification regardless.
                 
                 completeHandshake(remotePhoneNumber)
            }
        } else {
            // Role: Bob (Responder)
            // 1. Listen for Layered Ping (2.2k OR 19k)
            log("Bob: Listening for LAYERED PING...")
            audioTransceiver.startDualListening(
                AudioTransceiver.FREQUENCY_UNIVERSAL, 
                AudioTransceiver.FREQUENCY_GHOST_PING
            ) { audiblePing, silentPing ->
                
                log("Bob: Ping Detected! Silent=$silentPing, Audible=$audiblePing")
                
                if (silentPing) {
                    // High Speed Path
                    log("Bob: High Quality Link (5G). Sending Silent Pong (19.5k)...")
                    audioTransceiver.sendPing(AudioTransceiver.FREQUENCY_GHOST_PONG)
                } else {
                    // Low Speed Path (3G Fallback)
                    log("Bob: Low Quality Link (3G/Legacy). Sending Audible Pong (2.5k)...")
                    audioTransceiver.sendPing(AudioTransceiver.FREQUENCY_UNIVERSAL_PONG)
                }
                
                // 2. Complete and wait for SMS
                completeHandshake(remotePhoneNumber)
            }
        }
    }

    /**
     * Collision Resolution: Higher phone number is the Initiator (Alice).
     */
    private fun determineInitiator(myNum: String, theirNum: String): Boolean {
        val n1 = myNum.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0
        val n2 = theirNum.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0
        return n1 > n2
    }

    private fun completeHandshake(remotePhoneNumber: String) {
        log("Handshake Audio Phase Complete. Triggering SMS Protocol...")
        audioTransceiver.stopListening()
        
        // "I heard you on voice, now I'm triggering the SMS handshake."
        protocolOrchestrator.startSecureSession(remotePhoneNumber)
        
        isHandshakeComplete.set(true)
    }

    private fun log(msg: String) {
        android.util.Log.d("SigaoGhost", msg)
        // Pipe to Flutter via AudioTransceiver's logger if hooked
    }
}
