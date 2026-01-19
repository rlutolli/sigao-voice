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

    private val handshakeState = HandshakeState()
    private val handler = Handler(Looper.getMainLooper())
    private var amIAlice = false

    /**
     * Called when a call is established (OFFHOOK).
     */
    /**
     * Called when a call is established (OFFHOOK).
     */
    fun onCallEstablished(remotePhoneNumber: String) {
        if (handshakeState.getCurrentState() != HandshakeState.State.IDLE) return

        handshakeState.transitionTo(HandshakeState.State.STABILIZING)
        log("onCallEstablished: Stabilizing for 500ms...")

        handler.postDelayed({
            performSignalCheck(remotePhoneNumber)
        }, 500)
    }

    private fun performSignalCheck(remotePhoneNumber: String) {
        handshakeState.transitionTo(HandshakeState.State.SIGNAL_CHECK)
        log("performSignalCheck: Measuring Noise Floor (100ms)...")
        
        // Mock Signal Check (in real impl, we'd run AudioRecord briefly)
        handler.postDelayed({
            // Assuming signal is good
            startHandshakeSequence(remotePhoneNumber)
        }, 100)
    }

    private fun startHandshakeSequence(remotePhoneNumber: String) {
        amIAlice = determineInitiator(myPhoneNumber, remotePhoneNumber)
        log("startHandshakeSequence: amIAlice=$amIAlice")

        if (amIAlice) {
            runAliceSequence(remotePhoneNumber)
        } else {
            runBobSequence(remotePhoneNumber)
        }
    }

    private fun runAliceSequence(remotePhoneNumber: String) {
        handshakeState.transitionTo(HandshakeState.State.SENDING_PING)
        
        // 1. Mute / Duck
        audioTransceiver.muteProximity()
        
        // 2. Check for Bluetooth (Force 2.2kHz Stealth if needed)
        val isBluetooth = audioTransceiver.isBluetoothActive()
        
        if (isBluetooth) {
            log("Alice: BLUETOOTH DETECTED. Using Stealth 2.2kHz only (19kHz skipped).")
            audioTransceiver.sendPing(AudioTransceiver.FREQUENCY_UNIVERSAL)
        } else {
             log("Alice: Speaker Active. Sending Composite Ping (2.2k + 19k).")
             audioTransceiver.sendCompositePing(
                AudioTransceiver.FREQUENCY_UNIVERSAL,
                AudioTransceiver.FREQUENCY_GHOST_PING
            )
        }

        // 3. Listen for Pong
        handshakeState.transitionTo(HandshakeState.State.LISTENING_FOR_PING)
        log("Alice: Listening for PONG...")
        
        audioTransceiver.startDualListening(
            AudioTransceiver.FREQUENCY_UNIVERSAL_PONG,
            AudioTransceiver.FREQUENCY_GHOST_PONG
        ) { audiblePong, silentPong ->
            
             log("Alice: Pong Detected! Silent=$silentPong, Audible=$audiblePong")
             completeHandshake(remotePhoneNumber)
        }
    }

    private fun runBobSequence(remotePhoneNumber: String) {
        handshakeState.transitionTo(HandshakeState.State.LISTENING_FOR_PING)
        log("Bob: Listening for LAYERED PING...")
        
        // Bob listens first
        audioTransceiver.startDualListening(
            AudioTransceiver.FREQUENCY_UNIVERSAL,
            AudioTransceiver.FREQUENCY_GHOST_PING
        ) { audiblePing, silentPing ->
            
            log("Bob: Ping Detected! Silent=$silentPing, Audible=$audiblePing")
            
            // 1. Mute to reply
            audioTransceiver.muteProximity()
            
            handshakeState.transitionTo(HandshakeState.State.SENDING_PONG)
            
            if (silentPing) {
                log("Bob: Sending Silent Pong...")
                audioTransceiver.sendPing(AudioTransceiver.FREQUENCY_GHOST_PONG)
            } else {
                log("Bob: Sending Audible Pong...")
                audioTransceiver.sendPing(AudioTransceiver.FREQUENCY_UNIVERSAL_PONG)
            }
            
            completeHandshake(remotePhoneNumber)
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
        handshakeState.transitionTo(HandshakeState.State.SECURE)
        log("Handshake Audio Phase Complete. Unmuting and Triggering SMS Protocol...")
        
        // Stop listening loop
        audioTransceiver.stopListening()
        
        // Play success chime
        audioTransceiver.playSystemTone(500) 
        
        // Unmute Line
        audioTransceiver.unmuteProximity()
        
        // Trigger verification (Double Ratchet via SMS)
        protocolOrchestrator.startSecureSession(remotePhoneNumber)
    }

    private fun log(msg: String) {
        android.util.Log.d("SigaoGhost", msg)
    }
}
