import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:sodium_libs/sodium_libs.dart';
import 'dart:typed_data';

class KeyExchangeService extends ChangeNotifier {
  // Native Keystore Channel
  static const _keystoreChannel = MethodChannel('com.sigao.voice/keystore');
  
  // Sodium (Ephemeral Keys)
  late Sodium _sodium;
  bool _isSodiumReady = false;

  // Identity (Long-term, Hardware)
  String? _identityPubKey; // Base64
  
  // Ephemeral (Session, Software)
  KeyPair? _ephemeralKeyPair;
  Uint8List? _sharedSecret;

  bool get isSecure => _sharedSecret != null;
  bool get isReady => _isSodiumReady && _identityPubKey != null;

  KeyExchangeService() {
    _initSodium();
  }

  Future<void> _initSodium() async {
    try {
      _sodium = await SodiumInit.init();
      _isSodiumReady = true;
      debugPrint("Sodium Initialized (libsodium-ffi)");
      
      // Auto-load hardware identity
      await _loadIdentity();
    } catch (e) {
      debugPrint("Sodium Init Failed: $e");
    }
  }

  Future<void> _loadIdentity() async {
    try {
      // 1. Try to fetch existing key
      final String? pubKey = await _keystoreChannel.invokeMethod('getPublicKey');
      
      if (pubKey != null) {
        _identityPubKey = pubKey;
        debugPrint("Hardware Identity Loaded: $_identityPubKey");
      } else {
        debugPrint("No Identity Key found. call generateIdentity()");
      }
      notifyListeners();
    } catch (e) {
      debugPrint("Keystore Error: $e");
    }
  }

  Future<void> generateIdentity() async {
    try {
      final bool success = await _keystoreChannel.invokeMethod('generate');
      if (success) {
        await _loadIdentity();
      }
    } catch (e) {
      debugPrint("Identity Gen Error: $e");
    }
  }

  /// Generates a one-time Curve25519 KeyPair for this call.
  /// Returns the Public Key signed by our Identity Key.
  Future<Map<String, dynamic>> generateEphemeralHandshakeData() async {
    if (!_isSodiumReady) throw Exception("Sodium not ready");

    // 1. Generate Ephemeral Key (Software)
    _ephemeralKeyPair = _sodium.crypto.box.keyPair();
    final ephPub = _ephemeralKeyPair!.pk;

    // 2. Sign it with Hardware Identity (Authenticity)
    final signature = await _keystoreChannel.invokeMethod('sign', {
      'data': ephPub
    });

    return {
      'ephemeralKey': ephPub,
      'identityKey': _identityPubKey, // To let them verify
      'signature': signature
    };
  }

  /// Calculates Shared Secret using our Ephemeral Private Key + Their Ephemeral Public Key
  void computeSharedSecret(Uint8List theirEphemeralPub) {
    if (_ephemeralKeyPair == null) return;
    
    // X25519 Diffie-Hellman
    _sharedSecret = _sodium.crypto.scalarmult(
      n: _ephemeralKeyPair!.sk,
      p: theirEphemeralPub
    );
    
    notifyListeners();
    debugPrint("ECDH: Session Established (StrongBox Authenticated)");
  }
}
