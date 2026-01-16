#ifndef SIGAO_DSP_H
#define SIGAO_DSP_H

#include <vector>
#include <deque>
#include <cstdint>
#include <mutex>

namespace Sigao {

    // --- LDPC (802.11n N=648 R=1/2) ---
    class LdpcCode {
    public:
        LdpcCode(); 
        ~LdpcCode();

        // Hard input -> Encoded bits (Systematic: Data + Parity)
        std::vector<uint8_t> encode(const std::vector<uint8_t>& info_bits);

        // Soft input (LLR) -> Decoded Hard bits
        // Returns true if checksum valid
        bool decode(const std::vector<float>& llr_in, std::vector<uint8_t>& out_bits, int max_iters = 10);

    private:
        static const int Z = 27;  // Sub-matrix size
        static const int N = 648; // Total bits
        static const int K = 324; // Info bits
        static const int R_row = 12; // M_base
        static const int C_col = 24; // N_base
        
        // Parity Check Matrix (Compressed Shift Values)
        std::vector<std::vector<int>> H_shifts;
        
        void init_tables();
    };

    // --- Adaptive Jitter Buffer ---
    struct AudioFrame {
        uint16_t seq_num;
        uint32_t timestamp;
        std::vector<float> payload; // Modulated symbols / Codec2 bytes? 
                                    // Usually Jitter Buffers store Encoded Bytes.
                                    // Here we buffer Codec2 Bytes (uint8)
        std::vector<uint8_t> data;
    };

    class JitterBuffer {
    public:
        JitterBuffer();
        ~JitterBuffer();

        // Add packet from network. Handles re-ordering.
        void push(const std::vector<uint8_t>& payload, uint16_t seq);

        // Get frame for playback. Returns false if underrun (needs Comfort Noise).
        bool pop(std::vector<uint8_t>& out_payload);

    private:
        std::deque<AudioFrame> buffer;
        uint16_t last_popped_seq;
        
        // Adaptation Logic
        int target_depth_ms; // 20 - 80
        int current_depth_ms;
        
        // Stats
        int late_frames_count;
        int good_frames_count;
        
        const int FRAME_DURATION_MS = 40; // Codec2 Mode 1300
        mutable std::mutex mtx;

        void adapt_depth();
    };

}

#endif
