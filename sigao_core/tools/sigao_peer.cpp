// sigao_peer — two-device end-to-end secure exchange over a TCP relay.
//
// Demonstrates a real Profile-A (data transport) link: two peers connect to an
// UNTRUSTED relay, perform X25519 ECDH through it, then exchange
// XSalsa20-Poly1305 authenticated frames the relay cannot read.
//
//   usage: sigao_peer <alice|bob> <host> <port> [num_frames]
//
// Wire format: each message is [4-byte big-endian length][bytes].
//   - handshake: a 32-byte X25519 public key
//   - data:      ciphertext from sigao_encrypt() (nonce + tag + ct)
//
// alice sends N voice-sized frames; bob verifies each and replies with an ACK
// that alice verifies — proving authenticated bidirectional comms end to end.

#include "sigao_core.h"

#include <cstdio>
#include <cstring>
#include <cstdint>
#include <string>
#include <vector>
#include <unistd.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/socket.h>

static bool write_all(int fd, const uint8_t* p, size_t n) {
    size_t off = 0;
    while (off < n) {
        ssize_t r = write(fd, p + off, n - off);
        if (r <= 0) return false;
        off += (size_t)r;
    }
    return true;
}

static bool read_all(int fd, uint8_t* p, size_t n) {
    size_t off = 0;
    while (off < n) {
        ssize_t r = read(fd, p + off, n - off);
        if (r <= 0) return false;
        off += (size_t)r;
    }
    return true;
}

static bool send_msg(int fd, const uint8_t* data, uint32_t len) {
    uint8_t hdr[4] = {(uint8_t)(len >> 24), (uint8_t)(len >> 16),
                      (uint8_t)(len >> 8), (uint8_t)len};
    return write_all(fd, hdr, 4) && (len == 0 || write_all(fd, data, len));
}

static bool recv_msg(int fd, std::vector<uint8_t>& out) {
    uint8_t hdr[4];
    if (!read_all(fd, hdr, 4)) return false;
    uint32_t len = ((uint32_t)hdr[0] << 24) | ((uint32_t)hdr[1] << 16) |
                   ((uint32_t)hdr[2] << 8) | hdr[3];
    if (len > (1u << 20)) return false;
    out.resize(len);
    return len == 0 || read_all(fd, out.data(), len);
}

static int connect_relay(const char* host, const char* port) {
    struct addrinfo hints{}, *res = nullptr;
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    if (getaddrinfo(host, port, &hints, &res) != 0) return -1;
    int fd = -1;
    for (auto* p = res; p; p = p->ai_next) {
        fd = socket(p->ai_family, p->ai_socktype, p->ai_protocol);
        if (fd < 0) continue;
        if (connect(fd, p->ai_addr, p->ai_addrlen) == 0) break;
        close(fd);
        fd = -1;
    }
    freeaddrinfo(res);
    return fd;
}

int main(int argc, char** argv) {
    if (argc < 4) {
        std::fprintf(stderr, "usage: %s <alice|bob> <host> <port> [num_frames]\n", argv[0]);
        return 2;
    }
    std::string role = argv[1];
    const char* host = argv[2];
    const char* port = argv[3];
    int frames = (argc > 4) ? atoi(argv[4]) : 5;

    std::printf("[%s] core %s — connecting to relay %s:%s\n",
                role.c_str(), sigao_version(), host, port);

    int fd = connect_relay(host, port);
    if (fd < 0) { std::fprintf(stderr, "[%s] connect failed\n", role.c_str()); return 1; }

    // X25519 ECDH through the relay.
    unsigned char myPub[32], myPriv[32], theirPub[32], key[32];
    sigao_gen_keypair(myPub, myPriv);
    if (!send_msg(fd, myPub, 32)) return 1;
    std::vector<uint8_t> peer;
    if (!recv_msg(fd, peer) || peer.size() != 32) {
        std::fprintf(stderr, "[%s] handshake failed\n", role.c_str()); return 1;
    }
    std::memcpy(theirPub, peer.data(), 32);
    sigao_compute_secret(key, myPriv, theirPub);
    std::printf("[%s] ECDH complete; shared key established\n", role.c_str());

    int ok = 0, bad = 0;

    if (role == "alice") {
        for (int i = 0; i < frames; ++i) {
            char msg[64];
            int mlen = std::snprintf(msg, sizeof(msg), "SIGAO secure voice frame #%d", i);
            unsigned char ct[256];
            int clen = sigao_encrypt(key, (const unsigned char*)msg, mlen, ct, sizeof(ct));
            if (clen < 0 || !send_msg(fd, ct, (uint32_t)clen)) { bad++; continue; }

            // Expect an authenticated ACK back from bob.
            std::vector<uint8_t> resp;
            if (!recv_msg(fd, resp)) { bad++; break; }
            unsigned char pt[256];
            int n = sigao_decrypt(key, resp.data(), (int)resp.size(), pt, sizeof(pt));
            if (n > 0 && std::strncmp((char*)pt, "ACK", 3) == 0) {
                std::printf("[alice] frame #%d delivered, ACK verified\n", i);
                ok++;
            } else {
                std::printf("[alice] frame #%d: bad/unauthenticated ACK\n", i);
                bad++;
            }
        }
    } else { // bob
        for (int i = 0; i < frames; ++i) {
            std::vector<uint8_t> in;
            if (!recv_msg(fd, in)) { bad++; break; }
            unsigned char pt[256];
            int n = sigao_decrypt(key, in.data(), (int)in.size(), pt, sizeof(pt));
            if (n > 0) {
                pt[n] = 0;
                std::printf("[bob] received+verified: \"%s\"\n", (char*)pt);
                ok++;
                char ack[32];
                int al = std::snprintf(ack, sizeof(ack), "ACK %d", i);
                unsigned char ct[128];
                int cl = sigao_encrypt(key, (const unsigned char*)ack, al, ct, sizeof(ct));
                if (cl < 0 || !send_msg(fd, ct, (uint32_t)cl)) { bad++; break; }
            } else {
                std::printf("[bob] frame #%d FAILED authentication\n", i);
                bad++;
            }
        }
    }

    close(fd);
    std::printf("[%s] DONE: %d ok, %d bad\n", role.c_str(), ok, bad);
    return bad == 0 ? 0 : 1;
}
