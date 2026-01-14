#ifndef SIGAO_CORE_H
#define SIGAO_CORE_H

#include <vector>
#include <string>
#include <cstdint>
#include <cmath>

#ifdef WIN32
    #ifdef SIGAO_EXPORTS
        #define SIGAO_API __declspec(dllexport)
    #else
        #define SIGAO_API __declspec(dllimport)
    #endif
#else
    #define SIGAO_API __attribute__((visibility("default")))
#endif

namespace Sigao {

    class SigaoModem {
    public:
        SigaoModem(int sampleRate = 8000);
        ~SigaoModem();

        // High Level API
        std::vector<float> modulate_message(const std::string& message);
        std::string demodulate_signal(const std::vector<float>& signal, float& out_ber);

    private:
        int fs;
        float subcarrier_spacing;
        int K_overlap;
        std::vector<float> phydyas_coeffs;
        
        std::vector<int> data_subcarriers;
        std::vector<int> pilot_subcarriers;

        void init_subcarriers();
        std::vector<float> get_proto_filter(int L);
        
        // Internal DSP
        // Internal DSP
        std::vector<float> generate_pilots(int num_samples);
        
        // Handshake Logic (Goertzel)
        bool detect_handshake_tone(const std::vector<float>& samples);
    };

}

// C-API for FFI (Flutter/Android)
extern "C" {
    SIGAO_API void* sigao_create_modem(int sampleRate);
    SIGAO_API void sigao_destroy_modem(void* handle);
    
    // Returns buffer length, allocates result in *outBuffer
    // Caller must free outBuffer using sigao_free_buffer
    SIGAO_API int sigao_modulate(void* handle, const char* msg, float** outBuffer);
    
    // Audio Pipeline
    SIGAO_API int sigao_audio_ingest(void* handle, const short* pcm, int len, float** outBuffer);
    
    // Handshake
    // Returns 1 if 1900Hz Pilot detected, 0 otherwise
    SIGAO_API int sigao_detect_handshake(void* handle, const short* pcm, int len);

    // Returns 0 on success, <0 on error. writes recovered chars to outMsg (pre-allocated)
    SIGAO_API int sigao_demodulate(void* handle, const float* signal, int len, char* outMsg, int maxLen, float* outBER);
    
    SIGAO_API void sigao_free_buffer(float* buffer);
    SIGAO_API const char* sigao_version();
}

#endif
