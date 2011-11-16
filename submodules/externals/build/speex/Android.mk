
LOCAL_PATH:= $(call my-dir)/../../speex
include $(CLEAR_VARS)

LOCAL_MODULE:= libspeex

# Need some non-thumb arm instructions
LOCAL_ARM_MODE := arm

libspeex_SRC_FILES := \
	libspeex/cb_search.c \
	libspeex/exc_10_32_table.c \
	libspeex/exc_8_128_table.c \
	libspeex/filters.c \
	libspeex/gain_table.c \
	libspeex/hexc_table.c \
	libspeex/high_lsp_tables.c \
	libspeex/lsp.c \
	libspeex/ltp.c \
	libspeex/speex.c \
	libspeex/stereo.c \
	libspeex/vbr.c \
	libspeex/vq.c \
	libspeex/bits.c \
	libspeex/exc_10_16_table.c \
	libspeex/exc_20_32_table.c \
	libspeex/exc_5_256_table.c \
	libspeex/exc_5_64_table.c \
	libspeex/gain_table_lbr.c \
	libspeex/hexc_10_32_table.c \
	libspeex/lpc.c \
	libspeex/lsp_tables_nb.c \
	libspeex/modes.c \
	libspeex/modes_wb.c \
	libspeex/nb_celp.c \
	libspeex/quant_lsp.c \
	libspeex/sb_celp.c \
	libspeex/speex_callbacks.c \
	libspeex/speex_header.c \
	libspeex/window.c

# Default FFT is FFTW3
fft_SRC_FILES =

# Un-comment for KISS_FFT and SMALL_FFT
fft_SRC_FILES += libspeex/smallft.c

# Un-comment for KISS_FFT
fft_SRC_FILES += \
	libspeex/kiss_fft.c \
	libspeex/kiss_fftr.c 

libspeexdsp_SRC_FILES := \
	libspeex/preprocess.c \
	libspeex/jitter.c \
	libspeex/mdf.c \
	libspeex/fftwrap.c \
	libspeex/filterbank.c \
	libspeex/resample.c \
	libspeex/buffer.c \
	libspeex/scal.c \
	libspeex/speexdsp.c \
	$(fft_SRC_FILES)

LOCAL_SRC_FILES := \
	$(libspeex_SRC_FILES) \
	$(libspeexdsp_SRC_FILES)

#	-DARM4_ASM 


USE_FLOAT=0

FIXED_POINT_FLAGS=\
	-DARM5E_ASM\
	-DDISABLE_FLOAT_API \
	-DFIXED_POINT=1

ifeq ($(TARGET_ARCH),arm)
	ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
		LOCAL_CFLAGS += -DARMV7NEON_ASM
		# add NEON support
		LOCAL_SRC_FILES += libspeex/resample_neon.c.neon
		ifeq ($(USE_FLOAT),1)
			LOCAL_CFLAGS += -DFLOATING_POINT=1 
		else 
			LOCAL_CFLAGS += $(FIXED_POINT_FLAGS)
		endif
	else
		LOCAL_CFLAGS += $(FIXED_POINT_FLAGS)
	endif 
else
LOCAL_CFLAGS += \
	-DFLOATING_POINT=1
endif



LOCAL_CFLAGS += \
	-UHAVE_CONFIG_H \
	-include $(LOCAL_PATH)/../build/speex/speex_AndroidConfig.h \
	'-DEXPORT=__attribute__((visibility("default")))'

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../build/speex \
	$(LOCAL_PATH)/include

include $(BUILD_STATIC_LIBRARY)

