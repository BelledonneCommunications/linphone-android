LOCAL_PATH:= $(call my-dir)/../../ffmpeg
include $(CLEAR_VARS)

LOCAL_MODULE := liblinavcodec

LOCAL_SRC_FILES = \
	libavcodec/aandcttab.c \
	libavcodec/allcodecs.c \
	libavcodec/audioconvert.c \
	libavcodec/avpacket.c \
	libavcodec/bitstream.c \
	libavcodec/bitstream_filter.c \
	libavcodec/cabac.c \
	libavcodec/dsputil.c \
	libavcodec/error_resilience.c \
	libavcodec/faandct.c \
	libavcodec/faanidct.c \
	libavcodec/flvdec.c \
	libavcodec/flvenc.c \
	libavcodec/golomb.c \
	libavcodec/h263.c \
	libavcodec/h263_parser.c \
	libavcodec/h263dec.c \
	libavcodec/h264.c \
	libavcodec/h264_cabac.c \
	libavcodec/h264_cavlc.c \
	libavcodec/h264_direct.c \
	libavcodec/h264_loopfilter.c \
	libavcodec/h264_ps.c \
	libavcodec/h264_refs.c \
	libavcodec/h264_sei.c \
	libavcodec/h264dsp.c \
	libavcodec/h264idct.c \
	libavcodec/h264pred.c \
	libavcodec/imgconvert.c \
	libavcodec/intelh263dec.c \
	libavcodec/inverse.c \
	libavcodec/ituh263dec.c \
	libavcodec/ituh263enc.c \
	libavcodec/jfdctfst.c \
	libavcodec/jfdctint.c \
	libavcodec/jrevdct.c \
	libavcodec/mjpeg.c \
	libavcodec/mjpegdec.c \
	libavcodec/motion_est.c \
	libavcodec/mpeg12data.c \
	libavcodec/mpeg4video.c \
	libavcodec/mpeg4video_parser.c \
	libavcodec/mpeg4videodec.c \
	libavcodec/mpeg4videoenc.c \
	libavcodec/mpegvideo.c \
	libavcodec/mpegvideo_enc.c \
	libavcodec/opt.c \
	libavcodec/options.c \
	libavcodec/parser.c \
	libavcodec/ratecontrol.c \
	libavcodec/raw.c \
	libavcodec/resample.c \
	libavcodec/resample2.c \
	libavcodec/simple_idct.c \
	libavcodec/utils.c \
	libavcodec/pthread.c


LOCAL_CFLAGS += -DHAVE_AV_CONFIG_H  --std=c99

#for including config.h:
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../build/ffmpeg-x86 \
	$(LOCAL_PATH)/libavcodec/x86 \
	$(LOCAL_PATH)/ \
	$(LOCAL_PATH)/libavutil 

LOCAL_SHARED_LIBRARIES := liblinavutil liblinavcore

include $(BUILD_SHARED_LIBRARY)

