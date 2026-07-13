#ifndef SIGAO_FSK_H
#define SIGAO_FSK_H

#include <vector>
#include <cstdint>

namespace Sigao {

// A real, invertible binary FSK modem (replaces the previous FBMC modulator
// that had no working demodulator).
//
//   - Bit 0 -> tone at F0, Bit 1 -> tone at F1 (phase-continuous)
//   - Frame: [lead-in][32-bit sync word][16-bit length][payload][16-bit CRC]
//   - Demod: per-symbol Goertzel power comparison, with symbol-timing search
//            and sync-word correlation so it tolerates a leading offset and
//            additive noise.
//
// modulate(bytes) and demodulate(samples) are true inverses on a clean
// channel and degrade gracefully with noise (verified by the host tests).
class FskModem {
public:
    explicit FskModem(int sampleRate = 8000);

    // Bytes -> audio samples (float, roughly [-0.7, 0.7]).
    std::vector<float> modulate(const std::vector<uint8_t>& payload);

    // Audio samples -> bytes.
    //   require_crc = true  : only accept a frame whose transport CRC matches
    //                         (use when no outer FEC/AEAD protects the payload).
    //   require_crc = false : return the best sync-aligned payload regardless
    //                         of transport CRC, leaving integrity to an outer
    //                         layer (FEC + AEAD). This is required so that FEC
    //                         can actually correct bit errors instead of the
    //                         frame being dropped by the CRC gate.
    // Returns true if a frame was recovered.
    bool demodulate(const std::vector<float>& samples, std::vector<uint8_t>& out_payload,
                    bool require_crc = true);

    int samplesPerSymbol() const { return N_; }
    int sampleRate() const { return fs_; }

private:
    int   fs_;
    int   N_;     // samples per symbol
    float f0_;    // tone for bit 0
    float f1_;    // tone for bit 1
    float amp_;

    std::vector<int> sync_bits_;

    void appendBit(std::vector<float>& out, int bit, double& phase) const;
    void bytesToBits(const std::vector<uint8_t>& bytes, std::vector<int>& bits) const;
    float goertzelPower(const float* x, int n, float freq) const;
    // Decode the whole signal to a bit vector for a given starting sample.
    void decodeBits(const std::vector<float>& s, int start, std::vector<int>& bits) const;
};

} // namespace Sigao

#endif
