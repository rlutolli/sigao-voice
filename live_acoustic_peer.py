import os
import time
import math
import struct
import wave
import sys
import subprocess

# Configuration
SAMPLE_RATE = 48000
CHUNK_DURATION = 1.0 # Analysis window (Integer seconds for arecord compatibility)
FREQ_PING = 3000.0
FREQ_PONG = 3500.0
THRESHOLD_AMP = 2000 # Minimum amplitude (16-bit signed)

def generate_tone(filename, frequency, duration):
    n_samples = int(SAMPLE_RATE * duration)
    data = bytearray()
    for i in range(n_samples):
        sample = 0.5 * math.sin(2 * math.pi * frequency * i / SAMPLE_RATE)
        val = int(max(-1.0, min(1.0, sample)) * 32767)
        data.extend(struct.pack('<h', val))
    
    with wave.open(filename, 'w') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(data)

def estimate_frequency(wav_filename):
    try:
        if not os.path.exists(wav_filename):
            return 0, 0
            
        with wave.open(wav_filename, 'rb') as wf:
            frames = wf.readframes(wf.getnframes())
            # Convert to list of shorts
            total_samples = len(frames) // 2
            fmt = "<%dh" % total_samples
            samples = struct.unpack(fmt, frames)

            # Zero Crossing Counter
            zero_crossings = 0
            max_amp = 0
            
            for i in range(1, len(samples)):
                sample = samples[i]
                prev = samples[i-1]
                if abs(sample) > max_amp:
                    max_amp = abs(sample)
                if (prev > 0 and sample <= 0) or (prev <= 0 and sample > 0):
                     zero_crossings += 1
            
            # Simple ZCR Frequency Estimation
            duration = total_samples / SAMPLE_RATE
            if duration == 0: return 0, 0
            freq = (zero_crossings / 2.0) / duration
            
            if max_amp > 32000:
                print(f"   [WARNING] CLIPPING DETECTED (Amp: {max_amp}) - Lower Volume!   ", end="\r")
                
            return freq, max_amp
    except Exception as e:
        print(f"Error analyzing audio: {e}")
        return 0, 0

def record_chunk(filename, duration):
    # Use arecord (standard linux)
    # -d duration (must be int on some systems), -f dat (48k), -c 1 (mono), -r 48000
    # duration must be integer
    dur_int = int(duration)
    if dur_int < 1: dur_int = 1
    
    if os.path.exists(filename):
        os.remove(filename)
        
    cmd = f"arecord -d {dur_int} -f S16_LE -c 1 -r {SAMPLE_RATE} -q {filename}"
    subprocess.call(cmd, shell=True)

def play_sound(filename):
    subprocess.call(f"aplay -q {filename}", shell=True)

def mode_bob_listen():
    print(f"\n[Bob Mode] Listening for {FREQ_PING}Hz Ping...")
    while True:
        record_chunk("temp_input.wav", CHUNK_DURATION)
        freq, amp = estimate_frequency("temp_input.wav")
        print(f"   Analysis: {freq:.1f} Hz (Amp: {amp})", end="\r")
        
        if amp > THRESHOLD_AMP and abs(freq - FREQ_PING) < 200:
            print(f"\n[DETECTED] Ping {freq:.1f}Hz! Sending Pong...")
            play_sound("pong.wav")
            print("[Bob] Pong Sent. Listening for Identity Burst...")
            # Detect Data Burst (mock)
            time.sleep(1)
            record_chunk("rx_burst.wav", 2.0)
            print("[Bob] Recorded 'rx_burst.wav'. Handshake Complete.")
            break

def mode_alice_call():
    print(f"\n[Alice Mode] Sending {FREQ_PING}Hz Ping in 3 seconds...")
    time.sleep(1)
    print("3...")
    time.sleep(1)
    print("2...")
    time.sleep(1)
    
    print("[Alice] Sending Ping...")
    play_sound("ping.wav")
    
    print(f"[Alice] Listening for {FREQ_PONG}Hz Pong...")
    start_time = time.time()
    while time.time() - start_time < 5.0: # 5s timeout
        record_chunk("temp_input.wav", CHUNK_DURATION)
        freq, amp = estimate_frequency("temp_input.wav")
        print(f"   Analysis: {freq:.1f} Hz (Amp: {amp})", end="\r")
        
        if amp > THRESHOLD_AMP and abs(freq - FREQ_PONG) < 200:
            print(f"\n[DETECTED] Pong {freq:.1f}Hz! Handshake Established!")
            print("[Alice] Sending Identity Burst...")
            # Play a short chirp as identity
            play_sound("ping.wav") # Mock identity
            break
    else:
        print("\n[Timeout] No Pong detected.")

def main():
    print("Preparing Audio Assets...")
    generate_tone("ping.wav", FREQ_PING, 1.0)
    generate_tone("pong.wav", FREQ_PONG, 1.0)
    
    print("=== Sigao Live Peer (Laptop) ===")
    print("1. Bob Mode (Listen for 3kHz, Reply 3.5kHz)")
    print("2. Alice Mode (Call 3kHz, Listen 3.5kHz)")
    choice = input("Select Mode (1/2): ")
    
    if choice == "1":
        mode_bob_listen()
    elif choice == "2":
        mode_alice_call()
    else:
        print("Invalid choice.")

if __name__ == "__main__":
    main()
