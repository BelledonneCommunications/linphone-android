APP_PROJECT_PATH := $(call my-dir)/../
APP_MODULES      :=libspeex libgsm libortp libosip2 libeXosip2 libmediastreamer2  liblinphone
#APP_STL := stlport_static

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
APP_MODULES      +=libmsilbc 
endif

ifneq ($(LINPHONE_VIDEO),0)
APP_MODULES += libavutil libavcore libavcodec libswscale
endif

ifneq ($(BUILD_AMR),0)
APP_MODULES += libopencoreamr libmsamr
endif

ifeq ($(WITH_OPENSSL),1)
APP_MODULES += libcrypto libssl
endif

ifneq ($(BUILD_X264),0)
APP_MODULES +=libx264 libmsx264
endif

ifeq ($(RING),yes)
APP_MODULES      += libring
endif

APP_BUILD_SCRIPT:=$(call my-dir)/Android.mk
APP_PLATFORM := android-8
APP_ABI := armeabi armeabi-v7a
#APP_OPTIM := debug

