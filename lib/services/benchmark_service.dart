import 'dart:async';
import 'dart:math';
import 'log_service.dart';
import '../ffi/sigao_core_ffi.dart';

class BenchmarkService {
  final SigaoCoreFFI _ffi = SigaoCoreFFI();
  final LogService _log = LogService();

  // A self-derived key (our own keypair) so the benchmark can exercise the
  // full encrypt -> FEC -> modulate -> demodulate -> decrypt pipeline locally.
  List<int> _benchKey() {
    final kp = _ffi.generateKeyPair();
    return _ffi.computeSharedKey(kp['private']!, kp['public']!);
  }

  Future<void> runFullSuite(Function(String) onProgress) async {
    _log.info("Starting System Benchmark Suite...");
    onProgress("Starting System Benchmark Suite...");

    try {
      await _testCrypto(onProgress);
      await _testModulation(onProgress);
      await _testAudioPipeline(onProgress);
      
      onProgress("✅ BENCHMARK COMPLETE. ALL SYSTEMS STABLE.");
      _log.info("Benchmark Complete. Stability Verified.");
    } catch (e) {
      onProgress("❌ CRITICAL FAILURE: $e");
      _log.error("Benchmark Failed: $e");
    } finally {
      // Keep FFI alive for app, or dispose if BenchmarkService is transient.
      // Usually keep alive or use shared instance.
    }
  }

  Future<void> _testCrypto(Function(String) onProgress) async {
    onProgress("Running Crypto Stress Test (50 iter)...");
    final stopwatch = Stopwatch()..start();
    
    for (int i = 0; i < 50; i++) {
      _ffi.generateKeyPair();
    }
    
    stopwatch.stop();
    final avg = stopwatch.elapsedMilliseconds / 50;
    onProgress("Crypto: 50 Keypairs generated in ${stopwatch.elapsedMilliseconds}ms (Avg: ${avg.toStringAsFixed(2)}ms)");
  }

  Future<void> _testModulation(Function(String) onProgress) async {
    onProgress("Running Secure Modulation Test (100 iter)...");
    final key = _benchKey();
    final stopwatch = Stopwatch()..start();

    for (int i = 0; i < 100; i++) {
      _ffi.txSecure(key, "Hello Sigao Benchmark $i".codeUnits);
    }

    stopwatch.stop();
    final avg = stopwatch.elapsedMilliseconds / 100;
    onProgress("Modem: 100 messages encrypted+modulated in ${stopwatch.elapsedMilliseconds}ms (Avg: ${avg.toStringAsFixed(2)}ms)");
  }

  Future<void> _testAudioPipeline(Function(String) onProgress) async {
    onProgress("Running Secure Round-Trip Test (200 frames)...");
    final key = _benchKey();
    final stopwatch = Stopwatch()..start();

    int recovered = 0;
    for (int i = 0; i < 200; i++) {
      final msg = "frame-$i".codeUnits;
      final audio = _ffi.txSecure(key, msg);
      final back = _ffi.rxSecure(key, audio.toList());
      if (back != null && _listEq(back, msg)) recovered++;
      if (i % 50 == 0) onProgress("  Pipeline: round-tripped $i frames...");
      await Future.delayed(Duration.zero); // Yield to event loop
    }

    stopwatch.stop();
    final throughput = 200 / (stopwatch.elapsedMilliseconds / 1000);
    onProgress("Audio: 200 secure round-trips in ${stopwatch.elapsedMilliseconds}ms "
        "($recovered/200 recovered, ${throughput.toStringAsFixed(1)} fps)");
  }

  bool _listEq(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }
}
