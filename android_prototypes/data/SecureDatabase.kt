package com.sigao.prototypes.data

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import java.io.File

/**
 * "The Vault": SQLCipher encrypted database for storing all SMS/RCS messages.
 * Requires a passphrase derived from Biometric Authentication to open.
 */
class SecureDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "sigao_secure.db"
        const val DATABASE_VERSION = 1
        
        init {
            // Load SQLCipher native libraries
            // System.loadLibrary("sqlcipher")
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Enforce foreign keys and secure defaults
        db.execSQL("PRAGMA foreign_keys = ON;")
        db.execSQL("PRAGMA secure_delete = ON;") // Overwrite deleted data with zeros

        // Create Messages Table
        db.execSQL("""
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                thread_id TEXT NOT NULL,
                sender TEXT NOT NULL,
                body BLOB NOT NULL, -- Encrypted content (if E2E) or Plain (if Standard)
                timestamp INTEGER,
                is_encrypted INTEGER DEFAULT 0
            );
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle migration securely
    }

    /**
     * Opens the database using the "Double Lock" Bio-Key.
     * This key MUST come from the BiometricGuard callback.
     */
    fun getUnlockerWritableDatabase(passphrase: ByteArray): SQLiteDatabase {
        return super.getWritableDatabase(passphrase.toString(Charsets.UTF_8))
    }
}
