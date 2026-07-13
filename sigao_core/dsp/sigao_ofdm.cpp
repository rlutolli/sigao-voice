#include "sigao_ofdm.h"
#include "sigao_fec.h"
#include <cmath>
#include <algorithm>

namespace Sigao {

namespace {
    constexpr double kPI = 3.14159265358979323846;
    constexpr double kTwoPI = 2.0 * kPI;

    const uint8_t kSyncBytes[4] = {0x1A, 0xCF, 0xFC, 0x1D};
    constexpr int   kMaxPayload = 4096;
    constexpr int   kSyncMismatchTol = 4;

    // DQPSK Gray mapping: index -> phase increment (units of pi/2).
    // bits (hi,lo): 00->0, 01->1, 11->2, 10->3
    inline int bitsToIncr(int hi, int lo) {
        int v = (hi << 1) | lo;       // 00,01,10,11
        switch (v) {
            case 0: return 0; // 00
            case 1: return 1; // 01
            case 3: return 2; // 11
            case 2: return 3; // 10
        }
        return 0;
    }
    inline void incrToBits(int idx, int& hi, int& lo) {
        switch (((idx % 4) + 4) % 4) {
            case 0: hi = 0; lo = 0; break;
            case 1: hi = 0; lo = 1; break;
            case 2: hi = 1; lo = 1; break;
            case 3: hi = 1; lo = 0; break;
        }
    }
}

OfdmModem::OfdmModem(int sampleRate)
    : fs_(sampleRate),
      Nfft_(128),
      Ncp_(32),
      Nsym_(160),
      baud_(sampleRate / 160),
      firstBin_(8),
      numCarriers_(32),
      amp_(0.9f) {
    sync_bits_.reserve(32);
    for (int i = 0; i < 4; ++i)
        for (int b = 7; b >= 0; --b)
            sync_bits_.push_back((kSyncBytes[i] >> b) & 1);
}

std::vector<float> OfdmModem::modulate(const std::vector<uint8_t>& payload) {
    // Frame bit stream: sync(32) + len(16) + payload + crc(16), zero-padded to
    // a whole number of OFDM symbols.
    std::vector<int> bits;
    bits.insert(bits.end(), sync_bits_.begin(), sync_bits_.end());

    uint16_t len = (uint16_t)payload.size();
    for (int b = 15; b >= 0; --b) bits.push_back((len >> b) & 1);
    for (uint8_t byte : payload)
        for (int b = 7; b >= 0; --b) bits.push_back((byte >> b) & 1);

    std::vector<uint8_t> crc_input;
    crc_input.push_back((uint8_t)(len >> 8));
    crc_input.push_back((uint8_t)(len & 0xFF));
    crc_input.insert(crc_input.end(), payload.begin(), payload.end());
    uint16_t crc = crc16_ccitt(crc_input.data(), crc_input.size());
    for (int b = 15; b >= 0; --b) bits.push_back((crc >> b) & 1);

    int bitsPerSym = numCarriers_ * 2;
    while ((int)bits.size() % bitsPerSym != 0) bits.push_back(0);
    int numDataSym = (int)bits.size() / bitsPerSym;

    // Running phase per carrier (radians). Reference symbol uses phase 0.
    std::vector<double> phase(numCarriers_, 0.0);

    // Helper to render one OFDM symbol (with cyclic prefix) given per-carrier phases.
    auto renderSymbol = [&](const std::vector<double>& ph, std::vector<float>& out) {
        for (int n = -Ncp_; n < Nfft_; ++n) {
            double acc = 0.0;
            for (int c = 0; c < numCarriers_; ++c) {
                int k = firstBin_ + c;
                acc += std::cos(kTwoPI * k * n / (double)Nfft_ + ph[c]);
            }
            out.push_back((float)(amp_ * acc / numCarriers_));
        }
    };

    std::vector<float> out;
    out.reserve((numDataSym + 2) * Nsym_ + Nfft_);

    // Short silence lead-in so the receiver's energy detector has a clean edge.
    for (int i = 0; i < Nsym_ / 2; ++i) out.push_back(0.0f);

    // Reference symbol (all carriers phase 0).
    renderSymbol(phase, out);

    // Data symbols (differential).
    int bi = 0;
    for (int m = 0; m < numDataSym; ++m) {
        for (int c = 0; c < numCarriers_; ++c) {
            int hi = bits[bi++];
            int lo = bits[bi++];
            phase[c] += (kPI / 2.0) * bitsToIncr(hi, lo);
        }
        renderSymbol(phase, out);
    }

    for (int i = 0; i < Nsym_; ++i) out.push_back(0.0f); // tail
    return out;
}

void OfdmModem::measureSymbol(const std::vector<float>& s, int start,
                              std::vector<float>& phases) const {
    phases.assign(numCarriers_, 0.0f);
    for (int c = 0; c < numCarriers_; ++c) {
        int k = firstBin_ + c;
        double I = 0.0, Q = 0.0;
        for (int n = 0; n < Nfft_; ++n) {
            double x = s[start + n];
            double ang = kTwoPI * k * n / (double)Nfft_;
            I += x * std::cos(ang);
            Q -= x * std::sin(ang);
        }
        phases[c] = (float)std::atan2(Q, I);
    }
}

bool OfdmModem::tryDecode(const std::vector<float>& s, int s0,
                          std::vector<uint8_t>& payload,
                          int& sync_mismatch, bool& crc_ok) const {
    const int total = (int)s.size();
    // s0 points at the useful part (post-CP) of the reference symbol.
    if (s0 < 0 || s0 + Nfft_ > total) return false;

    std::vector<float> prev, cur;
    measureSymbol(s, s0, prev);

    std::vector<int> bits;
    int m = 1;
    while (true) {
        int start = s0 + m * Nsym_;
        if (start + Nfft_ > total) break;
        measureSymbol(s, start, cur);
        for (int c = 0; c < numCarriers_; ++c) {
            double d = cur[c] - prev[c];
            int idx = (int)std::lround(d / (kPI / 2.0));
            int hi, lo;
            incrToBits(idx, hi, lo);
            bits.push_back(hi);
            bits.push_back(lo);
        }
        prev = cur;
        ++m;
    }

    const int S = (int)sync_bits_.size();
    if ((int)bits.size() < S + 32) return false;

    int maxSyncPos = (int)bits.size() - S;
    int bestMismatch = kSyncMismatchTol + 1;
    int bestPos = -1;
    for (int p = 0; p <= maxSyncPos; ++p) {
        int mism = 0;
        for (int k = 0; k < S && mism <= kSyncMismatchTol; ++k)
            if (bits[p + k] != sync_bits_[k]) mism++;
        if (mism < bestMismatch) { bestMismatch = mism; bestPos = p; }
        if (mism == 0) break;
    }
    if (bestPos < 0 || bestMismatch > kSyncMismatchTol) return false;

    int q = bestPos + S;
    if (q + 16 > (int)bits.size()) return false;
    uint16_t len = 0;
    for (int b = 0; b < 16; ++b) len = (uint16_t)((len << 1) | bits[q + b]);
    if (len > kMaxPayload) return false;

    int payStart = q + 16;
    if (payStart + (int)len * 8 + 16 > (int)bits.size()) return false;

    payload.assign(len, 0);
    for (int i = 0; i < (int)len; ++i) {
        uint8_t v = 0;
        for (int b = 0; b < 8; ++b) v = (uint8_t)((v << 1) | bits[payStart + i * 8 + b]);
        payload[i] = v;
    }

    int crcStart = payStart + (int)len * 8;
    uint16_t rx_crc = 0;
    for (int b = 0; b < 16; ++b) rx_crc = (uint16_t)((rx_crc << 1) | bits[crcStart + b]);

    std::vector<uint8_t> crc_input;
    crc_input.push_back((uint8_t)(len >> 8));
    crc_input.push_back((uint8_t)(len & 0xFF));
    crc_input.insert(crc_input.end(), payload.begin(), payload.end());
    crc_ok = (crc16_ccitt(crc_input.data(), crc_input.size()) == rx_crc);
    sync_mismatch = bestMismatch;
    return true;
}

bool OfdmModem::demodulate(const std::vector<float>& samples, std::vector<uint8_t>& out_payload,
                           bool require_crc) {
    const int total = (int)samples.size();
    if (total < 4 * Nsym_) return false;

    // Coarse onset: first non-overlapping window with significant energy.
    int coarse = 0;
    {
        double maxE = 0.0;
        std::vector<double> e;
        for (int pos = 0; pos + Nfft_ <= total; pos += Nfft_) {
            double acc = 0.0;
            for (int n = 0; n < Nfft_; ++n) acc += (double)samples[pos + n] * samples[pos + n];
            e.push_back(acc);
            if (acc > maxE) maxE = acc;
        }
        double thresh = maxE * 0.25;
        for (int i = 0; i < (int)e.size(); ++i)
            if (e[i] >= thresh) { coarse = i * Nfft_; break; }
    }

    // The reference symbol's useful part begins near coarse + Ncp. Search a
    // window of start offsets and pick the best-scoring valid decode.
    int lo = std::max(0, coarse - Nsym_);
    int hi = std::min(total - Nfft_, coarse + 2 * Nsym_);

    bool have = false;
    int bestScore = 1 << 30;
    std::vector<uint8_t> bestPayload;

    for (int s0 = lo; s0 <= hi; s0 += 2) {
        std::vector<uint8_t> payload;
        int mism = 0;
        bool crc_ok = false;
        if (!tryDecode(samples, s0, payload, mism, crc_ok)) continue;

        if (require_crc) {
            if (crc_ok) { out_payload = std::move(payload); return true; }
            continue;
        }
        int score = mism + (crc_ok ? 0 : 1000);
        if (!have || score < bestScore) {
            have = true;
            bestScore = score;
            bestPayload = std::move(payload);
        }
    }

    if (!require_crc && have) { out_payload = std::move(bestPayload); return true; }
    return false;
}

} // namespace Sigao
