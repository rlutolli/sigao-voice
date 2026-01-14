import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'audio/audio_engine.dart';
import 'ui/theme/sigao_theme.dart';
import 'ui/screens/home_screen.dart';
import 'services/log_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Preload Audio System for DTMF
  final audioEngine = AudioEngine();
  await audioEngine.initSystem();

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => LogService()),
        ChangeNotifierProvider.value(value: audioEngine), // Use existing instance
      ],
      child: const SigaoApp(),
    ),
  );
}

class SigaoApp extends StatelessWidget {
  const SigaoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sigao Voice',
      debugShowCheckedModeBanner: false,
      theme: SigaoTheme.lightTheme,
      darkTheme: SigaoTheme.darkTheme,
      themeMode: ThemeMode.system, // Respect system setting
      home: const HomeScreen(),
    );
  }
}
