import 'package:flutter/foundation.dart';
import 'package:flutter_soloud/flutter_soloud.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:async';
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
  List<int>? _loopbackKey;

  // Pre-initialize SoLoud on startup
  Future<void> initSystem() async {
    if (_soloud.isInitialized) return;
    try {
      await _soloud.init(
        sampleRate: 8000,
        bufferSize: 1024,
        channels: Channels.mono,
      );
      debugPrint("AudioEngine: SoLoud Initialized");
    } catch (e) {
      // Headless / audioless environments (e.g. emulator with -no-audio) can
      // fail here. Audio is optional for the secure-link/data paths, so log
      // and continue instead of taking down the app.
      debugPrint("AudioEngine: SoLoud init skipped ($e)");
    }
  }

  Future<void> start() async {
    if (_isRunning) return;

    final status = await Permission.microphone.request();
    if (status != PermissionStatus.granted) {
      debugPrint("Microphone permission denied");
      return;
    }

    try {
      // Ensure initialized (if initSystem missed)
      await initSystem();

      // Initialize Core
      _ffi = SigaoCoreFFI();
      LogService().info("Sigao Core: ${_ffi!.version()}");

      // Derive a local key so we can demonstrate the REAL secure pipeline
      // (encrypt -> FEC -> modulate -> demodulate -> decrypt) as a self-loopback.
      final kp = _ffi!.generateKeyPair();
      _loopbackKey = _ffi!.computeSharedKey(kp['private']!, kp['public']!);

      // NOTE: This timer is a stand-in for real microphone capture and a real
      // peer transport, which are not yet wired (see README "Status"). It feeds
      // a text payload through the actual native secure channel and verifies the
      // round-trip, so the cryptographic + DSP pipeline is exercised for real.
      int counter = 0;
      _captureMockTimer = Timer.periodic(const Duration(milliseconds: 500), (timer) {
        final message = "Sigao secure frame #${counter++}";
        try {
          final audio = _ffi!.txSecure(_loopbackKey!, message.codeUnits);
          final recovered = _ffi!.rxSecure(_loopbackKey!, audio.toList());
          if (recovered != null) {
            final text = String.fromCharCodes(recovered);
            final msg = "[CORE] Secure loopback OK: "
                "tx ${audio.length} samples -> rx \"$text\"";
            debugPrint(msg);
            LogService().info(msg);
          } else {
            const err = "[CORE] Secure loopback FAILED to recover frame";
            debugPrint(err);
            LogService().error(err);
          }
        } catch (e) {
          LogService().error("[CORE] Pipeline error: $e");
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
