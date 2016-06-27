LOCAL_PATH:=$(call my-dir)/../../libmatroska-c

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	corec/corec/array/array.c \
	corec/corec/helpers/charconvert/charconvert_utf8.c \
	corec/corec/helpers/date/date_libc.c \
	corec/corec/helpers/file/bufstream.c \
	corec/corec/helpers/file/memstream.c \
	corec/corec/helpers/file/streams.c \
	corec/corec/helpers/file/tools.c \
	corec/corec/helpers/file/file_libc.c \
	corec/corec/helpers/file/stream_stdio.c \
	corec/corec/helpers/parser/parser2.c \
	corec/corec/helpers/parser/strtab.c \
	corec/corec/helpers/parser/strtypes.c \
	corec/corec/helpers/parser/dataheap.c \
	corec/corec/helpers/parser/buffer.c \
	corec/corec/helpers/parser/hotkey.c \
	corec/corec/helpers/parser/nodelookup.c \
	corec/corec/helpers/parser/urlpart.c \
	corec/corec/multithread/multithread_pthread.c \
	corec/corec/node/node.c \
	corec/corec/node/nodetree.c \
	corec/corec/str/str.c \
	corec/corec/str/str_linux.c

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH) \
	$(LOCAL_PATH)/corec

LOCAL_SRC_FILES += \
	libebml2/ebmlbinary.c \
	libebml2/ebmlcrc.c \
	libebml2/ebmldate.c \
	libebml2/ebmlelement.c \
	libebml2/ebmlmain.c \
	libebml2/ebmlmaster.c \
	libebml2/ebmlnumber.c \
	libebml2/ebmlstring.c \
	libebml2/ebmlvoid.c

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/libebml2

LOCAL_SRC_FILES += \
	libmatroska2/matroskablock.c \
	libmatroska2/matroskamain.c \
	libmatroska2/matroska_sem.c

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/libmatroska2

LOCAL_CFLAGS := \
	-DCOREC_PARSER \
	-DNDEBUG \
	-DCONFIG_EBML_WRITING \
	-DCONFIG_EBML_UNICODE \
	-DCONFIG_STDIO \
	-DCONFIG_FILEPOS_64


LOCAL_MODULE := libmatroska2

#turn off warnings since we cannot fix them
LOCAL_CFLAGS += -w

include $(BUILD_STATIC_LIBRARY)

