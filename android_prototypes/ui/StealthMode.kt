package com.sigao.prototypes.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText

/**
 * Enforces local "Stealth" protections:
 * 1. Incognito Keyboard (No Learning)
 * 2. Screen Shield (No Screenshots)
 * 3. Private Space awareness
 */
object StealthMode {

    /**
     * Apply "Incognito" flags to an EditText.
     * Prevents Gboard/SwiftKey from learning typed words.
     */
    fun applyIncognitoKeyboard(editText: EditText) {
        // IME_FLAG_NO_PERSONALIZED_LEARNING (API 26+)
        // Suggests to the IME that it should not learn from the user's input.
        editText.imeOptions = editText.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        
        // Additional hints for privacy
        editText.inputType = editText.inputType or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    /**
     * Apply "Screen Shield" to an Activity.
     * Prevents screenshots and screen recording (returns black screen).
     */
    fun applyScreenShield(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    /**
     * Checks if the app is running in a "Private Space" (Android 15/16+ feature).
     * Uses UserManager.isQuietModeEnabled() heuristic or profile checks.
     */
    fun isRunningInPrivateSpace(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 35) return false // Pre-Android 16 assumption

        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        
        // Logic depends on exact Android 16 API (Private Space usually runs as a separate profile)
        // This is a heuristic placeholder:
        val isProfile = userManager.isUserAGoat // Placeholder for actual profile check
        // Real implementation would check userManager.isPrivateProfile() (hypothetical future API)
        
        return false // Default for prototype
    }

    /**
     * Returns true if the "Unsecured Link" banner should be shown.
     * @param isSecureContact State from ProtocolOrchestrator
     */
    fun shouldShowUnsecuredWarning(isSecureContact: Boolean): Boolean {
        return !isSecureContact
    }
}
