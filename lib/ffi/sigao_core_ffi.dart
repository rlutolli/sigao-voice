import 'dart:ffi';
import 'dart:io';
import 'package:ffi/ffi.dart';

// Typedefs (matching C++ signatures)
typedef sigao_create_modem_c = Pointer<Void> Function(Int32 sampleRate);
typedef sigao_create_modem_dart = Pointer<Void> Function(int sampleRate);

typedef sigao_destroy_modem_c = Void Function(Pointer<Void> handle);
typedef sigao_destroy_modem_dart = void Function(Pointer<Void> handle);

typedef sigao_modulate_c = Int32 Function(Pointer<Void> handle, Pointer<Utf8> msg, Pointer<Pointer<Float>> outBuffer);
typedef sigao_modulate_dart = int Function(Pointer<Void> handle, Pointer<Utf8> msg, Pointer<Pointer<Float>> outBuffer);

typedef sigao_audio_ingest_c = Int32 Function(Pointer<Void> handle, Pointer<Int16> pcm, Int32 len, Pointer<Pointer<Float>> outBuffer);
typedef sigao_audio_ingest_dart = int Function(Pointer<Void> handle, Pointer<Int16> pcm, int len, Pointer<Pointer<Float>> outBuffer);

typedef sigao_free_buffer_c = Void Function(Pointer<Float> buffer);
typedef sigao_free_buffer_dart = void Function(Pointer<Float> buffer);

typedef sigao_detect_handshake_c = Int32 Function(Pointer<Void> handle, Pointer<Int16> pcm, Int32 len);
typedef sigao_detect_handshake_dart = int Function(Pointer<Void> handle, Pointer<Int16> pcm, int len);

typedef sigao_gen_keypair_c = Void Function(Pointer<Uint8> pub, Pointer<Uint8> priv);
typedef sigao_gen_keypair_dart = void Function(Pointer<Uint8> pub, Pointer<Uint8> priv);

typedef sigao_compute_secret_c = Void Function(Pointer<Uint8> secret, Pointer<Uint8> myPriv, Pointer<Uint8> theirPub);
typedef sigao_compute_secret_dart = void Function(Pointer<Uint8> secret, Pointer<Uint8> myPriv, Pointer<Uint8> theirPub);

class SigaoCoreFFI {
  late DynamicLibrary _lib;
  late Pointer<Void> _modemHandle;
  
  // Functions
  late sigao_create_modem_dart _createModem;
  late sigao_destroy_modem_dart _destroyModem;
  late sigao_modulate_dart _modulate;
  late sigao_audio_ingest_dart _ingest;
  late sigao_detect_handshake_dart _detect;
  late sigao_gen_keypair_dart _genKeyPair;
  late sigao_compute_secret_dart _computeSecret;
  late sigao_free_buffer_dart _freeBuffer;

  SigaoCoreFFI() {
    // Load library
    if (Platform.isAndroid) {
      _lib = DynamicLibrary.open("libsigao_core.so");
    } else if (Platform.isLinux) {
      // Path assumption for dev
      _lib = DynamicLibrary.open("../sigao_core/build/libsigao_core.so");
    } else {
      _lib = DynamicLibrary.process();
    }

    // Lookup functions
    _createModem = _lib.lookupFunction<sigao_create_modem_c, sigao_create_modem_dart>("sigao_create_modem");
    _destroyModem = _lib.lookupFunction<sigao_destroy_modem_c, sigao_destroy_modem_dart>("sigao_destroy_modem");
    _modulate = _lib.lookupFunction<sigao_modulate_c, sigao_modulate_dart>("sigao_modulate");
    _ingest = _lib.lookupFunction<sigao_audio_ingest_c, sigao_audio_ingest_dart>("sigao_audio_ingest");
    _detect = _lib.lookupFunction<sigao_detect_handshake_c, sigao_detect_handshake_dart>("sigao_detect_handshake");
    _genKeyPair = _lib.lookupFunction<sigao_gen_keypair_c, sigao_gen_keypair_dart>("sigao_gen_keypair");
    _computeSecret = _lib.lookupFunction<sigao_compute_secret_c, sigao_compute_secret_dart>("sigao_compute_secret");
    _freeBuffer = _lib.lookupFunction<sigao_free_buffer_c, sigao_free_buffer_dart>("sigao_free_buffer");
    
    // Create instance
    _modemHandle = _createModem(8000);
  }

  void dispose() {
    _destroyModem(_modemHandle);
  }

  List<double> modulateMessage(String message) {
    final msgPtr = message.toNativeUtf8();
    final outBufPtr = calloc<Pointer<Float>>();
    
    try {
      int len = _modulate(_modemHandle, msgPtr, outBufPtr);
      if (len <= 0) return [];
      
      final floatPtr = outBufPtr.value;
      final result = <double>[];
      for (int i = 0; i < len; i++) {
        result.add(floatPtr[i]);
      }
      
      _freeBuffer(floatPtr);
      return result;
    } finally {
      calloc.free(msgPtr);
      calloc.free(outBufPtr);
    }
  }

  // New: Ingest PCM Audio (Short[]) -> Return Modulated Floats
  List<double> ingestAudio(List<int> pcmData) {
    final pcmPtr = calloc<Int16>(pcmData.length);
    final pcmList = pcmPtr.asTypedList(pcmData.length);
    pcmList.setAll(0, pcmData);
    
    final outBufPtr = calloc<Pointer<Float>>();

    try {
      // Pass to C++
      int len = _ingest(_modemHandle, pcmPtr, pcmData.length, outBufPtr);
      if (len <= 0) return [];

      final floatPtr = outBufPtr.value;
      final result = <double>[];
      for (int i = 0; i < len; i++) {
        result.add(floatPtr[i]);
      }
      
      _freeBuffer(floatPtr);
      return result;
    } finally {
      calloc.free(pcmPtr);
      calloc.free(outBufPtr);
    }
  }
  // New: Handshake Detection
  bool detectHandshake(List<int> pcmData) {
    if (pcmData.isEmpty) return false;
    
    final pcmPtr = calloc<Int16>(pcmData.length);
    final pcmList = pcmPtr.asTypedList(pcmData.length);
    pcmList.setAll(0, pcmData);

    try {
      int result = _detect(_modemHandle, pcmPtr, pcmData.length);
      return result == 1;
    } finally {
      calloc.free(pcmPtr);
      calloc.free(pcmPtr);
    }
  }

  // --- ECDH Operations ---

  // Returns {public: List<int>, private: List<int>}
  Map<String, List<int>> generateKeyPair() {
    final pubPtr = calloc<Uint8>(32);
    final privPtr = calloc<Uint8>(32);

    try {
      _genKeyPair(pubPtr, privPtr);
      
      final pubList = pubPtr.asTypedList(32).toList();
      final privList = privPtr.asTypedList(32).toList();
      
      return {'public': pubList, 'private': privList};
    } finally {
      calloc.free(pubPtr);
      calloc.free(privPtr);
    }
  }

  List<int> computeSharedSecret(List<int> myPrivate, List<int> theirPublic) {
    if (myPrivate.length != 32 || theirPublic.length != 32) throw Exception("Invalid Key Length");

    final secretPtr = calloc<Uint8>(32);
    final privPtr = calloc<Uint8>(32);
    final pubPtr = calloc<Uint8>(32);
    
    privPtr.asTypedList(32).setAll(0, myPrivate);
    pubPtr.asTypedList(32).setAll(0, theirPublic);

    try {
      _computeSecret(secretPtr, privPtr, pubPtr);
      return secretPtr.asTypedList(32).toList();
    } finally {
      calloc.free(secretPtr);
      calloc.free(privPtr);
      calloc.free(pubPtr);
    }
  }
}
