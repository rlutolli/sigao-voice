#include "sigao_fec.h"

namespace Sigao {
namespace {

// --- Bit stream helpers (MSB-first) ---
struct BitWriter {
    std::vector<uint8_t> bytes;
    int bit_pos = 0; // 0..7 within the current (last) byte

    void put(int bit) {
        if (bit_pos == 0) bytes.push_back(0);
        if (bit & 1) bytes.back() |= (uint8_t)(1 << (7 - bit_pos));
        bit_pos = (bit_pos + 1) & 7;
    }
};

struct BitReader {
    const std::vector<uint8_t>& bytes;
    size_t bit_index = 0;
    explicit BitReader(const std::vector<uint8_t>& b) : bytes(b) {}

    bool has(size_t n) const { return bit_index + n <= bytes.size() * 8; }

    int get() {
        size_t byte = bit_index >> 3;
        int off = 7 - (int)(bit_index & 7);
        bit_index++;
        if (byte >= bytes.size()) return 0;
        return (bytes[byte] >> off) & 1;
    }
};

// Encode a 4-bit nibble (d0 is MSB) into a 7-bit Hamming codeword.
// Codeword bit layout (index 0..6): [p1, p2, d1, p3, d2, d3, d4]
inline void hamming74_encode(int nibble, int out_bits[7]) {
    int d1 = (nibble >> 3) & 1;
    int d2 = (nibble >> 2) & 1;
    int d3 = (nibble >> 1) & 1;
    int d4 = (nibble >> 0) & 1;
    int p1 = d1 ^ d2 ^ d4;
    int p2 = d1 ^ d3 ^ d4;
    int p3 = d2 ^ d3 ^ d4;
    out_bits[0] = p1;
    out_bits[1] = p2;
    out_bits[2] = d1;
    out_bits[3] = p3;
    out_bits[4] = d2;
    out_bits[5] = d3;
    out_bits[6] = d4;
}

// Decode a 7-bit codeword, correcting a single-bit error if present.
// Returns the recovered 4-bit nibble; increments 'corrections' if a bit
// was flipped to repair the codeword.
inline int hamming74_decode(int b[7], int& corrections) {
    // Received positions (1-indexed Hamming): treat the bit at standard
    // positions. Recompute syndrome over the [p1,p2,d1,p3,d2,d3,d4] layout.
    int p1 = b[0], p2 = b[1], d1 = b[2], p3 = b[3], d2 = b[4], d3 = b[5], d4 = b[6];

    int s1 = p1 ^ d1 ^ d2 ^ d4;
    int s2 = p2 ^ d1 ^ d3 ^ d4;
    int s3 = p3 ^ d2 ^ d3 ^ d4;

    int syndrome = (s3 << 2) | (s2 << 1) | s1; // 1..7 -> Hamming position

    if (syndrome != 0) {
        corrections++;
        // Map Hamming position (1=p1,2=p2,3=d1,4=p3,5=d2,6=d3,7=d4) to b[] index.
        switch (syndrome) {
            case 1: b[0] ^= 1; break; // p1
            case 2: b[1] ^= 1; break; // p2
            case 3: b[2] ^= 1; break; // d1
            case 4: b[3] ^= 1; break; // p3
            case 5: b[4] ^= 1; break; // d2
            case 6: b[5] ^= 1; break; // d3
            case 7: b[6] ^= 1; break; // d4
            default: break;
        }
        d1 = b[2]; d2 = b[4]; d3 = b[5]; d4 = b[6];
    }
    return (d1 << 3) | (d2 << 2) | (d3 << 1) | d4;
}

} // namespace

namespace Fec {

size_t encoded_size(size_t num_input_bytes) {
    size_t codewords = num_input_bytes * 2; // two nibbles per byte
    size_t bits = codewords * 7;
    return (bits + 7) / 8;
}

std::vector<uint8_t> encode(const std::vector<uint8_t>& data) {
    BitWriter bw;
    for (uint8_t byte : data) {
        int hi = (byte >> 4) & 0xF;
        int lo = byte & 0xF;
        int code[7];
        hamming74_encode(hi, code);
        for (int i = 0; i < 7; ++i) bw.put(code[i]);
        hamming74_encode(lo, code);
        for (int i = 0; i < 7; ++i) bw.put(code[i]);
    }
    return bw.bytes;
}

std::vector<uint8_t> decode(const std::vector<uint8_t>& coded,
                            size_t num_output_bytes,
                            int& corrected_errors) {
    corrected_errors = 0;
    BitReader br(coded);
    std::vector<uint8_t> out;
    out.reserve(num_output_bytes);

    for (size_t i = 0; i < num_output_bytes; ++i) {
        int code[7];

        for (int j = 0; j < 7; ++j) code[j] = br.get();
        int hi = hamming74_decode(code, corrected_errors);

        for (int j = 0; j < 7; ++j) code[j] = br.get();
        int lo = hamming74_decode(code, corrected_errors);

        out.push_back((uint8_t)((hi << 4) | lo));
    }
    return out;
}

} // namespace Fec

uint16_t crc16_ccitt(const uint8_t* data, size_t len) {
    uint16_t crc = 0xFFFF;
    for (size_t i = 0; i < len; ++i) {
        crc ^= (uint16_t)data[i] << 8;
        for (int b = 0; b < 8; ++b) {
            if (crc & 0x8000) crc = (uint16_t)((crc << 1) ^ 0x1021);
            else crc = (uint16_t)(crc << 1);
        }
    }
    return crc;
}

} // namespace Sigao
