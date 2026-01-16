import 'package:flutter/material.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import '../../ui/theme/sigao_theme.dart';
import 'active_call_screen.dart';

import 'package:provider/provider.dart';
import '../../services/contacts_provider.dart';

class ContactsScreen extends StatelessWidget {
  const ContactsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<ContactsProvider>(
      builder: (context, provider, child) {
        if (provider.isLoading) {
          return const Center(child: CircularProgressIndicator());
        }

        if (provider.permissionDenied) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.contacts_outlined, size: 64, color: SigaoTheme.accentColor),
                const SizedBox(height: 16),
                const Text("Access to Contacts needed"),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () => provider.fetchContacts(),
                  child: const Text("Grant Permission"),
                ),
              ],
            ),
          );
        }

        if (provider.contacts.isEmpty) {
          return const Center(child: Text("No contacts found"));
        }

        return ListView.builder(
          itemCount: provider.contacts.length,
          itemBuilder: (context, index) {
            final contact = provider.contacts[index];
            final phone = contact.phones.isNotEmpty ? contact.phones.first.number : "No number";
            
            // Mock "Sigao User" detection (Randomly assign Shield to some)
            final isSigaoUser = contact.displayName.contains("a"); // Placeholder logic

            return ListTile(
              leading: CircleAvatar(
                backgroundColor: SigaoTheme.primaryColor,
                child: Text(
                  contact.displayName.isNotEmpty ? contact.displayName[0] : "?",
                  style: const TextStyle(color: Colors.white),
                ),
              ),
              title: Text(contact.displayName),
              subtitle: Text(phone),
              trailing: isSigaoUser 
                ? const Icon(Icons.security, color: SigaoTheme.secureGreen) 
                : const Icon(Icons.phone_outlined),
              onTap: () {
                // Trigger Secure Call UI Simulation
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => ActiveCallScreen(
                      contactName: contact.displayName,
                      contactNumber: phone,
                    ),
                  ),
                );
              },
            );
          },
        );
      },
    );
  }
}
