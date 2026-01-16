import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../widgets/keypad_button.dart';
import '../../ui/theme/sigao_theme.dart';

class DialerScreen extends StatefulWidget {
  const DialerScreen({super.key});

  @override
  State<DialerScreen> createState() => _DialerScreenState();
}

class _DialerScreenState extends State<DialerScreen> {
  final TextEditingController _numberController = TextEditingController();

  void _onDigitPress(String digit) {
    setState(() {
      _numberController.text += digit;
    });
  }

  void _onDelete() {
    if (_numberController.text.isNotEmpty) {
      setState(() {
        _numberController.text = _numberController.text.substring(0, _numberController.text.length - 1);
      });
    }
  }

  Future<void> _makeCall() async {
    final number = _numberController.text;
    if (number.isEmpty) return;

    final Uri launchUri = Uri(scheme: 'tel', path: number);
    if (await canLaunchUrl(launchUri)) {
      await launchUrl(launchUri);
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Could not launch system dialer")),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final txtColor = isDark ? Colors.white : Colors.black;

    // Google Phone has white/dark background, frameless buttons
    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: Column(
        children: [
          const Spacer(flex: 2), // Push down to bottom half

          // Number Display
          Container(
            alignment: Alignment.center,
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: TextField(
              controller: _numberController,
              textAlign: TextAlign.center,
              readOnly: true,
              showCursor: false, // No cursor like real dialer
              enableIMEPersonalizedLearning: false, // Incognito Keyboard
              style: TextStyle(
                fontSize: 36,
                fontWeight: FontWeight.w400,
                color: txtColor,
              ),
              decoration: const InputDecoration(border: InputBorder.none),
            ),
          ),
          
          const Spacer(flex: 1),

          // Frameless Keypad Grid
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 48.0),
            child: GridView.count(
              shrinkWrap: true,
              crossAxisCount: 3,
              mainAxisSpacing: 16,
              crossAxisSpacing: 24,
              childAspectRatio: 1.3,
              physics: const NeverScrollableScrollPhysics(),
              children: [
                 KeypadButton(digit: "1", subText: "  ", onTap: () => _onDigitPress("1"), textColor: txtColor),
                 KeypadButton(digit: "2", subText: "ABC", onTap: () => _onDigitPress("2"), textColor: txtColor),
                 KeypadButton(digit: "3", subText: "DEF", onTap: () => _onDigitPress("3"), textColor: txtColor),
                 KeypadButton(digit: "4", subText: "GHI", onTap: () => _onDigitPress("4"), textColor: txtColor),
                 KeypadButton(digit: "5", subText: "JKL", onTap: () => _onDigitPress("5"), textColor: txtColor),
                 KeypadButton(digit: "6", subText: "MNO", onTap: () => _onDigitPress("6"), textColor: txtColor),
                 KeypadButton(digit: "7", subText: "PQRS", onTap: () => _onDigitPress("7"), textColor: txtColor),
                 KeypadButton(digit: "8", subText: "TUV", onTap: () => _onDigitPress("8"), textColor: txtColor),
                 KeypadButton(digit: "9", subText: "WXYZ", onTap: () => _onDigitPress("9"), textColor: txtColor),
                 KeypadButton(digit: "*", onTap: () => _onDigitPress("*"), textColor: txtColor),
                 KeypadButton(digit: "0", subText: "+", onTap: () => _onDigitPress("0"), textColor: txtColor),
                 KeypadButton(digit: "#", onTap: () => _onDigitPress("#"), textColor: txtColor),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // Bottom Action Row
          Padding(
            padding: const EdgeInsets.only(bottom: 48.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly, // Distribute evenly
              children: [
                 // Left spacer (to balance Backspace) or Voicemail logic
                 const SizedBox(width: 64, height: 64), 

                 // Google-Style Floating FAB
                 SizedBox(
                   width: 72,
                   height: 72,
                   child: FloatingActionButton(
                     onPressed: _makeCall,
                     backgroundColor: SigaoTheme.primaryColor, // Google Blue
                     elevation: 4,
                     shape: const CircleBorder(),
                     child: const Icon(Icons.call, color: Colors.white, size: 32),
                   ),
                 ),

                 // Backspace
                 SizedBox(
                   width: 64,
                   height: 64,
                   child: IconButton(
                     onPressed: _onDelete,
                     icon: Icon(Icons.backspace_outlined, color: txtColor.withOpacity(0.7)),
                     splashRadius: 24,
                   ),
                 ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // --- Test Logic ---
  static const platform = MethodChannel('com.sigao.voice/handshake');

  Future<void> _startGhostTest() async {
    try {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Bob Mode: Listening for 3kHz...")));
      }
      await platform.invokeMethod('startListener');
    } catch (e) {
      print("Failed to start listener: $e");
    }
  }

  Future<void> _startGhostCall() async {
    try {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Alice Mode: Calling (3kHz)...")));
      }
      await platform.invokeMethod('startCall');
    } catch (e) {
      print("Failed to start call: $e");
    }
  }
}
