#ifndef SIGAO_CORE_H
#define SIGAO_CORE_H

#include <cstdint>

#ifdef _WIN32
    #ifdef SIGAO_EXPORTS
        #define SIGAO_API __declspec(dllexport)
    #else
        #define SIGAO_API __declspec(dllimport)
    #endif
#else
    #define SIGAO_API __attribute__((visibility("default")))
#endif

/*
 * Sigao Core C-API (FFI surface for Flutter / Android / host tests).
 *
 * This build provides a *real*, verifiable secure acoustic data channel:
 *   - X25519 ECDH + XSalsa20-Poly1305 authenticated encryption (TweetNaCl)
 *   - Hamming(7,4) forward error correction
 *   - Binary FSK modem with working modulate AND demodulate
 *
 * The voice vocoder (Codec2) is intentionally NOT part of this API yet; that
 * integration is tracked separately. Everything exposed here is exercised by
 * the host test suite (tests/core_selftest + tests/hostile_channel_sim.py).
 */

extern "C" {

// ---- Lifecycle ----
SIGAO_API void*       sigao_create_modem(int sampleRate);
SIGAO_API void        sigao_destroy_modem(void* handle);
SIGAO_API const char* sigao_version();
SIGAO_API void        sigao_free_buffer(float* buffer);

// ---- Crypto: X25519 key agreement (32-byte keys) ----
// Generates an X25519 keypair. Buffers must be 32 bytes each.
SIGAO_API void sigao_gen_keypair(unsigned char* public_key, unsigned char* private_key);

// Derives the 32-byte shared symmetric key from our private key and the peer's
// public key (X25519 + HSalsa20, identical on both ends). Returns 0 on success.
SIGAO_API int  sigao_compute_secret(unsigned char* shared_key,
                                    const unsigned char* my_private,
                                    const unsigned char* their_public);

// ---- Crypto: authenticated encryption with a shared key ----
// Output layout: [24-byte nonce][16-byte tag + ciphertext].
// Returns bytes written, or <0 on error. 'out' must have room for msglen+40.
SIGAO_API int sigao_encrypt(const unsigned char* shared_key,
                            const unsigned char* msg, int msglen,
                            unsigned char* out, int max_out);

// Verifies and decrypts. Returns plaintext length, or <0 on auth failure.
SIGAO_API int sigao_decrypt(const unsigned char* shared_key,
                            const unsigned char* in, int inlen,
                            unsigned char* out, int max_out);

// ---- Low-level modem (no crypto/FEC) — primarily for tests ----
// Modulate raw bytes to audio. Allocates *outBuffer (free with sigao_free_buffer).
// Returns sample count, or 0 on error.
SIGAO_API int sigao_modulate_bytes(void* handle, const unsigned char* data, int len,
                                   float** outBuffer);

// Demodulate audio to raw bytes (requires transport CRC). Returns byte count
// written to 'out', or <0 if no valid frame was found.
SIGAO_API int sigao_demodulate_bytes(void* handle, const float* signal, int len,
                                     unsigned char* out, int max_out);

// ---- End-to-end secure channel (crypto + FEC + modem) ----
// Encrypt -> FEC-encode -> frame -> FSK modulate. Allocates *outBuffer.
// Returns sample count, or <0 on error.
SIGAO_API int sigao_tx_secure(void* handle, const unsigned char* shared_key,
                              const unsigned char* msg, int msglen, float** outBuffer);

// Demodulate -> FEC-decode -> verify+decrypt. Returns plaintext length written
// to 'out', or <0 if the frame could not be recovered/authenticated.
SIGAO_API int sigao_rx_secure(void* handle, const unsigned char* shared_key,
                              const float* signal, int len,
                              unsigned char* out, int max_out);

// ---- High-rate secure channel (OFDM/DQPSK, for compressed voice) ----
// Same crypto + FEC pipeline as sigao_tx/rx_secure, but carried over the
// multi-carrier OFDM modem (~3200 bit/s gross) instead of FSK (~200 bit/s),
// which is required to sustain a Codec2 voice stream.
SIGAO_API int sigao_tx_voice(void* handle, const unsigned char* shared_key,
                             const unsigned char* msg, int msglen, float** outBuffer);
SIGAO_API int sigao_rx_voice(void* handle, const unsigned char* shared_key,
                             const float* signal, int len,
                             unsigned char* out, int max_out);

// Gross (pre-FEC, pre-crypto) bit rate of the OFDM voice modem, in bit/s.
SIGAO_API int sigao_voice_gross_bitrate(void* handle);

// ---- Handshake tone detection ----
// Returns 1 if a modem tone is detected in the PCM window, else 0.
SIGAO_API int sigao_detect_handshake(void* handle, const short* pcm, int len);

} // extern "C"

#endif
