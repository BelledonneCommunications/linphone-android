LOCAL_PATH:= $(call my-dir)/../../codec2

BUILD_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libcodec2


LOCAL_SRC_FILES += \
	src/dump.c \
	src/lpc.c \
	src/nlp.c \
	src/postfilter.c \
	src/sine.c \
	src/codec2.c \
	src/fifo.c \
	src/fdmdv.c \
	src/kiss_fft.c \
	src/interp.c \
	src/lsp.c \
	src/phase.c \
	src/quantise.c \
	src/pack.c \
	src/codebook.c \
	src/codebookd.c \
	src/codebookvq.c \
	src/codebookjnd.c \
	src/codebookjvm.c \
	src/codebookvqanssi.c \
	src/codebookdt.c \
	src/codebookge.c \
	src/golay23.c

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/src

LOCAL_CFLAGS := -ffast-math	-include $(BUILD_PATH)/codec2_prefixed_symbols.h


LOCAL_ARM_MODE := arm

include $(BUILD_STATIC_LIBRARY)
