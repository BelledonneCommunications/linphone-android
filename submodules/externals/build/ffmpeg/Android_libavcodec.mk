LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libavcodec-linphone
LOCAL_SRC_FILES := $(TARGET_ARCH)/libavcodec/libavcodec-linphone.so

include $(PREBUILT_SHARED_LIBRARY)
