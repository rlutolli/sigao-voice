import 'package:flutter/material.dart';
import '../../ui/theme/sigao_theme.dart';
import 'dart:async';

class ActiveCallScreen extends StatefulWidget {
  final String contactName;
  final String contactNumber;
  final bool isIncoming;

  const ActiveCallScreen({
    super.key,
    required this.contactName,
    required this.contactNumber,
    this.isIncoming = false,
  });

  @override
  State<ActiveCallScreen> createState() => _ActiveCallScreenState();
}

class _ActiveCallScreenState extends State<ActiveCallScreen> {
  // Call State
  bool _isConnected = false;
  bool _isSecure = false;
  Duration _duration = Duration.zero;
  Timer? _timer;

  // SAS Mock Data
  final List<String> _sasEmojis = ["🍎", "🚗", "🌳", "☀️"];
  bool _sasVerified = false;

  @override
  void initState() {
    super.initState();
    _simulateCallFlow();
  }

  void _simulateCallFlow() async {
    // 1. Dialing / Ringing
    await Future.delayed(const Duration(seconds: 2));
    if (!mounted) return;
    
    setState(() {
      _isConnected = true;
    });

    // Start Timer
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (mounted) {
        setState(() {
          _duration = Duration(seconds: timer.tick);
        });
      }
    });

    // 2. Handshake & Security Establishment
    await Future.delayed(const Duration(seconds: 2));
    if (!mounted) return;

    setState(() {
      _isSecure = true; // Handshake Success
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  String _formatDuration(Duration d) {
    String twoDigits(int n) => n.toString().padLeft(2, "0");
    String twoDigitMinutes = twoDigits(d.inMinutes.remainder(60));
    String twoDigitSeconds = twoDigits(d.inSeconds.remainder(60));
    return "${twoDigits(d.inHours)}:$twoDigitMinutes:$twoDigitSeconds";
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F151C), // Deep Signal-like Dark Blue
      body: SafeArea(
        child: Column(
          children: [
            // Top Bar: Verification State
            if (_isSecure)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 12),
                color: _sasVerified ? SigaoTheme.secureGreen : SigaoTheme.primaryColor.withOpacity(0.2),
                child: Column(
                  children: [
                    Text(
                      _sasVerified ? "SECURE CALL verified" : "Verify Safety Number",
                      style: TextStyle(
                        color: _sasVerified ? Colors.white : SigaoTheme.primaryColor,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1.0,
                      ),
                    ),
                    if (!_sasVerified) ...[
                      const SizedBox(height: 8),
                      // SAS Emojis
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: _sasEmojis.map((e) => Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                          child: Text(e, style: const TextStyle(fontSize: 28)),
                        )).toList(),
                      ),
                      const SizedBox(height: 4),
                      TextButton(
                        onPressed: () {
                          setState(() {
                            _sasVerified = true;
                          });
                        },
                        child: const Text("MARK AS VERIFIED"),
                      ),
                    ]
                  ],
                ),
              ),

            const Spacer(),

            // Avatar & Info
            CircleAvatar(
              radius: 64,
              backgroundColor: Colors.grey[800],
              child: Text(
                widget.contactName.isNotEmpty ? widget.contactName[0] : "?",
                style: const TextStyle(fontSize: 48, color: Colors.white),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              widget.contactName,
              style: const TextStyle(fontSize: 32, color: Colors.white, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
             Text(
              _isConnected ? _formatDuration(_duration) : (widget.isIncoming ? "Incoming..." : "Calling..."),
              style: const TextStyle(fontSize: 16, color: Colors.white70),
            ),
            
            // Security Badge
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: _isSecure ? SigaoTheme.secureGreen.withOpacity(0.2) : SigaoTheme.warningOrange.withOpacity(0.2),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: _isSecure ? SigaoTheme.secureGreen : SigaoTheme.warningOrange,
                ),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    _isSecure ? Icons.lock : Icons.lock_open,
                    size: 14,
                    color: _isSecure ? SigaoTheme.secureGreen : SigaoTheme.warningOrange,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    _isSecure ? "SIGAO ENCRYPTED" : "HANDSHAKING...",
                    style: TextStyle(
                      color: _isSecure ? SigaoTheme.secureGreen : SigaoTheme.warningOrange,
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),

            const Spacer(),

            // Controls
            Padding(
              padding: const EdgeInsets.only(bottom: 48.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  IconButton(
                    iconSize: 32,
                    icon: const Icon(Icons.mic_off, color: Colors.white),
                    onPressed: () {},
                  ),
                  GestureDetector(
                    onTap: () => Navigator.pop(context),
                    child: Container(
                      width: 72,
                      height: 72,
                      decoration: const BoxDecoration(
                        color: Colors.red,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.call_end, color: Colors.white, size: 36),
                    ),
                  ),
                  IconButton(
                    iconSize: 32,
                    icon: const Icon(Icons.volume_up, color: Colors.white),
                    onPressed: () {},
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
