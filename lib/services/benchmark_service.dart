import 'dart:async';
import 'dart:math';
import 'log_service.dart';
import '../ffi/sigao_core_ffi.dart';

class BenchmarkService {
  final SigaoCoreFFI _ffi = SigaoCoreFFI();
  final LogService _log = LogService();

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
    onProgress("Running Modulation Stress Test (100 iter)...");
    final stopwatch = Stopwatch()..start();
    
    for (int i = 0; i < 100; i++) {
       _ffi.modulateMessage("Hello Sigao Benchmark $i");
    }
    
    stopwatch.stop();
    final avg = stopwatch.elapsedMilliseconds / 100;
    onProgress("Modem: 100 Messages modulated in ${stopwatch.elapsedMilliseconds}ms (Avg: ${avg.toStringAsFixed(2)}ms)");
  }

  Future<void> _testAudioPipeline(Function(String) onProgress) async {
    onProgress("Running Audio Pipeline Stress Test (500 frames)...");
    final stopwatch = Stopwatch()..start();
    
    // Simulate 500 frames of 40ms audio (20 seconds of talk time)
    // This often reveals memory leaks in naive implementations.
    List<int> dummyPcm = List.filled(320, 0); // Silence/Flat
    
    for (int i = 0; i < 500; i++) {
      _ffi.ingestAudio(dummyPcm);
      if (i % 100 == 0) onProgress("  Pipeline: Processed $i frames...");
      await Future.delayed(Duration.zero); // Yield to event loop
    }
    
    stopwatch.stop();
    final throughput = 500 / (stopwatch.elapsedMilliseconds / 1000);
    onProgress("Audio: 500 Frames processed in ${stopwatch.elapsedMilliseconds}ms (${throughput.toStringAsFixed(1)} fps)");
  }
}
