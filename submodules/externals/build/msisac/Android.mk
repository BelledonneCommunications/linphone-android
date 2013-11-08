LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libmsisac-linphone
LOCAL_MODULE_FILENAME := libmsisac-linphone-$(TARGET_ARCH)
LOCAL_SRC_FILES := $(TARGET_ARCH)/libmsisac-linphone-$(TARGET_ARCH).so

include $(PREBUILT_SHARED_LIBRARY)
