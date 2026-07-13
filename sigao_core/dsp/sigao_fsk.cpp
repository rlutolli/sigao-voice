#include "sigao_fsk.h"
#include "sigao_fec.h"
#include <cmath>
#include <algorithm>

namespace Sigao {

namespace {
    constexpr double kPI = 3.14159265358979323846;

    // 32-bit frame sync word (CCSDS attached sync marker 0x1ACFFC1D).
    // Good autocorrelation properties for bit-level alignment.
    const uint8_t kSyncBytes[4] = {0x1A, 0xCF, 0xFC, 0x1D};

    // Lead-in symbols (alternating) so energy detection / AGC settles before
    // the sync word arrives.
    constexpr int kLeadInSymbols = 16;

    constexpr int   kMaxPayload = 4096;
    constexpr int   kSyncMismatchTol = 3; // allowed bit errors when matching sync
}

FskModem::FskModem(int sampleRate)
    : fs_(sampleRate),
      N_(sampleRate / 200), // 200 baud
      f0_(1000.0f),
      f1_(2000.0f),
      amp_(0.7f) {
    if (N_ < 8) N_ = 8;
    sync_bits_.reserve(32);
    for (int i = 0; i < 4; ++i)
        for (int b = 7; b >= 0; --b)
            sync_bits_.push_back((kSyncBytes[i] >> b) & 1);
}

void FskModem::appendBit(std::vector<float>& out, int bit, double& phase) const {
    double f = bit ? f1_ : f0_;
    double dphi = 2.0 * kPI * f / (double)fs_;
    for (int n = 0; n < N_; ++n) {
        out.push_back((float)(amp_ * std::cos(phase)));
        phase += dphi;
        if (phase > 2.0 * kPI) phase -= 2.0 * kPI;
    }
}

void FskModem::bytesToBits(const std::vector<uint8_t>& bytes, std::vector<int>& bits) const {
    for (uint8_t byte : bytes)
        for (int b = 7; b >= 0; --b)
            bits.push_back((byte >> b) & 1);
}

std::vector<float> FskModem::modulate(const std::vector<uint8_t>& payload) {
    // Build the frame bit sequence: sync + len(16) + payload + crc16.
    std::vector<int> bits;

    // Sync word
    bits.insert(bits.end(), sync_bits_.begin(), sync_bits_.end());

    // Length (16-bit big-endian)
    uint16_t len = (uint16_t)payload.size();
    for (int b = 15; b >= 0; --b) bits.push_back((len >> b) & 1);

    // Payload
    bytesToBits(payload, bits);

    // CRC over [len_hi, len_lo, payload...]
    std::vector<uint8_t> crc_input;
    crc_input.push_back((uint8_t)(len >> 8));
    crc_input.push_back((uint8_t)(len & 0xFF));
    crc_input.insert(crc_input.end(), payload.begin(), payload.end());
    uint16_t crc = crc16_ccitt(crc_input.data(), crc_input.size());
    for (int b = 15; b >= 0; --b) bits.push_back((crc >> b) & 1);

    // Render: lead-in (alternating) then frame bits, phase-continuous.
    std::vector<float> out;
    out.reserve((kLeadInSymbols + bits.size() + 8) * N_);
    double phase = 0.0;
    for (int i = 0; i < kLeadInSymbols; ++i) appendBit(out, i & 1, phase);
    for (int bit : bits) appendBit(out, bit, phase);
    // Small tail of silence to flush the last symbol's decoding window.
    for (int i = 0; i < N_; ++i) out.push_back(0.0f);
    return out;
}

float FskModem::goertzelPower(const float* x, int n, float freq) const {
    double w = 2.0 * kPI * freq / (double)fs_;
    double coeff = 2.0 * std::cos(w);
    double s0 = 0, s1 = 0, s2 = 0;
    for (int i = 0; i < n; ++i) {
        s0 = x[i] + coeff * s1 - s2;
        s2 = s1;
        s1 = s0;
    }
    return (float)(s1 * s1 + s2 * s2 - coeff * s1 * s2);
}

void FskModem::decodeBits(const std::vector<float>& s, int start, std::vector<int>& bits) const {
    int total = (int)s.size();
    for (int pos = start; pos + N_ <= total; pos += N_) {
        float e0 = goertzelPower(&s[pos], N_, f0_);
        float e1 = goertzelPower(&s[pos], N_, f1_);
        bits.push_back(e1 > e0 ? 1 : 0);
    }
}

bool FskModem::demodulate(const std::vector<float>& samples, std::vector<uint8_t>& out_payload,
                          bool require_crc) {
    const int total = (int)samples.size();
    if (total < N_ * (int)(sync_bits_.size() + 32)) return false;

    // Coarse energy-based start: first window whose tone energy is clearly
    // above the local noise floor.
    int coarse = 0;
    {
        float maxE = 0.0f;
        std::vector<float> energies;
        for (int pos = 0; pos + N_ <= total; pos += N_) {
            float e = goertzelPower(&samples[pos], N_, f0_) + goertzelPower(&samples[pos], N_, f1_);
            energies.push_back(e);
            if (e > maxE) maxE = e;
        }
        float thresh = maxE * 0.25f;
        for (int i = 0; i < (int)energies.size(); ++i) {
            if (energies[i] >= thresh) { coarse = i * N_; break; }
        }
    }

    // Search symbol-timing offsets around the coarse start. For each, decode
    // the bit stream and look for the sync word; track the best candidate.
    int search_lo = std::max(0, coarse - 2 * N_);
    int search_hi = std::min(total - N_, coarse + 2 * N_);

    bool                 have_best = false;
    int                  best_score = 1 << 30; // lower is better
    bool                 best_crc_ok = false;
    std::vector<uint8_t> best_payload;

    const int S = (int)sync_bits_.size();

    for (int off = search_lo; off <= search_hi; ++off) {
        std::vector<int> bits;
        decodeBits(samples, off, bits);
        if ((int)bits.size() < S + 32) continue;

        int maxSyncPos = (int)bits.size() - S;
        for (int p = 0; p <= maxSyncPos; ++p) {
            int mismatches = 0;
            for (int k = 0; k < S && mismatches <= kSyncMismatchTol; ++k)
                if (bits[p + k] != sync_bits_[k]) mismatches++;
            if (mismatches > kSyncMismatchTol) continue;

            int q = p + S;
            if (q + 16 > (int)bits.size()) continue;
            uint16_t len = 0;
            for (int b = 0; b < 16; ++b) len = (uint16_t)((len << 1) | bits[q + b]);
            if (len > kMaxPayload) continue;

            int payload_bits_start = q + 16;
            int need = (int)len * 8 + 16; // payload + crc
            if (payload_bits_start + need > (int)bits.size()) continue;

            std::vector<uint8_t> payload(len, 0);
            for (int i = 0; i < (int)len; ++i) {
                uint8_t v = 0;
                for (int b = 0; b < 8; ++b)
                    v = (uint8_t)((v << 1) | bits[payload_bits_start + i * 8 + b]);
                payload[i] = v;
            }

            int crc_start = payload_bits_start + (int)len * 8;
            uint16_t rx_crc = 0;
            for (int b = 0; b < 16; ++b) rx_crc = (uint16_t)((rx_crc << 1) | bits[crc_start + b]);

            std::vector<uint8_t> crc_input;
            crc_input.push_back((uint8_t)(len >> 8));
            crc_input.push_back((uint8_t)(len & 0xFF));
            crc_input.insert(crc_input.end(), payload.begin(), payload.end());
            bool crc_ok = (crc16_ccitt(crc_input.data(), crc_input.size()) == rx_crc);

            if (require_crc) {
                if (crc_ok) { out_payload = std::move(payload); return true; }
                continue;
            }

            // No CRC gate: prefer a CRC-valid candidate, otherwise the one
            // with the cleanest sync correlation. Integrity is enforced by
            // the outer FEC + AEAD layer.
            int score = mismatches + (crc_ok ? 0 : 1000);
            if (!have_best || score < best_score) {
                have_best = true;
                best_score = score;
                best_crc_ok = crc_ok;
                best_payload = std::move(payload);
            }
        }
    }

    if (!require_crc && have_best) {
        (void)best_crc_ok;
        out_payload = std::move(best_payload);
        return true;
    }
    return false;
}

} // namespace Sigao
