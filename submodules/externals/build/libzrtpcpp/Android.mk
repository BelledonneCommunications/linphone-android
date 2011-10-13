MK_DIR:=$(call my-dir)
LOCAL_EXTERNALS:= $(MK_DIR)/../..
LOCAL_PATH:= $(LOCAL_EXTERNALS:=)/libzrtpcpp
include $(CLEAR_VARS)



LOCAL_SRC_FILES := \
	 	src/Base32.cpp \
 		src/ZIDFile.cpp \
	 	src/ZIDRecord.cpp \
	 	src/ZrtpCallbackWrapper.cpp \
	 	src/ZrtpConfigure.cpp \
	 	src/ZRtp.cpp \
	 	src/ZrtpCrc32.cpp \
	 	src/ZrtpCWrapper.cpp \
	 	src/ZrtpPacketClearAck.cpp \
	 	src/ZrtpPacketCommit.cpp \
	 	src/ZrtpPacketConf2Ack.cpp \
	 	src/ZrtpPacketConfirm.cpp \
	 	src/ZrtpPacketDHPart.cpp \
	 	src/ZrtpPacketErrorAck.cpp \
	 	src/ZrtpPacketError.cpp \
	 	src/ZrtpPacketGoClear.cpp \
	 	src/ZrtpPacketHelloAck.cpp \
	 	src/ZrtpPacketHello.cpp \
	 	src/ZrtpPacketPingAck.cpp \
	 	src/ZrtpPacketPing.cpp \
		src/ZrtpPacketRelayAck.cpp \
		src/ZrtpPacketSASrelay.cpp \
	 	src/ZrtpStateClass.cpp \
	 	src/ZrtpTextData.cpp

LOCAL_SRC_FILES += \
	 	src/libzrtpcpp/crypto/openssl/AesCFB.cpp \
	 	src/libzrtpcpp/crypto/openssl/hmac256.cpp \
	 	src/libzrtpcpp/crypto/openssl/hmac384.cpp \
	 	src/libzrtpcpp/crypto/openssl/InitializeOpenSSL.cpp \
	 	src/libzrtpcpp/crypto/openssl/sha256.cpp \
	 	src/libzrtpcpp/crypto/openssl/sha384.cpp \
	 	src/libzrtpcpp/crypto/openssl/ZrtpDH.cpp \
	 	src/libzrtpcpp/crypto/TwoCFB.cpp \
		src/libzrtpcpp/crypto/twofish.c \
		src/libzrtpcpp/crypto/twofish_cfb.c


#	 	 src/ZrtpQueue.cpp 

#	 	 src/libzrtpcpp/crypto/gcrypt/gcryptAesCFB.cpp \
	 	 src/libzrtpcpp/crypto/gcrypt/gcrypthmac256.cpp \
	 	 src/libzrtpcpp/crypto/gcrypt/gcrypthmac384.cpp \
	 	 src/libzrtpcpp/crypto/gcrypt/gcryptsha256.cpp \
	 	 src/libzrtpcpp/crypto/gcrypt/gcryptsha384.cpp \
	 	 src/libzrtpcpp/crypto/gcrypt/gcryptZrtpDH.cpp \
	 	 src/libzrtpcpp/crypto/gcrypt/InitializeGcrypt.cpp \
	

LOCAL_CFLAGS := -D__EXPORT=""

LOCAL_C_INCLUDES += \
	$(MK_DIR)/ \
	$(LOCAL_PATH)/src/ \
	$(LOCAL_EXTERNALS)/openssl \
        $(LOCAL_EXTERNALS)/openssl/include 



# Build dynamic and static versions
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_MODULE:= libzrtpcpp
LOCAL_SHARED_LIBRARIES := liblincrypto liblinssl
include $(BUILD_SHARED_LIBRARY)
else
LOCAL_STATIC_LIBRARIES := libcrypto-static libssl-static
LOCAL_MODULE:= libzrtpcpp-static
include $(BUILD_STATIC_LIBRARY)
endif


