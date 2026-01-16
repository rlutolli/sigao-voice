import math
import wave
import struct
import random

# Configuration
SAMPLE_RATE = 44100
DURATION_PING = 1.0
DURATION_PONG = 1.0
DURATION_KEY_SWAP = 2.0
DURATION_ENCRYPTED = 3.0
DURATION_DECRYPTED = 3.0
FILENAME = "sigao_handshake_sim.wav"
FILENAME_ROBUST = "sigao_robust_sim.wav"

def generate_tone(frequency, duration, volume=0.5):
    n_samples = int(SAMPLE_RATE * duration)
    data = []
    for i in range(n_samples):
        # Apply fade in/out to avoid clicks
        envelope = 1.0
        if i < 1000: envelope = i / 1000
        if i > n_samples - 1000: envelope = (n_samples - i) / 1000
        
        sample = volume * envelope * math.sin(2 * math.pi * frequency * i / SAMPLE_RATE)
        data.append(sample)
    return data

def generate_noise(duration, volume=0.5):
    n_samples = int(SAMPLE_RATE * duration)
    data = []
    for i in range(n_samples):
        sample = volume * (random.random() * 2 - 1)
        data.append(sample)
    return data

def generate_data_burst(duration, volume=0.5, frequencies=[1200, 2400]):
    # FSK Simulation (High/Low beeps)
    n_samples = int(SAMPLE_RATE * duration)
    data = []
    baud_rate = 20 # frequency changes per second
    samples_per_symbol = int(SAMPLE_RATE / baud_rate)
    
    current_freq = 0
    phase = 0
    
    for i in range(n_samples):
        if i % samples_per_symbol == 0:
            current_freq = random.choice(frequencies) # Custom tones
        
        phase += 2 * math.pi * current_freq / SAMPLE_RATE
        sample = volume * math.sin(phase)
        data.append(sample)
    return data

def generate_synthetic_voice(duration, volume=0.6):
    # Simulate "Voice" using FM Synthesis (Frequency Modulation)
    # This creates a harmonic, vowel-like sound that varies over time
    n_samples = int(SAMPLE_RATE * duration)
    data = []
    phase_carrier = 0
    phase_modulator = 0
    
    # "Talk" cadence parameters
    freq_carrier_base = 150 # Fundamental voice pitch (Hz)
    freq_modulator = 300    # Formant
    
    for i in range(n_samples):
        t = i / SAMPLE_RATE
        
        # Vary pitch to simulate intonation
        pitch_wobble = math.sin(2 * math.pi * 1.5 * t) * 20
        fc = freq_carrier_base + pitch_wobble
        
        # Vary modulation index to simulate syllables
        mod_index = 2.0 + 1.5 * math.sin(2 * math.pi * 4 * t)
        
        # FM Synthesis formula: A * sin(carrier + I * sin(modulator))
        phase_modulator += 2 * math.pi * freq_modulator / SAMPLE_RATE
        mod_val = mod_index * math.sin(phase_modulator)
        
        phase_carrier += 2 * math.pi * fc / SAMPLE_RATE
        sample = volume * math.sin(phase_carrier + mod_val)
        
        data.append(sample)
    return data

def save_wav(data, filename):
    # Normalize and pack to 16-bit PCM
    output_bytes = bytearray()
    for sample in data:
        # Clamp to -1.0 to 1.0
        s = max(-1.0, min(1.0, sample))
        # Scale to 16-bit integer
        i = int(s * 32767)
        output_bytes.extend(struct.pack('<h', i))

    with wave.open(filename, 'w') as wav_file:
        wav_file.setnchannels(1) # Mono
        wav_file.setsampwidth(2) # 2 bytes per sample (16-bit)
        wav_file.setframerate(SAMPLE_RATE)
        wav_file.writeframes(output_bytes)
    print(f"Generated {filename}")

def main():
    print("Generating Sigao Simulation...")
    
    # 1. Silent Ping (0.0 - 1.0s) -> 18kHz
    # High frequency, might be inaudible depending on speakers/age
    print("1. Generating Ping (18kHz)...")
    audio_ping = generate_tone(18000, DURATION_PING)
    
    # 2. Silent Pong (1.0 - 2.0s) -> 19kHz
    print("2. Generating Pong (19kHz)...")
    audio_pong = generate_tone(19000, DURATION_PONG)
    
    print("3. Generating Key Swap (Bidirectional)...")
    # Alice sends her Key (2.0s - 3.0s)
    print("   - Alice sending Identity Key (Standard Pitch)...")
    audio_key_alice = generate_data_burst(1.0, volume=0.5, frequencies=[1200, 2400]) 
    
    # Bob sends his Key (3.0s - 4.0s) - Use LOWER freq to distinguish
    print("   - Bob sending Identity Key (Low Pitch)...")
    audio_key_bob = generate_data_burst(1.0, volume=0.5, frequencies=[600, 800])

    # 4. Encrypted Voice (4.0 - 7.0s) -> White Noise (Scrambled)
    print("4. Generating Encrypted Voice (Scrambled)...")
    audio_encrypted = generate_noise(DURATION_ENCRYPTED, volume=0.2)
    
    # 5. Decrypted Voice (7.0 - 10.0s) -> Clear Speech
    print("5. Generating Decrypted Voice (Clear)...")
    audio_voice_clear = generate_synthetic_voice(DURATION_DECRYPTED)
    
    # --- Generate Robust Audio Parts ---
    print("\nGenerating Sigao Robust (Audible) Simulation...")
    print("1. Generating Robust Ping (3kHz)...")
    robust_ping = generate_tone(3000, DURATION_PING)
    
    print("2. Generating Robust Pong (3.5kHz)...")
    robust_pong = generate_tone(3500, DURATION_PONG)
    
    # Concatenate all parts
    # Robust Ping -> Robust Pong -> Alice Key -> Bob Key -> Encrypted -> Decrypted
    full_robust = robust_ping + robust_pong + audio_key_alice + audio_key_bob + audio_encrypted + audio_voice_clear
    save_wav(full_robust, FILENAME_ROBUST)

if __name__ == "__main__":
    main()
