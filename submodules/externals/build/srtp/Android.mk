
LOCAL_PATH:= $(call my-dir)/../../srtp
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	 	 srtp/srtp.c \
		 srtp/ekt.c \
	 	 crypto/ae_xfm/xfm.c \
	 	 crypto/cipher/aes.c \
	 	 crypto/cipher/aes_cbc.c \
	 	 crypto/cipher/aes_icm.c \
	 	 crypto/cipher/cipher.c \
	 	 crypto/cipher/null_cipher.c \
	 	 crypto/hash/auth.c \
	 	 crypto/hash/hmac.c \
	 	 crypto/hash/null_auth.c \
	 	 crypto/hash/sha1.c \
	 	 crypto/kernel/alloc.c \
	 	 crypto/kernel/err.c \
	 	 crypto/kernel/key.c \
	 	 crypto/kernel/crypto_kernel.c \
	 	 crypto/math/gf2_8.c \
	 	 crypto/math/stat.c \
	 	 crypto/replay/rdb.c \
	 	 crypto/replay/rdbx.c \
	 	 crypto/replay/ut_sim.c \
	 	 crypto/rng/ctr_prng.c \
	 	 crypto/rng/prng.c 

LOCAL_CFLAGS := -Wall -O4 -fexpensive-optimizations -funroll-loops -DCPU_CISC -include config.h

ifeq ($(SRTP_USES_LINUX_KERNEL), 1)
LOCAL_SRC_FILES += \
	 	 crypto/rng/rand_linux_kernel.c 
LOCAL_CFLAGS += -DSRTP_KERNEL -DSRTP_KERNEL_LINUX
else
LOCAL_SRC_FILES += \
	 	 crypto/rng/rand_source.c 
endif


ifeq ($(SRTP_USES_MATH),1)
LOCAL_SRC_FILES += crypto/math/math.c
else
LOCAL_SRC_FILES += crypto/math/datatypes.c 
endif

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/include \
	$(LOCAL_PATH)/crypto/include 



# Build dynamic and static versions
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_MODULE:= libsrtp
include $(BUILD_SHARED_LIBRARY)
else
LOCAL_MODULE:= libsrtp-static
include $(BUILD_STATIC_LIBRARY)
endif


