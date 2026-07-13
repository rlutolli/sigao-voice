cmake_minimum_required(VERSION 3.10)
project(sigao_core C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Sigao Core: real X25519 + XSalsa20-Poly1305 (TweetNaCl), Hamming(7,4) FEC,
# and a binary FSK modem with working modulate/demodulate.
#
# No external dependencies (no OpenSSL, no Codec2) — everything needed for the
# secure data channel is self-contained, so the same sources build for the
# host (tests) and for Android (NDK) unchanged.

set(SIGAO_SOURCES
    sigao_core.cpp
    dsp/sigao_fsk.cpp
    dsp/sigao_ofdm.cpp
    dsp/sigao_fec.cpp
    crypto/tweetnacl.c
    crypto/randombytes.c
)

add_library(sigao_core SHARED ${SIGAO_SOURCES})

target_include_directories(sigao_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/crypto
    ${CMAKE_CURRENT_SOURCE_DIR}/dsp
)

target_link_libraries(sigao_core m)

if(ANDROID)
    find_library(log-lib log)
    target_link_libraries(sigao_core ${log-lib})
endif()

set_target_properties(sigao_core PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN 1
)

# ---- Host self-test (not built for Android) ----
if(NOT ANDROID)
    add_executable(sigao_selftest tests/core_selftest.cpp ${SIGAO_SOURCES})
    target_include_directories(sigao_selftest PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/crypto
        ${CMAKE_CURRENT_SOURCE_DIR}/dsp
    )
    target_link_libraries(sigao_selftest m)

    add_executable(sigao_peer tools/sigao_peer.cpp ${SIGAO_SOURCES})
    target_include_directories(sigao_peer PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/crypto
        ${CMAKE_CURRENT_SOURCE_DIR}/dsp
    )
    target_link_libraries(sigao_peer m)
endif()
