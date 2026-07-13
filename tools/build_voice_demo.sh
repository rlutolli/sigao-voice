#!/usr/bin/env bash
# Builds and runs the end-to-end secure-voice demo on the host (macOS).
#
#   real speech -> Codec2 1300 -> X25519/XSalsa20-Poly1305 -> UDP loopback
#                -> decrypt+verify -> Codec2 decode -> WAV + latency
#
# Produces demo_output/*.wav (+ .mp3) and prints per-frame latency.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CORE="$ROOT/sigao_core"
C2="$CORE/external/codec2"
OUT="$ROOT/demo_output"
TMP="$(mktemp -d)"
mkdir -p "$OUT"

# 1. Build Codec2 for the host (once).
if [ ! -f "$C2/build_host_c2/src/libcodec2.dylib" ] && [ ! -f "$C2/build_host_c2/src/libcodec2.so" ]; then
  echo "[1/4] Building Codec2 (host)..."
  cmake -S "$C2" -B "$C2/build_host_c2" -DCMAKE_BUILD_TYPE=Release >/dev/null
  cmake --build "$C2/build_host_c2" --target codec2 >/dev/null
fi

# 2. Compile the demo (C as C, C++ as C++) and link Codec2.
echo "[2/4] Compiling demo..."
INC="-I$CORE -I$CORE/crypto -I$CORE/dsp -I$C2/src -I$C2/build_host_c2"
clang   -O2 $INC -c "$CORE/crypto/tweetnacl.c"   -o "$TMP/tweetnacl.o"
clang   -O2 $INC -c "$CORE/crypto/randombytes.c" -o "$TMP/rand.o"
for s in sigao_core.cpp dsp/sigao_fsk.cpp dsp/sigao_ofdm.cpp dsp/sigao_fec.cpp; do
  clang++ -std=c++17 -O2 $INC -c "$CORE/$s" -o "$TMP/$(basename "$s").o"
done
clang++ -std=c++17 -O2 $INC -c "$CORE/tools/sigao_voice_demo.cpp" -o "$TMP/demo.o"
clang++ "$TMP"/*.o -L"$C2/build_host_c2/src" -lcodec2 \
        -Wl,-rpath,"$C2/build_host_c2/src" -o "$TMP/sigao_voice_demo"

# 3. Generate ~10s of real speech for each party (macOS say + afconvert).
echo "[3/4] Generating speech..."
say -v Samantha -o "$TMP/alice.aiff" "Hi Bob, this is Alice. I am calling you over the Sigao secure voice link. Everything we say is encrypted end to end. Can you hear me clearly on your side?"
say -v Daniel   -o "$TMP/bob.aiff"   "Hey Alice, this is Bob. Yes, I can hear you perfectly. The audio is compressed with Codec two and sealed in the encrypted channel. This is a solid test of the full secure pipeline."
afconvert -f WAVE -d LEI16@8000 -c 1 "$TMP/alice.aiff" "$OUT/alice_original.wav"
afconvert -f WAVE -d LEI16@8000 -c 1 "$TMP/bob.aiff"   "$OUT/bob_original.wav"

# 4. Run the full secure-voice procedure.
echo "[4/4] Running secure voice exchange..."
"$TMP/sigao_voice_demo" "$OUT/alice_original.wav" "$OUT/bob_original.wav" "$OUT"
command -v lame >/dev/null && lame --quiet -b 64 "$OUT/conversation.wav" "$OUT/conversation.mp3" || true

echo "Done. See $OUT/"
