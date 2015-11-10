# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

MY_WEBRTC_PATH := $(call my-dir)/../
LOCAL_PATH := $(MY_WEBRTC_PATH)/../../webrtc/webrtc/system_wrappers/source

include $(CLEAR_VARS)

include $(MY_WEBRTC_PATH)/Android.mk

LOCAL_ARM_MODE := arm
LOCAL_MODULE := libwebrtc_system_wrappers
LOCAL_MODULE_TAGS := optional
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := \
    cpu_features_android.c

LOCAL_CFLAGS := \
    $(MY_WEBRTC_COMMON_DEFS)

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/../.. \
    $(LOCAL_PATH)/../interface \
    $(LOCAL_PATH)/spreadsortlib

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libdl \
    libstlport
    
LOCAL_STATIC_LIBRARIES := cpufeatures

ifndef NDK_ROOT
include external/stlport/libstlport.mk
endif

include $(BUILD_STATIC_LIBRARY)
$(call import-module,android/cpufeatures)
