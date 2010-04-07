
LOCAL_PATH:= $(call my-dir)/../../osip
include $(CLEAR_VARS)


libosip2_SRC_FILES = \
	src/osip2/ict_fsm.c \
	src/osip2/ist_fsm.c \
	src/osip2/nict_fsm.c \
	src/osip2/nist_fsm.c \
	src/osip2/ict.c \
	src/osip2/ist.c \
	src/osip2/nict.c \
	src/osip2/nist.c \
	src/osip2/fsm_misc.c \
	src/osip2/osip.c \
	src/osip2/osip_transaction.c \
	src/osip2/osip_event.c \
	src/osip2/port_fifo.c \
	src/osip2/osip_dialog.c \
	src/osip2/osip_time.c \
	src/osip2/port_sema.c \
	src/osip2/port_thread.c \
	src/osip2/port_condv.c

libosipparser2_SRC_FILES = \
	src/osipparser2/osip_proxy_authorization.c \
	src/osipparser2/osip_cseq.c \
	src/osipparser2/osip_record_route.c \
	src/osipparser2/osip_route.c \
	src/osipparser2/osip_to.c \
	src/osipparser2/osip_from.c \
	src/osipparser2/osip_uri.c \
	src/osipparser2/osip_authorization.c \
	src/osipparser2/osip_header.c \
	src/osipparser2/osip_www_authenticate.c \
	src/osipparser2/osip_via.c \
	src/osipparser2/osip_body.c \
	src/osipparser2/osip_md5c.c \
	src/osipparser2/osip_message.c \
	src/osipparser2/osip_list.c \
	src/osipparser2/osip_call_id.c \
	src/osipparser2/osip_message_parse.c \
	src/osipparser2/osip_contact.c \
	src/osipparser2/osip_message_to_str.c \
	src/osipparser2/osip_content_length.c \
	src/osipparser2/osip_parser_cfg.c \
	src/osipparser2/osip_content_type.c \
	src/osipparser2/osip_proxy_authenticate.c \
	src/osipparser2/osip_mime_version.c \
	src/osipparser2/osip_port.c

# BUILD_MAXSIZE: -UMINISIZE
libosipparser2_SRC_FILES += \
	src/osipparser2/osip_accept_encoding.c \
	src/osipparser2/osip_content_encoding.c \
	src/osipparser2/osip_authentication_info.c \
	src/osipparser2/osip_proxy_authentication_info.c \
	src/osipparser2/osip_accept_language.c \
	src/osipparser2/osip_accept.c \
	src/osipparser2/osip_alert_info.c \
	src/osipparser2/osip_error_info.c \
	src/osipparser2/osip_allow.c \
	src/osipparser2/osip_content_disposition.c \
	src/osipparser2/sdp_accessor.c \
	src/osipparser2/sdp_message.c \
	src/osipparser2/osip_call_info.c

LOCAL_SRC_FILES := \
	$(libosip2_SRC_FILES) \
	$(libosipparser2_SRC_FILES)

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/include

LOCAL_MODULE:= libosip2

# global flags
LOCAL_CFLAGS += \
	-UHAVE_CONFIG_H \
	-include $(LOCAL_PATH)/../build/osip/libosip2_AndroidConfig.h

# specific flags
LOCAL_CFLAGS += \
	-DOSIP_MT \
	-DHAVE_PTHREAD \
	-DENABLE_TRACE \
	-DUSE_GPERF

#LOCAL_LDLIBS += -lpthread

include $(BUILD_STATIC_LIBRARY)
