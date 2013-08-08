LOCAL_PATH:= $(call my-dir)/../../sqlite3
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	sqlite3.c

LOCAL_MODULE:= liblinsqlite

include $(BUILD_STATIC_LIBRARY)

