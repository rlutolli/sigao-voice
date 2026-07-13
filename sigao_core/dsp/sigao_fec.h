#ifndef SIGAO_FEC_H
#define SIGAO_FEC_H

#include <vector>
#include <cstdint>

namespace Sigao {

// Forward Error Correction using Hamming(7,4).
//
// Each input byte is split into two 4-bit nibbles; each nibble is encoded
// into a 7-bit codeword that can detect and correct any single-bit error
// within that codeword. The encoded codewords are packed MSB-first into a
// contiguous bit stream and returned as bytes.
//
// This is a genuine, paired encoder/decoder (unlike the previous placeholder):
// flipping any one bit per 7-bit group is corrected on decode. It roughly
// doubles the data size (4 -> 7 bits) which is an honest cost of the FEC.
namespace Fec {

    // Encode arbitrary bytes -> Hamming(7,4) coded bytes.
    std::vector<uint8_t> encode(const std::vector<uint8_t>& data);

    // Decode coded bytes -> recovered bytes, correcting single-bit errors.
    // 'num_output_bytes' is how many original bytes to reconstruct (the
    // caller knows this from its own framing). Returns the corrected bytes
    // and, via 'corrected_errors', the number of single-bit corrections made.
    std::vector<uint8_t> decode(const std::vector<uint8_t>& coded,
                                size_t num_output_bytes,
                                int& corrected_errors);

    // Size in bytes of the encoded stream for 'num_input_bytes' of input.
    size_t encoded_size(size_t num_input_bytes);

} // namespace Fec

// CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF). Used by the modem framing
// for a fast transport-integrity check independent of the AEAD tag.
uint16_t crc16_ccitt(const uint8_t* data, size_t len);

} // namespace Sigao

#endif
