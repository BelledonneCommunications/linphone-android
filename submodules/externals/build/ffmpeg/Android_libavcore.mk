
LOCAL_PATH:= $(call my-dir)/../../ffmpeg/
include $(CLEAR_VARS)

LOCAL_MODULE := libavcore


LOCAL_SRC_FILES := \
	libavcore/imgutils.c \
	libavcore/parseutils.c \
	libavcore/utils.c


LOCAL_CFLAGS += -DHAVE_AV_CONFIG_H



#for including config.h:
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../build/ffmpeg  $(LOCAL_PATH)/
include $(BUILD_STATIC_LIBRARY)

