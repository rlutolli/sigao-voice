import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';
import '../../ffi/sigao_core_ffi.dart';
import '../../services/secure_link.dart';

/// Interactive on-device test of the secure network transport.
///
/// Two devices connect to a TCP relay (via `adb reverse`), perform the X25519
/// handshake, and exchange authenticated frames. Can be driven entirely from
/// the command line via launch-intent extras (see [maybeAutoLaunch]).
class SecureLinkScreen extends StatefulWidget {
  final Map<String, dynamic> config;
  const SecureLinkScreen({super.key, this.config = const {}});

  static const _channel = MethodChannel('com.sigao.voice/securelink');

  /// Wires up secure-link auto-launch:
  ///   - cold start: poll `getConfig` for launch-intent extras
  ///   - warm start: native pushes `runConfig` via onNewIntent (singleTop)
  /// In both cases, if `role` is present, push/refresh this screen.
  static Future<void> maybeAutoLaunch(GlobalKey<NavigatorState> navKey) async {
    void launch(Map cfg) {
      if (cfg['role'] == null) return;
      // Drop any previous secure-link screen (and its relay connection) so we
      // don't leave a stale socket that confuses the relay's peer pairing.
      navKey.currentState?.popUntil((route) => route.isFirst);
      navKey.currentState?.push(MaterialPageRoute(
        builder: (_) => SecureLinkScreen(config: Map<String, dynamic>.from(cfg)),
      ));
    }

    _channel.setMethodCallHandler((call) async {
      if (call.method == 'runConfig' && call.arguments is Map) {
        launch(call.arguments as Map);
      }
      return null;
    });

    try {
      final cfg = await _channel.invokeMethod<Map>('getConfig');
      if (cfg != null) launch(cfg);
    } catch (_) {
      // channel not available; manual navigation still works
    }
  }

  @override
  State<SecureLinkScreen> createState() => _SecureLinkScreenState();
}

class _SecureLinkScreenState extends State<SecureLinkScreen> {
  final _hostCtrl = TextEditingController(text: '127.0.0.1');
  final _portCtrl = TextEditingController(text: '7100');
  String _role = 'alice';
  int _frames = 5;

  final List<String> _log = [];
  SecureSession? _session;
  SigaoCoreFFI? _core;
  bool _running = false;
  int _txOk = 0, _rxOk = 0;

  @override
  void initState() {
    super.initState();
    final c = widget.config;
    if (c['host'] != null) _hostCtrl.text = '${c['host']}';
    if (c['port'] != null) _portCtrl.text = '${c['port']}';
    if (c['role'] != null) _role = '${c['role']}';
    if (c['frames'] != null) _frames = int.tryParse('${c['frames']}') ?? 5;
    if (c['role'] != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _run());
    }
  }

  void _logLine(String s) {
    // Prefixed so `adb logcat | grep SecureLink` works.
    debugPrint('SecureLink: $s');
    if (mounted) setState(() => _log.add(s));
  }

  Future<void> _run() async {
    if (_running) return;
    setState(() {
      _running = true;
      _txOk = 0;
      _rxOk = 0;
      _log.clear();
    });

    final host = _hostCtrl.text.trim();
    final port = int.tryParse(_portCtrl.text.trim()) ?? 7100;
    _logLine('role=$_role connecting to relay $host:$port');

    try {
      _core = SigaoCoreFFI();
      _logLine('core ${_core!.version()}');

      final session = SecureSession(
        core: _core!,
        transport: TcpRelayTransport(host: host, port: port, log: _logLine),
        log: _logLine,
      );
      _session = session;

      session.onReady = () {
        _logLine('ECDH complete; secure session ready');
        if (_role == 'alice') {
          // Send from onReady so launch order / handshake delay don't matter.
          _sendAliceFrames(session);
        } else {
          _logLine('listening as bob; ready to receive');
        }
      };

      session.onData = (data, seq) {
        final text = String.fromCharCodes(data);
        if (_role == 'bob') {
          _logLine('rx seq=$seq: "$text" -> ACK');
          session.send('ACK $seq'.codeUnits);
          setState(() => _rxOk++);
        } else {
          _logLine('rx ACK: "$text"');
          setState(() => _rxOk++);
        }
      };

      await session.start();
      // Soft watchdog: report (don't abort) if the peer hasn't joined yet.
      Future.delayed(const Duration(seconds: 20), () {
        if (mounted && !session.isReady) {
          _logLine('still waiting for peer... (relay running + peer launched?)');
        }
      });
    } catch (e) {
      _logLine('ERROR: $e');
    } finally {
      setState(() => _running = false);
    }
  }

  Future<void> _sendAliceFrames(SecureSession session) async {
    for (var i = 0; i < _frames; i++) {
      session.send('SIGAO secure frame #$i'.codeUnits);
      setState(() => _txOk++);
      await Future.delayed(const Duration(milliseconds: 200));
    }
    _logLine('sent $_txOk frames');
  }

  Future<void> _stop() async {
    await _session?.close();
    _session = null;
    _core?.dispose();
    _core = null;
    _logLine('stopped');
  }

  @override
  void dispose() {
    _session?.close();
    _core?.dispose();
    _hostCtrl.dispose();
    _portCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Secure Link (UDP/TCP test)')),
      body: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(children: [
              Expanded(
                child: TextField(
                  controller: _hostCtrl,
                  decoration: const InputDecoration(labelText: 'Relay host'),
                ),
              ),
              const SizedBox(width: 8),
              SizedBox(
                width: 90,
                child: TextField(
                  controller: _portCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Port'),
                ),
              ),
            ]),
            const SizedBox(height: 8),
            Row(children: [
              const Text('Role: '),
              DropdownButton<String>(
                value: _role,
                items: const [
                  DropdownMenuItem(value: 'alice', child: Text('alice (sender)')),
                  DropdownMenuItem(value: 'bob', child: Text('bob (receiver)')),
                ],
                onChanged: _running ? null : (v) => setState(() => _role = v ?? 'alice'),
              ),
              const Spacer(),
              Text('tx:$_txOk  rx:$_rxOk'),
            ]),
            const SizedBox(height: 8),
            Row(children: [
              Expanded(
                child: ElevatedButton(
                  onPressed: _running ? null : _run,
                  child: Text(_running ? 'Running...' : 'Connect & Run'),
                ),
              ),
              const SizedBox(width: 8),
              OutlinedButton(onPressed: _stop, child: const Text('Stop')),
            ]),
            const Divider(),
            Expanded(
              child: Container(
                color: Colors.black,
                padding: const EdgeInsets.all(8),
                child: ListView.builder(
                  itemCount: _log.length,
                  itemBuilder: (_, i) => Text(
                    _log[i],
                    style: const TextStyle(
                        color: Colors.greenAccent, fontFamily: 'monospace', fontSize: 12),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
