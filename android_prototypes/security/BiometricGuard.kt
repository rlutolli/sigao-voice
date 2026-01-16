package com.sigao.prototypes.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Gates access to the App (Level 1) and Secure Threads (Level 2).
 * Enforces Class 3 (Strong) Biometrics.
 */
class BiometricGuard(private val activity: FragmentActivity) {

    private val executor: Executor = ContextCompat.getMainExecutor(activity)

    /**
     * Authenticates the user for a specific action (e.g., "Open Database", "View Thread").
     */
    fun authenticate(
        reasonTitle: String = "Unlock Sigao",
        reasonSubtitle: String = "Biometric authentication required",
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (Int, CharSequence) -> Unit
    ) {
        if (!canAuthenticateStrong()) {
            onError(-1, "Strong Biometrics not available on this device.")
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(reasonTitle)
            .setSubtitle(reasonSubtitle)
            // .setNegativeButtonText("Use PIN") // Optional fallback
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Soft failure (wrong finger), prompt stays open
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }

    private fun canAuthenticateStrong(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
