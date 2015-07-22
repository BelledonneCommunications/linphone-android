LOCAL_PATH:= $(call my-dir)/../../sqlite3
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	sqlite3.c

# Android does not provide a way to create temporary files which are needed
# for transactions... so we work in memory only
# see http://www.sqlite.org/compile.html#temp_store
LOCAL_CFLAGS += -DSQLITE_TEMP_STORE=3

LOCAL_MODULE:= liblinsqlite

include $(BUILD_STATIC_LIBRARY)

