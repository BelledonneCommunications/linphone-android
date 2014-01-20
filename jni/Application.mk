APP_PROJECT_PATH := $(call my-dir)/../
NDK_TOOLCHAIN_VERSION := 4.8

ifeq ($(BUILD_MEDIASTREAMER2_SDK),)
BUILD_MEDIASTREAMER2_SDK=0
endif

APP_MODULES :=libspeex libgsm libortp libmediastreamer2
ifeq ($(BUILD_MEDIASTREAMER2_SDK), 0)
APP_MODULES += antlr3 libbellesip liblinphone liblpxml2
endif

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

ifeq ($(BUILD_G729),)
BUILD_G729=0
endif

ifeq ($(BUILD_VIDEO),)
BUILD_VIDEO=1
endif

ifeq ($(BUILD_MEDIASTREAMER2_SDK), 0)
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
ifneq (,$(findstring armeabi,$(TARGET_ARCH_ABI)))
APP_MODULES += libwebrtc_spl_neon libwebrtc_aecm_neon
endif
endif

ifeq ($(BUILD_WEBRTC_ISAC), 1)
APP_MODULES += libwebrtc_spl libwebrtc_isacfix libmsisac
ifneq (,$(findstring armeabi,$(TARGET_ARCH_ABI)))
APP_MODULES += libwebrtc_spl_neon libwebrtc_isacfix_neon
endif
endif

ifeq ($(BUILD_MEDIASTREAMER2_SDK), 0)
ifeq ($(RING),yes)
APP_MODULES      += libring
endif

ifeq ($(BUILD_TUNNEL), 1)
APP_MODULES += libtunnelclient
endif
endif

ifeq ($(BUILD_GPLV3_ZRTP), 1)
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
