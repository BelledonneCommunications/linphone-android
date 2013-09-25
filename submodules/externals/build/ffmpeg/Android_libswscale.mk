LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libswscale-linphone
LOCAL_SRC_FILES := $(TARGET_ARCH)/libswscale/libswscale-linphone.so

include $(PREBUILT_SHARED_LIBRARY)
