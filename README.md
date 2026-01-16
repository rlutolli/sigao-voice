# Sigao Voice: Secure Standalone Dialer
**Project Codename**: Sigao | **Version**: 0.3.0-pro (Hardened)

Sigao is a secure, standalone Android dialer built for resilient communication in hostile network environments. It replaces the system phone app to provide encrypted voice calls over standard data channels (5G/LTE), protected by military-grade error correction.

## Key Features

### 1. Standalone System Dialer
Sigao registers as the default phone app logic (Android `InCallService`).
*   **Lock Screen Support**: Calls ring through and display UI over the lock screen.
*   **Hardware Buttons**: Volume keys control call audio, power button ends calls.
*   **Audio Routing**: Automatically routes audio to earpiece or Bluetooth headset, bypassing system processing where possible.

### 2. Hardware-Backed Security
*   **StrongBox KeyStore**: Identity keys are generated inside the device's dedicated security chip (Titan M / Secure Element). They never leave the hardware.
*   **Perfect Forward Secrecy**: Each call uses a new, ephemeral encryption key generated via `libsodium` (Curve25519).
*   **Anti-Tamper**: The app signature is verified against the hardware keystore.

### 3. DSP Hardening (Phase 6)
The custom C++ audio engine (`SigaoCore`) includes advanced signal processing for unstable networks:
*   **LDPC Error Correction**: Uses IEEE 802.11n standard error correction (Min-Sum Decoder) to recover lost voice packets without retransmission.
*   **Adaptive Jitter Buffer**: Dynamically adjusts buffer depth (20ms to 80ms) to smooth out network delays while keeping latency low.
*   **Pilot Tone Synchronization**: Inserts high-power pilot signals to maintain connection stability even when the cellular vocoder distorts phase.

## Technical Architecture
*   **Frontend**: Flutter (Dart) for UI and system integration.
*   **Core**: C++17 Shared Library (FFI) handling all DSP, Modulation, and Crypto.
*   **Codec**: Codec2 (Mode 1300) for ultra-low bandwidth (1.3kbps) voice compression.

## Build Instructions
1.  **Prerequisites**: Flutter SDK, Android NDK, CMake.
2.  **Compile**: `flutter run --release`
3.  **Benchmark**: `cd tests && python3 hostile_channel_sim.py` (Requires Linux host build of `sigao_core`).
