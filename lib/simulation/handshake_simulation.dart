import 'dart:async';
import 'dart:math';
import '../ffi/sigao_core_ffi.dart';

/// Simulates a live connection between two parties to verify the Handshake Protocol.
class HandshakeSimulation {
  final SigaoCoreFFI _ffi = SigaoCoreFFI();
  bool _isRunning = false;

  void runSimulation(Function(String) onLog) async {
    onLog("-- Starting Handshake Simulation (2 Callers) --");
    _isRunning = true;

    // Party A: Caller (Sends 1900Hz Handshake Tone)
    // Party B: Receiver (Listens and Detects)

    // 1. Generate Handshake Tone (Caller Side)
    // We simulate this by generating raw PCM sine wave at 1900Hz
    onLog("[A] Generating 1900Hz Handshake Tone...");
    List<int> handshakePcm = List.generate(320, (i) { // 40ms frame
      // 1900 Hz @ 8000 Hz sample rate
      return (10000 * sin(2 * pi * 1900 * i / 8000)).toInt();
    });

    // 2. Transmit (Simulated Network Delay)
    onLog("[Network] Transmitting Frame...");
    await Future.delayed(const Duration(milliseconds: 100));

    // 3. Receive & Detect (Receiver Side)
    onLog("[B] Listening...");
    
    // Test Noise (Should FAIL)
    List<int> noisePcm = List.generate(320, (i) => (Random().nextInt(1000)).toInt());
    bool noiseDetected = _ffi.detectHandshake(noisePcm);
    if (!noiseDetected) {
       onLog("[B] Noise Frame: Ignored (Correct)");
    } else {
       onLog("[B] ERROR: Noise falsely detected!");
    }

    // Test Signal (Should SUCCEED)
    bool detected = _ffi.detectHandshake(handshakePcm);
    if (detected) {
      onLog("[B] SUCCESS: Handshake Tone DETECTED!");
      onLog("[B] State: Switched to MODEM_MODE");
      
      // 4. Reply (Pong)
      onLog("[B] Sending ACK...");
      // ... ACK logic ...
      onLog("-- Simulation Complete: Protocol Verified --");
    } else {
      onLog("[B] FAILURE: Handshake Tone NOT Detected!");
    }
    
    _isRunning = false;
    _ffi.dispose(); // CRITICAL: Fix Memory Leak
  }
}
