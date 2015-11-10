#############################
# Build the non-neon library.

MY_WEBRTC_PATH := $(call my-dir)/../../../
LOCAL_PATH := $(MY_WEBRTC_PATH)/../../webrtc/webrtc/modules/audio_processing/aecm

include $(CLEAR_VARS)

include $(MY_WEBRTC_PATH)/Android.mk

LOCAL_ARM_MODE := arm
LOCAL_MODULE_CLASS := STATIC_LIBRARIES
LOCAL_MODULE := libwebrtc_aecm
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
    echo_control_mobile.c \
    aecm_core.c \
    aecm_core_c.c

# Flags passed to both C and C++ files.
LOCAL_CFLAGS := $(MY_WEBRTC_COMMON_DEFS)

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/../utility \
    $(LOCAL_PATH)/../../.. \
    $(LOCAL_PATH)/../../../common_audio/signal_processing/include \
    $(LOCAL_PATH)/../../../system_wrappers/interface \
    $(LOCAL_PATH)/../../../.. \

LOCAL_STATIC_LIBRARIES += libwebrtc_system_wrappers

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libdl \
    libstlport

ifndef NDK_ROOT
include external/stlport/libstlport.mk
endif
include $(BUILD_STATIC_LIBRARY)

#########################
# Build the neon library.
ifeq ($(WEBRTC_BUILD_NEON_LIBS),true)

include $(CLEAR_VARS)

include $(MY_WEBRTC_PATH)/Android.mk

LOCAL_ARM_MODE := arm
LOCAL_MODULE_CLASS := STATIC_LIBRARIES
LOCAL_MODULE := libwebrtc_aecm_neon
LOCAL_MODULE_TAGS := optional

# Generate a header file aecm_core_neon_offsets.h which will be included in
# assembly file aecm_core_neon.S, from file aecm_core_neon_offsets.c.
#$(LOCAL_PATH)/aecm_core_neon_offsets.h: $(LOCAL_PATH)/aecm_core_neon_offsets.S
#	python $(LOCAL_PATH)/../../../build/generate_asm_header.py $^ $@ offset_aecm_
#
#$(LOCAL_PATH)/aecm_core_neon_offsets.S: $(LOCAL_PATH)/aecm_core_neon_offsets.c
#	$(TARGET_CC) $(addprefix -I, $(LOCAL_INCLUDES)) $(addprefix -isystem ,\
#            $(TARGET_C_INCLUDES)) -S -o $@ $^
#
#$(LOCAL_PATH)/aecm_core_neon.S: $(LOCAL_PATH)/aecm_core_neon_offsets.h

LOCAL_SRC_FILES := aecm_core_neon.c

# Flags passed to both C and C++ files.
LOCAL_CFLAGS := \
    $(MY_WEBRTC_COMMON_DEFS) \
    -mfpu=neon \
    -mfloat-abi=softfp \
    -flax-vector-conversions

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/../../.. \
    $(LOCAL_PATH)/../../../common_audio/signal_processing/include \
    $(MY_WEBRTC_PATH)/modules/audio_processing/aecm \
    $(LOCAL_PATH)/../../../.. \

LOCAL_INCLUDES := $(LOCAL_C_INCLUDES)

ifndef NDK_ROOT
include external/stlport/libstlport.mk
endif
include $(BUILD_STATIC_LIBRARY)

endif # ifeq ($(WEBRTC_BUILD_NEON_LIBS),true)
