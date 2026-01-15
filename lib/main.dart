import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'audio/audio_engine.dart';
import 'ui/theme/sigao_theme.dart';
import 'ui/theme/theme_provider.dart';
import 'ui/screens/home_screen.dart';
import 'services/log_service.dart';
import 'services/key_exchange_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Preload Audio System for DTMF (Async / Non-blocking)
  final audioEngine = AudioEngine();
  audioEngine.initSystem(); // Fire and forget for faster startup

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => LogService()),
        ChangeNotifierProvider(create: (_) => ThemeProvider()),
        ChangeNotifierProvider(create: (_) => KeyExchangeService()),
        ChangeNotifierProvider.value(value: audioEngine),
      ],
      child: const SigaoApp(),
    ),
  );
}

class SigaoApp extends StatelessWidget {
  const SigaoApp({super.key});

  @override
  Widget build(BuildContext context) {
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
