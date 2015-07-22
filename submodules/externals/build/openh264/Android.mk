LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libopenh264
LOCAL_SRC_FILES := $(TARGET_ARCH)/libopenh264.a

include $(PREBUILT_STATIC_LIBRARY)
