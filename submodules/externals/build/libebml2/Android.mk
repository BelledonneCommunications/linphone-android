LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libebml2
LOCAL_SRC_FILES := $(TARGET_ARCH)/libebml2.a

include $(PREBUILT_STATIC_LIBRARY)