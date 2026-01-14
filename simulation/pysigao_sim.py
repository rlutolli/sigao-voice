#!/usr/bin/env python3
"""
Sigao Voice - Simulation Artifact
FBMC/OQAM Modulator/Demodulator for Data-over-Voice (DoV)
"""

import numpy as np
import scipy.signal as signal
from scipy.io import wavfile
import argparse
import sys
import os
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding
from cryptography.hazmat.backends import default_backend

# --- Constants ---
FS = 8000           # Sample rate (Hz) - AMR-NB base
SUBCARRIER_SPACING = 75.0 # Hz
SYMBOL_PERIOD = 1.0 / SUBCARRIER_SPACING # 13.33 ms
K_OVERLAP = 4       # PHYDYAS overlap factor
NUM_SUBCARRIERS = int(FS / 2 / SUBCARRIER_SPACING) # ~53
USEFUL_BW_START = 300 # Hz
USEFUL_BW_END = 3400  # Hz

# --- PHYDYAS Filter ---
def get_phydyas_coeffs(K):
    # Frequency response coefficients for K=4
    # H0=1, H1=0.97196, H2=0.70711, H3=0.23515
    return np.array([1.0, 0.97196, 0.70711, 0.23515])

class SigaoModem:
    def __init__(self):
        self.fs = FS
        self.delta_f = SUBCARRIER_SPACING
        self.samples_per_symbol = self.fs / self.delta_f # 106.666
        
        self.K = K_OVERLAP
        self.coeffs = get_phydyas_coeffs(self.K)
        
        # Subcarrier map
        self.subcarriers = []
        self.pilots = []
        self.data_indices = []
        
        # Bandwidth 300-3400
        start_idx = int(USEFUL_BW_START / self.delta_f)
        end_idx = int(USEFUL_BW_END / self.delta_f)
        
        # Pilot Logic: Harmonics of 150Hz
        # 150, 300, 450...
        self.pilot_freqs = set()
        f = 150
        while f < 3400:
            if f >= 300:
                self.pilot_freqs.add(f)
            f += 150
            
        for i in range(start_idx, end_idx):
            freq = i * self.delta_f
            # Find closest freq match for pilot?
            is_pilot = False
            for p in self.pilot_freqs:
                if abs(freq - p) < (self.delta_f / 2):
                    is_pilot = True
                    break
            
            if is_pilot:
                self.pilots.append(i)
            else:
                self.data_indices.append(i)
                
        print(f"Active Data Subcarriers: {len(self.data_indices)}")
        print(f"Pilot Subcarriers: {len(self.pilots)}")

    def modulate(self, bitstream):
        # Convert bits to 4-OQAM symbols (2 bits -> 1 real symbol amplitude)
        # 00 -> -3, 01 -> -1, 11 -> +1, 10 -> +3 (Gray coding)
        # Just simple mapping for now
        # Map bits to PAM symbols
        symbols = []
        for i in range(0, len(bitstream), 2):
            if i+1 >= len(bitstream): break
            b = (bitstream[i] << 1) | bitstream[i+1]
            if b == 0: val = -3.0
            elif b == 1: val = -1.0
            elif b == 3: val = 1.0 # 11
            elif b == 2: val = 3.0 # 10
            symbols.append(val)
        
        # Create grid: Rows=Subcarriers, Cols=Time
        num_data_streams = len(self.data_indices)
        if num_data_streams == 0:
            return np.zeros(100)
            
        num_symbols_time = (len(symbols) + num_data_streams - 1) // num_data_streams
        grid = np.zeros((num_data_streams, num_symbols_time))
        
        idx = 0
        for t in range(num_symbols_time):
            for s in range(num_data_streams):
                if idx < len(symbols):
                    grid[s, t] = symbols[idx]
                    idx += 1
        
        # Modulate summation
        total_time_samples = int((num_symbols_time + self.K) * (self.samples_per_symbol)) # roughly
        output_signal = np.zeros(total_time_samples + 1000)
        
        # Pre-compute prototype filter
        L = int(self.K * self.samples_per_symbol)
        if L % 2 != 0: L += 1 # Ensure even for ease
        
        m_indices = np.arange(L)
        
        # Simple raised cosine or Blackman as placeholder for PHYDYAS time domain if complex
        # But using the eq:
        proto = np.ones(L) * self.coeffs[0]
        for k in range(1, 4):
            proto += 2 * ((-1)**k) * self.coeffs[k] * np.cos(2 * np.pi * k * m_indices / L)
        proto /= np.sqrt(np.sum(proto**2))
        
        time_offset_samples = self.samples_per_symbol / 2.0
        
        print("Modulating...")
        # Superposition
        # Iterate over Data Subcarriers
        for i, sub_idx in enumerate(self.data_indices):
            freq = sub_idx * self.delta_f
            
            # Formant Shaping (Power control)
            gain = 1.0
            if 400 <= freq <= 800: gain = 1.0    # F1
            elif 1200 <= freq <= 2000: gain = 0.5 # F2
            elif freq > 2500: gain = 0.2         # F3
            else: gain = 0.05                    # Nulls
            
            phase_rot = np.pi / 2.0 # OQAM Phase shift term
            
            for n in range(num_symbols_time):
                symbol_val = grid[i, n]
                if symbol_val == 0: continue
                
                # t_start for this symbol
                t_center = n * time_offset_samples
                istart = int(t_center)
                if istart + L >= len(output_signal): break
                
                # Phase phi_mn = (pi/2) * (m+n)
                phi = (np.pi / 2) * (sub_idx + n)
                
                # Carrier term
                t_local = np.arange(L) / self.fs
                # Using continuous time t for carrier
                t_global_start = istart / self.fs
                carrier_phase = 2 * np.pi * freq * (t_local + t_global_start) + phi
                carrier = np.cos(carrier_phase) * 2
                
                wave = symbol_val * gain * proto * carrier
                
                output_signal[istart:istart+L] += wave

        # Add Pilots
        print("Adding Pilots...")
        for p_idx in self.pilots:
            freq = p_idx * self.delta_f
            t_all = np.arange(len(output_signal)) / self.fs
            pilot_wave = 1.5 * np.cos(2 * np.pi * freq * t_all) # Constant amplitude pilot
            output_signal += pilot_wave
            
        # Normalize
        max_val = np.max(np.abs(output_signal))
        if max_val > 0:
            output_signal /= max_val
        return output_signal

# --- Synchronization ---
def generate_gold_code():
    # Simple PN sequence for correlation (Length 31 for example, or longer)
    # Using a fixed pseudo-random sequence for PoC
    np.random.seed(42) # Fixed seed for "Gold Code"
    return np.random.choice([-1, 1], size=128)

class SigaoDemodulator:
    def __init__(self):
        self.fs = FS
        self.delta_f = SUBCARRIER_SPACING
        self.samples_per_symbol = self.fs / self.delta_f
        self.K = K_OVERLAP
        self.coeffs = get_phydyas_coeffs(self.K)
        
        # Reconstruct subcarrier map (same as Modulator)
        self.pilots = [] # Correction: Initialize list
        self.pilot_freqs = set()
        f = 150
        while f < 3400:
            if f >= 300: self.pilot_freqs.add(f)
            f += 150 
        
        self.data_indices = []
        start_idx = int(USEFUL_BW_START / self.delta_f)
        end_idx = int(USEFUL_BW_END / self.delta_f)
        for i in range(start_idx, end_idx):
            freq = i * self.delta_f
            is_pilot = any(abs(freq - p) < (self.delta_f/2) for p in self.pilot_freqs)
            if is_pilot:
                self.pilots.append(i) # Add to list
            else:
                self.data_indices.append(i)

    def sync(self, stream):
        # Correlate with Gold Code
        gold = generate_gold_code()
        # Upsample Gold Code to match FS?
        # Simulation: Make the preamble a baseband signal?
        # For simplicity in this script, we assume perfect sync or 
        # we can just assume the stream start is index 0 since we generated it.
        # But let's verify length.
        return 0 # Offset

    def demodulate(self, stream, num_symbols_est):
        # Per-subcarrier matched filtering
        
        # 1. Recover symbols
        num_data = len(self.data_indices)
        rx_grid = np.zeros((num_data, num_symbols_est))
        
        time_offset_samples = self.samples_per_symbol / 2.0
        L = int(self.K * self.samples_per_symbol)
        if L % 2 != 0: L += 1
        
        # Pre-compute proto
        m_indices = np.arange(L)
        proto = np.ones(L) * self.coeffs[0]
        for k in range(1, 4):
            proto += 2 * ((-1)**k) * self.coeffs[k] * np.cos(2 * np.pi * k * m_indices / L)
        proto /= np.sqrt(np.sum(proto**2))
        
        # First pass: Measure Pilots for AGC
        print("Demodulating...")
        pilot_energies = []
        for p_idx in self.pilots:
             freq = p_idx * self.delta_f
             # Measure energy at this freq over the duration
             # Simple DFT bin approx or correlation
             # Correlate entire stream with pilot tone?
             
             # Let's take a chunk from the middle to measure
             mid = len(stream) // 2
             chunk_len = 1000
             if mid + chunk_len < len(stream):
                 segment = stream[mid:mid+chunk_len]
                 t_seg = np.arange(chunk_len) / self.fs
                 # Demod pilot: multiply by cos and sin?
                 # Pilot was 1.5 * cos
                 # Proj = sum(segment * cos) * 2 / N
                 
                 ref = np.cos(2 * np.pi * freq * t_seg)
                 # Correct phase is unknown?
                 # Magnitude: sqrt(I^2 + Q^2)
                 
                 i_comp = np.sum(segment * np.cos(2 * np.pi * freq * t_seg))
                 q_comp = np.sum(segment * np.sin(2 * np.pi * freq * t_seg))
                 mag = 2.0 * np.sqrt(i_comp**2 + q_comp**2) / chunk_len
                 pilot_energies.append(mag)
        
        avg_pilot_mag = np.mean(pilot_energies) if pilot_energies else 1.0
        print(f"AGC: Avg Pilot Mag = {avg_pilot_mag:.4f}, Expected = 1.5")
        
        scale_factor = 1.0
        if avg_pilot_mag > 0.001:
            scale_factor = 1.5 / avg_pilot_mag
        print(f"AGC: Correction Factor = {scale_factor:.4f}")

        for i, sub_idx in enumerate(self.data_indices):
            freq = sub_idx * self.delta_f
            
            # Formant gain undo
            # TX: symbol * gain * proto * carrier
            # RX metric approx: symbol * gain * 2.0
            # BUT stream is scaled down by Normalize(max_val) in TX
            # AGC correction restores the original level relative to Pilots which were also scaled.
            # Pilots were 1.5. 
            # If we scale stream by 'scale_factor', then Pilots become 1.5.
            # Then Data becomes (symbol * gain * proto * carrier).
            # Then Metric becomes symbol * gain * 2.
            
            gain = 1.0
            if 400 <= freq <= 800: gain = 1.0
            elif 1200 <= freq <= 2000: gain = 0.5
            elif freq > 2500: gain = 0.2
            else: gain = 0.05
            
            for n in range(num_symbols_est):
                t_center  = n * time_offset_samples
                istart = int(t_center)
                if istart + L >= len(stream): break
                
                chunk = stream[istart:istart+L]
                
                phi = (np.pi / 2) * (sub_idx + n)
                cursor_carrier = np.cos(2 * np.pi * freq * (np.arange(L)/self.fs + istart/self.fs) + phi) * 2
                
                metric = np.sum(chunk * proto * cursor_carrier)
                
                # Apply AGC and Normalization
                # recovered_val ~= metric * scale_factor
                # recovered_sym = recovered_val / (gain * 2.0)
                
                val = (metric * scale_factor) / (gain * 2.0)
                rx_grid[i, n] = val
                
        # 2. Slicing
        # Symbols were -3, -1, 1, 3
        # We need to map back to bits
        recovered_bits = []
        
        # Flatten grid
        symbols_seq = []
        for t in range(num_symbols_est):
            for s in range(num_data):
                symbols_seq.append(rx_grid[s, t])
        
        for val in symbols_seq:
            # Slicer
            # -3, -1, 1, 3
            # Thresholds: -2, 0, 2
            
            # Simple distance check
            dist_m3 = abs(val - (-3.0))
            dist_m1 = abs(val - (-1.0))
            dist_1 = abs(val - 1.0)
            dist_3 = abs(val - 3.0)
            
            m = min(dist_m3, dist_m1, dist_1, dist_3)
            
            dec = 0
            if m == dist_m3: dec = 0 # 00
            elif m == dist_m1: dec = 1 # 01
            elif m == dist_1: dec = 3 # 11
            elif m == dist_3: dec = 2 # 10
            
            # Dec to bits
            recovered_bits.append((dec >> 1) & 1)
            recovered_bits.append(dec & 1)
            
        return recovered_bits

def channel_simulate(audio, fs):
    """
    Simulate AMR-WB / EVS channel effects.
    1. Bandpass 300-3400.
    2. Quantization noise (add white noise).
    """
    print("Simulating Channel...")
    # Bandpass
    try:
        sos = signal.butter(10, [300, 3400], 'bandpass', fs=fs, output='sos')
        filtered = signal.sosfilt(sos, audio)
    except Exception as e:
        print(f"Filter error (scipy version?): {e}")
        filtered = audio
    
    # Add noise (SNR 25dB - mild for first test)
    noise = np.random.normal(0, 0.005, len(filtered))
    return filtered + noise

def main():
    parser = argparse.ArgumentParser(description='Sigao Voice Simulation')
    parser.add_argument('--input', type=str, help='Input file (text)', default="Hello Sigao")
    parser.add_argument('--output', type=str, help='Output WAV file', default="sigao_out.wav")
    args = parser.parse_args()

    # 1. Prepare Data
    print(f"Input: {args.input}")
    data = args.input.encode('utf-8')
    # Convert to bits
    bits = []
    for byte in data:
        for i in range(8):
            bits.append((byte >> (7-i)) & 1)
            
    print(f"Tx Bits: {len(bits)}")
            
    # 2. Modulate
    modem = SigaoModem()
    waveform = modem.modulate(bits)
    
    # 3. Channel
    rx_waveform = channel_simulate(waveform, FS)
    
    # 4. Save
    wavfile.write(args.output, FS, (rx_waveform * 32767).astype(np.int16))
    print(f"Saved simulation to {args.output}")
    
    # 5. Demodulate (Verification)
    demod = SigaoDemodulator()
    
    # Estimate num symbols
    # Total samples / (samples_per_symbol / 2) roughly
    est_symbols = int(len(rx_waveform) / (modem.samples_per_symbol/2)) 
    # Use accurate count from bits for this unit test if possible, or slight overestimate
    est_symbols = waveform.shape[0] // int(modem.samples_per_symbol/2) + 2
    
    rx_bits = demod.demodulate(rx_waveform, est_symbols)
    
    # Convert back to bytes
    rx_bytes = bytearray()
    # Trim to expected length
    limit = len(bits)
    if len(rx_bits) > limit: rx_bits = rx_bits[:limit]
    
    # Pack
    for i in range(0, len(rx_bits), 8):
        if i+8 > len(rx_bits): break
        b = 0
        for j in range(8):
            b |= (rx_bits[i+j] << (7-j))
        rx_bytes.append(b)
        
    try:
        rec_str = rx_bytes.decode('utf-8', errors='ignore')
        print(f"Recovered Text: {rec_str}")
        
        # Calculate BER
        errors = 0
        for i in range(len(bits)):
             if i < len(rx_bits) and bits[i] != rx_bits[i]:
                 errors += 1
        ber = errors / len(bits)
        print(f"BER: {ber:.4f}")
        
        if ber < 0.1:
            print("SUCCESS: Data recovered with low error rate.")
        else:
            print("FAILURE: High BER.")
            
    except Exception as e:
        print(f"Decoding error: {e}")

if __name__ == "__main__":
    main()
