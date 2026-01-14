import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_soloud/flutter_soloud.dart';

class KeypadButton extends StatelessWidget {
  final String digit;
  final String? subText;
  final VoidCallback onTap;
  final Color backgroundColor;
  final Color textColor;

  const KeypadButton({
    super.key,
    required this.digit,
    this.subText,
    required this.onTap,
    this.backgroundColor = Colors.transparent,
    this.textColor = Colors.black,
  });

  Future<void> _playTone() async {
    try {
      final name = digit == '*' ? 'star' : (digit == '#' ? 'hash' : digit);
      final asset = 'assets/sounds/dtmf_$name.wav';
      
      // Load and play
      // Note: In production, we should preload these in initState of parent,
      // but for now load-and-play is fast enough for <100kb files.
      final source = await SoLoud.instance.loadAsset(asset);
      await SoLoud.instance.play(source);
      // Auto-dispose is tricky here due to async, but SoLoud handles it reasonably. 
      // For a perfect dialer we'd cache the sources.
    } catch (e) {
      debugPrint("DTMF Playback Error: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTap: () {
        _playTone();
        HapticFeedback.lightImpact();
        onTap();
      },
      child: Container(
        margin: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: backgroundColor,
          shape: BoxShape.circle,
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              digit,
              style: TextStyle(
                fontSize: 36,
                fontWeight: FontWeight.w400,
                color: textColor,
              ),
            ),
            if (subText != null && subText!.isNotEmpty)
              Text(
                subText!,
                style: TextStyle(
                  fontSize: 10,
                  color: textColor.withOpacity(0.6),
                  letterSpacing: 1.0,
                ),
              ),
          ],
        ),
      ),
    );
  }
}
