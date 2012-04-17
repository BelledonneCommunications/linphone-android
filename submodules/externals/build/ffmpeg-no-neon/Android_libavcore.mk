
LOCAL_PATH:= $(call my-dir)/../../ffmpeg/
include $(CLEAR_VARS)

LOCAL_MODULE := libavcorenoneon


LOCAL_SRC_FILES := \
	libavcore/imgutils.c \
	libavcore/parseutils.c \
	libavcore/utils.c 

#	libavcore/samplefmt.c \
#	libavcore/audioconvert.c


LOCAL_CFLAGS += -DHAVE_AV_CONFIG_H



#for including config.h:
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../build/ffmpeg-no-neon  $(LOCAL_PATH)/

LOCAL_SHARED_LIBRARIES := libavutilnoneon

include $(BUILD_SHARED_LIBRARY)

