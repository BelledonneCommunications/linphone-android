LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libavutil-linphone
LOCAL_SRC_FILES := $(TARGET_ARCH)/libavutil/libavutil-linphone.so

include $(PREBUILT_SHARED_LIBRARY)
