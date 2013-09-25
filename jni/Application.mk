APP_PROJECT_PATH := $(call my-dir)/../
APP_MODULES      :=libspeex libgsm libortp antlr3 libbellesip libmediastreamer2 liblinphone libneon liblpxml2
APP_STL := stlport_static

#default values:

ifeq ($(BUILD_AMRNB),)
BUILD_AMRNB=light
endif

ifeq ($(BUILD_AMRWB),)
BUILD_AMRWB=0
endif

ifeq ($(BUILD_SRTP),)
BUILD_SRTP=1
endif

ifeq ($(BUILD_X264),)
BUILD_X264=0
endif

ifeq ($(BUILD_X264),)
BUILD_X264=0
endif

ifeq ($(BUILD_G729),)
BUILD_G729=0
endif

ifeq ($(BUILD_VIDEO),)
BUILD_VIDEO=1
endif

#since we want to modify BUILD_VIDEO and BUILD_X264 depending on platform, we need to make a copy because the 
#variables given on command line take precedence over the ones defined internally.
ifeq ($(BUILD_VIDEO),1)
_BUILD_VIDEO=1
endif

ifeq ($(BUILD_X264),1)
_BUILD_X264=1
endif

#sqlite
ifeq ($(BUILD_SQLITE),1)
APP_MODULES += liblinsqlite
endif

#uPnp
ifeq ($(BUILD_UPNP),1)
APP_MODULES += libupnp
endif

ifeq ($(BUILD_TLS),1)
APP_MODULES +=polarssl
endif

ifeq ($(BUILD_VIDEO),1)
APP_MODULES += libavutil-linphone libavcodec-linphone libswscale-linphone
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

ifneq ($(BUILD_OPUS), 0)
APP_MODULES += libopus
endif

ifneq ($(BUILD_WEBRTC_AECM), 0)
APP_MODULES += libwebrtc_system_wrappers libwebrtc_spl libwebrtc_apm_utility libwebrtc_aecm
APP_MODULES += libwebrtc_spl_neon libwebrtc_aecm_neon
endif

ifeq ($(RING),yes)
APP_MODULES      += libring
endif

ifeq ($(BUILD_TUNNEL), 1)
APP_MODULES += libtunnelclient
endif

ifeq ($(BUILD_GPLV3_ZRTP), 1)
APP_MODULES += liblincrypto liblinssl
APP_MODULES      += libzrtpcpp
endif

APP_MODULES      +=libmsilbc

ifeq ($(BUILD_SRTP), 1)
APP_MODULES      += libsrtp
endif

linphone-root-dir:=$(APP_PROJECT_PATH)

APP_BUILD_SCRIPT:=$(call my-dir)/Android.mk
APP_PLATFORM := android-8
APP_ABI := armeabi-v7a armeabi
ifeq ($(BUILD_FOR_X86), 1)
APP_ABI += x86
endif
