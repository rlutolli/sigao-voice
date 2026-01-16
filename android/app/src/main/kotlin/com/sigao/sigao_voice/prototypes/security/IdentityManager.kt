package com.sigao.sigao_voice.prototypes.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest

/**
 * Manages Identity Trust Lifecycle.
 * Implements TOFU (Trust On First Use) and Safety Number generation.
 */
class IdentityManager(private val context: Context) {

    // In-memory store for prototype (Prod would use SecureDatabase)
    private val trustedKeys = mutableMapOf<String, String>() // Phone -> Base64(PublicKey)

    /**
     * Verifies the identity of a remote contact.
     * Implements TOFU.
     * 
     * @throws SecurityException If the key has changed (Potential Man-In-The-Middle).
     */
    fun verifyIdentity(phoneNumber: String, remotePublicKey: ByteArray) {
        val remoteKeyStr = Base64.encodeToString(remotePublicKey, Base64.NO_WRAP)
        
        if (trustedKeys.containsKey(phoneNumber)) {
            val existingKey = trustedKeys[phoneNumber]
            if (existingKey != remoteKeyStr) {
                // LOCKDOWN: Identity Changed!
                throw SecurityException("SECURITY ALERT: Identity Key mismatch for $phoneNumber. Possible interception or device change.")
            }
        } else {
            // TOFU: First time seeing this contact. Pin the key.
            trustedKeys[phoneNumber] = remoteKeyStr
        }
    }

    /**
     * Generates a "Safety Number" fingerprint for manual verification.
     * Uses SHA-256(Sorted(MyKey || TheirKey)).
     */
    fun generateSafetyNumber(myPublicKey: ByteArray, theirPublicKey: ByteArray): String {
        // Deterministic ordering to ensure A->B and B->A generate same number
        val myKeyStr = Base64.encodeToString(myPublicKey, Base64.NO_WRAP)
        val theirKeyStr = Base64.encodeToString(theirPublicKey, Base64.NO_WRAP)
        
        val combined = if (myKeyStr < theirKeyStr) {
            myKeyStr + theirKeyStr
        } else {
            theirKeyStr + myKeyStr
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        
        // Convert to numeric chunks for easier reading (e.g. 12345 67890)
        return hash.take(15).joinToString("") { "%02d".format(it) }.substring(0, 30) // Simplified
    }
}
