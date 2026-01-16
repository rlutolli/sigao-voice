package com.sigao.sigao_voice.prototypes.data

import android.database.Cursor

/**
 * Repository that provides a unified view of messages for the UI.
 * Handles the "Dual-Mode" reality (Encrypted vs Cleartext).
 */
class UnifiedThreadRepo(private val secureDatabase: SecureDatabase) {

    data class UnifiedMessage(
        val id: Long,
        val sender: String,
        val body: String,
        val timestamp: Long,
        val isEncrypted: Boolean
    )

    /**
     * Retrieves the conversation thread for a specific phone number.
     * Decrypts on the fly if needed.
     */
    fun getThread(phoneNumber: String, passphrase: ByteArray): List<UnifiedMessage> {
        val messages = mutableListOf<UnifiedMessage>()
        val db = secureDatabase.getUnlockerWritableDatabase(passphrase)
        
        val cursor = db.rawQuery(
            "SELECT id, sender, body, timestamp, is_encrypted FROM messages WHERE thread_id = ? ORDER BY timestamp ASC",
            arrayOf(phoneNumber)
        )

        cursor.use { c ->
            while (c.moveToNext()) {
                val isEncrypted = c.getInt(4) == 1
                val blob = c.getBlob(2)
                
                // Decryption logic would normally happen here via CryptoEngine if Encrypted
                // For this prototype view, we assume the specific decrypted view logic is handled upstream or blob is plain
                val bodyText = if (isEncrypted) {
                    "[Encrypted Message]" // Placeholder: Real app calls CryptoEngine.decrypt(blob)
                } else {
                    String(blob, Charsets.UTF_8)
                }

                messages.add(UnifiedMessage(
                    id = c.getLong(0),
                    sender = c.getString(1),
                    body = bodyText,
                    timestamp = c.getLong(3),
                    isEncrypted = isEncrypted
                ))
            }
        }
        return messages
    }
}
