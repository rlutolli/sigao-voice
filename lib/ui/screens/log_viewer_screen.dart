import 'package:flutter/material.dart';
import '../../services/log_service.dart';
import '../../ui/theme/sigao_theme.dart';
import 'package:provider/provider.dart';

class LogViewerScreen extends StatelessWidget {
  const LogViewerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("System Logs"),
        actions: [
          IconButton(
            icon: const Icon(Icons.copy),
            onPressed: () {
               // TODO: Copy to clipboard
            },
          )
        ],
      ),
      backgroundColor: Colors.black,
      body: Consumer<LogService>(
        builder: (context, logService, child) {
          return ListView.builder(
            itemCount: logService.logs.length,
            padding: const EdgeInsets.all(8),
            itemBuilder: (context, index) {
              // Show newest at bottom (or top if reversed, but standard console is bottom)
              // Let's reverse to show newest at top for mobile convenience
              final log = logService.logs[logService.logs.length - 1 - index];
              
              Color color = Colors.green;
              if (log.contains("[ERROR]")) color = Colors.red;
              if (log.contains("[DEBUG]")) color = Colors.blue;

              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 2.0),
                child: Text(
                  log,
                  style: TextStyle(
                    fontFamily: 'Courier',
                    color: color,
                    fontSize: 12,
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
