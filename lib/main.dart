import 'dart:async'; // For runZonedGuarded
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'audio/audio_engine.dart';
import 'ui/theme/sigao_theme.dart';
import 'ui/theme/theme_provider.dart';
import 'ui/screens/home_screen.dart';
import 'services/log_service.dart';
import 'services/key_exchange_service.dart';
import 'services/contacts_provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Preload Audio System for DTMF (Async / Non-blocking)
  final audioEngine = AudioEngine();
  // Init handled in SigaoApp.build via PostFrameCallback for max speed

  // Capture all print/debugPrint calls to LogService
  runZonedGuarded(() {
    runApp(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => LogService()),
          ChangeNotifierProvider(create: (_) => ThemeProvider()),
          ChangeNotifierProvider(create: (_) => KeyExchangeService()),
          ChangeNotifierProvider(create: (_) => ContactsProvider()), // New
          ChangeNotifierProvider.value(value: audioEngine),
        ],
        child: const SigaoApp(),
      ),
    );
  }, (error, stack) {
    LogService().error("Uncaught: $error");
    debugPrint("Uncaught: $error");
  }, zoneSpecification: ZoneSpecification(
    print: (self, parent, zone, line) {
      LogService().info(line); // Capture standard prints
      parent.print(zone, line);
    },
  ));
  
  // Override debugPrint to also pipe to LogService
  final originalDebugPrint = debugPrint;
  debugPrint = (String? message, {int? wrapWidth}) {
    if (message != null) LogService().debug(message);
    originalDebugPrint(message, wrapWidth: wrapWidth);
  };
}

class SigaoApp extends StatelessWidget {
  const SigaoApp({super.key});

  @override
  Widget build(BuildContext context) {
    // Defer Audio Init to ensure UI renders first
    WidgetsBinding.instance.addPostFrameCallback((_) {
       Provider.of<AudioEngine>(context, listen: false).initSystem();
    });

    return Consumer<ThemeProvider>(
      builder: (context, themeProvider, child) {
        return MaterialApp(
          title: 'Sigao Voice',
          debugShowCheckedModeBanner: false,
          theme: SigaoTheme.lightTheme,
          darkTheme: SigaoTheme.darkTheme,
          themeMode: themeProvider.themeMode, 
          home: const HomeScreen(),
        );
      },
    );
  }
}
