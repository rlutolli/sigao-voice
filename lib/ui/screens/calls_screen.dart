import 'package:flutter/material.dart';
import '../../ui/theme/sigao_theme.dart';

class CallsScreen extends StatelessWidget {
  const CallsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.call_missed_outgoing, size: 64, color: Colors.grey[300]),
          const SizedBox(height: 16),
          const Text("No recent calls", style: TextStyle(color: Colors.grey)),
          const SizedBox(height: 8),
          const Text("Secure calls will appear here", style: TextStyle(fontSize: 12, color: SigaoTheme.primaryColor)),
        ],
      ),
    );
  }
}
