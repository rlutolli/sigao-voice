package com.sigao.sigao_voice.prototypes.logic

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.util.Arrays

/**
 * Manages the "Nuclear Option" (Panic Button).
 * Executes atomic destruction of sensitive data.
 */
object PanicManager {

    private const val TAG = "SigaoPanic"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_IDENTITY = "sigao_identity_key"
    private const val DB_NAME = "sigao_secure.db" // Adjust to actual DB name

    /**
     * TRIGGER: Executes the full wipe sequence.
     * This cannot be undone.
     */
    fun triggerPanic(context: Context) {
        Log.e(TAG, "!!! PANIC TRIGGERED !!! STARTING WIPE SEQUENCE.")

        // 1. Volatile Wipe (RAM)
        wipeMemory()

        // 2. Persistent Wipe (Storage & HSM)
        wipeStorage(context)
        
        // 3. Kill Process
        Log.e(TAG, "Wipe Complete. Committing Suicide.")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun wipeMemory() {
        Log.d(TAG, "Stage 1: Wiping RAM...")
        
        // In a real app, we would track all active Key objects and zeroes them.
        // For prototype, we rely on GC and Finalization.
        System.gc()
        System.runFinalization()
        
        Log.d(TAG, "Stage 1: RAM Wipe Requested (GC + Finalization).")
    }

    private fun wipeStorage(context: Context) {
        Log.d(TAG, "Stage 2: Wiping Storage & HSM...")

        try {
            // A. Revoke Hardware Keys
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS_IDENTITY)) {
                keyStore.deleteEntry(KEY_ALIAS_IDENTITY)
                Log.d(TAG, "HSM: Identity Key Revoked.")
            }

            // B. Zero-Fill Database
            val dbPath = context.getDatabasePath(DB_NAME)
            if (dbPath.exists()) {
                zeroFillAndDelete(dbPath)
            } else {
                Log.d(TAG, "DB: File not found (already deleted?).")
            }
            
            // C. Clear Preferences
            // context.getSharedPreferences("...", Context.MODE_PRIVATE).edit().clear().commit()

        } catch (e: Exception) {
            Log.e(TAG, "PANIC FAILED: ${e.message}", e)
            // Depending on strictness, maybe retry or crash?
        }
    }

    /**
     * Synchronously overwrites a file with random bytes before deleting it.
     * Defeats simple filesystem recovery.
     */
    private fun zeroFillAndDelete(file: File) {
        try {
            val length = file.length()
            val raf = RandomAccessFile(file, "rws") // Synchronous write to device
            val buffer = ByteArray(4096) // 4KB chunks
            val random = java.security.SecureRandom()
            
            Log.d(TAG, "Zero-Filling ${length} bytes...")
            
            var connectionParams = 0L
            while (connectionParams < length) {
                random.nextBytes(buffer) // Fill with random noise (or zeros)
                raf.write(buffer)
                connectionParams += buffer.size
            }
            
            raf.fd.sync() // Force physical sync
            raf.close()
            
            // Now delete
            val deleted = file.delete()
            Log.d(TAG, "File secure delete result: $deleted")
            
        } catch (e: Exception) {
            Log.e(TAG, "Zero-Fill Failed", e)
            // Attempt standard delete as fallback
            file.delete()
        }
    }
    
    /**
     * Helper to zero-out byte arrays in memory.
     */
    fun wipeByteArray(data: ByteArray?) {
        if (data == null) return
        Arrays.fill(data, 0.toByte())
    }
}
