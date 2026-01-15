#ifndef CURVE25519_H
#define CURVE25519_H

#ifdef __cplusplus
extern "C" {
#endif

// Generates a public key from a random private key.
// Private key: 32 random bytes (clamped inside).
// Public key: 32 bytes output.
void curve25519_donna(unsigned char *mypublic, const unsigned char *secret, const unsigned char *basepoint);

#ifdef __cplusplus
}
#endif

#endif
