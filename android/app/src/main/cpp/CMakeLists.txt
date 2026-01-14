cmake_minimum_required(VERSION 3.10.2)

project("sigao_voice")

# Import Sigao Core
# Pointing to the root sigao_core directory
set(SIGAO_CORE_DIR ../../../../../sigao_core)

# We need to add the source code of Sigao Core directly because 'add_library' with prebuilt shared libs 
# can be tricky with packaging in simplified CMake.
# We will INCLUDE the sigao_core/CMakeLists.txt logic? 
# Better: Just add the source files here or Use add_subdirectory if it was inside.
# Since it's outside, we just refer to sources.

include_directories(${SIGAO_CORE_DIR})
# Also include Codec2 headers
include_directories(${SIGAO_CORE_DIR}/external/codec2/src)

# Define Native Lib (The JNI Bridge)
add_library(native-lib SHARED native-lib.cpp)

# Define Sigao Core Lib (The Logic)
# We need to list sources again or link the prebuilt.
# Linking prebuilt .so from outside can be an issue if not placed in jniLibs.
# We will compile it from source here to be safe and easiest.

# Re-list Codec2 Sources (Subset)
set(CODEC2_DIR ${SIGAO_CORE_DIR}/external/codec2)
set(CODEC2_SOURCES
    ${CODEC2_DIR}/src/codec2.c
    ${CODEC2_DIR}/src/lpc.c
    ${CODEC2_DIR}/src/nlp.c
    ${CODEC2_DIR}/src/postfilter.c
    ${CODEC2_DIR}/src/sine.c
    ${CODEC2_DIR}/src/codec2_fft.c
    ${CODEC2_DIR}/src/kiss_fft.c
    ${CODEC2_DIR}/src/kiss_fftr.c
    ${CODEC2_DIR}/src/interp.c
    ${CODEC2_DIR}/src/lsp.c
    ${CODEC2_DIR}/src/phase.c
    ${CODEC2_DIR}/src/quantise.c
    ${CODEC2_DIR}/src/pack.c
    ${CODEC2_DIR}/src/mbest.c
    ${CODEC2_DIR}/src/newamp1.c
    
    # Generated Codebooks (from build/src)
    ${CODEC2_DIR}/build/src/codebook.c
    ${CODEC2_DIR}/build/src/codebookd.c
    ${CODEC2_DIR}/build/src/codebookge.c
    ${CODEC2_DIR}/build/src/codebookjmv.c
    ${CODEC2_DIR}/build/src/codebooknewamp1.c
    ${CODEC2_DIR}/build/src/codebooknewamp1_energy.c
)

add_library(sigao_core SHARED
            ${SIGAO_CORE_DIR}/sigao_core.cpp
            ${CODEC2_SOURCES})

# Links
find_library(log-lib log)

target_link_libraries(native-lib sigao_core ${log-lib})
target_link_libraries(sigao_core m ${log-lib})
