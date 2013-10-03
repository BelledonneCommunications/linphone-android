LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libswscale-linphone
LOCAL_MODULE_FILENAME := libswscale-linphone-$(TARGET_ARCH)
LOCAL_SRC_FILES := $(TARGET_ARCH)/libswscale/libswscale-linphone-$(TARGET_ARCH).so

include $(PREBUILT_SHARED_LIBRARY)
