# Sigao Voice: Secure Standalone Dialer
**Project Codename**: Sigao | **Version**: 0.4.0 (Core hardening)

Sigao is a research project exploring resilient, encrypted communication over
acoustic / data channels on Android. This version replaces the previous
placeholder core (which contained fake "crypto" and a non-invertible modem)
with a **real, host-verified secure data channel**.

> **Research / educational use only.** See [DISCLAIMER.md](DISCLAIMER.md) and
> the [Legal Transparency Report](LEGAL_TRANSPARENCY_REPORT.md). This software
> has not been independently security-audited.

## What actually works today (verified)

The native core (`sigao_core/`) implements and the test suite verifies:

* **Real key agreement** — X25519 ECDH (TweetNaCl). Verified against the
  **RFC 7748 known-answer vector** and for two-party agreement.
* **Real authenticated encryption** — XSalsa20-Poly1305 (`crypto_secretbox`).
  Round-trips correctly and **rejects tampered ciphertext**.
* **Real forward error correction** — Hamming(7,4), with verified single-bit
  error correction (paired encoder/decoder, not a placeholder).
* **A real modem** — two working modems (modulate AND demodulate), verified by
  loopback and noise tests:
  * **FSK** (200 bit/s) for text / control / pairing.
  * **OFDM/DQPSK** (~3200 bit/s gross, 32 subcarriers × 50 baud × 2 bits) for
    the voice path — enough to sustain Codec2 1300 plus crypto/FEC overhead.
    The previous build could only transmit and topped out far too low.
* **End-to-end secure channel** — `message → encrypt → FEC → modulate →
  [noisy channel] → demodulate → FEC-decode → verify+decrypt → message`,
  verified clean and across an additive-noise sweep, on **both** modems
  (`sigao_tx/rx_secure` over FSK, `sigao_tx/rx_voice` over OFDM).

Run the verification yourself:

```bash
cd sigao_core
cmake -S . -B build_host && cmake --build build_host
./build_host/sigao_selftest                 # C++ self-test (RFC vectors + round-trips)
cd .. && python3 tests/hostile_channel_sim.py   # end-to-end over a simulated channel
```

## Status: what is NOT done yet

To keep claims honest, the following are **not** implemented/wired and are the
next steps toward a field-testable app:

* **Microphone capture / speaker playback** — `lib/audio/audio_engine.dart`
  currently runs a *self-loopback* of the real native pipeline (encrypt →
  modulate → demodulate → decrypt) on a timer. It does not yet read the mic or
  play received audio. Replace the timer with a real capture/playback stream.
* **Peer transport** — there is no network or live acoustic link between two
  devices yet. The channel is proven in-process / in-simulation only.
* **Voice vocoder (Codec2)** — the data channel carries bytes (e.g. text). Real
  voice requires integrating a Codec2 build into the pipeline; the previous mock
  vocoder has been removed rather than left pretending to work.
* **Identity binding** — `KeyStoreService` generates a hardware-backed signing
  key, but ephemeral keys are not yet signed/verified end-to-end in the call flow.

## Roadmap to live voice (research-derived)

Budget targets (8 kHz, 40 ms frames = 320 samples; Codec2 1300 = 52 bits/frame):

| Layer | Target |
| --- | --- |
| Codec2 1300 payload | 1300 bit/s |
| + AEAD (session nonce prefix + short counter) + FEC | ~2.3–2.6 kbit/s |
| OFDM modem gross capacity (now implemented) | **3200 bit/s** ✓ |
| End-to-end one-way latency (UDP) | ~250–350 ms |
| Jitter buffer depth | 3–4 frames (120–160 ms) |

Remaining integration work, in order:

1. **Native audio engine (Oboe/AAudio)** — capture/playback in C++ at 8 kHz mono
   PCM16, 40 ms frames, `LowLatency` mode, off the Dart isolate. Use an
   `UNPROCESSED`/`VOICE_RECOGNITION` source to avoid AGC/AEC mangling tones.
2. **Codec2 1300** — link `libcodec2` in the NDK build; `codec2_encode` mic
   frames before the secure pipeline, `codec2_decode` after.
3. **UDP transport (Profile A)** — `RawDatagramSocket`; 1–2 Codec2 frames per
   packet; compact authenticated header (version, stream id, seq, counter).
   This is the practical product path.
4. **Adaptive jitter buffer + concealment** — reorder, conceal lost frames
   (repeat-then-fade), drift compensation.
5. **Acoustic voice (Profile B, experimental)** — the OFDM modem is the path;
   data-over-cellular-vocoder remains low-reliability and is a research goal,
   not a product guarantee.

## Technical Architecture
* **Frontend**: Flutter (Dart) for UI and system integration.
* **Core**: self-contained C/C++17 shared library (FFI), no external deps:
  * `crypto/` — TweetNaCl (X25519, XSalsa20-Poly1305) + OS CSPRNG
  * `dsp/sigao_fsk.*` — FSK modem (text/control, 200 bit/s)
  * `dsp/sigao_ofdm.*` — OFDM/DQPSK modem (voice, ~3200 bit/s gross)
  * `dsp/sigao_fec.*` — Hamming(7,4) FEC + CRC-16
  * `sigao_core.cpp` — C-API tying it together (see `sigao_core.h`)

## Build (Android)
1. **Prerequisites**: Flutter SDK, Android NDK, CMake.
2. **Run**: `flutter run --release`

The same core sources compile for the host (for tests) and for Android with no
dependency changes.
