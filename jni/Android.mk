root-dir:=$(APP_PROJECT_PATH)
#default values
BUILD_AMR=light
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
BUILD_X264=1
LINPHONE_VIDEO=1
endif


ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
ifeq ($(BUILD_GPLV3_ZRTP), 1)
BUILD_SRTP=1
ZRTP_C_INCLUDE= \
	$(root-dir)/submodules/externals/libzrtpcpp/src
endif

ifeq ($(BUILD_SRTP), 1)
SRTP_C_INCLUDE= \
	$(root-dir)/submodules/externals/srtp/include \
	$(root-dir)/submodules/externals/srtp/crypto/include
endif
endif



include $(root-dir)/submodules/externals/build/speex/Android.mk

include $(root-dir)/submodules/externals/build/gsm/Android.mk

include $(root-dir)/submodules/externals/build/exosip/Android.mk

include $(root-dir)/submodules/externals/build/osip/Android.mk

include $(root-dir)/submodules/externals/openssl/Android.mk

include $(root-dir)/submodules/linphone/oRTP/build/android/Android.mk

include $(root-dir)/submodules/linphone/mediastreamer2/build/android/Android.mk
include $(root-dir)/submodules/linphone/mediastreamer2/tests/Android.mk



ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
include $(root-dir)/submodules/msilbc/Android.mk

ifeq ($(BUILD_X264), 1)
include $(root-dir)/submodules/msx264/Android.mk
include $(root-dir)/submodules/externals/build/x264/Android.mk
endif

include $(root-dir)/submodules/externals/build/ffmpeg/Android.mk

ifeq ($(BUILD_GPLV3_ZRTP), 1)
include $(root-dir)/submodules/externals/build/libzrtpcpp/Android.mk
endif

ifeq ($(BUILD_SRTP), 1)
include $(root-dir)/submodules/externals/build/srtp/Android.mk
endif
endif #armeabi-v7a


include $(root-dir)/submodules/linphone/build/android/Android.mk

ifneq ($(BUILD_AMR), 0)
include $(root-dir)/submodules/externals/build/opencore-amr/Android.mk
include $(root-dir)/submodules/msamr/Android.mk
endif



