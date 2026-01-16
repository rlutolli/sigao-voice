package com.sigao.sigao_voice.prototypes.logic

import java.util.concurrent.ConcurrentHashMap

/**
 * Validates the "Out-of-Order Delivery" requirement.
 * Holds encrypted messages in memory until the Handshake completes.
 */
class MessageBuffer {

    // Map<PhoneNumber, List<EncryptedBlob>>
    private val pendingQueue = ConcurrentHashMap<String, MutableList<ByteArray>>()

    /**
     * Stash a message that arrived before the session was ready.
     */
    fun stashMessage(phoneNumber: String, encryptedBlob: ByteArray) {
        pendingQueue.computeIfAbsent(phoneNumber) { mutableListOf() }
            .add(encryptedBlob)
    }

    /**
     * Retrieve and clear stashed messages for a user.
     * Called when ProtocolOrchestrator transitions to SECURE.
     */
    fun flushMessages(phoneNumber: String): List<ByteArray> {
        return pendingQueue.remove(phoneNumber) ?: emptyList()
    }

    fun hasPendingMessages(phoneNumber: String): Boolean {
        return pendingQueue.containsKey(phoneNumber) && pendingQueue[phoneNumber]!!.isNotEmpty()
    }
}
