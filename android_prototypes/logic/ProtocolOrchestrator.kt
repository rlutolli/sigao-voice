package com.sigao.prototypes.logic

import android.content.Context
import android.telephony.SmsManager
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the "Decision Engine" for Hybrid SMS/RCS security.
 * Determines if a contact is a "Sigao Peer" or "Standard Contact".
 */
class ProtocolOrchestrator(
    private val context: Context,
    private val cryptoEngine: com.sigao.prototypes.security.CryptoEngine,
    private val identityManager: com.sigao.prototypes.security.IdentityManager,
    private val messageBuffer: MessageBuffer
) {

    // --- Constants ---
    companion object {
        const val PREFIX_HANDSHAKE_REQUEST = "??SIGAO_REQ??" // V2
        const val PREFIX_HANDSHAKE_RESPONSE = "??SIGAO_RESP??" // V2
        const val PREFIX_HANDSHAKE_ACK = "??SIGAO_ACK??" // V2
        const val HANDSHAKE_TIMEOUT_MS = 30_000L
    }

    // --- State Machine ---
    enum class SecurityState {
        UNKNOWN,
        PENDING_HANDSHAKE,
        SECURE,  // Hardened: Double Ratchet Active
        INSECURE // Standard: Cleartext fallback
    }

    // Track state per contact (Phone Number -> State)
    private val _contactStates = ConcurrentHashMap<String, MutableStateFlow<SecurityState>>()

    /**
     * Initiates the secure session handshake with a target contact.
     * This is the "Handshake" phase.
     */
    fun startSecureSession(phoneNumber: String) {
        val stateFlow = getContactStateFlow(phoneNumber)
        if (stateFlow.value == SecurityState.SECURE) return

        // Deterministic Collision Resolution:
        // If both sides start at once, the one with the "higher" phone number acts as Initiator.
        // For simplicity in prototype, we just send the request.
        
        updateState(phoneNumber, SecurityState.PENDING_HANDSHAKE)
        
        // Send KEY_REQUEST
        sendSilentSMS(phoneNumber, PREFIX_HANDSHAKE_REQUEST + getMyEncodedIdentity())
    }

    /**
     * Called when an SMS is received.
     * Returns TRUE if the message is a SIGAO control message and should be hidden from UI.
     */
    fun processIncomingMessage(phoneNumber: String, messageBody: String): Boolean {
        return when {
            messageBody.startsWith(PREFIX_HANDSHAKE_REQUEST) -> {
                handleHandshakeRequest(phoneNumber, messageBody)
                true
            }
            messageBody.startsWith(PREFIX_HANDSHAKE_RESPONSE) -> {
                handleHandshakeResponse(phoneNumber, messageBody)
                true
            }
            messageBody.startsWith(PREFIX_HANDSHAKE_ACK) -> {
                // Handshake complete on other side, ensured.
                true
            }
            else -> false
        }
    }

    private fun handleHandshakeRequest(phoneNumber: String, messageBody: String) {
        val payload = messageBody.removePrefix(PREFIX_HANDSHAKE_REQUEST)
        // 1. Verify Identity (TOFU)
        try {
            val remoteKey = Base64.decode(payload, Base64.NO_WRAP)
            identityManager.verifyIdentity(phoneNumber, remoteKey)
            
            // 2. Respond with our Key (KEY_RESPONSE)
            sendSilentSMS(phoneNumber, PREFIX_HANDSHAKE_RESPONSE + getMyEncodedIdentity())
            
            // 3. Mark Secure (Responder Side)
            updateState(phoneNumber, SecurityState.SECURE)
            
        } catch (e: SecurityException) {
            // Identity Mismatch!
            // triggerPanicUI(phoneNumber)
            updateState(phoneNumber, SecurityState.INSECURE)
        }
    }

    private fun handleHandshakeResponse(phoneNumber: String, messageBody: String) {
         val payload = messageBody.removePrefix(PREFIX_HANDSHAKE_RESPONSE)
         try {
             val remoteKey = Base64.decode(payload, Base64.NO_WRAP)
             identityManager.verifyIdentity(phoneNumber, remoteKey)
             
             // 2. Mark Secure (Initiator Side)
             updateState(phoneNumber, SecurityState.SECURE)
             
             // 3. Send ACK (so they know we got it)
             sendSilentSMS(phoneNumber, PREFIX_HANDSHAKE_ACK)
             
             // 4. Flush Pending Buffer
             if (messageBuffer.hasPendingMessages(phoneNumber)) {
                 val pending = messageBuffer.flushMessages(phoneNumber)
                 // Ingest these into DB...
             }
         } catch (e: SecurityException) {
             updateState(phoneNumber, SecurityState.INSECURE)
         }
    }

    /**
     * Called if no handshake reply is received within [HANDSHAKE_TIMEOUT_MS].
     */
    fun onHandshakeTimeout(phoneNumber: String) {
        val state = _contactStates[phoneNumber]?.value
        if (state == SecurityState.PENDING_HANDSHAKE) {
            updateState(phoneNumber, SecurityState.INSECURE)
            // Trigger UI Warning: "Communication UNENCRYPTED"
        }
    }

    private fun sendSilentSMS(phoneNumber: String, content: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(phoneNumber, null, content, null, null)
        } catch (e: SecurityException) {
            updateState(phoneNumber, SecurityState.INSECURE)
        }
    }

    private fun getMyEncodedIdentity(): String {
        return Base64.encodeToString(cryptoEngine.getPublicIdentityKey(), Base64.NO_WRAP)
    }

    // --- Helper Methods ---

    private fun getContactStateFlow(phoneNumber: String): MutableStateFlow<SecurityState> {
        return _contactStates.computeIfAbsent(phoneNumber) {
            MutableStateFlow(SecurityState.UNKNOWN)
        }
    }

    private fun updateState(phoneNumber: String, newState: SecurityState) {
        getContactStateFlow(phoneNumber).value = newState
    }

    fun isContactSecure(phoneNumber: String): Boolean {
        return _contactStates[phoneNumber]?.value == SecurityState.SECURE
    }
}
