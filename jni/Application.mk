APP_PROJECT_PATH := $(call my-dir)/../
APP_MODULES      :=libspeex libgsm libortp libosip2 libeXosip2 libmediastreamer2  liblinphone


ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
APP_MODULES      +=libmsilbc libavutil libavcore libavcodec libswscale
endif
APP_BUILD_SCRIPT:=$(call my-dir)/Android.mk
APP_PLATFORM := android-3
APP_ABI := armeabi armeabi-v7a
#APP_OPTIM := debug

