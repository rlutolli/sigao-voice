import 'package:flutter/material.dart';
import 'calls_screen.dart';
import 'contacts_screen.dart';
import 'dialer_screen.dart';
import 'settings_screen.dart';
import '../../ui/theme/sigao_theme.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _selectedIndex = 2; // Default to Keypad (Index 2)

  final List<Widget> _screens = const [
    CallsScreen(),
    ContactsScreen(),
    DialerScreen(),
    SettingsScreen(),
  ];

  final List<String> _titles = const [
    "Calls",
    "Contacts",
    "Keypad",
    "Settings",
  ];

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_selectedIndex]),
        elevation: 0,
        actions: [
          if (_selectedIndex == 1) // Contacts
             IconButton(icon: const Icon(Icons.search), onPressed: () {}),
          if (_selectedIndex == 0) // Calls
             IconButton(icon: const Icon(Icons.filter_list), onPressed: () {}),
        ],
      ),
      body: SafeArea(
        child: IndexedStack(
          index: _selectedIndex,
          children: _screens,
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: _onItemTapped,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.call_outlined),
            selectedIcon: Icon(Icons.call),
            label: 'Calls',
          ),
          NavigationDestination(
            icon: Icon(Icons.contacts_outlined),
            selectedIcon: Icon(Icons.contacts),
            label: 'Contacts',
          ),
          NavigationDestination(
            icon: Icon(Icons.dialpad_outlined),
            selectedIcon: Icon(Icons.dialpad),
            label: 'Keypad',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: 'Settings',
          ),
        ],
      ),
    );
  }
}
