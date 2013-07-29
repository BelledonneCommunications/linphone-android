
LOCAL_PATH:= $(call my-dir)/../../polarssl/library
include $(CLEAR_VARS)

LOCAL_MODULE:= libpolarssl

LOCAL_SRC_FILES := \
		aes.c \
		arc4.c \
		asn1parse.c \
		asn1write.c \
		base64.c \
		bignum.c \
		blowfish.c \
		camellia.c \
		certs.c \
		cipher.c \
		cipher_wrap.c \
		ctr_drbg.c  \
		debug.c \
		des.c \
		dhm.c \
		entropy.c \
		entropy_poll.c \
		error.c \
		gcm.c \
		havege.c \
		md2.c \
		md4.c \
		md5.c \
		md.c \
		md_wrap.c \
		net.c \
		padlock.c \
		pbkdf2.c \
		pem.c \
		pkcs11.c \
		rsa.c \
		sha1.c \
		sha2.c \
		sha4.c \
		ssl_cache.c \
		ssl_cli.c \
		ssl_srv.c \
		ssl_tls.c \
		timing.c \
		version.c \
		x509parse.c \
		x509write.c \
		xtea.c



#LOCAL_CFLAGS += \

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../include

include $(BUILD_STATIC_LIBRARY)

