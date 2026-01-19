package com.sigao.sigao_voice.prototypes.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

/**
 * Manages Identity Trust Lifecycle.
 * Implements TOFU (Trust On First Use), Safety Number generation,
 * and Double Ratchet Session Keys.
 */
class IdentityManager(private val context: Context) {

    // In-memory store for prototype (Prod would use SecureDatabase)
    private val trustedKeys = mutableMapOf<String, String>() // Phone -> Base64(IdentityKey)
    
    // Session Keys for 1:1 Double Ratchet
    private val sessionKeys = mutableMapOf<String, SecretKeySpec>() // Phone -> CurrentChainKey
    
    // Group Sender Keys (GroupId -> MySenderKey)
    private val groupSenderKeys = mutableMapOf<String, SecretKeySpec>()

    /**
     * Verifies the identity of a remote contact.
     * Implements TOFU.
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
        
        return hash.take(15).joinToString("") { "%02d".format(it) }.substring(0, 30)
    }

    // --- Double Ratchet Session Logic ---

    fun rotateSessionKey(phoneNumber: String) {
        // Mock Ratchet: Hash previous key to get next key
        val currentKey = sessionKeys[phoneNumber] ?: generateInitialSessionKey()
        val nextKeyBytes = MessageDigest.getInstance("SHA-256").digest(currentKey.encoded)
        sessionKeys[phoneNumber] = SecretKeySpec(nextKeyBytes, "AES")
    }

    fun getEncryptionKey(phoneNumber: String): SecretKeySpec {
        return sessionKeys.getOrPut(phoneNumber) { generateInitialSessionKey() }
    }

    private fun generateInitialSessionKey(): SecretKeySpec {
        return SecretKeySpec("MockInitialKey123".toByteArray(), "AES")
    }

    // --- Group Sender Keys ---

    fun getGroupSenderKey(groupId: String): SecretKeySpec {
        return groupSenderKeys.getOrPut(groupId) { 
            SecretKeySpec("GroupKey_${groupId}".toByteArray().take(16).toByteArray(), "AES") 
        }
    }
}
