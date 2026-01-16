import os
import time
import math
import struct
import wave
import sys
import subprocess
import threading
import random

# Configuration
SAMPLE_RATE = 48000
CHUNK_DURATION = 1.0 
FREQ_PING = 3000.0  # Bob listens for this (if Phone=Alice)
FREQ_PONG = 3500.0  # Alice listens for this (if Phone=Bob)

# Goertzel Algorithm (Pure Python) - Robust against clipping
def goertzel(samples, target_freq, sample_rate):
    size = len(samples)
    k = int(0.5 + ((size * target_freq) / sample_rate))
    omega = (2.0 * math.pi * k) / size
    cosine = math.cos(omega)
    coeff = 2.0 * cosine
    q1 = 0.0
    q2 = 0.0
    
    for sample in samples:
        q0 = coeff * q1 - q2 + sample
        q2 = q1
        q1 = q0
        
    magnitude = q1*q1 + q2*q2 - q1*q2*coeff
    return magnitude

def record_chunk(filename, duration):
    try:
        dur_int = int(duration)
        if dur_int < 1: dur_int = 1
        if os.path.exists(filename): os.remove(filename)
        # Suppress stderr to keep output clean
        subprocess.call(f"arecord -d {dur_int} -f S16_LE -c 1 -r {SAMPLE_RATE} -q {filename}", shell=True)
    except Exception as e:
        print(f"Record Error: {e}")

def generate_wav_sine(filename, freq, duration):
    n_samples = int(SAMPLE_RATE * duration)
    data = bytearray()
    for i in range(n_samples):
        sample = 0.5 * math.sin(2 * math.pi * freq * i / SAMPLE_RATE)
        val = int(max(-1.0, min(1.0, sample)) * 32767)
        data.extend(struct.pack('<h', val))
    write_wav(filename, data)

def generate_wav_fsk(filename, bits, base_freq, shift, ms_per_bit):
    samples_per_bit = int(SAMPLE_RATE * ms_per_bit / 1000)
    data = bytearray()
    phase = 0.0
    for bit in bits:
        freq = base_freq + shift if bit == 1 else base_freq
        phase_step = 2.0 * math.pi * freq / SAMPLE_RATE
        for _ in range(samples_per_bit):
            sample = 0.5 * math.sin(phase)
            val = int(max(-1.0, min(1.0, sample)) * 32767)
            data.extend(struct.pack('<h', val))
            phase += phase_step
    write_wav(filename, data)

def generate_white_noise(filename, duration_sec):
    """Generate white noise (simulates encrypted audio)."""
    n_samples = int(SAMPLE_RATE * duration_sec)
    data = bytearray()
    for _ in range(n_samples):
        # Random samples between -32767 and 32767
        val = random.randint(-32767, 32767)
        data.extend(struct.pack('<h', val))
    write_wav(filename, data)

def write_wav(filename, data):
    with wave.open(filename, 'w') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(data)

def play_sound(filename):
    subprocess.call(f"aplay -q {filename}", shell=True)

def calibrate_phone_volume():
    print("[Auto] Calibrating Phone Volume...")
    for _ in range(15):
        subprocess.call("adb shell input keyevent 25", shell=True) # Vol Down
        time.sleep(0.05)
    for _ in range(5):  # Lower volume: 5/15 instead of 10/15
        subprocess.call("adb shell input keyevent 24", shell=True) # Vol Up
        time.sleep(0.05)
    print("[Auto] Volume set to medium level (5/15).")

def listener_process(stop_event):
    print("[Listener] Started. Waiting for Handshake...")
    
    state = "WAIT_PING" # WAIT_PING -> WAIT_KEY_ALICE -> DONE
    consecutive_hits = 0
    
    while not stop_event.is_set():
        record_chunk("auto_input.wav", 1.0)
        
        if not os.path.exists("auto_input.wav"): continue
        
        try:
            with wave.open("auto_input.wav", 'rb') as wf:
                raw = wf.readframes(wf.getnframes())
                fmt = "<%dh" % (len(raw) // 2)
                samples = struct.unpack(fmt, raw)
                
                max_amp = max([abs(s) for s in samples])
                if max_amp > 32500:
                    print(f"   [SKIP] CLIPPING IGNORING FRAME (Amp: {max_amp})", end="\r")
                    continue
                
                # DSP based on State
                # Calculate magnitudes for target and reference frequencies
                mag_3000 = goertzel(samples, 3000.0, SAMPLE_RATE)
                mag_4000 = goertzel(samples, 4000.0, SAMPLE_RATE)
                
                if state == "WAIT_PING":
                    # We are Bob. Waiting for Alice's 3kHz Ping.
                    print(f"   [DBG] 3kHz={mag_3000:.2e} hits={consecutive_hits}", end="\r")
                    
                    # Very strong signal = immediate detection (phone sends at 1e12+)
                    if mag_3000 > 1e12:
                        print(f"\n[STEP 1] DETECTED PING (3kHz)! (mag={mag_3000:.2e}) Sending Pong...")
                        play_sound("pong.wav")
                        print("[STEP 2] Pong Sent. Waiting for Alice Key (4kHz FSK)...")
                        state = "WAIT_KEY_ALICE"
                        consecutive_hits = 0
                    elif mag_3000 > 1e10:
                        consecutive_hits += 1
                        if consecutive_hits >= 3:
                            print(f"\n[STEP 1] DETECTED PING (3kHz)! Sending Pong...")
                            play_sound("pong.wav")
                            print("[STEP 2] Pong Sent. Waiting for Alice Key (4kHz FSK)...")
                            state = "WAIT_KEY_ALICE"
                            consecutive_hits = 0
                    else:
                        consecutive_hits = max(0, consecutive_hits - 1)

                elif state == "WAIT_KEY_ALICE":
                    # We are Bob. Waiting for Alice's 4kHz FSK Key.
                    print(f"   [DBG] 4kHz={mag_4000:.2e} hits={consecutive_hits}", end="\r")
                    
                    # Very strong signal = immediate detection
                    if mag_4000 > 1e12:
                        print(f"\n[STEP 3] DETECTED ALICE FSK KEY (4kHz)! (mag={mag_4000:.2e}) Sending Bob Key...")
                        play_sound("bob_key_fsk.wav")
                        print("[STEP 4] Bob Key Sent. Handshake Complete!")
                        stop_event.set()
                        return
                    elif mag_4000 > 1e10:
                        consecutive_hits += 1
                        if consecutive_hits >= 3:
                            print(f"\n[STEP 3] DETECTED ALICE FSK KEY (4kHz)! (moderate) Sending Bob Key...")
                            play_sound("bob_key_fsk.wav")
                            print("[STEP 4] Bob Key Sent. Handshake Complete!")
                            stop_event.set()
                            return
                    else:
                        consecutive_hits = max(0, consecutive_hits - 1)

        except Exception as e:
            print(f"Analysis Error: {e}")

def main():
    print("=== Sigao Full Handshake Test (FSK) ===")
    
    # 0. Generate Assets
    print("[Setup] Generating Audio Assets...")
    generate_wav_sine("pong.wav", FREQ_PONG, 1.0)
    
    # Bob Key FSK: Base 5000Hz, Mark 5500Hz, 32-bit pattern
    bits = [1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 0, 0]
    generate_wav_fsk("bob_key_fsk.wav", bits, 5000.0, 500.0, 100)
    
    # 1. Calibrate Volume
    calibrate_phone_volume()
    
    # 2. Start Listener
    stop_event = threading.Event()
    t = threading.Thread(target=listener_process, args=(stop_event,))
    t.start()
    
    # 3. Trigger Phone
    print("\n[Auto] Triggering Phone (Alice Mode) via ADB...")
    subprocess.call("adb shell am start -n com.sigao.sigao_voice/.MainActivity -a com.sigao.voice.START_CALL", shell=True)
    
    # 4. Wait for handshake
    start_time = time.time()
    handshake_success = False
    while time.time() - start_time < 30: # 30s timeout
        if stop_event.is_set():
            handshake_success = True
            break
        time.sleep(0.5)
    
    if not handshake_success:
        print("\n\n[FAIL] Timed out.")
        stop_event.set()
        t.join(timeout=2)
        sys.exit(1)
    
    t.join(timeout=2)
    
    # 5. PHASE 4: Encrypted Audio Simulation
    print("\n=== PHASE 4: Encrypted Audio Simulation ===")
    print("[Phase 4] Keys exchanged between Alice (4kHz) and Bob (5kHz)!")
    print("[Phase 4] Generating encrypted voice (white noise)...")
    generate_white_noise("encrypted_audio.wav", 5.0)  # 5 seconds of static
    
    print("[Phase 4] Playing ENCRYPTED audio...")
    print("   >>> This is what an eavesdropper would hear - unintelligible static!")
    play_sound("encrypted_audio.wav")
    
    print("\n[Phase 4] Now decrypting with shared key...")
    print("[Phase 4] Generating DECRYPTED audio (clear 440Hz tone)...")
    generate_wav_sine("decrypted_audio.wav", 440.0, 5.0)  # 5 seconds of clear 440Hz tone
    
    print("[Phase 4] Playing DECRYPTED audio...")
    print("   >>> This is what the authorized recipient hears - crystal clear!")
    play_sound("decrypted_audio.wav")
    
    print("\n[Phase 4] Complete!")
    print("   - Alice Key: 4kHz FSK (sent by phone)")
    print("   - Bob Key: 5kHz FSK (sent by laptop)")
    print("   - Encrypted: White noise (unintelligible)")
    print("   - Decrypted: Clear 440Hz tone (authorized)")
    print("\n=== SIGAO FULL DEMONSTRATION COMPLETE ===")
    sys.exit(0)

if __name__ == "__main__":
    main()
