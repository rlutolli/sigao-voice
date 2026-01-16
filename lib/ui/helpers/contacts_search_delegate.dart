import 'package:flutter/material.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:provider/provider.dart';
import '../../services/contacts_provider.dart';
import '../screens/active_call_screen.dart';

class ContactsSearchDelegate extends SearchDelegate<String> {
  final List<Contact> contacts;

  ContactsSearchDelegate(this.contacts);

  @override
  List<Widget>? buildActions(BuildContext context) {
    return [
      IconButton(icon: const Icon(Icons.clear), onPressed: () => query = ""),
    ];
  }

  @override
  Widget? buildLeading(BuildContext context) {
    return IconButton(
      icon: const Icon(Icons.arrow_back), 
      onPressed: () => close(context, "")
    );
  }

  @override
  Widget buildResults(BuildContext context) {
    return _buildList(context);
  }

  @override
  Widget buildSuggestions(BuildContext context) {
    return _buildList(context);
  }

  Widget _buildList(BuildContext context) {
    final results = query.isEmpty
        ? []
        : contacts.where((c) => 
            c.displayName.toLowerCase().contains(query.toLowerCase())
          ).toList();

    if (results.isEmpty && query.isNotEmpty) {
      return Center(child: Text("No results for '$query'"));
    }
    
    // Suggest top contacts if query empty? Or show nothing.
    // Standard is show suggestion history or nothing. Showing nothing is fine.
    if (results.isEmpty) return const SizedBox();

    return ListView.builder(
      itemCount: results.length,
      itemBuilder: (context, index) {
        final contact = results[index];
        final phone = contact.phones.isNotEmpty ? contact.phones.first.number : "No number";
        
        return ListTile(
          leading: CircleAvatar(child: Text(contact.displayName[0])),
          title: Text(contact.displayName),
          subtitle: Text(phone),
          onTap: () {
             Navigator.pushReplacement(
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
  }
}
