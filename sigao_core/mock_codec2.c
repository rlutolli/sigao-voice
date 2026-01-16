#include <stdlib.h>
#include "codec2.h"

// Mock Codec2 for Host Testing of DSP Layer (Jitter/LDPC)
// This avoids building the complex DSP tables of Codec2.

struct CODEC2 {
    int mode;
};

struct CODEC2 * codec2_create(int mode) {
    struct CODEC2 *s = (struct CODEC2*)malloc(sizeof(struct CODEC2));
    s->mode = mode;
    return s;
}

void codec2_destroy(struct CODEC2 *codec2_state) {
    free(codec2_state);
}

int codec2_samples_per_frame(struct CODEC2 *codec2_state) {
    return 320; // Mode 1300
}

int codec2_bits_per_frame(struct CODEC2 *codec2_state) {
    return 52; // Mode 1300
}

void codec2_encode(struct CODEC2 *codec2_state, unsigned char *bits, short *speech) {
    // Fill with dummy data
    for(int i=0; i<7; i++) bits[i] = 0xAA;
}

void codec2_decode(struct CODEC2 *codec2_state, short *speech, const unsigned char *bits) {
    // Fill with silence/tone
    for(int i=0; i<320; i++) speech[i] = 0;
}
