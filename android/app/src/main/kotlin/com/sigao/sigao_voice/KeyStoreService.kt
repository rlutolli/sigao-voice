package com.sigao.sigao_voice

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import android.util.Log

class KeyStoreService(private val context: Context) {
    private val KEY_ALIAS = "SigaoIdentityKey"
    private val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private val TAG = "KeyStoreService"

    fun generateIdentityKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                Log.d(TAG, "Key already exists.")
                return true
            }

            val hasStrongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            Log.d(TAG, "StrongBox Available: $hasStrongBox")

            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                KEYSTORE_PROVIDER
            )
            
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            // .setUserAuthenticationRequired(true) // Optional: Require biometric?
            
            if (hasStrongBox) {
                builder.setIsStrongBoxBacked(true)
            }

            kpg.initialize(builder.build())
            kpg.generateKeyPair()
            
            Log.d(TAG, "Identity Key Generated (StrongBox: $hasStrongBox)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Key Gen Failed: $e")
            false
        }
    }

    fun getPublicKey(): String? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        return entry?.certificate?.publicKey?.encoded?.let {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
    }

    fun signData(data: ByteArray): ByteArray? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null

            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(entry.privateKey)
            signature.update(data)
            signature.sign()
        } catch (e: Exception) {
            Log.e(TAG, "Sign Failed: $e")
            null
        }
    }
}
