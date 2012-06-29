LOCAL_PATH:= $(call my-dir)/../../ffmpeg
include $(CLEAR_VARS)

LOCAL_MODULE := libavcodec

LOCAL_SRC_FILES = \
	libavcodec/allcodecs.c \
	libavcodec/aandcttab.c \
	libavcodec/arm/dsputil_arm.S.arm \
	libavcodec/arm/dsputil_armv6.S.arm \
	libavcodec/arm/dsputil_init_arm.c \
	libavcodec/arm/dsputil_init_armv5te.c \
	libavcodec/arm/dsputil_init_armv6.c \
	libavcodec/arm/dsputil_init_neon.c \
	libavcodec/arm/dsputil_init_vfp.c \
	libavcodec/arm/dsputil_neon.S.neon \
	libavcodec/arm/dsputil_vfp.S.neon \
	libavcodec/arm/fft_init_arm.c \
	libavcodec/arm/fft_neon.S.neon \
	libavcodec/arm/h264dsp_init_arm.c \
	libavcodec/arm/h264dsp_neon.S.neon \
	libavcodec/arm/h264idct_neon.S.neon \
	libavcodec/arm/h264pred_init_arm.c \
	libavcodec/arm/h264pred_neon.S.neon \
	libavcodec/arm/int_neon.S.neon \
	libavcodec/arm/jrevdct_arm.S \
	libavcodec/arm/mdct_neon.S.neon \
	libavcodec/arm/mpegvideo_arm.c \
	libavcodec/arm/mpegvideo_armv5te.c \
	libavcodec/arm/mpegvideo_armv5te_s.S \
	libavcodec/arm/mpegvideo_neon.S.neon \
	libavcodec/arm/simple_idct_arm.S \
	libavcodec/arm/simple_idct_armv5te.S \
	libavcodec/arm/simple_idct_armv6.S \
	libavcodec/arm/simple_idct_neon.S.neon \
	libavcodec/audioconvert.c.arm \
	libavcodec/avpacket.c \
	libavcodec/bitstream.c \
	libavcodec/bitstream_filter.c \
	libavcodec/cabac.c \
	libavcodec/dsputil.c.arm \
	libavcodec/error_resilience.c \
	libavcodec/faandct.c \
	libavcodec/faanidct.c \
	libavcodec/flvdec.c \
	libavcodec/flvenc.c \
	libavcodec/fft.c \
	libavcodec/golomb.c \
	libavcodec/h263.c.arm \
	libavcodec/h263_parser.c \
	libavcodec/h263dec.c \
	libavcodec/h264.c \
	libavcodec/h264_cabac.c.arm \
	libavcodec/h264_cavlc.c.arm \
	libavcodec/h264_direct.c.arm \
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
	libavcodec/mjpeg.c.arm \
	libavcodec/mjpegdec.c.arm \
	libavcodec/mjpegenc.c.arm \
	libavcodec/motion_est.c.arm \
	libavcodec/mpeg12data.c \
	libavcodec/mpeg4video.c.arm \
	libavcodec/mpeg4video_parser.c \
	libavcodec/mpeg4videodec.c.arm \
	libavcodec/mpeg4videoenc.c.arm \
	libavcodec/mpegvideo.c.arm \
	libavcodec/mpegvideo_enc.c.arm \
	libavcodec/opt.c \
	libavcodec/options.c \
	libavcodec/parser.c \
	libavcodec/ratecontrol.c \
	libavcodec/raw.c \
	libavcodec/resample.c \
	libavcodec/resample2.c \
	libavcodec/simple_idct.c \
	libavcodec/utils.c \
	libavcodec/svq3.c \
	libavcodec/pthread.c 


LOCAL_ARM_MODE := arm

#LOCAL_CFLAGS += -DHAVE_AV_CONFIG_H -Wa,-I$(LOCAL_PATH)/libavcodec/arm
LOCAL_CFLAGS += -DHAVE_AV_CONFIG_H 

#for including config.h:
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../build/ffmpeg  \
	$(LOCAL_PATH)/libavcodec/arm \
	$(LOCAL_PATH)/ \
	$(LOCAL_PATH)/libavutil 

LOCAL_SHARED_LIBRARIES := libavutil libavcore

include $(BUILD_SHARED_LIBRARY)

