import 'package:flutter/material.dart';
import 'package:flutter_contacts/flutter_contacts.dart';

class ContactsProvider extends ChangeNotifier {
  List<Contact> _contacts = [];
  List<Contact> get contacts => _contacts;
  
  bool _isLoading = true;
  bool get isLoading => _isLoading;
  
  bool _permissionDenied = false;
  bool get permissionDenied => _permissionDenied;

  // Initialize
  ContactsProvider() {
    fetchContacts();
  }

  Future<void> fetchContacts() async {
    _isLoading = true;
    notifyListeners();
    
    // Request permission first
    if (await FlutterContacts.requestPermission(readonly: true)) {
        try {
            // Get contacts with properties (phones, photos)
            final contacts = await FlutterContacts.getContacts(
                withProperties: true, 
                withPhoto: false // Keep lightweight for list
            );
            _contacts = contacts;
            _permissionDenied = false;
        } catch (e) {
            debugPrint("Error fetching contacts: $e");
            _contacts = [];
        }
    } else {
        _permissionDenied = true;
    }
    
    _isLoading = false;
    notifyListeners();
  }
}
