#
# List of compiled files and related options obtained using 
# ./configure --cross-prefix=arm-none-linux-gnueabi- --host=arm-none-linux-gnueabi --disable-pthread --enable-pic
# && make
#

LOCAL_PATH:= $(call my-dir)/../../x264
include $(CLEAR_VARS)

LOCAL_MODULE := libx264

LOCAL_SRC_FILES = \
	common/mc.c \
	common/predict.c \
	common/pixel.c \
	common/macroblock.c \
	common/frame.c \
	common/dct.c \
	common/cpu.c \
	common/cabac.c \
	common/common.c \
	common/mdate.c \
	common/rectangle.c \
	common/set.c \
	common/quant.c \
	common/deblock.c \
	common/vlc.c \
	common/mvpred.c \
	common/bitstream.c \
	encoder/analyse.c \
	encoder/me.c \
	encoder/ratecontrol.c \
	encoder/set.c \
	encoder/macroblock.c \
	encoder/cabac.c \
	encoder/cavlc.c \
	encoder/encoder.c \
	encoder/lookahead.c \
	common/arm/mc-c.c \
	common/arm/predict-c.c \
	common/arm/cpu-a.S \
	common/arm/pixel-a.S \
	common/arm/mc-a.S \
	common/arm/dct-a.S \
	common/arm/quant-a.S \
	common/arm/deblock-a.S \
	common/arm/predict-a.S 


LOCAL_ARM_MODE := arm


LOCAL_CFLAGS += -DPIC -DBIT_DEPTH=8 -std=gnu99

#for including config.h:
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../build/x264  \
	$(LOCAL_PATH)/ 

include $(BUILD_STATIC_LIBRARY)

