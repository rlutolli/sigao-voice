import 'ui/theme/theme_provider.dart';

//...

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => LogService()),
        ChangeNotifierProvider(create: (_) => ThemeProvider()),
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
