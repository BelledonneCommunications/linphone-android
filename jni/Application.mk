APP_PROJECT_PATH := $(call my-dir)/../
APP_MODULES      :=libspeex libgsm libortp libosip2 libeXosip2 libmediastreamer2 liblinphone
APP_STL := stlport_static

ifeq ($(LINPHONE_VIDEO),1)
APP_MODULES += libavutil libavcore libavcodec libswscale libvpx
endif

ifeq ($(BUILD_AMR),1)
APP_MODULES += libopencoreamr libmsamr
endif

ifeq ($(BUILD_X264),1)
APP_MODULES +=libx264 libmsx264
endif

ifeq ($(BUILD_SILK),1)
APP_MODULES +=libmssilk
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
