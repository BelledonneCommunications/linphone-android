LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libwels
LOCAL_SRC_FILES := $(TARGET_ARCH)/libwels.a

include $(PREBUILT_STATIC_LIBRARY)
