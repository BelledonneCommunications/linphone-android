LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libmatroska2
LOCAL_SRC_FILES := $(TARGET_ARCH)/libmatroska2.a

include $(PREBUILT_STATIC_LIBRARY)
