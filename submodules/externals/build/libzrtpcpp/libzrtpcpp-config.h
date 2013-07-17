
/* Define to 1 if you have the <gcrypt.h> header file. */
/* #undef HAVE_GCRYPT_H */

/* Define to 1 if you have the `pthread' library (-lpthread). */
/* #undef HAVE_LIBPTHREAD */

/* Define to 1 if you have the <openssl/aes.h> header file. */
#define  HAVE_OPENSSL_AES_H 1

/* Define to 1 if you have the <openssl/bn.h> header file. */
#define  HAVE_OPENSSL_BN_H 1

/* Define to 1 if you have the <openssl/sha.h> header file. */
#define  HAVE_OPENSSL_SHA_H 1

/* Define to 1 if you have the <pthread.h> header file. */
#define  HAVE_PTHREAD_H 1

/* Name of package */
#define PACKAGE libzrtpcpp

/* Version number of package */
#define  VERSION 2.1.0

/* Define to empty if `const' does not conform to ANSI C. */
#undef const

/* Define to `__inline__' or `__inline' if that's what the C compiler
   calls it, or to nothing if 'inline' is not supported under any name.  */
#ifndef __cplusplus
#undef inline
#endif

/* Define to rpl_malloc if the replacement function should be used. */
#undef malloc

/* Define to the equivalent of the C99 'restrict' keyword, or to
   nothing if this is not supported.  Do not define if restrict is
   supported directly.  */
#undef restrict
/* Work around a bug in Sun C++: it does not support _Restrict, even
   though the corresponding Sun C compiler does, which causes
   "#define restrict _Restrict" in the previous line.  Perhaps some future
   version of Sun C++ will work with _Restrict; if so, it'll probably
   define __RESTRICT, just as Sun C does.  */
#if defined __SUNPRO_CC && !defined __RESTRICT
# define _Restrict
#endif

/* Define to empty if the keyword `volatile' does not work. Warning: valid
   code using `volatile' can become incorrect without. Disable with care. */
#undef volatile
