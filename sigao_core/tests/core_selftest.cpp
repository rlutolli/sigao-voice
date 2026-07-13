// Host self-test for Sigao Core.
//
// Verifies the real implementations against known-answer vectors and via
// round-trip / fault-injection tests:
//   1. X25519 against the RFC 7748 known-answer vector
//   2. ECDH key agreement (both peers derive the same key)
//   3. AEAD encrypt/decrypt round-trip + tamper rejection
//   4. Hamming(7,4) FEC round-trip + single-bit error correction
//   5. FSK modem byte loopback (clean channel, with leading offset)
//   6. End-to-end secure channel (encrypt+FEC+modulate -> ... -> decrypt)
//   7. End-to-end secure channel over a noisy channel
//
// Exit code 0 = all passed, non-zero = failure.

#include "sigao_core.h"
#include "dsp/sigao_fec.h"
#include "dsp/sigao_fsk.h"
#include "dsp/sigao_ofdm.h"

extern "C" {
#include "crypto/tweetnacl.h"
}

#include <cstdio>
#include <cstring>
#include <cstdint>
#include <string>
#include <vector>
#include <random>

static int g_failures = 0;
static int g_checks = 0;

static void check(bool cond, const char* name) {
    g_checks++;
    if (cond) {
        std::printf("  [PASS] %s\n", name);
    } else {
        std::printf("  [FAIL] %s\n", name);
        g_failures++;
    }
}

static std::vector<uint8_t> fromHex(const std::string& h) {
    std::vector<uint8_t> v;
    for (size_t i = 0; i + 1 < h.size(); i += 2)
        v.push_back((uint8_t)std::stoi(h.substr(i, 2), nullptr, 16));
    return v;
}

static std::string toHex(const uint8_t* p, int n) {
    static const char* hx = "0123456789abcdef";
    std::string s;
    for (int i = 0; i < n; ++i) { s += hx[p[i] >> 4]; s += hx[p[i] & 0xF]; }
    return s;
}

int main() {
    std::printf("=== Sigao Core Self-Test ===\n");
    std::printf("version: %s\n\n", sigao_version());

    // --- 1. X25519 known-answer test (RFC 7748 section 5.2) ---
    std::printf("[1] X25519 RFC 7748 known-answer vector\n");
    {
        auto scalar = fromHex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4");
        auto ucoord = fromHex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c");
        auto expect = fromHex("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552");
        uint8_t out[32];
        crypto_scalarmult(out, scalar.data(), ucoord.data());
        check(std::memcmp(out, expect.data(), 32) == 0, "X25519 matches RFC 7748 vector");
        if (std::memcmp(out, expect.data(), 32) != 0)
            std::printf("       got %s\n", toHex(out, 32).c_str());
    }

    // --- 2. ECDH agreement via the public C-API ---
    std::printf("[2] X25519 ECDH agreement (public API)\n");
    {
        unsigned char aPub[32], aPriv[32], bPub[32], bPriv[32];
        sigao_gen_keypair(aPub, aPriv);
        sigao_gen_keypair(bPub, bPriv);
        unsigned char k1[32], k2[32];
        sigao_compute_secret(k1, aPriv, bPub);
        sigao_compute_secret(k2, bPriv, aPub);
        check(std::memcmp(k1, k2, 32) == 0, "both peers derive identical shared key");

        // Different keypair must NOT produce the same secret.
        unsigned char cPub[32], cPriv[32], k3[32];
        sigao_gen_keypair(cPub, cPriv);
        sigao_compute_secret(k3, cPriv, aPub);
        check(std::memcmp(k1, k3, 32) != 0, "unrelated key yields different secret");
    }

    // --- 3. AEAD round-trip + tamper detection ---
    std::printf("[3] Authenticated encryption\n");
    {
        unsigned char aPub[32], aPriv[32], bPub[32], bPriv[32], key[32];
        sigao_gen_keypair(aPub, aPriv);
        sigao_gen_keypair(bPub, bPriv);
        sigao_compute_secret(key, aPriv, bPub);

        const char* msg = "Sigao secure channel test message 12345";
        int mlen = (int)std::strlen(msg);
        unsigned char ct[256], pt[256];
        int clen = sigao_encrypt(key, (const unsigned char*)msg, mlen, ct, sizeof(ct));
        check(clen > mlen, "ciphertext produced");

        int dlen = sigao_decrypt(key, ct, clen, pt, sizeof(pt));
        check(dlen == mlen && std::memcmp(pt, msg, mlen) == 0, "decrypt recovers plaintext");

        // Tamper with one ciphertext byte -> must fail authentication.
        ct[clen / 2] ^= 0x01;
        int bad = sigao_decrypt(key, ct, clen, pt, sizeof(pt));
        check(bad < 0, "tampered ciphertext is rejected");
    }

    // --- 4. FEC round-trip + single-bit error correction ---
    std::printf("[4] Hamming(7,4) FEC\n");
    {
        std::vector<uint8_t> data = {0x00, 0xFF, 0xA5, 0x3C, 0x7E, 0x81, 0x42, 0x99};
        auto coded = Sigao::Fec::encode(data);
        int err = 0;
        auto dec = Sigao::Fec::decode(coded, data.size(), err);
        check(dec.size() >= data.size() &&
              std::memcmp(dec.data(), data.data(), data.size()) == 0,
              "clean FEC round-trip");

        // Flip one bit inside each 7-bit codeword region and confirm recovery.
        // Flip a single bit in the coded stream (affects one codeword).
        auto coded2 = coded;
        coded2[0] ^= 0x80; // flip MSB of first coded byte
        int err2 = 0;
        auto dec2 = Sigao::Fec::decode(coded2, data.size(), err2);
        check(std::memcmp(dec2.data(), data.data(), data.size()) == 0 && err2 >= 1,
              "single-bit error corrected by FEC");
    }

    // --- 5. FSK modem byte loopback ---
    std::printf("[5] FSK modem loopback\n");
    {
        Sigao::FskModem modem(8000);
        std::vector<uint8_t> payload;
        for (int i = 0; i < 64; ++i) payload.push_back((uint8_t)(i * 7 + 3));

        auto samples = modem.modulate(payload);
        check(!samples.empty(), "modulation produced samples");

        std::vector<uint8_t> rx;
        bool ok = modem.demodulate(samples, rx, true);
        check(ok && rx == payload, "clean loopback recovers bytes");

        // Prepend leading silence + noise to test sync/offset robustness.
        std::vector<float> shifted(137, 0.0f);
        shifted.insert(shifted.end(), samples.begin(), samples.end());
        std::vector<uint8_t> rx2;
        bool ok2 = modem.demodulate(shifted, rx2, true);
        check(ok2 && rx2 == payload, "loopback with leading offset recovers bytes");
    }

    // --- 6. End-to-end secure channel (clean) ---
    std::printf("[6] End-to-end secure channel (clean)\n");
    {
        void* tx = sigao_create_modem(8000);
        void* rx = sigao_create_modem(8000);
        unsigned char aPub[32], aPriv[32], bPub[32], bPriv[32], key[32];
        sigao_gen_keypair(aPub, aPriv);
        sigao_gen_keypair(bPub, bPriv);
        sigao_compute_secret(key, aPriv, bPub);

        const char* msg = "ATTACK AT DAWN -- secure acoustic link OK";
        int mlen = (int)std::strlen(msg);
        float* audio = nullptr;
        int n = sigao_tx_secure(tx, key, (const unsigned char*)msg, mlen, &audio);
        check(n > 0, "secure TX produced audio");

        unsigned char out[256];
        int rlen = sigao_rx_secure(rx, key, audio, n, out, sizeof(out));
        check(rlen == mlen && std::memcmp(out, msg, mlen) == 0,
              "secure RX recovers exact plaintext");

        if (audio) sigao_free_buffer(audio);
        sigao_destroy_modem(tx);
        sigao_destroy_modem(rx);
    }

    // --- 7. End-to-end secure channel over a noisy channel ---
    std::printf("[7] End-to-end secure channel (additive noise)\n");
    {
        void* tx = sigao_create_modem(8000);
        void* rx = sigao_create_modem(8000);
        unsigned char aPub[32], aPriv[32], bPub[32], bPriv[32], key[32];
        sigao_gen_keypair(aPub, aPriv);
        sigao_gen_keypair(bPub, bPriv);
        sigao_compute_secret(key, aPriv, bPub);

        const char* msg = "noisy channel resilience check";
        int mlen = (int)std::strlen(msg);
        float* audio = nullptr;
        int n = sigao_tx_secure(tx, key, (const unsigned char*)msg, mlen, &audio);

        std::vector<float> noisy(audio, audio + n);
        std::mt19937 rng(12345);
        std::normal_distribution<float> noise(0.0f, 0.08f); // ~ -19 dB vs 0.7 amp
        // Leading silence too.
        std::vector<float> chan(200, 0.0f);
        for (float s : noisy) chan.push_back(s + noise(rng));

        unsigned char out[256];
        int rlen = sigao_rx_secure(rx, key, chan.data(), (int)chan.size(), out, sizeof(out));
        check(rlen == mlen && std::memcmp(out, msg, mlen) == 0,
              "secure RX recovers plaintext through noise");

        if (audio) sigao_free_buffer(audio);
        sigao_destroy_modem(tx);
        sigao_destroy_modem(rx);
    }

    // --- 8. OFDM/DQPSK modem loopback + throughput ---
    std::printf("[8] OFDM/DQPSK modem (high-rate)\n");
    {
        Sigao::OfdmModem ofdm(8000);
        std::printf("       gross bitrate = %d bit/s\n", ofdm.grossBitrate());
        check(ofdm.grossBitrate() >= 2600,
              "gross bitrate sufficient for Codec2 1300 + crypto/FEC");

        std::vector<uint8_t> payload;
        for (int i = 0; i < 96; ++i) payload.push_back((uint8_t)(i * 5 + 1));

        auto samples = ofdm.modulate(payload);
        std::vector<uint8_t> rx;
        check(ofdm.demodulate(samples, rx, true) && rx == payload,
              "OFDM clean loopback recovers bytes");

        std::vector<float> shifted(91, 0.0f);
        shifted.insert(shifted.end(), samples.begin(), samples.end());
        std::vector<uint8_t> rx2;
        check(ofdm.demodulate(shifted, rx2, true) && rx2 == payload,
              "OFDM loopback with leading offset recovers bytes");
    }

    // --- 9. End-to-end secure VOICE channel over OFDM (clean + noise) ---
    std::printf("[9] End-to-end secure voice channel (OFDM)\n");
    {
        void* tx = sigao_create_modem(8000);
        void* rx = sigao_create_modem(8000);
        unsigned char aPub[32], aPriv[32], bPub[32], bPriv[32], key[32];
        sigao_gen_keypair(aPub, aPriv);
        sigao_gen_keypair(bPub, bPriv);
        sigao_compute_secret(key, aPriv, bPub);

        std::printf("       voice gross bitrate = %d bit/s\n", sigao_voice_gross_bitrate(tx));

        // A 7-byte payload models one Codec2-1300 voice frame.
        unsigned char frame[7] = {0xAA, 0x12, 0x7E, 0x03, 0xC4, 0x55, 0x90};
        float* audio = nullptr;
        int n = sigao_tx_voice(tx, key, frame, sizeof(frame), &audio);
        check(n > 0, "voice TX produced audio");

        unsigned char out[64];
        int rlen = sigao_rx_voice(rx, key, audio, n, out, sizeof(out));
        check(rlen == (int)sizeof(frame) && std::memcmp(out, frame, sizeof(frame)) == 0,
              "voice RX recovers Codec2-sized frame (clean)");

        // Noisy channel.
        std::vector<float> chan(120, 0.0f);
        std::mt19937 rng(999);
        std::normal_distribution<float> noise(0.0f, 0.02f);
        for (int i = 0; i < n; ++i) chan.push_back(audio[i] + noise(rng));
        unsigned char out2[64];
        int rlen2 = sigao_rx_voice(rx, key, chan.data(), (int)chan.size(), out2, sizeof(out2));
        check(rlen2 == (int)sizeof(frame) && std::memcmp(out2, frame, sizeof(frame)) == 0,
              "voice RX recovers frame through noise");

        if (audio) sigao_free_buffer(audio);
        sigao_destroy_modem(tx);
        sigao_destroy_modem(rx);
    }

    std::printf("\n=== %d/%d checks passed ===\n", g_checks - g_failures, g_checks);
    if (g_failures) { std::printf("RESULT: FAIL (%d failures)\n", g_failures); return 1; }
    std::printf("RESULT: PASS\n");
    return 0;
}
