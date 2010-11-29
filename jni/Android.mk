root-dir:=$(APP_PROJECT_PATH)

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LINPHONE_VIDEO=1
endif

include $(root-dir)/submodules/externals/build/speex/Android.mk

include $(root-dir)/submodules/externals/build/gsm/Android.mk

include $(root-dir)/submodules/externals/build/exosip/Android.mk

include $(root-dir)/submodules/externals/build/osip/Android.mk

include $(root-dir)/submodules/linphone/oRTP/build/android/Android.mk

include $(root-dir)/submodules/linphone/mediastreamer2/build/android/Android.mk

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
include $(root-dir)/submodules/msilbc/Android.mk
include $(root-dir)/submodules/externals/build/ffmpeg/Android.mk
include $(root-dir)/submodules/externals/build/x264/Android.mk
endif

include $(root-dir)/submodules/linphone/build/android/Android.mk




