import 'package:flutter/material.dart';
import '../../ui/theme/sigao_theme.dart';
import 'log_viewer_screen.dart';
import '../../services/call_service.dart';
import '../../simulation/handshake_simulation.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        const Padding(
          padding: EdgeInsets.all(16.0),
          child: Text("App Settings", style: TextStyle(fontWeight: FontWeight.bold, color: SigaoTheme.primaryColor)),
        ),
        ListTile(
          leading: const Icon(Icons.dark_mode_outlined),
          title: const Text("Appearance"),
          subtitle: const Text("System Default"),
          onTap: () {},
        ),
        ListTile(
          leading: const Icon(Icons.lock_outline),
          title: const Text("Privacy"),
          subtitle: const Text("Screen Lock, Incognito Keyboard"),
          onTap: () {},
        ),
        const Divider(),
        const Padding(
          padding: EdgeInsets.all(16.0),
          child: Text("Advanced", style: TextStyle(fontWeight: FontWeight.bold, color: SigaoTheme.primaryColor)),
        ),
        ListTile(
          leading: const Icon(Icons.sync_alt),
          title: const Text("Run Handshake Simulation (2-Party)"),
          subtitle: const Text("Verify Goertzel Tone Detection"),
          onTap: () {
             HandshakeSimulation().runSimulation((log) {
               ScaffoldMessenger.of(context).showSnackBar(
                 SnackBar(content: Text(log), duration: const Duration(milliseconds: 500)),
               );
               debugPrint(log);
             });
          },
        ),
        ListTile(
          leading: const Icon(Icons.notifications_active),
          title: const Text("Simulate Incoming Call"),
          subtitle: const Text("Test CallKit Integration"),
          onTap: () async {
            await CallService().showIncomingCall("Alice (Sigao)", "alice_123");
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text("Incoming Call Triggered")),
              );
            }
          },
        ),
        ListTile(
          leading: const Icon(Icons.terminal),
          title: const Text("Debug Logs"),
          subtitle: const Text("View SigaoNative & Audio Logs"),
          onTap: () {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const LogViewerScreen()),
            );
          },
        ),
        ListTile(
          leading: const Icon(Icons.info_outline),
          title: const Text("About Sigao Voice"),
          subtitle: const Text("v0.2.0 • Based on Signal Messenger"),
          onTap: () {
             showAboutDialog(
              context: context,
              applicationName: "Sigao Voice",
              applicationVersion: "0.2.0",
              applicationLegalese: "UI based on Signal Clone. Open Source MIT License.",
              children: [
                const SizedBox(height: 16),
                const Text("Core Protocol: Codec2 (1200bps) + FBMC + AES-256-GCM"),
              ],
            );
          },
        ),
      ],
    );
  }
}
