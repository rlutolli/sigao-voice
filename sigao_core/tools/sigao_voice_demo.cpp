// sigao_voice_demo — full secure-voice procedure, end to end, with real audio.
//
//   mic WAV -> Codec2 1300 (40ms/320-sample frames) -> X25519/XSalsa20-Poly1305
//           -> UDP (loopback) -> decrypt+verify -> Codec2 decode -> WAV out
//
// Simulates a back-and-forth call: Alice transmits her clip to Bob, then Bob
// transmits his to Alice, each paced in real time at 40 ms/frame. Writes what
// each party RECEIVED over the encrypted link and measures per-frame
// mouth-to-ear latency.
//
//   usage: sigao_voice_demo <alice.wav> <bob.wav> <out_dir>
//
// Uses the SAME native crypto the app uses (sigao_core) and the real Codec2.

#include "sigao_core.h"
#include "codec2.h"

#include <cstdio>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <chrono>
#include <algorithm>
#include <cmath>

#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>

using clk = std::chrono::steady_clock;
static int64_t now_ns() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(clk::now().time_since_epoch()).count();
}

// ---------- WAV (PCM16 mono) ----------
static uint32_t rd_u32(const uint8_t* p) { return p[0] | (p[1]<<8) | (p[2]<<16) | ((uint32_t)p[3]<<24); }
static uint16_t rd_u16(const uint8_t* p) { return p[0] | (p[1]<<8); }

static std::vector<int16_t> read_wav(const std::string& path, int& sampleRate) {
    std::vector<int16_t> pcm;
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) { std::fprintf(stderr, "cannot open %s\n", path.c_str()); return pcm; }
    fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    std::vector<uint8_t> buf(sz);
    fread(buf.data(), 1, sz, f); fclose(f);
    if (sz < 12 || memcmp(buf.data(), "RIFF", 4) || memcmp(buf.data()+8, "WAVE", 4)) {
        std::fprintf(stderr, "not a WAV: %s\n", path.c_str()); return pcm;
    }
    sampleRate = 8000;
    size_t off = 12;
    while (off + 8 <= (size_t)sz) {
        const uint8_t* ch = buf.data() + off;
        uint32_t clen = rd_u32(ch + 4);
        if (!memcmp(ch, "fmt ", 4)) {
            sampleRate = (int)rd_u32(ch + 12);
        } else if (!memcmp(ch, "data", 4)) {
            size_t n = std::min((size_t)clen, (size_t)sz - off - 8);
            const int16_t* s = (const int16_t*)(ch + 8);
            pcm.assign(s, s + n / 2);
            break;
        }
        off += 8 + clen + (clen & 1);
    }
    return pcm;
}

static void write_wav(const std::string& path, const std::vector<int16_t>& pcm, int sampleRate) {
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) { std::fprintf(stderr, "cannot write %s\n", path.c_str()); return; }
    uint32_t dataBytes = (uint32_t)(pcm.size() * 2);
    uint32_t riff = 36 + dataBytes;
    uint16_t ch = 1, bps = 16, fmt = 1;
    uint32_t byteRate = sampleRate * ch * bps / 8;
    uint16_t blockAlign = ch * bps / 8;
    fwrite("RIFF", 1, 4, f); fwrite(&riff, 4, 1, f); fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f); uint32_t f16 = 16; fwrite(&f16, 4, 1, f);
    fwrite(&fmt, 2, 1, f); fwrite(&ch, 2, 1, f);
    uint32_t sr = sampleRate; fwrite(&sr, 4, 1, f); fwrite(&byteRate, 4, 1, f);
    fwrite(&blockAlign, 2, 1, f); fwrite(&bps, 2, 1, f);
    fwrite("data", 1, 4, f); fwrite(&dataBytes, 4, 1, f);
    fwrite(pcm.data(), 2, pcm.size(), f);
    fclose(f);
}

// ---------- UDP ----------
static int make_udp(int port) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    sockaddr_in a{}; a.sin_family = AF_INET; a.sin_addr.s_addr = htonl(INADDR_LOOPBACK); a.sin_port = htons(port);
    if (bind(fd, (sockaddr*)&a, sizeof(a)) < 0) { perror("bind"); exit(1); }
    return fd;
}
static sockaddr_in addr_of(int port) {
    sockaddr_in a{}; a.sin_family = AF_INET; a.sin_addr.s_addr = htonl(INADDR_LOOPBACK); a.sin_port = htons(port);
    return a;
}

static const int N_SAMP = 320;          // Codec2 1300 frame (40 ms @ 8 kHz)
static const uint32_t SEQ_END = 0xFFFFFFFFu;

// Receiver thread: decrypt -> codec2 decode -> append PCM; record latency.
struct RxCtx {
    int fd;
    const unsigned char* key;
    struct CODEC2* dec;
    std::vector<int16_t> pcm;
    std::vector<double> latency_ms;
    std::atomic<bool> done{false};
};

static void rx_loop(RxCtx* ctx) {
    unsigned char buf[256];
    unsigned char pt[64];
    short speech[N_SAMP];
    while (!ctx->done.load()) {
        ssize_t n = recv(ctx->fd, buf, sizeof(buf), 0);
        if (n <= 0) continue;
        int plen = sigao_decrypt(ctx->key, buf, (int)n, pt, sizeof(pt));
        if (plen < 12) continue; // [seq(4)][tx_ns(8)] minimum
        uint32_t seq = (pt[0]<<24)|(pt[1]<<16)|(pt[2]<<8)|pt[3];
        int64_t tx_ns = 0; for (int i = 0; i < 8; ++i) tx_ns = (tx_ns << 8) | pt[4 + i];
        if (seq == SEQ_END) { ctx->done.store(true); break; }
        int64_t rx_ns = now_ns();
        ctx->latency_ms.push_back((rx_ns - tx_ns) / 1e6);
        // Remaining bytes are the Codec2 frame.
        unsigned char* c2 = pt + 12;
        codec2_decode(ctx->dec, speech, c2);
        ctx->pcm.insert(ctx->pcm.end(), speech, speech + N_SAMP);
    }
}

// Transmit src PCM as Codec2+AEAD frames, paced at 40 ms.
static void transmit(const std::vector<int16_t>& src, int fromFd, int toPort,
                     const unsigned char* key, struct CODEC2* enc) {
    auto to = addr_of(toPort);
    int nbyte = (codec2_bits_per_frame(enc) + 7) / 8;
    std::vector<int16_t> frame(N_SAMP);
    uint32_t seq = 0;
    for (size_t i = 0; i < src.size(); i += N_SAMP) {
        for (int j = 0; j < N_SAMP; ++j) frame[j] = (i + j < src.size()) ? src[i + j] : 0;
        std::vector<unsigned char> c2(nbyte);
        codec2_encode(enc, c2.data(), frame.data());

        // plaintext = [seq(4)][tx_ns(8)][codec2 bytes]
        std::vector<unsigned char> plain(12 + nbyte);
        plain[0]=(seq>>24)&0xFF; plain[1]=(seq>>16)&0xFF; plain[2]=(seq>>8)&0xFF; plain[3]=seq&0xFF;
        int64_t t = now_ns();
        for (int b = 0; b < 8; ++b) plain[4 + b] = (t >> (8 * (7 - b))) & 0xFF;
        memcpy(plain.data() + 12, c2.data(), nbyte);

        unsigned char ct[256];
        int clen = sigao_encrypt(key, plain.data(), (int)plain.size(), ct, sizeof(ct));
        if (clen > 0) sendto(fromFd, ct, clen, 0, (sockaddr*)&to, sizeof(to));
        seq++;
        std::this_thread::sleep_for(std::chrono::milliseconds(40)); // real-time pacing
    }
    // End marker.
    std::vector<unsigned char> end(12);
    end[0]=end[1]=end[2]=end[3]=0xFF;
    int64_t t = now_ns(); for (int b = 0; b < 8; ++b) end[4 + b] = (t >> (8 * (7 - b))) & 0xFF;
    unsigned char ct[64];
    int clen = sigao_encrypt(key, end.data(), (int)end.size(), ct, sizeof(ct));
    auto to2 = addr_of(toPort);
    if (clen > 0) sendto(fromFd, ct, clen, 0, (sockaddr*)&to2, sizeof(to2));
}

static void stats(const std::vector<double>& v, const char* label) {
    if (v.empty()) { std::printf("  %s: no frames\n", label); return; }
    double mn = 1e18, mx = -1e18, sum = 0;
    for (double x : v) { mn = std::min(mn, x); mx = std::max(mx, x); sum += x; }
    double mean = sum / v.size();
    double var = 0; for (double x : v) var += (x - mean) * (x - mean);
    double sd = std::sqrt(var / v.size());
    std::printf("  %s: frames=%zu  mean=%.3f ms  min=%.3f  max=%.3f  jitter(sd)=%.3f ms\n",
                label, v.size(), mean, mn, mx, sd);
}

int main(int argc, char** argv) {
    if (argc < 4) { std::fprintf(stderr, "usage: %s alice.wav bob.wav out_dir\n", argv[0]); return 2; }
    std::string aPath = argv[1], bPath = argv[2], outDir = argv[3];

    int sr = 8000;
    auto aliceVoice = read_wav(aPath, sr);
    auto bobVoice = read_wav(bPath, sr);
    if (aliceVoice.empty() || bobVoice.empty()) { std::fprintf(stderr, "failed to read inputs\n"); return 1; }
    std::printf("Sigao secure voice demo (core %s)\n", sigao_version());
    std::printf("Alice clip: %.2fs, Bob clip: %.2fs @ %d Hz\n",
                aliceVoice.size() / 8000.0, bobVoice.size() / 8000.0, sr);

    // X25519 ECDH (same primitives as the app).
    unsigned char aPub[32], aPriv[32], bPub[32], bPriv[32], aKey[32], bKey[32];
    sigao_gen_keypair(aPub, aPriv);
    sigao_gen_keypair(bPub, bPriv);
    sigao_compute_secret(aKey, aPriv, bPub);
    sigao_compute_secret(bKey, bPriv, aPub);
    if (memcmp(aKey, bKey, 32) != 0) { std::fprintf(stderr, "ECDH mismatch!\n"); return 1; }
    std::printf("X25519 handshake OK; shared key established\n");

    int aliceFd = make_udp(41001);
    int bobFd   = make_udp(41002);

    struct CODEC2* aliceEnc = codec2_create(CODEC2_MODE_1300);
    struct CODEC2* bobEnc   = codec2_create(CODEC2_MODE_1300);
    struct CODEC2* bobDec   = codec2_create(CODEC2_MODE_1300);
    struct CODEC2* aliceDec = codec2_create(CODEC2_MODE_1300);

    std::printf("Codec2 1300: %d samples/frame, %d bits/frame -> %d bytes\n",
                codec2_samples_per_frame(aliceEnc), codec2_bits_per_frame(aliceEnc),
                (codec2_bits_per_frame(aliceEnc) + 7) / 8);

    // --- Turn 1: Alice -> Bob ---
    RxCtx bobRx; bobRx.fd = bobFd; bobRx.key = bKey; bobRx.dec = bobDec;
    std::thread bobThread(rx_loop, &bobRx);
    std::printf("\n[Turn 1] Alice speaking to Bob (%.1fs)...\n", aliceVoice.size() / 8000.0);
    transmit(aliceVoice, aliceFd, 41002, aKey, aliceEnc);
    bobThread.join();

    // --- Turn 2: Bob -> Alice ---
    RxCtx aliceRx; aliceRx.fd = aliceFd; aliceRx.key = aKey; aliceRx.dec = aliceDec;
    std::thread aliceThread(rx_loop, &aliceRx);
    std::printf("[Turn 2] Bob replying to Alice (%.1fs)...\n", bobVoice.size() / 8000.0);
    transmit(bobVoice, bobFd, 41001, bKey, bobEnc);
    aliceThread.join();

    // --- Outputs ---
    write_wav(outDir + "/bob_received_from_alice.wav", bobRx.pcm, 8000);
    write_wav(outDir + "/alice_received_from_bob.wav", aliceRx.pcm, 8000);

    // Full conversation as heard over the secure link (Alice's turn, then Bob's).
    std::vector<int16_t> convo;
    convo.insert(convo.end(), bobRx.pcm.begin(), bobRx.pcm.end());
    std::vector<int16_t> gap(8000 / 2, 0); // 0.5s gap
    convo.insert(convo.end(), gap.begin(), gap.end());
    convo.insert(convo.end(), aliceRx.pcm.begin(), aliceRx.pcm.end());
    write_wav(outDir + "/conversation.wav", convo, 8000);

    std::printf("\n=== Latency (per 40 ms frame, transport + crypto, loopback) ===\n");
    stats(bobRx.latency_ms, "Alice->Bob");
    stats(aliceRx.latency_ms, "Bob->Alice");
    std::printf("Note: effective mouth-to-ear = 40 ms framing + the above + jitter buffer\n");
    std::printf("      (production jitter buffer adds ~80-160 ms).\n");

    std::printf("\nWrote: bob_received_from_alice.wav, alice_received_from_bob.wav, conversation.wav\n");

    codec2_destroy(aliceEnc); codec2_destroy(bobEnc);
    codec2_destroy(bobDec); codec2_destroy(aliceDec);
    close(aliceFd); close(bobFd);
    return 0;
}
