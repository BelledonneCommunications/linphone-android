
LOCAL_PATH:= $(call my-dir)/../../libantlr3c/src
include $(CLEAR_VARS)

LOCAL_MODULE:= libantlr3c

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../../../externals/libantlr3c/ \
	$(LOCAL_PATH)/../../../externals/libantlr3c/include \

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
	antlr3stringstream.c \
	antlr3tokenstream.c \
	antlr3treeparser.c \
	antlr3ucs2inputstream.c	

include $(BUILD_STATIC_LIBRARY)

