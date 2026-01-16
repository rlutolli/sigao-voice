#!/usr/bin/env python3
"""
SIGAO Secure Voice Simulation (Two-Way)
---------------------------------------
Simulates a secure conversation between two parties (Alice and Bob).
1. Handshake Exchange (Alice -> Bob, Bob -> Alice)
2. Eavesdropper's Perspective (Encrypted Static)
3. Participants' Perspective (Decrypted Clear Voice)

Usage:
    python3 sigao_lifecycle_sim.py [--input audio.mp3]
"""

import numpy as np
import scipy.signal as signal
from scipy.io import wavfile
import argparse
import subprocess
import os
import sys
import datetime

# --- Configuration ---
FS = 16000  # 16 kHz Wideband Audio
DURATION_HANDSHAKE_A = 1.5
DURATION_HANDSHAKE_B = 1.5
DURATION_SILENCE = 0.5

# AFSK Params
BAUD_RATE = 50 
FREQ_MARK_A = 1200; FREQ_SPACE_A = 2200 # Alice
FREQ_MARK_B = 1400; FREQ_SPACE_B = 2400 # Bob (Slightly different tone)

# Encryption Params
KEY_SEED = 12345

def format_time(seconds):
    td = datetime.timedelta(seconds=seconds)
    total_seconds = int(td.total_seconds())
    hours = total_seconds // 3600
    minutes = (total_seconds % 3600) // 60
    secs = total_seconds % 60
    millis = int((seconds - total_seconds) * 1000)
    return f"{hours:02}:{minutes:02}:{secs:02},{millis:03}"

def write_srt(filename, segments):
    with open(filename, 'w', encoding='utf-8') as f:
        for i, (start, end, text) in enumerate(segments, 1):
            f.write(f"{i}\n")
            f.write(f"{format_time(start)} --> {format_time(end)}\n")
            f.write(f"{text}\n\n")
    print(f"[*] Saved subtitles to {filename}")

def generate_handshake(duration, mark, space, fs=FS):
    num_bits = int(duration * BAUD_RATE)
    bits = np.random.randint(0, 2, num_bits)
    audio_segments = []
    current_phase = 0
    samples_per_bit = int(fs / BAUD_RATE)
    
    for b in bits:
        freq = mark if b == 1 else space
        t = np.arange(samples_per_bit) / fs
        segment = 0.5 * np.sin(2 * np.pi * freq * t + current_phase)
        audio_segments.append(segment)
        current_phase += 2 * np.pi * freq * (samples_per_bit / fs)
        current_phase %= (2 * np.pi)
    return np.concatenate(audio_segments)

def apply_phone_filter(audio, fs=FS):
    sos = signal.butter(10, [300, 3400], 'bandpass', fs=fs, output='sos')
    return signal.sosfilt(sos, audio)

def apply_cellular_degradation(audio, fs=FS):
    # Bandpass
    audio = apply_phone_filter(audio, fs)
    # 8k Resample simulation
    num_samples_original = len(audio)
    num_samples_8k = int(num_samples_original * (8000 / fs))
    audio_8k = signal.resample(audio, num_samples_8k)
    audio_recovered = signal.resample(audio_8k, num_samples_original)
    # Noise
    noise = np.random.normal(0, 0.005, len(audio_recovered))
    return audio_recovered + noise

def pitch_shift(audio, rate_change):
    """
    Simple pitch shift by resampling (changes duration too, which is fine)
    rate_change < 1.0 = Lower pitch, Slower
    rate_change > 1.0 = Higher pitch, Faster
    """
    new_len = int(len(audio) / rate_change)
    return signal.resample(audio, new_len)

def load_audio_input(filepath, fs=FS):
    print(f"[*] Loading Audio Input: {filepath}...")
    temp_wav = "temp_input_converted.wav"
    cmd = ["ffmpeg", "-y", "-i", filepath, "-ar", str(fs), "-ac", "1", temp_wav]
    
    try:
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        rate, data = wavfile.read(temp_wav)
        if data.dtype == np.int16: audio = data.astype(np.float32) / 32768.0
        elif data.dtype == np.uint8: audio = (data.astype(np.float32) - 128) / 128.0
        else: audio = data.astype(np.float32) / np.max(np.abs(data))
    except Exception as e:
        print(f"[!] Error: {e}")
        return generate_synthetic_voice(6.0, fs) # Fallback
    finally:
        if os.path.exists(temp_wav): os.remove(temp_wav)
    return audio

def generate_synthetic_voice(duration, fs=FS):
    t = np.arange(int(duration * fs)) / fs
    f0 = 150
    carrier = np.sin(2 * np.pi * f0 * t + 5 * np.sin(2 * np.pi * 0.5 * f0 * t))
    return carrier * 0.5

def generate_silence(duration, fs=FS):
    return np.zeros(int(duration * fs))

def xor_scramble(audio_float, seed):
    audio_int16 = (audio_float * 32767).astype(np.int16)
    rng = np.random.default_rng(seed)
    keystream = rng.integers(-32768, 32768, size=len(audio_int16), dtype=np.int16)
    audio_uint = audio_int16.view(np.uint16)
    key_uint = keystream.view(np.uint16)
    scrambled_uint = audio_uint ^ key_uint
    return scrambled_uint.view(np.int16).astype(np.float32) / 32767.0

def main():
    parser = argparse.ArgumentParser(description="SIGAO Two-Way Simulation")
    parser.add_argument("--output", default="sigao_simulation.wav", help="Output WAV filename")
    parser.add_argument("--input", help="Input MP3/WAV file")
    args = parser.parse_args()
    
    # 1. Load Input
    input_file = args.input
    if not input_file:
        possible = [f for f in os.listdir('.') if f.endswith('.mp3')]
        if possible: input_file = possible[0]
        
    if input_file and os.path.exists(input_file):
        full_audio = load_audio_input(input_file)
    else:
        full_audio = generate_synthetic_voice(10.0)

    # 2. Split into Alice and Bob
    midpoint = len(full_audio) // 2
    alice_raw = full_audio[:midpoint]
    bob_raw_source = full_audio[midpoint:]
    
    # Pitch shift Bob to sound different (0.85x speed/pitch = deeper voice)
    print("[*] Processing Voices (Alice vs Bob)...")
    bob_raw = pitch_shift(bob_raw_source, 0.85)
    
    # Degrade both to cellular quality
    alice_voice = apply_cellular_degradation(alice_raw)
    bob_voice = apply_cellular_degradation(bob_raw)
    
    # 3. Generate Timelines
    handshake_a = generate_handshake(DURATION_HANDSHAKE_A, FREQ_MARK_A, FREQ_SPACE_A)
    handshake_b = generate_handshake(DURATION_HANDSHAKE_B, FREQ_MARK_B, FREQ_SPACE_B)
    silence = generate_silence(DURATION_SILENCE)
    
    # Encryption
    print("[*] Generating Encrypted Sequences...")
    alice_enc = apply_phone_filter(xor_scramble(alice_voice, KEY_SEED))
    bob_enc = apply_phone_filter(xor_scramble(bob_voice, KEY_SEED + 1)) # Diff key/seed for realism? Or same session key.
    
    # Normalize Encrypted
    alice_enc = (alice_enc / np.max(np.abs(alice_enc))) * 0.8 if np.max(np.abs(alice_enc)) > 0 else alice_enc
    bob_enc = (bob_enc / np.max(np.abs(bob_enc))) * 0.8 if np.max(np.abs(bob_enc)) > 0 else bob_enc

    # --- Assembly ---
    print("[*] Assembling Two-Way Conversation Timeline...")
    
    # Structure:
    # 1. HS Alice
    # 2. HS Bob
    # 3. Eavesdropper View (Alice Encrypted, Bob Encrypted)
    # 4. Secure View (Alice Decrypted, Bob Decrypted)
    
    parts = []
    
    # Handshakes
    parts.append((handshake_a, "[HANDSHAKE] Alice: Sending Identity Key..."))
    parts.append((generated_gap := generate_silence(0.2), ""))
    parts.append((handshake_b, "[HANDSHAKE] Bob: Verified. Sending Session Key..."))
    parts.append((silence, ""))
    
    # Eavesdropper
    parts.append((alice_enc, "[EAVESDROPPER] Alice's Line: Encrypted Static"))
    parts.append((generated_gap, ""))
    parts.append((bob_enc, "[EAVESDROPPER] Bob's Line: Encrypted Static"))
    parts.append((silence, ""))
    
    # Secure View
    parts.append((alice_voice, "[SECURE] Alice: \"...\" (Decrypted)"))
    parts.append((generated_gap, ""))
    parts.append((bob_voice, "[SECURE] Bob: \"...\" (Decrypted)"))
    
    timeline = []
    srt_segments = []
    current_time = 0.0
    
    for audio, label in parts:
        duration = len(audio) / FS
        if duration == 0: continue
        timeline.append(audio)
        if label:
            srt_segments.append((current_time, current_time + duration, label))
        current_time += duration
        
    final_audio = np.concatenate(timeline)
    
    # Save
    print(f"[*] Saving to {args.output}...")
    max_val = np.max(np.abs(final_audio))
    if max_val > 0: final_audio /= max_val
    wavfile.write(args.output, FS, (final_audio * 32767).astype(np.int16))
    
    # SRT
    srt_filename = os.path.splitext(args.output)[0] + ".srt"
    write_srt(srt_filename, srt_segments)
    print("[+] Two-Way Simulation Complete.")

if __name__ == "__main__":
    main()
