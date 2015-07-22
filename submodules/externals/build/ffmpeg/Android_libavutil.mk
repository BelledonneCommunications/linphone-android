LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libavutil-linphone
LOCAL_MODULE_FILENAME := libavutil-linphone-$(TARGET_ARCH)
LOCAL_SRC_FILES := $(TARGET_ARCH)/libavutil/libavutil-linphone-$(TARGET_ARCH).so

include $(PREBUILT_SHARED_LIBRARY)
