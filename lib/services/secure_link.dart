import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import '../ffi/sigao_core_ffi.dart';

// End-to-end secure session over a pluggable transport.
//
//   X25519 ECDH handshake  ->  XSalsa20-Poly1305 authenticated frames
//
// Transport-agnostic: works over UDP (production Profile-A) or a TCP relay
// (test harness for phone+emulator via `adb reverse`). This file is
// Flutter-free so it can be exercised with plain `dart run`.

typedef PacketHandler = void Function(Uint8List packet);
typedef LogFn = void Function(String msg);

void _noop(String _) {}

/// Moves whole packets between this peer and the wire.
abstract class SigaoTransport {
  void onPacket(PacketHandler handler);
  Future<void> start();
  void send(Uint8List packet);
  Future<void> close();
}

/// UDP transport: one datagram == one packet. Production path.
class UdpTransport implements SigaoTransport {
  final int bindPort;
  final InternetAddress peerAddress;
  final int peerPort;
  final LogFn log;

  RawDatagramSocket? _socket;
  PacketHandler? _handler;

  UdpTransport({
    required this.bindPort,
    required this.peerAddress,
    required this.peerPort,
    this.log = _noop,
  });

  @override
  void onPacket(PacketHandler handler) => _handler = handler;

  @override
  Future<void> start() async {
    _socket = await RawDatagramSocket.bind(InternetAddress.anyIPv4, bindPort);
    _socket!.listen((event) {
      if (event == RawSocketEvent.read) {
        final dg = _socket!.receive();
        if (dg != null && _handler != null) {
          _handler!(Uint8List.fromList(dg.data));
        }
      }
    });
    log('UDP bound on $bindPort -> ${peerAddress.address}:$peerPort');
  }

  @override
  void send(Uint8List packet) {
    _socket?.send(packet, peerAddress, peerPort);
  }

  @override
  Future<void> close() async {
    _socket?.close();
    _socket = null;
  }
}

/// TCP relay transport: length-prefixed packets over a stream. Test path
/// (phone + emulator both reach a host relay via `adb reverse`).
class TcpRelayTransport implements SigaoTransport {
  final String host;
  final int port;
  final LogFn log;

  Socket? _socket;
  PacketHandler? _handler;
  final BytesBuilder _rx = BytesBuilder();

  TcpRelayTransport({required this.host, required this.port, this.log = _noop});

  @override
  void onPacket(PacketHandler handler) => _handler = handler;

  @override
  Future<void> start() async {
    _socket = await Socket.connect(host, port);
    _socket!.listen(_onData, onError: (e) => log('TCP error: $e'),
        onDone: () => log('TCP closed'));
    log('TCP relay connected $host:$port');
  }

  void _onData(List<int> data) {
    _rx.add(data);
    var buf = _rx.toBytes();
    int offset = 0;
    while (buf.length - offset >= 2) {
      final len = (buf[offset] << 8) | buf[offset + 1];
      if (buf.length - offset - 2 < len) break; // wait for more
      final pkt = Uint8List.sublistView(buf, offset + 2, offset + 2 + len);
      _handler?.call(Uint8List.fromList(pkt));
      offset += 2 + len;
    }
    // Keep the remaining unparsed tail.
    _rx.clear();
    if (offset < buf.length) _rx.add(buf.sublist(offset));
  }

  @override
  void send(Uint8List packet) {
    final len = packet.length;
    final framed = Uint8List(2 + len)
      ..[0] = (len >> 8) & 0xFF
      ..[1] = len & 0xFF
      ..setRange(2, 2 + len, packet);
    _socket?.add(framed);
  }

  @override
  Future<void> close() async {
    await _socket?.close();
    _socket = null;
  }
}

/// Secure session: handshake then authenticated DATA frames.
class SecureSession {
  static const int _magic0 = 0x53; // 'S'
  static const int _magic1 = 0x47; // 'G'
  static const int _ver = 0x01;
  static const int _tHello = 0x01;
  static const int _tData = 0x02;

  final SigaoCoreFFI core;
  final SigaoTransport transport;
  final LogFn log;

  Uint8List? _myPriv;
  Uint8List? _myPub;
  Uint8List? _sharedKey;
  int _txSeq = 0;
  int _lastRxSeq = -1;

  bool _ready = false;
  bool get isReady => _ready;

  Timer? _helloTimer;
  final Completer<void> _readyCompleter = Completer<void>();

  /// Called with (plaintext, seq) for each authenticated DATA frame.
  void Function(Uint8List data, int seq)? onData;

  /// Called once the shared key is established.
  void Function()? onReady;

  SecureSession({required this.core, required this.transport, this.log = _noop});

  Future<void> start({Duration helloInterval = const Duration(milliseconds: 300)}) async {
    final kp = core.generateKeyPair();
    _myPriv = kp['private'];
    _myPub = kp['public'];

    transport.onPacket(_onPacket);
    await transport.start();

    _sendHello();
    // Retransmit HELLO until the peer responds (covers UDP loss).
    _helloTimer = Timer.periodic(helloInterval, (_) {
      if (_ready) {
        _helloTimer?.cancel();
      } else {
        _sendHello();
      }
    });
  }

  Future<void> get ready => _readyCompleter.future;

  void _sendHello() {
    final pkt = Uint8List(4 + 32)
      ..[0] = _magic0
      ..[1] = _magic1
      ..[2] = _ver
      ..[3] = _tHello
      ..setRange(4, 36, _myPub!);
    transport.send(pkt);
  }

  void _onPacket(Uint8List pkt) {
    if (pkt.length < 4 || pkt[0] != _magic0 || pkt[1] != _magic1 || pkt[2] != _ver) {
      return; // stray / foreign packet
    }
    final type = pkt[3];
    if (type == _tHello) {
      if (pkt.length < 36) return;
      if (_ready) return;
      final theirPub = Uint8List.sublistView(pkt, 4, 36);
      _sharedKey = core.computeSharedKey(_myPriv!, theirPub);
      _ready = true;
      log('handshake complete; shared key established');
      _helloTimer?.cancel();
      // Reply once so a late-joining peer also completes.
      _sendHello();
      onReady?.call();
      if (!_readyCompleter.isCompleted) _readyCompleter.complete();
    } else if (type == _tData) {
      if (!_ready || pkt.length < 8) return;
      final seq = (pkt[4] << 24) | (pkt[5] << 16) | (pkt[6] << 8) | pkt[7];
      final ct = Uint8List.sublistView(pkt, 8);
      final pt = core.decrypt(_sharedKey!, ct);
      if (pt == null) {
        log('DATA seq=$seq failed authentication; dropped');
        return;
      }
      if (seq == _lastRxSeq) return; // duplicate
      _lastRxSeq = seq;
      onData?.call(pt, seq);
    }
  }

  /// Encrypts and sends an application frame. Returns the sequence number.
  int send(List<int> plaintext) {
    if (!_ready) throw StateError('session not ready');
    final ct = core.encrypt(_sharedKey!, plaintext);
    final seq = _txSeq++;
    final pkt = Uint8List(8 + ct.length)
      ..[0] = _magic0
      ..[1] = _magic1
      ..[2] = _ver
      ..[3] = _tData
      ..[4] = (seq >> 24) & 0xFF
      ..[5] = (seq >> 16) & 0xFF
      ..[6] = (seq >> 8) & 0xFF
      ..[7] = seq & 0xFF
      ..setRange(8, 8 + ct.length, ct);
    transport.send(pkt);
    return seq;
  }

  Future<void> close() async {
    _helloTimer?.cancel();
    await transport.close();
  }
}
