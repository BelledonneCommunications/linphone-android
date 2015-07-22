LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libavcodec-linphone
LOCAL_MODULE_FILENAME := libavcodec-linphone-$(TARGET_ARCH)
LOCAL_SRC_FILES := $(TARGET_ARCH)/libavcodec/libavcodec-linphone-$(TARGET_ARCH).so

include $(PREBUILT_SHARED_LIBRARY)
