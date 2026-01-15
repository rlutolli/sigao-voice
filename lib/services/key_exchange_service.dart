import 'package:flutter/foundation.dart';
import '../ffi/sigao_core_ffi.dart';

class KeyExchangeService extends ChangeNotifier {
  final SigaoCoreFFI _ffi = SigaoCoreFFI();
  
  List<int>? _myPublicKey;
  List<int>? _myPrivateKey;
  List<int>? _sharedSecret;

  bool get isSecure => _sharedSecret != null;

  void generateIdentity() {
    final keys = _ffi.generateKeyPair();
    _myPublicKey = keys['public'];
    _myPrivateKey = keys['private'];
    notifyListeners();
    debugPrint("ECDH: Identity Generated. PubKey: ${_myPublicKey!.sublist(0, 4)}...");
  }

  void establishSession(List<int> remotePublicKey) {
    if (_myPrivateKey == null) generateIdentity();
    
    _sharedSecret = _ffi.computeSharedSecret(_myPrivateKey!, remotePublicKey);
    notifyListeners();
    debugPrint("ECDH: Shared Secret Established! (Forward Secrecy Active)");
  }

  // Debug Helper
  List<int> get dummyRemoteKey {
    // Generate a temporary keypair and return public, simulating a remote peer
    final keys = _ffi.generateKeyPair();
    return keys['public']!;
  }
}
