#ifndef SIGAO_OFDM_H
#define SIGAO_OFDM_H

#include <vector>
#include <cstdint>

namespace Sigao {

// Multi-carrier OFDM-lite modem with differential QPSK (DQPSK) per subcarrier.
//
// Replaces the 200 baud binary FSK modem for the high-rate (voice) path. The
// FSK modem carries ~114 bit/s after FEC, which cannot sustain Codec2 1300
// (needs ~2.6 kbit/s after crypto + FEC). This modem follows the FreeDV-1600
// design family: many narrow subcarriers at a low symbol rate.
//
// Design (8 kHz sample rate):
//   - FFT size N = 128, cyclic prefix = 32  -> 160 samples/symbol = 50 baud
//   - 32 data subcarriers (bins 8..39 ~ 500-2437 Hz), DQPSK = 2 bits/carrier
//   - Gross rate = 32 * 50 * 2 = 3200 bit/s
//   - One reference symbol provides the differential phase origin.
//
// Two properties make demodulation robust and the demod a clean DFT:
//   - DQPSK *in time* cancels any constant per-subcarrier phase, so a fixed
//     sample-timing offset does not corrupt the data.
//   - The cyclic prefix absorbs small timing error without inter-carrier
//     interference.
class OfdmModem {
public:
    explicit OfdmModem(int sampleRate = 8000);

    // Bytes -> audio samples.
    std::vector<float> modulate(const std::vector<uint8_t>& payload);

    // Audio samples -> bytes. See FskModem for the meaning of require_crc.
    bool demodulate(const std::vector<float>& samples, std::vector<uint8_t>& out_payload,
                    bool require_crc = true);

    int grossBitrate() const { return numCarriers_ * baud_ * 2; }
    int sampleRate() const { return fs_; }

private:
    int fs_;
    int Nfft_;          // useful symbol length
    int Ncp_;           // cyclic prefix length
    int Nsym_;          // Nfft_ + Ncp_
    int baud_;          // symbols per second
    int firstBin_;      // lowest subcarrier bin
    int numCarriers_;   // number of data subcarriers
    float amp_;

    std::vector<int> sync_bits_;

    void measureSymbol(const std::vector<float>& s, int start, std::vector<float>& phases) const;
    bool tryDecode(const std::vector<float>& s, int s0, std::vector<uint8_t>& payload,
                   int& sync_mismatch, bool& crc_ok) const;
};

} // namespace Sigao

#endif
