// Host integration test for the in-app secure transport.
//
// Spins up two SecureSessions over real UDP loopback, performs the X25519
// handshake, and exchanges authenticated frames in both directions using the
// compiled native core (libsigao_core).
//
// Run from the repo root (so the macOS dylib path resolves):
//   ~/flutter/bin/dart run tool/secure_link_test.dart
//
// Exit 0 = pass.

import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import '../lib/ffi/sigao_core_ffi.dart';
import '../lib/services/secure_link.dart';

Future<void> main() async {
  final core = SigaoCoreFFI();
  stdout.writeln('core: ${core.version()}');

  final loop = InternetAddress('127.0.0.1');
  const aPort = 40051, bPort = 40052;
  const frames = 5;

  final alice = SecureSession(
    core: core,
    transport: UdpTransport(bindPort: aPort, peerAddress: loop, peerPort: bPort),
    log: (m) => stdout.writeln('[alice] $m'),
  );
  final bob = SecureSession(
    core: core,
    transport: UdpTransport(bindPort: bPort, peerAddress: loop, peerPort: aPort),
    log: (m) => stdout.writeln('[bob] $m'),
  );

  var bobGot = 0, aliceAcks = 0;

  bob.onData = (data, seq) {
    final text = String.fromCharCodes(data);
    stdout.writeln('[bob] received+verified seq=$seq: "$text"');
    bobGot++;
    bob.send('ACK $seq'.codeUnits);
  };
  alice.onData = (data, seq) {
    stdout.writeln('[alice] ACK verified: "${String.fromCharCodes(data)}"');
    aliceAcks++;
  };

  await bob.start();
  await alice.start();

  await Future.any([
    Future.wait([alice.ready, bob.ready]),
    Future.delayed(const Duration(seconds: 5)),
  ]);

  if (!alice.isReady || !bob.isReady) {
    stderr.writeln('RESULT: FAIL (handshake did not complete)');
    exit(1);
  }
  stdout.writeln('--- handshake OK, sending $frames frames ---');

  for (var i = 0; i < frames; i++) {
    alice.send('SIGAO secure UDP frame #$i'.codeUnits);
    await Future.delayed(const Duration(milliseconds: 50));
  }

  await Future.delayed(const Duration(milliseconds: 500));
  await alice.close();
  await bob.close();

  stdout.writeln('--- bob received $bobGot/$frames, alice got $aliceAcks/$frames ACKs ---');
  if (bobGot == frames && aliceAcks == frames) {
    stdout.writeln('RESULT: PASS');
    exit(0);
  }
  stdout.writeln('RESULT: FAIL');
  exit(1);
}
