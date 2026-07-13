# Core Verification

Two complementary tests verify the real secure channel (crypto + FEC + modem).

## 1. Build the core for the host

```bash
cd sigao_core
cmake -S . -B build_host
cmake --build build_host
```

This produces `libsigao_core.{so,dylib}` and the `sigao_selftest` executable.
The core has **no external dependencies** (no OpenSSL, no Codec2).

## 2. C++ self-test (known-answer vectors + round-trips)

```bash
./build_host/sigao_selftest
```

Checks:
1. **X25519** against the RFC 7748 known-answer vector
2. **ECDH** two-party key agreement (and that unrelated keys differ)
3. **AEAD** encrypt/decrypt round-trip and tamper rejection
4. **Hamming(7,4) FEC** round-trip and single-bit error correction
5. **FSK modem** byte loopback (including a leading sample offset)
6. **End-to-end secure channel** (clean)
7. **End-to-end secure channel** with additive Gaussian noise

Exit code `0` means all checks passed.

## 3. Python hostile-channel simulator (drives the compiled library)

```bash
python3 tests/hostile_channel_sim.py
```

Loads the shared library via `ctypes`, performs an X25519 handshake, then sends
a message through `tx_secure` → simulated channel (leading silence + additive
noise + amplitude scaling) → `rx_secure`, reporting the recovery rate across a
noise sweep.

## Expected result

Both tests report `RESULT: PASS`. Clean and low-noise channels recover the
plaintext exactly; the AEAD tag guarantees that any frame that *does* decode is
authentic.
