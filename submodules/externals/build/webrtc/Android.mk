MY_WEBRTC_COMMON_DEFS := \
    -DWEBRTC_ANDROID \
    -DWEBRTC_LINUX \
    -DWEBRTC_CLOCK_TYPE_REALTIME \
    -DWEBRTC_POSIX \
    -fPIC 

ifneq (,$(findstring armeabi,$(TARGET_ARCH_ABI)))
MY_WEBRTC_COMMON_DEFS += -DWEBRTC_ARCH_ARM
endif

ifeq ($(TARGET_ARCH_ABI), armeabi-v7a)
MY_WEBRTC_COMMON_DEFS += -DWEBRTC_DETECT_ARM_NEON
endif

#turn off warnings since we cannot fix them
MY_WEBRTC_COMMON_DEFS += -w

