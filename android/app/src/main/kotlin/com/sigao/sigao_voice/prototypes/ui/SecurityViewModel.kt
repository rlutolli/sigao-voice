package com.sigao.sigao_voice.prototypes.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SecurityViewModel : ViewModel() {

    // --- Shield Pulse State ---
    enum class PulseState {
        SECURE,     // Green (Background Service Active)
        HANDSHAKE,  // Yellow (Negotiating)
        OFFLINE,    // Red (Service Stopped)
        PANIC       // Flashing Red (Wipe Triggered)
    }

    private val _shieldState = MutableStateFlow(PulseState.SECURE)
    val shieldState: StateFlow<PulseState> = _shieldState.asStateFlow()

    // --- Trust Map State ---
    data class ContactShield(
        val name: String,
        val phoneNumber: String,
        val isRcsVerified: Boolean,
        val isSmsVerified: Boolean,
        val isVoiceVerified: Boolean
    ) {
        fun getShieldLevel(): Float {
            // 1.0 = Full Shield, 0.33 = Voice Only
            var score = 0f
            if (isVoiceVerified) score += 0.4f
            if (isSmsVerified) score += 0.3f
            if (isRcsVerified) score += 0.3f
            return score
        }
    }

    private val _contacts = MutableStateFlow<List<ContactShield>>(emptyList())
    val contacts: StateFlow<List<ContactShield>> = _contacts.asStateFlow()

    // --- Vault Config State ---
    data class VaultConfig(
        val isEphemeral: Boolean = false,
        val ephemeralDurationHours: Int = 24,
        val isIncognitoInput: Boolean = true
    )

    private val _vaultConfig = MutableStateFlow(VaultConfig())
    val vaultConfig: StateFlow<VaultConfig> = _vaultConfig.asStateFlow()

    init {
        // Mock Data for Prototype
        loadMockData()
    }

    private fun loadMockData() {
        _contacts.value = listOf(
            ContactShield("Alice", "+15550101", true, true, true), // Full Shield
            ContactShield("Bob", "+15550102", false, true, true),  // Partial
            ContactShield("Charlie", "+15550103", false, false, false) // Unsecured
        )
    }

    // --- Actions ---

    fun setPulseState(state: PulseState) {
        _shieldState.value = state
    }

    fun triggerPanic() {
        _shieldState.value = PulseState.PANIC
        // In real app: IdentityManager.wipeKeys()
        android.util.Log.e("SecurityViewModel", "PANIC TRIGGERED! Wiping keys...")
    }

    fun updateVaultConfig(isEphemeral: Boolean, duration: Int) {
        _vaultConfig.value = _vaultConfig.value.copy(
            isEphemeral = isEphemeral,
            ephemeralDurationHours = duration
        )
    }
}
