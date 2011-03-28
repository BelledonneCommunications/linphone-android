
LOCAL_PATH:= $(call my-dir)/../../exosip
include $(CLEAR_VARS)

LOCAL_MODULE := libeXosip2

# Don't prelink this library.  
LOCAL_PRELINK_MODULE := false

LOCAL_SRC_FILES = \
	src/eXosip.c \
	src/eXconf.c \
	src/eXregister_api.c \
	src/eXcall_api.c \
	src/eXmessage_api.c \
	src/eXtransport.c \
	src/jrequest.c \
	src/jresponse.c \
	src/jcallback.c \
	src/jdialog.c \
	src/udp.c \
	src/jcall.c \
	src/jreg.c \
	src/eXutils.c \
	src/jevents.c \
	src/misc.c \
	src/jauth.c \
	src/eXosip_transport_hook.c

LOCAL_SRC_FILES += \
	src/eXtl.c \
	src/eXtl_udp.c \
	src/eXtl_tcp.c \
	src/eXtl_dtls.c \
	src/eXtl_tls.c

LOCAL_SRC_FILES += \
	src/milenage.c \
	src/rijndael.c 

# BUILD_MAXSIZE: -UMINISIZE
LOCAL_SRC_FILES += \
	src/eXsubscription_api.c \
	src/eXoptions_api.c \
	src/eXinsubscription_api.c \
	src/eXpublish_api.c \
	src/jnotify.c \
	src/jsubscribe.c \
	src/inet_ntop.c \
	src/jpipe.c \
	src/eXrefer_api.c \
	src/jpublish.c \
	src/sdp_offans.c

LOCAL_CFLAGS += \
	-UHAVE_CONFIG_H \
	-include $(LOCAL_PATH)/../build/exosip/libeXosip2_AndroidConfig.h \
	-DOSIP_MT \
	-DENABLE_TRACE

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/include \
	$(LOCAL_PATH)/../osip/include \

#LOCAL_SHARED_LIBRARIES := libosip2

include $(BUILD_STATIC_LIBRARY)
