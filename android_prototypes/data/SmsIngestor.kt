package com.sigao.prototypes.data

import android.content.Context
import android.net.Uri
import android.database.Cursor
import android.provider.Telephony

/**
 * Migrates legacy messages from the System SMS Database to the Sigao Secure Vault.
 * This allows "Inbox Syncing".
 */
class SmsIngestor(
    private val context: Context,
    private val secureDatabase: SecureDatabase
) {

    fun ingestSystemMessages(passphrase: ByteArray) {
        val cr = context.contentResolver
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val cursor: Cursor? = cr.query(uri, projection, null, null, "date DESC LIMIT 1000") // Start with last 1000

        cursor?.use { c ->
            val db = secureDatabase.getUnlockerWritableDatabase(passphrase)
            db.beginTransaction()
            try {
                val idxAddress = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val idxBody = c.getColumnIndex(Telephony.Sms.BODY)
                val idxDate = c.getColumnIndex(Telephony.Sms.DATE)

                while (c.moveToNext()) {
                    val address = c.getString(idxAddress)
                    val body = c.getString(idxBody)
                    val date = c.getLong(idxDate)
                    
                    // Insert into Secure Vault
                    // Note: We flag these as 'is_encrypted = 0' (Legacy)
                    val sql = "INSERT INTO messages (thread_id, sender, body, timestamp, is_encrypted) VALUES (?, ?, ?, ?, 0)"
                    val statement = db.compileStatement(sql)
                    statement.bindString(1, address) // Thread ID = Phone Number
                    statement.bindString(2, address)
                    statement.bindBlob(3, body.toByteArray(Charsets.UTF_8)) // Store body as blob
                    statement.bindLong(4, date)
                    statement.executeInsert()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
