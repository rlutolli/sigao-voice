import 'dart:ffi';
import 'dart:io';
import 'dart:typed_data';
import 'package:ffi/ffi.dart';

// FFI bindings for the Sigao Core native library.
//
// The native side provides a real secure acoustic data channel:
//   X25519 ECDH  ->  XSalsa20-Poly1305 AEAD  ->  Hamming(7,4) FEC  ->  FSK modem
//
// See sigao_core/sigao_core.h for the authoritative C signatures.

// ---- Native typedefs ----
typedef _CreateModemC = Pointer<Void> Function(Int32 sampleRate);
typedef _CreateModemD = Pointer<Void> Function(int sampleRate);

typedef _DestroyModemC = Void Function(Pointer<Void> handle);
typedef _DestroyModemD = void Function(Pointer<Void> handle);

typedef _VersionC = Pointer<Utf8> Function();
typedef _VersionD = Pointer<Utf8> Function();

typedef _FreeBufferC = Void Function(Pointer<Float> buffer);
typedef _FreeBufferD = void Function(Pointer<Float> buffer);

typedef _GenKeypairC = Void Function(Pointer<Uint8> pub, Pointer<Uint8> priv);
typedef _GenKeypairD = void Function(Pointer<Uint8> pub, Pointer<Uint8> priv);

typedef _ComputeSecretC = Int32 Function(
    Pointer<Uint8> sharedKey, Pointer<Uint8> myPriv, Pointer<Uint8> theirPub);
typedef _ComputeSecretD = int Function(
    Pointer<Uint8> sharedKey, Pointer<Uint8> myPriv, Pointer<Uint8> theirPub);

typedef _EncryptC = Int32 Function(Pointer<Uint8> key, Pointer<Uint8> msg, Int32 msglen,
    Pointer<Uint8> out, Int32 maxOut);
typedef _EncryptD = int Function(Pointer<Uint8> key, Pointer<Uint8> msg, int msglen,
    Pointer<Uint8> out, int maxOut);

typedef _TxSecureC = Int32 Function(Pointer<Void> handle, Pointer<Uint8> key,
    Pointer<Uint8> msg, Int32 msglen, Pointer<Pointer<Float>> outBuffer);
typedef _TxSecureD = int Function(Pointer<Void> handle, Pointer<Uint8> key,
    Pointer<Uint8> msg, int msglen, Pointer<Pointer<Float>> outBuffer);

typedef _RxSecureC = Int32 Function(Pointer<Void> handle, Pointer<Uint8> key,
    Pointer<Float> signal, Int32 len, Pointer<Uint8> out, Int32 maxOut);
typedef _RxSecureD = int Function(Pointer<Void> handle, Pointer<Uint8> key,
    Pointer<Float> signal, int len, Pointer<Uint8> out, int maxOut);

typedef _VoiceBitrateC = Int32 Function(Pointer<Void> handle);
typedef _VoiceBitrateD = int Function(Pointer<Void> handle);

typedef _DetectHandshakeC = Int32 Function(Pointer<Void> handle, Pointer<Int16> pcm, Int32 len);
typedef _DetectHandshakeD = int Function(Pointer<Void> handle, Pointer<Int16> pcm, int len);

/// Thrown when the native channel cannot produce or recover a frame.
class SigaoCoreException implements Exception {
  final String message;
  SigaoCoreException(this.message);
  @override
  String toString() => 'SigaoCoreException: $message';
}

class SigaoCoreFFI {
  static const int keyBytes = 32;

  late final DynamicLibrary _lib;
  late final Pointer<Void> _handle;

  late final _CreateModemD _createModem;
  late final _DestroyModemD _destroyModem;
  late final _VersionD _version;
  late final _FreeBufferD _freeBuffer;
  late final _GenKeypairD _genKeypair;
  late final _ComputeSecretD _computeSecret;
  late final _EncryptD _encrypt;
  late final _EncryptD _decrypt;
  late final _TxSecureD _txSecure;
  late final _RxSecureD _rxSecure;
  late final _TxSecureD _txVoice;
  late final _RxSecureD _rxVoice;
  late final _VoiceBitrateD _voiceBitrate;
  late final _DetectHandshakeD _detectHandshake;

  SigaoCoreFFI({int sampleRate = 8000}) {
    _lib = _open();

    _createModem = _lib.lookupFunction<_CreateModemC, _CreateModemD>('sigao_create_modem');
    _destroyModem = _lib.lookupFunction<_DestroyModemC, _DestroyModemD>('sigao_destroy_modem');
    _version = _lib.lookupFunction<_VersionC, _VersionD>('sigao_version');
    _freeBuffer = _lib.lookupFunction<_FreeBufferC, _FreeBufferD>('sigao_free_buffer');
    _genKeypair = _lib.lookupFunction<_GenKeypairC, _GenKeypairD>('sigao_gen_keypair');
    _computeSecret = _lib.lookupFunction<_ComputeSecretC, _ComputeSecretD>('sigao_compute_secret');
    _encrypt = _lib.lookupFunction<_EncryptC, _EncryptD>('sigao_encrypt');
    _decrypt = _lib.lookupFunction<_EncryptC, _EncryptD>('sigao_decrypt');
    _txSecure = _lib.lookupFunction<_TxSecureC, _TxSecureD>('sigao_tx_secure');
    _rxSecure = _lib.lookupFunction<_RxSecureC, _RxSecureD>('sigao_rx_secure');
    _txVoice = _lib.lookupFunction<_TxSecureC, _TxSecureD>('sigao_tx_voice');
    _rxVoice = _lib.lookupFunction<_RxSecureC, _RxSecureD>('sigao_rx_voice');
    _voiceBitrate =
        _lib.lookupFunction<_VoiceBitrateC, _VoiceBitrateD>('sigao_voice_gross_bitrate');
    _detectHandshake =
        _lib.lookupFunction<_DetectHandshakeC, _DetectHandshakeD>('sigao_detect_handshake');

    _handle = _createModem(sampleRate);
  }

  DynamicLibrary _open() {
    if (Platform.isAndroid) return DynamicLibrary.open('libsigao_core.so');
    if (Platform.isLinux) {
      return DynamicLibrary.open('sigao_core/build_host/libsigao_core.so');
    }
    if (Platform.isMacOS) {
      return DynamicLibrary.open('sigao_core/build_host/libsigao_core.dylib');
    }
    if (Platform.isWindows) return DynamicLibrary.open('sigao_core.dll');
    return DynamicLibrary.process();
  }

  String version() => _version().toDartString();

  void dispose() {
    _destroyModem(_handle);
  }

  // ---- Key agreement ----

  /// Generates an X25519 keypair: {'public': [...32], 'private': [...32]}.
  Map<String, Uint8List> generateKeyPair() {
    final pub = calloc<Uint8>(keyBytes);
    final priv = calloc<Uint8>(keyBytes);
    try {
      _genKeypair(pub, priv);
      return {
        'public': Uint8List.fromList(pub.asTypedList(keyBytes)),
        'private': Uint8List.fromList(priv.asTypedList(keyBytes)),
      };
    } finally {
      calloc.free(pub);
      calloc.free(priv);
    }
  }

  /// Derives the 32-byte shared key from our private key and the peer's public key.
  Uint8List computeSharedKey(List<int> myPrivate, List<int> theirPublic) {
    _require(myPrivate.length == keyBytes && theirPublic.length == keyBytes,
        'keys must be 32 bytes');
    final out = calloc<Uint8>(keyBytes);
    final priv = calloc<Uint8>(keyBytes)..asTypedList(keyBytes).setAll(0, myPrivate);
    final pub = calloc<Uint8>(keyBytes)..asTypedList(keyBytes).setAll(0, theirPublic);
    try {
      final rc = _computeSecret(out, priv, pub);
      if (rc != 0) throw SigaoCoreException('compute_secret failed ($rc)');
      return Uint8List.fromList(out.asTypedList(keyBytes));
    } finally {
      calloc.free(out);
      calloc.free(priv);
      calloc.free(pub);
    }
  }

  // ---- AEAD (no modem) ----

  Uint8List encrypt(List<int> sharedKey, List<int> message) {
    _require(sharedKey.length == keyBytes, 'key must be 32 bytes');
    final maxOut = message.length + 64;
    final key = calloc<Uint8>(keyBytes)..asTypedList(keyBytes).setAll(0, sharedKey);
    final msg = calloc<Uint8>(message.isEmpty ? 1 : message.length);
    if (message.isNotEmpty) msg.asTypedList(message.length).setAll(0, message);
    final out = calloc<Uint8>(maxOut);
    try {
      final n = _encrypt(key, msg, message.length, out, maxOut);
      if (n < 0) throw SigaoCoreException('encrypt failed');
      return Uint8List.fromList(out.asTypedList(n));
    } finally {
      calloc.free(key);
      calloc.free(msg);
      calloc.free(out);
    }
  }

  Uint8List decrypt(List<int> sharedKey, List<int> ciphertext) {
    _require(sharedKey.length == keyBytes, 'key must be 32 bytes');
    final maxOut = ciphertext.length + 16;
    final key = calloc<Uint8>(keyBytes)..asTypedList(keyBytes).setAll(0, sharedKey);
    final inp = calloc<Uint8>(ciphertext.length)..asTypedList(ciphertext.length).setAll(0, ciphertext);
    final out = calloc<Uint8>(maxOut);
    try {
      final n = _decrypt(key, inp, ciphertext.length, out, maxOut);
      if (n < 0) throw SigaoCoreException('decrypt/authentication failed');
      return Uint8List.fromList(out.asTypedList(n));
    } finally {
      calloc.free(key);
      calloc.free(inp);
      calloc.free(out);
    }
  }

  // ---- End-to-end secure channel ----

  /// Encrypt + FEC + modulate a message into audio samples.
  Float32List txSecure(List<int> sharedKey, List<int> message) {
    _require(sharedKey.length == keyBytes, 'key must be 32 bytes');
    final key = calloc<Uint8>(keyBytes)..asTypedList(keyBytes).setAll(0, sharedKey);
    final msg = calloc<Uint8>(message.isEmpty ? 1 : message.length);
    if (message.isNotEmpty) msg.asTypedList(message.length).setAll(0, message);
    final outPtr = calloc<Pointer<Float>>();
    try {
      final n = _txSecure(_handle, key, msg, message.length, outPtr);
      if (n <= 0) throw SigaoCoreException('tx_secure failed');
      final samples = Float32List.fromList(outPtr.value.asTypedList(n));
      _freeBuffer(outPtr.value);
      return samples;
    } finally {
      calloc.free(key);
      calloc.free(msg);
      calloc.free(outPtr);
    }
  }

  /// Demodulate + FEC-decode + verify/decrypt audio samples into a message.
  /// Returns null if no frame could be recovered/authenticated.
  Uint8List? rxSecure(List<int> sharedKey, List<double> samples, {int maxMessage = 4096}) {
    return _rxImpl(_rxSecure, sharedKey, samples, maxMessage);
  }

  // ---- High-rate voice channel (OFDM/DQPSK, ~3200 bit/s gross) ----

  /// Encrypt + FEC + OFDM-modulate (for compressed voice frames).
  Float32List txVoice(List<int> sharedKey, List<int> message) {
    return _txImpl(_txVoice, sharedKey, message);
  }

  /// Demodulate (OFDM) + FEC-decode + verify/decrypt.
  Uint8List? rxVoice(List<int> sharedKey, List<double> samples, {int maxMessage = 4096}) {
    return _rxImpl(_rxVoice, sharedKey, samples, maxMessage);
  }

  /// Gross (pre-FEC, pre-crypto) bit rate of the OFDM voice modem.
  int voiceGrossBitrate() => _voiceBitrate(_handle);

  // ---- shared TX/RX plumbing ----

  Float32List _txImpl(_TxSecureD fn, List<int> sharedKey, List<int> message) {
    _require(sharedKey.length == keyBytes, 'key must be 32 bytes');
    final key = calloc<Uint8>(keyBytes)..asTypedList(keyBytes).setAll(0, sharedKey);
    final msg = calloc<Uint8>(message.isEmpty ? 1 : message.length);
    if (message.isNotEmpty) msg.asTypedList(message.length).setAll(0, message);
    final outPtr = calloc<Pointer<Float>>();
    try {
      final n = fn(_handle, key, msg, message.length, outPtr);
      if (n <= 0) throw SigaoCoreException('tx failed');
      final samples = Float32List.fromList(outPtr.value.asTypedList(n));
      _freeBuffer(outPtr.value);
      return samples;
    } finally {
      calloc.free(key);
      calloc.free(msg);
      calloc.free(outPtr);
    }
  }

  Uint8List? _rxImpl(_RxSecureD fn, List<int> sharedKey, List<double> samples, int maxMessage) {
    _require(sharedKey.length == keyBytes, 'key must be 32 bytes');
    final key = calloc<Uint8>(keyBytes)..asTypedList(keyBytes).setAll(0, sharedKey);
    final sig = calloc<Float>(samples.length)..asTypedList(samples.length).setAll(0, samples);
    final out = calloc<Uint8>(maxMessage);
    try {
      final n = fn(_handle, key, sig, samples.length, out, maxMessage);
      if (n < 0) return null;
      return Uint8List.fromList(out.asTypedList(n));
    } finally {
      calloc.free(key);
      calloc.free(sig);
      calloc.free(out);
    }
  }

  // ---- Handshake tone detection ----

  bool detectHandshake(List<int> pcm) {
    if (pcm.isEmpty) return false;
    final p = calloc<Int16>(pcm.length)..asTypedList(pcm.length).setAll(0, pcm);
    try {
      return _detectHandshake(_handle, p, pcm.length) == 1;
    } finally {
      calloc.free(p);
    }
  }

  void _require(bool cond, String msg) {
    if (!cond) throw SigaoCoreException(msg);
  }
}
