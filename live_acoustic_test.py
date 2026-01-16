import os
import time
import math
import struct
import wave

# Configuration
SAMPLE_RATE = 48000
DURATION = 1.0 # 1 second ping
FREQ = 3000.0 # 3kHz Ping
FILENAME = "live_test_ping.wav"

def generate_tone(frequency, duration):
    n_samples = int(SAMPLE_RATE * duration)
    data = bytearray()
    for i in range(n_samples):
        sample = 0.5 * math.sin(2 * math.pi * frequency * i / SAMPLE_RATE)
        # 16-bit PCM
        val = int(max(-1.0, min(1.0, sample)) * 32767)
        data.extend(struct.pack('<h', val))
    
    with wave.open(FILENAME, 'w') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(data)
    print(f"[Generate] Created {FILENAME} ({frequency}Hz)")

def main():
    print("=== SIGAO LIVE ACOUSTIC TEST (Alice Mode) ===")
    print(" This script acts as the Initiator (Alice).")
    print(" Ensure your Android phone is running Sigao and volume is UP.")
    print(" Ensure you have pressed 'Start Test Listener' in the app (if implemented).")
    print("===========================================")
    
    # 1. Generate the Ping file
    generate_tone(FREQ, DURATION)
    
    input("Press ENTER to send PING (3kHz)...")
    
    print(f"[Transmitter] Broadcasting {FREQ}Hz Ping...")
    # Use standard Linux aplay
    ret = os.system(f"aplay {FILENAME}")
    
    if ret != 0:
        print("[Error] 'aplay' failed. Is this Linux? Install usage: sudo apt install alsa-utils")
    else:
        print("[Transmitter] Ping Sent!")
        print(">> Check your Phone's Logcat for 'DETECTED TONE: 3000.0 Hz'")
        print(">> adb logcat -s SigaoGhost")

if __name__ == "__main__":
    main()
