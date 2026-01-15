import 'package:flutter/material.dart';
import 'services/benchmark_service.dart';

void main() {
  runApp(const BenchmarkApp());
}

class BenchmarkApp extends StatelessWidget {
  const BenchmarkApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: BenchmarkRunner(),
      ),
    );
  }
}

class BenchmarkRunner extends StatefulWidget {
  const BenchmarkRunner({super.key});

  @override
  State<BenchmarkRunner> createState() => _BenchmarkRunnerState();
}

class _BenchmarkRunnerState extends State<BenchmarkRunner> {
  String _status = "Initializing...";
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _runBenchmarks();
  }

  Future<void> _runBenchmarks() async {
    await Future.delayed(const Duration(seconds: 1)); // Wait for UI
    
    BenchmarkService().runFullSuite((log) {
      if (!mounted) return;
      setState(() {
        _status += "\n$log";
      });
      // Auto-scroll
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.hasClients) {
          _scrollController.animateTo(
            _scrollController.position.maxScrollExtent, 
            duration: const Duration(milliseconds: 200), 
            curve: Curves.easeOut);
        }
      });
      debugPrint("[BENCHMARK_CLI] $log"); // Print to console for Agent to see
    });
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black,
      padding: const EdgeInsets.all(24),
      child: Center(
        child: SingleChildScrollView(
          controller: _scrollController,
          child: Text(
            _status,
            style: const TextStyle(color: Colors.greenAccent, fontFamily: 'monospace', fontSize: 14),
          ),
        ),
      ),
    );
  }
}
