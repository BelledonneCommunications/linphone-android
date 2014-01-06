LOCAL_PATH:= $(call my-dir)/../../libzrtpcpp
BUILD_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	zrtp/ZrtpCallbackWrapper.cpp \
	zrtp/ZRtp.cpp \
	zrtp/ZrtpCrc32.cpp \
	zrtp/ZrtpPacketCommit.cpp \
	zrtp/ZrtpPacketConf2Ack.cpp \
	zrtp/ZrtpPacketConfirm.cpp \
	zrtp/ZrtpPacketDHPart.cpp \
	zrtp/ZrtpPacketGoClear.cpp \
	zrtp/ZrtpPacketClearAck.cpp \
	zrtp/ZrtpPacketHelloAck.cpp \
	zrtp/ZrtpPacketHello.cpp \
	zrtp/ZrtpPacketError.cpp \
	zrtp/ZrtpPacketErrorAck.cpp \
	zrtp/ZrtpPacketPingAck.cpp \
	zrtp/ZrtpPacketPing.cpp \
	zrtp/ZrtpPacketSASrelay.cpp \
	zrtp/ZrtpPacketRelayAck.cpp \
	zrtp/ZrtpStateClass.cpp \
	zrtp/ZrtpTextData.cpp \
	zrtp/ZrtpConfigure.cpp \
	zrtp/ZrtpCWrapper.cpp \
	zrtp/Base32.cpp \
	zrtp/zrtpB64Encode.c \
	zrtp/zrtpB64Decode.c \
	common/osSpecifics.c  \


#	zrtp/ZrtpSdesStream.cpp

LOCAL_SRC_FILES += \
	bnlib/bn00.c \
	bnlib/lbn00.c \
	bnlib/bn.c \
	bnlib/lbnmem.c \
	bnlib/sieve.c \
	bnlib/prime.c \
	bnlib/bnprint.c \
	bnlib/jacobi.c \
	bnlib/germain.c \
	bnlib/ec/ec.c \
	bnlib/ec/ecdh.c \
	bnlib/ec/curve25519-donna.c

LOCAL_SRC_FILES += \
	zrtp/crypto/skeinMac256.cpp \
	zrtp/crypto/skein256.cpp \
	zrtp/crypto/skeinMac384.cpp \
	zrtp/crypto/skein384.cpp 

LOCAL_SRC_FILES += \
	zrtp/crypto/zrtpDH.cpp \
	zrtp/crypto/hmac256.cpp \
	zrtp/crypto/sha256.cpp \
	zrtp/crypto/hmac384.cpp \
	zrtp/crypto/sha384.cpp \
	zrtp/crypto/aesCFB.cpp \
	zrtp/crypto/twoCFB.cpp \
	zrtp/crypto/sha2.c

LOCAL_SRC_FILES += \
	zrtp/ZIDCacheFile.cpp \
	zrtp/ZIDRecordFile.cpp

LOCAL_SRC_FILES += \
	cryptcommon/macSkein.cpp \
	cryptcommon/skein.c \
	cryptcommon/skein_block.c \
	cryptcommon/skeinApi.c \
	cryptcommon/twofish.c \
	cryptcommon/twofish_cfb.c 

LOCAL_SRC_FILES += \
	cryptcommon/ZrtpRandom.cpp \
	common/Thread.cpp \
	common/MutexClass.cpp \
	common/EventClass.cpp

LOCAL_SRC_FILES += \
	cryptcommon/aescrypt.c \
	cryptcommon/aeskey.c \
	cryptcommon/aestab.c \
	cryptcommon/aes_modes.c



LOCAL_CFLAGS := -D__EXPORT="" -fexceptions

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/zrtp \
	$(LOCAL_PATH)/bnlib


LOCAL_MODULE := libzrtpcpp
LOCAL_MODULE_FILENAME := libzrtpcpp-$(TARGET_ARCH_ABI)
include $(BUILD_SHARED_LIBRARY)
