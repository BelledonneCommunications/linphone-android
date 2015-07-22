LOCAL_PATH:= $(call my-dir)/../../libxml2
include $(CLEAR_VARS)

# We need to build this for both the device (as a shared library)
# and the host (as a static library for tools to use).

common_SRC_FILES := \
	SAX.c \
	entities.c \
	encoding.c \
	error.c \
	parserInternals.c \
	parser.c \
	tree.c \
	hash.c \
	list.c \
	xmlIO.c \
	xmlmemory.c \
	uri.c \
	valid.c \
	xlink.c \
	HTMLparser.c \
	HTMLtree.c \
	debugXML.c \
	xpath.c \
	xpointer.c \
	xinclude.c \
	nanohttp.c \
	nanoftp.c \
	DOCBparser.c \
	catalog.c \
	globals.c \
	threads.c \
	c14n.c \
	xmlstring.c \
	xmlregexp.c \
	xmlschemas.c \
	xmlschemastypes.c \
	xmlunicode.c \
	xmlreader.c \
	relaxng.c \
	dict.c \
	SAX2.c \
	legacy.c \
	chvalid.c \
	pattern.c \
	xmlsave.c \
	xmlmodule.c \
	xmlwriter.c \
	schematron.c

common_C_INCLUDES += \
	$(LOCAL_PATH)/include \
	$(LOCAL_PATH)/../build/libxml2  \

LOCAL_C_INCLUDES += $(common_C_INCLUDES)
#LOCAL_CFLAGS += -fvisibility=hidden

LOCAL_SRC_FILES := $(common_SRC_FILES)

LOCAL_MODULE:= liblpxml2

#turn off warnings since we cannot fix them
LOCAL_CFLAGS += -w

include $(BUILD_STATIC_LIBRARY)

