/*
 * randombytes() provider required by TweetNaCl.
 *
 * Pulls cryptographically secure random bytes from the operating system:
 *   - Android / Linux: getrandom(2) when available, else /dev/urandom
 *   - macOS / BSD:     arc4random_buf(3)
 *
 * This is a hard dependency for key generation and nonce selection.
 * If the OS CSPRNG cannot be read, the process aborts rather than
 * silently emitting weak/zero key material.
 */

#include <stddef.h>
#include <stdlib.h>

#if defined(__APPLE__) || defined(__FreeBSD__) || defined(__OpenBSD__)
  #include <stdlib.h>   /* arc4random_buf */
  #define SIGAO_HAVE_ARC4RANDOM 1
#else
  #include <unistd.h>
  #include <fcntl.h>
  #include <errno.h>
  #if defined(__linux__)
    #include <sys/syscall.h>
    #if defined(SYS_getrandom)
      #include <linux/random.h>
      #define SIGAO_HAVE_GETRANDOM 1
    #endif
  #endif
#endif

void randombytes(unsigned char *buf, unsigned long long n)
{
#if defined(SIGAO_HAVE_ARC4RANDOM)
    arc4random_buf(buf, (size_t)n);
#else
    unsigned long long off = 0;

  #if defined(SIGAO_HAVE_GETRANDOM)
    while (off < n) {
        long r = syscall(SYS_getrandom, buf + off, (size_t)(n - off), 0);
        if (r < 0) {
            if (errno == EINTR) continue;
            break; /* fall through to /dev/urandom */
        }
        off += (unsigned long long)r;
    }
    if (off >= n) return;
  #endif

    int fd = open("/dev/urandom", O_RDONLY);
    if (fd < 0) abort();
    while (off < n) {
        ssize_t r = read(fd, buf + off, (size_t)(n - off));
        if (r < 0) {
            if (errno == EINTR) continue;
            close(fd);
            abort();
        }
        off += (unsigned long long)r;
    }
    close(fd);
#endif
}
