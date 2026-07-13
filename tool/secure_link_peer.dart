// Single secure-link peer over the TCP relay, using the SAME SecureSession /
// TcpRelayTransport code the app uses. Lets a host process interoperate with
// the on-device app for end-to-end testing.
//
//   ~/flutter/bin/dart run tool/secure_link_peer.dart <alice|bob> <host> <port> [frames]
//
// alice sends N frames (each ACKed by bob); bob receives and ACKs.

import 'dart:async';
import 'dart:io';

import '../lib/ffi/sigao_core_ffi.dart';
import '../lib/services/secure_link.dart';

Future<void> main(List<String> args) async {
  if (args.length < 3) {
    stderr.writeln('usage: secure_link_peer <alice|bob> <host> <port> [frames]');
    exit(2);
  }
  final role = args[0];
  final host = args[1];
  final port = int.parse(args[2]);
  final frames = args.length > 3 ? int.parse(args[3]) : 5;

  final core = SigaoCoreFFI();
  stdout.writeln('[$role] core ${core.version()}');

  final session = SecureSession(
    core: core,
    transport: TcpRelayTransport(host: host, port: port, log: (m) => stdout.writeln('[$role] $m')),
    log: (m) => stdout.writeln('[$role] $m'),
  );

  var rx = 0;
  final done = Completer<void>();

  session.onReady = () async {
    stdout.writeln('[$role] secure session ready');
    if (role == 'alice') {
      for (var i = 0; i < frames; i++) {
        session.send('SIGAO host frame #$i'.codeUnits);
        await Future.delayed(const Duration(milliseconds: 200));
      }
      stdout.writeln('[alice] sent $frames frames');
    }
  };

  session.onData = (data, seq) {
    final text = String.fromCharCodes(data);
    if (role == 'bob') {
      stdout.writeln('[bob] rx seq=$seq: "$text" -> ACK');
      session.send('ACK $seq'.codeUnits);
    } else {
      stdout.writeln('[alice] rx ACK: "$text"');
    }
    if (++rx >= frames && !done.isCompleted) done.complete();
  };

  await session.start();
  await Future.any([done.future, Future.delayed(const Duration(seconds: 60))]);
  await Future.delayed(const Duration(milliseconds: 300));
  await session.close();
  stdout.writeln('[$role] DONE: rx=$rx/$frames');
  exit(rx >= frames ? 0 : 1);
}
