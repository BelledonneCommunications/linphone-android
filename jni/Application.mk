APP_PROJECT_PATH := $(call my-dir)/../
APP_MODULES      :=libspeex libgsm libortp libosip2 libeXosip2 libmediastreamer2 liblinphone liblinphonenoneon
APP_STL := stlport_static

#default values
ifeq ($(BUILD_AMRNB),)
BUILD_AMRNB=light
endif
ifeq ($(BUILD_AMRWB),)
BUILD_AMRWB=0
endif
ifeq ($(BUILD_SRTP),)
BUILD_SRTP=1
endif

ifeq ($(LINPHONE_VIDEO),1)
APP_MODULES += libavutil libavcore libavcodec libswscale
APP_MODULES += libavutilnoneon libavcorenoneon libavcodecnoneon libswscalenoneon
APP_MODULES += libvpx
endif

_BUILD_AMR=0
ifneq ($(BUILD_AMRNB), 0)
_BUILD_AMR=1
endif

ifneq ($(BUILD_AMRWB), 0)
_BUILD_AMR=1
endif

ifneq ($(_BUILD_AMR), 0)
APP_MODULES += libopencoreamr libmsamr
endif

ifneq ($(BUILD_AMRWB), 0)
APP_MODULES += libvoamrwbenc
endif

ifeq ($(BUILD_X264),1)
APP_MODULES +=libx264 libmsx264
endif

ifeq ($(BUILD_SILK),1)
APP_MODULES +=libmssilk
endif

ifeq ($(BUILD_G729),1)
APP_MODULES +=libbcg729 libmsbcg729
endif

ifeq ($(RING),yes)
APP_MODULES      += libring
endif

ifeq ($(BUILD_TUNNEL), 1)
APP_MODULES += libtunnelclient
endif

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
APP_MODULES += liblincrypto liblinssl
APP_MODULES      +=libmsilbc

ifeq ($(BUILD_GPLV3_ZRTP), 1)
APP_MODULES      += libzrtpcpp
endif

ifeq ($(BUILD_SRTP), 1)
APP_MODULES      += libsrtp
endif
endif #armeabi-v7a


linphone-root-dir:=$(APP_PROJECT_PATH)

APP_BUILD_SCRIPT:=$(call my-dir)/Android.mk
APP_PLATFORM := android-8
#APP_ABI := armeabi
APP_ABI := armeabi-v7a armeabi
APP_CFLAGS:=-DDISABLE_NEON
