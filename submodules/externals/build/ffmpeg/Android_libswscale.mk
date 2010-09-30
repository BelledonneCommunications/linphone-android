##lib swcale###################
LOCAL_PATH:= $(call my-dir)../../ffmpeg/
include $(CLEAR_VARS)

LOCAL_MODULE := libswscale

LOCAL_SRC_FILES = \
	libswscale/options.c \
	libswscale/rgb2rgb.c \
	libswscale/swscale.c \
	libswscale/utils.c \
	libswscale/yuv2rgb.c 

LOCAL_CFLAGS += -DHAVE_AV_CONFIG_H

#for including config.h:
LOCAL_C_INCLUDES += $(call my-dir)/
include $(BUILD_STATIC_LIBRARY)

