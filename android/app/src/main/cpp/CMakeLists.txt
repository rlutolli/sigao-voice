cmake_minimum_required(VERSION 3.10.2)

project("sigao_voice")

# Path to the shared Sigao Core sources (repo_root/sigao_core).
set(SIGAO_CORE_DIR ../../../../../sigao_core)

include_directories(
    ${SIGAO_CORE_DIR}
    ${SIGAO_CORE_DIR}/crypto
    ${SIGAO_CORE_DIR}/dsp
)

# JNI bridge stub.
add_library(native-lib SHARED native-lib.cpp)

# Sigao Core: real X25519 + XSalsa20-Poly1305 (TweetNaCl), Hamming(7,4) FEC,
# and the FSK modem. Self-contained — no Codec2 / OpenSSL needed. These are the
# exact same sources verified by the host self-test.
add_library(sigao_core SHARED
            ${SIGAO_CORE_DIR}/sigao_core.cpp
            ${SIGAO_CORE_DIR}/dsp/sigao_fsk.cpp
            ${SIGAO_CORE_DIR}/dsp/sigao_ofdm.cpp
            ${SIGAO_CORE_DIR}/dsp/sigao_fec.cpp
            ${SIGAO_CORE_DIR}/crypto/tweetnacl.c
            ${SIGAO_CORE_DIR}/crypto/randombytes.c)

find_library(log-lib log)

target_link_libraries(native-lib sigao_core ${log-lib})
target_link_libraries(sigao_core m ${log-lib})
