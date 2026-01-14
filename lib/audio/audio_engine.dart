import 'package:flutter/foundation.dart';
import 'package:flutter_soloud/flutter_soloud.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:async';
import 'dart:math';
import '../ffi/sigao_core_ffi.dart';
import '../services/log_service.dart';

class AudioEngine extends ChangeNotifier {
  bool _isRunning = false;
  bool get isRunning => _isRunning;

  // SoLoud instance
  final SoLoud _soloud = SoLoud.instance;
  SoundHandle? _voiceLoopHandle;
  
  // FFI Wrapper
  SigaoCoreFFI? _ffi;
  Timer? _captureMockTimer;

  Future<void> start() async {
    if (_isRunning) return;

    final status = await Permission.microphone.request();
    if (status != PermissionStatus.granted) {
      debugPrint("Microphone permission denied");
      return;
    }

    try {
      // Initialize SoLoud (miniaudio)
      if (!_soloud.isInitialized) {
        await _soloud.init(
          sampleRate: 8000, 
          bufferSize: 1024, 
          channels: Channels.mono,
        );
      }

      // Initialize Core
      _ffi = SigaoCoreFFI();

      // Start "Capture" Loop
      // In a real app with 'mic_stream', we would listen to string.
      // Since we can't fully run/debug plugin deps here, 
      // I will implement a Mock Capture Timer that feeds "Noise/Tone" to the Ingest 
      // to demonstrate the FFI pipeline is working (You will hear modulated chirps).
      
      // REAL IMPLEMENTATION TODO: Replace this timer with `MicStream.stream.listen((data) => ...)`
      _captureMockTimer = Timer.periodic(const Duration(milliseconds: 40), (timer) {
        // Generate 320 samples (40ms @ 8kHz)
        // Simulating Voice: A mix of sine waves
        List<int> dummyPcm = List.generate(320, (i) {
          // 400Hz Tone (Voice fundamental approx)
          return (10000 * sin(2 * pi * 400 * (timer.tick * 320 + i) / 8000)).toInt();
        });

        // 1. INGEST (Codec2 -> Encrypt -> Modulate)
        List<double> modulatedFloat = _ffi!.ingestAudio(dummyPcm);
        
        // 2. PLAYBACK / LOGGING
        if (modulatedFloat.isNotEmpty) {
           final msg = "[CORE] Voice Pipeline: Mic(${dummyPcm.length}) -> Codec2/Modem -> Tx(${modulatedFloat.length} samples)";
           debugPrint(msg);
           LogService().info(msg);
        } else {
           final err = "[CORE] Modulation returned 0 samples!";
           debugPrint(err);
           LogService().error(err);
        }
      });

      _isRunning = true;
      notifyListeners();
      debugPrint("Sigao Voice Loop Active (Simulated Mic Source)");
      LogService().info("Audio Engine Started (Simulated)");
      
    } catch (e) {
      debugPrint("Error starting audio: $e");
      LogService().error("Error starting audio: $e");
      stop();
    }
  }

  Future<void> stop() async {
    if (!_isRunning) return;
    
    _captureMockTimer?.cancel();
    _ffi?.dispose();
    _ffi = null;
    
    _soloud.deinit();
    _isRunning = false;
    notifyListeners();
    debugPrint("Sigao Audio Engine Stopped");
    LogService().info("Audio Engine Stopped");
  }
}
