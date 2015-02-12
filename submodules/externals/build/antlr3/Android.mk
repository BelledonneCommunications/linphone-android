
LOCAL_PATH:= $(call my-dir)/../../antlr3/runtime/C/src
include $(CLEAR_VARS)

LOCAL_MODULE:= antlr3

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../../../../externals/antlr3 \
	$(LOCAL_PATH)/../include
LOCAL_SRC_FILES := \
	antlr3baserecognizer.c \
	antlr3basetree.c \
	antlr3basetreeadaptor.c \
	antlr3bitset.c \
	antlr3collections.c \
	antlr3commontoken.c \
	antlr3commontree.c \
	antlr3commontreeadaptor.c \
	antlr3commontreenodestream.c \
	antlr3convertutf.c \
	antlr3cyclicdfa.c \
	antlr3debughandlers.c \
	antlr3encodings.c \
	antlr3exception.c \
	antlr3filestream.c \
	antlr3inputstream.c \
	antlr3intstream.c \
	antlr3lexer.c \
	antlr3parser.c \
	antlr3rewritestreams.c \
	antlr3string.c \
	antlr3tokenstream.c \
	antlr3treeparser.c \

#turn off warnings since we cannot fix them
LOCAL_CFLAGS += -w

include $(BUILD_STATIC_LIBRARY)

