#include "sigao_core.h"
#include "dsp/sigao_fsk.h"
#include "dsp/sigao_ofdm.h"
#include "dsp/sigao_fec.h"

#include <vector>
#include <cstring>
#include <cmath>

extern "C" {
#include "crypto/tweetnacl.h"
extern void randombytes(unsigned char*, unsigned long long);
}

#ifdef __ANDROID__
    #include <android/log.h>
    #define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "SigaoNative", __VA_ARGS__)
#else
    #include <cstdio>
    #define LOGD(...) do { } while (0)
#endif

namespace {

constexpr int CRYPTO_KEY     = 32; // crypto_secretbox key bytes
constexpr int CRYPTO_NONCE   = 24; // crypto_secretbox nonce bytes
constexpr int CRYPTO_ZERO    = 32; // crypto_secretbox ZEROBYTES
constexpr int CRYPTO_BOXZERO = 16; // crypto_secretbox BOXZEROBYTES
constexpr int CRYPTO_OVERHEAD = CRYPTO_NONCE + (CRYPTO_ZERO - CRYPTO_BOXZERO); // 24 + 16 = 40

struct SigaoContext {
    Sigao::FskModem  modem;    // low-rate, for text / control / pairing
    Sigao::OfdmModem voice;    // high-rate, for compressed voice
    explicit SigaoContext(int fs) : modem(fs), voice(fs) {}
};

// Authenticated encryption using crypto_secretbox (XSalsa20-Poly1305).
// Output = nonce(24) || boxed(16-byte tag + ciphertext).
int seal(const unsigned char* key, const unsigned char* msg, int msglen,
         std::vector<unsigned char>& out) {
    if (msglen < 0) return -1;

    std::vector<unsigned char> padded(CRYPTO_ZERO + msglen, 0);
    std::memcpy(padded.data() + CRYPTO_ZERO, msg, msglen);

    std::vector<unsigned char> c(CRYPTO_ZERO + msglen, 0);
    unsigned char nonce[CRYPTO_NONCE];
    randombytes(nonce, CRYPTO_NONCE);

    if (crypto_secretbox(c.data(), padded.data(), padded.size(), nonce, key) != 0)
        return -1;

    // Transmit nonce + c[BOXZERO..] (the leading 16 bytes of c are zero padding).
    out.clear();
    out.insert(out.end(), nonce, nonce + CRYPTO_NONCE);
    out.insert(out.end(), c.begin() + CRYPTO_BOXZERO, c.end());
    return (int)out.size();
}

// Reverse of seal(). Returns plaintext length, or -1 on auth failure.
int unseal(const unsigned char* key, const unsigned char* in, int inlen,
           std::vector<unsigned char>& out) {
    if (inlen < CRYPTO_OVERHEAD) return -1;

    const unsigned char* nonce = in;
    int box_len = inlen - CRYPTO_NONCE; // = BOXZERO + ciphertext

    std::vector<unsigned char> c(CRYPTO_BOXZERO + box_len, 0);
    std::memcpy(c.data() + CRYPTO_BOXZERO, in + CRYPTO_NONCE, box_len);

    std::vector<unsigned char> m(c.size(), 0);
    if (crypto_secretbox_open(m.data(), c.data(), c.size(), nonce, key) != 0)
        return -1;

    int msglen = (int)c.size() - CRYPTO_ZERO;
    if (msglen < 0) return -1;
    out.assign(m.begin() + CRYPTO_ZERO, m.end());
    return msglen;
}

// Build the modem payload for a message: seal (AEAD) -> length-prefix -> FEC.
bool buildSecurePayload(const unsigned char* key, const unsigned char* msg, int msglen,
                        std::vector<unsigned char>& coded) {
    std::vector<unsigned char> sealed;
    if (seal(key, msg, msglen, sealed) < 0) return false;

    std::vector<unsigned char> inner;
    inner.push_back((unsigned char)(sealed.size() >> 8));
    inner.push_back((unsigned char)(sealed.size() & 0xFF));
    inner.insert(inner.end(), sealed.begin(), sealed.end());

    coded = Sigao::Fec::encode(inner);
    return true;
}

// Reverse of buildSecurePayload: FEC-decode -> read length -> AEAD open.
int recoverSecure(const unsigned char* key, const std::vector<unsigned char>& coded,
                  std::vector<unsigned char>& plain) {
    size_t num_bytes = (coded.size() * 8) / 14;
    if (num_bytes < 2) return -1;

    int corrections = 0;
    std::vector<unsigned char> inner = Sigao::Fec::decode(coded, num_bytes, corrections);
    if (inner.size() < 2) return -1;

    int sealed_len = (inner[0] << 8) | inner[1];
    if (sealed_len < 0 || (size_t)(2 + sealed_len) > inner.size()) return -1;

    return unseal(key, inner.data() + 2, sealed_len, plain);
}

} // namespace

extern "C" {

SIGAO_API void* sigao_create_modem(int sampleRate) {
    LOGD("Sigao core init @ %d Hz", sampleRate);
    return new SigaoContext(sampleRate > 0 ? sampleRate : 8000);
}

SIGAO_API void sigao_destroy_modem(void* handle) {
    delete static_cast<SigaoContext*>(handle);
}

SIGAO_API const char* sigao_version() {
    return "0.4.1 (x25519+aead, fsk+ofdm, fec)";
}

SIGAO_API void sigao_free_buffer(float* buffer) {
    delete[] buffer;
}

// ---- Crypto ----

SIGAO_API void sigao_gen_keypair(unsigned char* public_key, unsigned char* private_key) {
    crypto_box_keypair(public_key, private_key); // X25519: pub = scalarmult_base(priv)
}

SIGAO_API int sigao_compute_secret(unsigned char* shared_key,
                                   const unsigned char* my_private,
                                   const unsigned char* their_public) {
    // X25519 DH followed by HSalsa20, matching crypto_box's beforenm step so
    // both peers derive an identical 32-byte key.
    return crypto_box_beforenm(shared_key, their_public, my_private);
}

SIGAO_API int sigao_encrypt(const unsigned char* shared_key,
                            const unsigned char* msg, int msglen,
                            unsigned char* out, int max_out) {
    std::vector<unsigned char> sealed;
    int n = seal(shared_key, msg, msglen, sealed);
    if (n < 0 || n > max_out) return -1;
    std::memcpy(out, sealed.data(), n);
    return n;
}

SIGAO_API int sigao_decrypt(const unsigned char* shared_key,
                            const unsigned char* in, int inlen,
                            unsigned char* out, int max_out) {
    std::vector<unsigned char> plain;
    int n = unseal(shared_key, in, inlen, plain);
    if (n < 0 || n > max_out) return -1;
    if (n > 0) std::memcpy(out, plain.data(), n);
    return n;
}

// ---- Low-level modem ----

SIGAO_API int sigao_modulate_bytes(void* handle, const unsigned char* data, int len,
                                   float** outBuffer) {
    auto* ctx = static_cast<SigaoContext*>(handle);
    std::vector<unsigned char> payload(data, data + len);
    std::vector<float> samples = ctx->modem.modulate(payload);
    if (samples.empty()) return 0;
    *outBuffer = new float[samples.size()];
    std::memcpy(*outBuffer, samples.data(), samples.size() * sizeof(float));
    return (int)samples.size();
}

SIGAO_API int sigao_demodulate_bytes(void* handle, const float* signal, int len,
                                     unsigned char* out, int max_out) {
    auto* ctx = static_cast<SigaoContext*>(handle);
    std::vector<float> samples(signal, signal + len);
    std::vector<unsigned char> payload;
    if (!ctx->modem.demodulate(samples, payload, /*require_crc=*/true)) return -1;
    if ((int)payload.size() > max_out) return -1;
    std::memcpy(out, payload.data(), payload.size());
    return (int)payload.size();
}

// ---- Secure channel ----

SIGAO_API int sigao_tx_secure(void* handle, const unsigned char* shared_key,
                              const unsigned char* msg, int msglen, float** outBuffer) {
    auto* ctx = static_cast<SigaoContext*>(handle);
    std::vector<unsigned char> coded;
    if (!buildSecurePayload(shared_key, msg, msglen, coded)) return -1;

    std::vector<float> samples = ctx->modem.modulate(coded);
    if (samples.empty()) return -1;
    *outBuffer = new float[samples.size()];
    std::memcpy(*outBuffer, samples.data(), samples.size() * sizeof(float));
    return (int)samples.size();
}

SIGAO_API int sigao_rx_secure(void* handle, const unsigned char* shared_key,
                              const float* signal, int len,
                              unsigned char* out, int max_out) {
    auto* ctx = static_cast<SigaoContext*>(handle);
    std::vector<float> samples(signal, signal + len);
    std::vector<unsigned char> coded;
    // FEC + AEAD provide integrity, so don't gate on the transport CRC.
    if (!ctx->modem.demodulate(samples, coded, /*require_crc=*/false)) return -1;

    std::vector<unsigned char> plain;
    int n = recoverSecure(shared_key, coded, plain);
    if (n < 0 || n > max_out) return -1;
    if (n > 0) std::memcpy(out, plain.data(), n);
    return n;
}

// ---- High-rate secure channel over OFDM (voice path) ----

SIGAO_API int sigao_tx_voice(void* handle, const unsigned char* shared_key,
                             const unsigned char* msg, int msglen, float** outBuffer) {
    auto* ctx = static_cast<SigaoContext*>(handle);
    std::vector<unsigned char> coded;
    if (!buildSecurePayload(shared_key, msg, msglen, coded)) return -1;

    std::vector<float> samples = ctx->voice.modulate(coded);
    if (samples.empty()) return -1;
    *outBuffer = new float[samples.size()];
    std::memcpy(*outBuffer, samples.data(), samples.size() * sizeof(float));
    return (int)samples.size();
}

SIGAO_API int sigao_rx_voice(void* handle, const unsigned char* shared_key,
                             const float* signal, int len,
                             unsigned char* out, int max_out) {
    auto* ctx = static_cast<SigaoContext*>(handle);
    std::vector<float> samples(signal, signal + len);
    std::vector<unsigned char> coded;
    if (!ctx->voice.demodulate(samples, coded, /*require_crc=*/false)) return -1;

    std::vector<unsigned char> plain;
    int n = recoverSecure(shared_key, coded, plain);
    if (n < 0 || n > max_out) return -1;
    if (n > 0) std::memcpy(out, plain.data(), n);
    return n;
}

SIGAO_API int sigao_voice_gross_bitrate(void* handle) {
    auto* ctx = static_cast<SigaoContext*>(handle);
    return ctx->voice.grossBitrate();
}

// ---- Handshake detection ----

SIGAO_API int sigao_detect_handshake(void* handle, const short* pcm, int len) {
    auto* ctx = static_cast<SigaoContext*>(handle);
    if (len <= 0) return 0;

    std::vector<float> x(len);
    double energy = 0.0;
    for (int i = 0; i < len; ++i) {
        x[i] = (float)pcm[i] / 32768.0f;
        energy += (double)x[i] * x[i];
    }
    if (energy < 1e-4) return 0;

    // Detect a strong tone at either modem frequency.
    int fs = ctx->modem.sampleRate();
    int N = ctx->modem.samplesPerSymbol();
    if (len < N) N = len;

    auto goertzel = [&](float freq) {
        double w = 2.0 * 3.14159265358979323846 * freq / fs;
        double coeff = 2.0 * std::cos(w);
        double s1 = 0, s2 = 0;
        for (int i = 0; i < N; ++i) {
            double s0 = x[i] + coeff * s1 - s2;
            s2 = s1; s1 = s0;
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2;
    };

    double tone = goertzel(1000.0f) + goertzel(2000.0f);
    return (tone > energy * 0.5) ? 1 : 0;
}

} // extern "C"
