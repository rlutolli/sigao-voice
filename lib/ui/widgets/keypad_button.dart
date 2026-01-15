import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTap: () {
        HapticFeedback.lightImpact();
        onTap();
      },
      child: Container(
        margin: const EdgeInsets.all(4), // Reduced margin
        decoration: BoxDecoration(
          color: backgroundColor,
          shape: BoxShape.circle,
        ),
        child: FittedBox( // Fixes Overflow by scaling down if needed
          fit: BoxFit.scaleDown,
          child: Padding(
            padding: const EdgeInsets.all(8.0),
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
        ),
      ),
    );
  }
}
