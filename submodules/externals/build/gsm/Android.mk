
LOCAL_PATH:= $(call my-dir)/../../gsm
include $(CLEAR_VARS)

LOCAL_MODULE:= libgsm

LOCAL_SRC_FILES := \
		src/add.c            \
        	src/code.c           \
        	src/debug.c          \
        	src/decode.c         \
                src/long_term.c      \
                src/lpc.c            \
                src/preprocess.c     \
                src/rpe.c            \
                src/gsm_destroy.c    \
                src/gsm_decode.c     \
                src/gsm_encode.c     \
                src/gsm_explode.c    \
                src/gsm_implode.c    \
                src/gsm_create.c     \
                src/gsm_print.c      \
                src/gsm_option.c     \
                src/short_term.c     \
                src/table.c


#LOCAL_CFLAGS += \

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/inc

include $(BUILD_STATIC_LIBRARY)

