#!/usr/bin/env python3
"""
Capturing relay = the "cell tower" / carrier vantage point.

Forwards length-framed packets between two peers AND wiretaps every frame to a
capture file. It performs NO crypto: it only sees ciphertext, exactly like a
passive interceptor sitting on the network path.

    python3 tools/relay_capture.py <port> <capture_out.bin>

Capture record format (repeated):
    dir(1)  0 = A->B, 1 = B->A
    ts(8)   capture time, ns, big-endian
    len(2)  payload length, big-endian
    payload(len)   the raw application packet (H/D/E)
"""
import socket
import struct
import sys
import threading
import time

def read_exact(sock, n):
    buf = b""
    while len(buf) < n:
        c = sock.recv(n - len(buf))
        if not c:
            return None
        buf += c
    return buf

def pump(src, dst, direction, cap, lock):
    try:
        while True:
            hdr = read_exact(src, 2)
            if hdr is None:
                break
            n = (hdr[0] << 8) | hdr[1]
            payload = read_exact(src, n) if n else b""
            if payload is None:
                break
            # forward verbatim
            dst.sendall(hdr + payload)
            # wiretap
            rec = bytes([direction]) + struct.pack(">Q", time.time_ns()) + struct.pack(">H", n) + payload
            with lock:
                cap.write(rec)
                cap.flush()
    except OSError:
        pass
    finally:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass

def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 7100
    cap_path = sys.argv[2] if len(sys.argv) > 2 else "tower_capture.bin"
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", port))
    srv.listen(2)
    print(f"[tower] listening on :{port}, capturing ciphertext to {cap_path}")

    c1, a1 = srv.accept(); print(f"[tower] peer A {a1}")
    c2, a2 = srv.accept(); print(f"[tower] peer B {a2}")
    print("[tower] wiretapping (sees only ciphertext)...")

    cap = open(cap_path, "wb")
    lock = threading.Lock()
    t1 = threading.Thread(target=pump, args=(c1, c2, 0, cap, lock), daemon=True)
    t2 = threading.Thread(target=pump, args=(c2, c1, 1, cap, lock), daemon=True)
    t1.start(); t2.start(); t1.join(); t2.join()
    cap.close()
    srv.close()
    print(f"[tower] session ended; capture saved to {cap_path}")

if __name__ == "__main__":
    main()
