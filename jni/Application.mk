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

ifeq ($(BUILD_OPENH264),)
BUILD_OPENH264=0
endif

ifeq ($(BUILD_G729),)
BUILD_G729=0
endif

ifeq ($(BUILD_CODEC2),)
BUILD_CODEC2=0
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

APP_MODULES +=bctoolbox bctoolbox_tester
ifeq ($(BUILD_TLS),1)
ifeq ($(BUILD_BCTOOLBOX_MBEDTLS),1)
APP_MODULES +=mbedtls
else
APP_MODULES +=polarssl
endif
endif
endif

ifeq ($(BUILD_VIDEO),1)
APP_MODULES += libffmpeg-linphone
APP_MODULES += libvpx
ifeq ($(BUILD_X264),1)
APP_MODULES +=libx264 libmsx264
endif
ifeq ($(BUILD_OPENH264),1)
APP_MODULES += libopenh264 libmsopenh264
endif
ifeq ($(BUILD_MATROSKA), 1)
APP_MODULES += libmatroska2
endif
endif # BUILD_VIDEO

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

ifeq ($(BUILD_SILK),1)
APP_MODULES +=libmssilk
endif

ifeq ($(BUILD_G729),1)
APP_MODULES +=libbcg729 libmsbcg729
endif

ifneq ($(BUILD_OPUS), 0)
APP_MODULES += libopus
endif

ifeq ($(BUILD_ILBC), 1)
APP_MODULES += libwebrtc_spl libwebrtc_ilbc libmswebrtc
ifneq (,$(findstring armeabi,$(TARGET_ARCH_ABI)))
APP_MODULES += libwebrtc_spl_neon
endif
endif

ifneq ($(BUILD_WEBRTC_AECM), 0)
APP_MODULES += libwebrtc_system_wrappers libwebrtc_spl libwebrtc_apm_utility libwebrtc_aecm libmswebrtc
ifneq (,$(findstring armeabi,$(TARGET_ARCH_ABI)))
APP_MODULES += libwebrtc_spl_neon libwebrtc_aecm_neon
endif
endif

ifeq ($(BUILD_WEBRTC_ISAC), 1)
APP_MODULES += libwebrtc_spl libwebrtc_isacfix libmswebrtc
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

ifeq ($(BUILD_ZRTP), 1)
APP_MODULES      += libbzrtp
endif

ifeq ($(BUILD_CODEC2), 1)
APP_MODULES      +=libcodec2 libmscodec2
endif

ifeq ($(BUILD_SRTP), 1)
APP_MODULES      += libsrtp
endif

linphone-root-dir:=$(APP_PROJECT_PATH)

APP_BUILD_SCRIPT:=$(call my-dir)/Android.mk
APP_PLATFORM := android-8
APP_ABI := armeabi-v7a
ifeq ($(BUILD_FOR_ARM), 1)
APP_ABI += armeabi
endif
ifeq ($(BUILD_FOR_X86), 1)
APP_ABI += x86
endif

APP_CFLAGS += -Werror -Wall -Wno-strict-aliasing -Wno-unused-function
# Thanks cpufeature.c imported from the NDK...
APP_CFLAGS += -Wno-unused-variable
