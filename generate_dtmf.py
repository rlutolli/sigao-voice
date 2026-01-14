import wave
import math
import struct
import os

# DTMF Frequencies
DTMF_FREQS = {
    '1': (1209, 697), '2': (1336, 697), '3': (1477, 697),
    '4': (1209, 770), '5': (1336, 770), '6': (1477, 770),
    '7': (1209, 852), '8': (1336, 852), '9': (1477, 852),
    '*': (1209, 941), '0': (1336, 941), '#': (1477, 941),
}

SAMPLE_RATE = 44100
DURATION = 0.3  # 300ms tone

def generate_tone(symbol, filename):
    freq1, freq2 = DTMF_FREQS[symbol]
    n_samples = int(SAMPLE_RATE * DURATION)
    
    with wave.open(filename, 'w') as wav_file:
        wav_file.setnchannels(1) # Mono
        wav_file.setsampwidth(2) # 16-bit
        wav_file.setframerate(SAMPLE_RATE)
        
        for i in range(n_samples):
            t = float(i) / SAMPLE_RATE
            # Mix two sine waves
            val = 0.5 * math.sin(2 * math.pi * freq1 * t) + \
                  0.5 * math.sin(2 * math.pi * freq2 * t)
            # Scale to 16-bit integer
            data = struct.pack('<h', int(val * 32767.0))
            wav_file.writeframesraw(data)

os.makedirs('assets/sounds', exist_ok=True)

for symbol in DTMF_FREQS:
    safe_name = symbol
    if symbol == '*': safe_name = 'star'
    elif symbol == '#': safe_name = 'hash'
    
    filename = f"assets/sounds/dtmf_{safe_name}.wav"
    print(f"Generating {filename}...")
    generate_tone(symbol, filename)
