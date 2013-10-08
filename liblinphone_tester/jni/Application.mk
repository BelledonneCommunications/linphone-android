LOCAL_PATH:= $(call my-dir)
include ../jni/Application.mk
APP_PROJECT_PATH := $(LOCAL_PATH)/../
APP_BUILD_SCRIPT := $(LOCAL_PATH)/Android.mk
APP_OPTIM := debug

APP_MODULES += cunit liblinphone_tester 
