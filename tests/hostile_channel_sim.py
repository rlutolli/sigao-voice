#!/usr/bin/env python3
"""
Sigao Voice - Hostile Channel Simulator (real secure channel).

Drives the actual compiled C core through ctypes:
  X25519 ECDH  ->  XSalsa20-Poly1305 AEAD  ->  Hamming(7,4) FEC  ->  FSK modem

It generates the modulated audio for a message, passes it through a simulated
acoustic channel (leading silence + additive Gaussian noise + amplitude
scaling), then demodulates/decodes/decrypts on a second modem instance and
checks the recovered plaintext.

Build first:
    cd sigao_core && cmake -S . -B build_host && cmake --build build_host

Run:
    python3 tests/hostile_channel_sim.py
"""

import ctypes
import os
import sys
import math
import random

HERE = os.path.dirname(os.path.abspath(__file__))
CANDIDATES = [
    os.path.join(HERE, "..", "sigao_core", "build_host", "libsigao_core.dylib"),
    os.path.join(HERE, "..", "sigao_core", "build_host", "libsigao_core.so"),
    os.path.join(HERE, "..", "sigao_core", "build", "libsigao_core.so"),
]

lib_path = next((p for p in CANDIDATES if os.path.exists(p)), None)
if lib_path is None:
    print("Error: libsigao_core not found. Build it first:")
    print("  cd sigao_core && cmake -S . -B build_host && cmake --build build_host")
    sys.exit(1)

sig = ctypes.CDLL(lib_path)

# --- Signatures ---
sig.sigao_create_modem.argtypes = [ctypes.c_int]
sig.sigao_create_modem.restype = ctypes.c_void_p
sig.sigao_destroy_modem.argtypes = [ctypes.c_void_p]
sig.sigao_version.restype = ctypes.c_char_p
sig.sigao_free_buffer.argtypes = [ctypes.POINTER(ctypes.c_float)]

sig.sigao_gen_keypair.argtypes = [ctypes.POINTER(ctypes.c_ubyte), ctypes.POINTER(ctypes.c_ubyte)]
sig.sigao_compute_secret.argtypes = [ctypes.POINTER(ctypes.c_ubyte),
                                     ctypes.POINTER(ctypes.c_ubyte),
                                     ctypes.POINTER(ctypes.c_ubyte)]
sig.sigao_compute_secret.restype = ctypes.c_int

sig.sigao_tx_secure.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_ubyte),
                                ctypes.POINTER(ctypes.c_ubyte), ctypes.c_int,
                                ctypes.POINTER(ctypes.POINTER(ctypes.c_float))]
sig.sigao_tx_secure.restype = ctypes.c_int

sig.sigao_rx_secure.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_ubyte),
                                ctypes.POINTER(ctypes.c_float), ctypes.c_int,
                                ctypes.POINTER(ctypes.c_ubyte), ctypes.c_int]
sig.sigao_rx_secure.restype = ctypes.c_int


def buf32():
    return (ctypes.c_ubyte * 32)()


def derive_shared_keys():
    a_pub, a_priv = buf32(), buf32()
    b_pub, b_priv = buf32(), buf32()
    sig.sigao_gen_keypair(a_pub, a_priv)
    sig.sigao_gen_keypair(b_pub, b_priv)
    a_key, b_key = buf32(), buf32()
    sig.sigao_compute_secret(a_key, a_priv, b_pub)
    sig.sigao_compute_secret(b_key, b_priv, a_pub)
    return a_key, b_key


def channel(samples, noise_amp, lead=200, gain=0.9, rng=random.Random(1)):
    out = [0.0] * lead
    for s in samples:
        out.append(s * gain + rng.gauss(0.0, noise_amp))
    return out


def main():
    print("=== Sigao Voice: Hostile Channel Simulator ===")
    print("core version:", sig.sigao_version().decode())
    print("library     :", os.path.relpath(lib_path, HERE))

    a_key, b_key = derive_shared_keys()
    if bytes(a_key) != bytes(b_key):
        print("[FAIL] ECDH shared keys differ!")
        sys.exit(1)
    print("[OK] X25519 ECDH: both peers agree on shared key\n")

    tx = sig.sigao_create_modem(8000)
    rx = sig.sigao_create_modem(8000)

    message = "Sigao: resilient secure acoustic link"
    print(f'Message: "{message}"\n')

    print("Noise sweep (10 trials each):")
    overall_ok = True
    for noise_amp in [0.0, 0.03, 0.06, 0.10, 0.15]:
        ok = 0
        for t in range(10):
            res = run_trip_noise(tx, rx, a_key, message, noise_amp, t)
            if res == message:
                ok += 1
        rate = ok * 10
        flag = "OK " if ok >= 9 else ("~  " if ok >= 5 else "XX ")
        print(f"  [{flag}] noise sigma={noise_amp:4.2f} -> {ok}/10 recovered ({rate}%)")
        if noise_amp <= 0.06 and ok < 9:
            overall_ok = False

    sig.sigao_destroy_modem(tx)
    sig.sigao_destroy_modem(rx)

    print()
    if overall_ok:
        print("RESULT: PASS (clean & low-noise channels fully recovered)")
        sys.exit(0)
    print("RESULT: FAIL")
    sys.exit(1)


def run_trip_noise(tx, rx, key, message, noise_amp, trial):
    msg = message.encode("utf-8")
    msg_arr = (ctypes.c_ubyte * len(msg)).from_buffer_copy(msg)
    out_ptr = ctypes.POINTER(ctypes.c_float)()
    n = sig.sigao_tx_secure(tx, key, msg_arr, len(msg), ctypes.byref(out_ptr))
    if n <= 0:
        return None
    samples = [out_ptr[i] for i in range(n)]
    sig.sigao_free_buffer(out_ptr)

    rng = random.Random(7000 + trial)
    chan = channel(samples, noise_amp, lead=120 + trial * 13, gain=0.9, rng=rng)
    chan_arr = (ctypes.c_float * len(chan))(*chan)

    out = (ctypes.c_ubyte * 256)()
    rlen = sig.sigao_rx_secure(rx, key, chan_arr, len(chan), out, 256)
    if rlen < 0:
        return None
    return bytes(out[:rlen]).decode("utf-8", errors="replace")


if __name__ == "__main__":
    main()
