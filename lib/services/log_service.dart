import 'package:flutter/material.dart';

class LogService extends ChangeNotifier {
  static final LogService _instance = LogService._internal();
  factory LogService() => _instance;
  LogService._internal();

  final List<String> _logs = [];
  List<String> get logs => _logs;

  void info(String message) {
    _addLog("[INFO] $message");
  }

  void error(String message) {
    _addLog("[ERROR] $message");
  }

  void debug(String message) {
    _addLog("[DEBUG] $message");
  }

  void _addLog(String entry) {
    final timestamp = DateTime.now().toIso8601String().split('T').last.split('.').first;
    _logs.add("$timestamp $entry");
    // Keep max 1000 logs
    if (_logs.length > 1000) {
      _logs.removeAt(0);
    }
    // Notify on main thread
    Future.microtask(() => notifyListeners());
  }
}
