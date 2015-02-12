LOCAL_PATH:= $(call my-dir)/../../libupnp
include $(CLEAR_VARS)

# We need to build this for both the device (as a shared library)
# and the host (as a static library for tools to use).


LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../build/libupnp/inc \
	$(LOCAL_PATH)/upnp/inc \
	$(LOCAL_PATH)/upnp/src/inc \
        $(LOCAL_PATH)/threadutil/inc \
	$(LOCAL_PATH)/ixml/inc \
	$(LOCAL_PATH)/ixml/src/inc \

LOCAL_SRC_FILES := \
        upnp/src/ssdp/ssdp_device.c \
        upnp/src/ssdp/ssdp_server.c \
        upnp/src/ssdp/ssdp_ctrlpt.c \
        upnp/src/genlib/service_table/service_table.c \
        upnp/src/genlib/util/upnp_timeout.c \
        upnp/src/genlib/util/membuffer.c \
        upnp/src/genlib/util/strintmap.c \
        upnp/src/genlib/util/util.c \
        upnp/src/genlib/net/uri/uri.c \
        upnp/src/genlib/net/http/httpreadwrite.c \
        upnp/src/genlib/net/http/statcodes.c \
        upnp/src/genlib/net/http/httpparser.c \
        upnp/src/genlib/net/http/webserver.c \
        upnp/src/genlib/net/http/parsetools.c \
        upnp/src/genlib/net/sock.c \
        upnp/src/genlib/miniserver/miniserver.c \
        upnp/src/genlib/client_table/client_table.c \
        upnp/src/api/upnptools.c \
        upnp/src/api/UpnpString.c \
        upnp/src/api/upnpapi.c \
        upnp/src/api/upnpdebug.c \
        upnp/src/uuid/sysdep.c \
        upnp/src/uuid/uuid.c \
        upnp/src/uuid/md5.c \
        upnp/src/soap/soap_device.c \
        upnp/src/soap/soap_ctrlpt.c \
        upnp/src/soap/soap_common.c \
        upnp/src/win_dll.c \
        upnp/src/inet_pton.c \
        upnp/src/urlconfig/urlconfig.c \
        upnp/src/gena/gena_callback2.c \
        upnp/src/gena/gena_ctrlpt.c \
        upnp/src/gena/gena_device.c \
        threadutil/src/FreeList.c \
        threadutil/src/LinkedList.c \
        threadutil/src/ThreadPool.c \
        threadutil/src/TimerThread.c \
        ixml/src/ixmldebug.c \
        ixml/src/node.c \
        ixml/src/ixmlmembuf.c \
        ixml/src/attr.c \
        ixml/src/ixmlparser.c \
        ixml/src/element.c \
        ixml/src/nodeList.c \
        ixml/src/ixml.c \
        ixml/src/document.c \
        ixml/src/namedNodeMap.c \

LOCAL_C_FLAGS += -DPTHREAD_MUTEX_RECURSIVE=PTHREAD_MUTEX_RECURSIVE

LOCAL_MODULE:= libupnp

#turn off warnings since we cannot fix them
LOCAL_CFLAGS += -w

include $(BUILD_STATIC_LIBRARY)

