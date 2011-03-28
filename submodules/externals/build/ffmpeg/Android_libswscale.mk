##lib swcale###################
LOCAL_PATH:= $(call my-dir)/../../ffmpeg/libswscale/
include $(CLEAR_VARS)

LOCAL_MODULE := libswscale

LOCAL_SRC_FILES = \
	options.c \
	rgb2rgb.c \
	swscale.c \
	utils.c \
	yuv2rgb.c 

LOCAL_CFLAGS += -DHAVE_AV_CONFIG_H

LOCAL_ARM_MODE := arm

#for including config.h:
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../../build/ffmpeg  \
					$(LOCAL_PATH)/ \
					$(LOCAL_PATH)/../

LOCAL_SHARED_LIBRARIES := libavutil

include $(BUILD_SHARED_LIBRARY)

