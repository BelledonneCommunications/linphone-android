root-dir:=$(APP_PROJECT_PATH)


ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
BUILD_X264=1
LINPHONE_VIDEO=1
else
BUILD_X264=0
endif

include $(root-dir)/submodules/externals/build/speex/Android.mk

include $(root-dir)/submodules/externals/build/gsm/Android.mk

include $(root-dir)/submodules/externals/build/exosip/Android.mk

include $(root-dir)/submodules/externals/build/osip/Android.mk

ifeq ($(WITH_OPENSSL), 1)
include $(root-dir)/submodules/externals/openssl/Android.mk
endif

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
endif

include $(root-dir)/submodules/linphone/build/android/Android.mk




