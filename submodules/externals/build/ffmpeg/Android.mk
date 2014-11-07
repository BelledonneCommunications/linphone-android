LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libffmpeg-linphone
LOCAL_MODULE_FILENAME := libffmpeg-linphone-$(TARGET_ARCH)
LOCAL_SRC_FILES := $(TARGET_ARCH)/libffmpeg-linphone-$(TARGET_ARCH).so

include $(PREBUILT_SHARED_LIBRARY)
