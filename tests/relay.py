#!/usr/bin/env python3
"""
Untrusted TCP relay for the two-device Sigao test.

Accepts exactly two TCP connections and pipes raw bytes between them in both
directions. It performs NO crypto and cannot read the payloads — it models a
dumb server / network path. The two peers (sigao_peer) do X25519 ECDH and
XSalsa20-Poly1305 over this pipe end to end.

    python3 tests/relay.py [port]   # default 7000

Use with adb so phone + emulator both reach the host:
    adb -s <phone>    reverse tcp:7000 tcp:7000
    adb -s <emulator> reverse tcp:7000 tcp:7000
"""

import socket
import sys
import threading


def pipe(src, dst, tag):
    try:
        while True:
            data = src.recv(4096)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 7000
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", port))
    srv.listen(2)
    print(f"[relay] listening on 0.0.0.0:{port}; serves repeated 2-peer sessions")

    while True:
        c1, a1 = srv.accept()
        print(f"[relay] peer A connected from {a1}")
        c2, a2 = srv.accept()
        print(f"[relay] peer B connected from {a2}")
        print("[relay] pairing; piping bytes (relay cannot read encrypted payloads)")

        t1 = threading.Thread(target=pipe, args=(c1, c2, "A->B"), daemon=True)
        t2 = threading.Thread(target=pipe, args=(c2, c1, "B->A"), daemon=True)
        t1.start()
        t2.start()
        t1.join()
        t2.join()
        print("[relay] session closed; waiting for next pair\n")


if __name__ == "__main__":
    main()
