NDK_PATH=$(shell dirname `which ndk-build`)
SDK_PATH=$(shell dirname `which android`)
SDK_PLATFORM_TOOLS_PATH=$(shell dirname `which adb`)
NUMCPUS=$(shell grep -c '^processor' /proc/cpuinfo || echo "4" )
TOPDIR=$(shell pwd)
PATCH_FFMPEG=$(shell cd submodules/externals/ffmpeg && git status | grep neon)
LINPHONE_VERSION=$(shell cd submodules/linphone && git describe)
LINPHONE_ANDROID_DEBUG_VERSION=$(shell git describe)
ANDROID_MOST_RECENT_TARGET=$(shell android list target -c | grep android | tail -n1)

NDK_DEBUG=0
BUILD_UPNP=1
BUILD_REMOTE_PROVISIONING=1
BUILD_X264=0
BUILD_AMRNB=full # 0, light or full
BUILD_AMRWB=0
BUILD_GPLV3_ZRTP=0
BUILD_SILK=1
BUILD_G729=0
BUILD_TUNNEL=0
BUILD_WEBRTC_AECM=1
BUILD_FOR_X86=1
USE_JAVAH=1
LINPHONE_VIDEO=1

# Checks
CHECK_MSG=$(shell ./check_tools.sh)
ifneq ($(CHECK_MSG),)
    $(error $(CHECK_MSG))
endif
include check_tools.mk

all: update-project prepare-sources generate-apk

install: install-apk run-linphone

prepare-ffmpeg:
ifeq ($(PATCH_FFMPEG),)
	@patch -p0 < $(TOPDIR)/patches/ffmpeg_scalar_product_remove_alignment_hints.patch
endif
#libilbc
LIBILBC_SRC_DIR=$(TOPDIR)/submodules/libilbc-rfc3951
LIBILBC_BUILD_DIR=$(LIBILBC_SRC_DIR)
$(LIBILBC_SRC_DIR)/configure:
	cd $(LIBILBC_SRC_DIR) && ./autogen.sh

$(LIBILBC_BUILD_DIR)/Makefile: $(LIBILBC_SRC_DIR)/configure
	cd $(LIBILBC_BUILD_DIR) && \
	./configure \

$(LIBILBC_BUILD_DIR)/src/iLBC_decode.c: $(LIBILBC_BUILD_DIR)/Makefile
	cd $(LIBILBC_BUILD_DIR)/downloads && make \
	|| ( echo "iLBC prepare stage failed" ; exit 1 )
	
prepare-ilbc: $(LIBILBC_BUILD_DIR)/src/iLBC_decode.c
#libvpx
LIBVPX_SRC_DIR=$(TOPDIR)/submodules/externals/libvpx
$(LIBVPX_SRC_DIR)/vp8/common/asm_com_offsets.c.S:
	cd $(LIBVPX_SRC_DIR) && \
	./configure --target=armv7-android-gcc --sdk-path=$(NDK_PATH) --enable-error-concealment && \
	make asm_com_offsets.asm \
	|| ( echo "VP8 prepare stage failed." ; exit 1 )
	
prepare-vpx: $(LIBVPX_SRC_DIR)/vp8/common/asm_com_offsets.c.S
#SILK
LIBMSSILK_SRC_DIR=$(TOPDIR)/submodules/mssilk
LIBMSSILK_BUILD_DIR=$(LIBMSSILK_SRC_DIR)
$(LIBMSSILK_SRC_DIR)/configure:
	cd $(LIBMSSILK_SRC_DIR) && ./autogen.sh

$(LIBMSSILK_BUILD_DIR)/Makefile: $(LIBMSSILK_SRC_DIR)/configure
	cd $(LIBMSSILK_BUILD_DIR) && \
	$(LIBMSSILK_SRC_DIR)/configure --host=arm-linux MEDIASTREAMER_CFLAGS=" " MEDIASTREAMER_LIBS=" " 

#make sure to update this path if SILK sdk is changed
$(LIBMSSILK_BUILD_DIR)/sdk/SILK_SDK_SRC_v1.0.8/SILK_SDK_SRC_ARM_v1.0.8/src/SKP_Silk_resampler.c: $(LIBMSSILK_BUILD_DIR)/Makefile
	cd $(LIBMSSILK_BUILD_DIR)/sdk && \
	make extract-sources \
	|| ( echo "SILK audio plugin prepare state failed." ; exit 1 )

prepare-silk: $(LIBMSSILK_BUILD_DIR)/sdk/SILK_SDK_SRC_v1.0.8/SILK_SDK_SRC_ARM_v1.0.8/src/SKP_Silk_resampler.c

#srtp
$(TOPDIR)/submodules/externals/srtp/config.h : $(TOPDIR)/submodules/externals/build/srtp/config.h
	@cd $(TOPDIR)/submodules/externals/srtp/ && \
	cp ../build/srtp/config.h . \
	|| ( echo "SRTP prepare state failed." ; exit 1 )

prepare-srtp: $(TOPDIR)/submodules/externals/srtp/config.h

prepare-mediastreamer2:
	@cd $(TOPDIR)/submodules/linphone/mediastreamer2/src/ && \
	eval `cat Makefile.am | grep xxd | grep yuv2rgb.vs | sed 's/$$$$builddir/./'` && \
	eval `cat Makefile.am | grep xxd | grep yuv2rgb.fs | sed 's/$$$$builddir/./'` && \
	if ! [ -e yuv2rgb.vs.h ]; then echo "yuv2rgb.vs.h creation error (do you have 'xxd' application installed ?)"; exit 1; fi && \
	if ! [ -e yuv2rgb.fs.h ]; then echo "yuv2rgb.fs.h creation error (do you have 'xxd' application installed ?)"; exit 1; fi


ANLTR3_SRC_DIR=$(TOPDIR)/submodules/externals/antlr3/runtime/C/include/
ANTLR3_BUILD_DIR=$(ANTLR3_SRC_DIR)
$(ANLTR3_SRC_DIR)/antlr3config.h: $(TOPDIR)/submodules/externals/build/antlr3/antlr3config.h
	cp $(TOPDIR)/submodules/externals/build/antlr3/antlr3config.h $(ANLTR3_SRC_DIR)
prepare-antlr3: $(ANLTR3_SRC_DIR)/antlr3config.h 

%.tokens: %.g
	$(ANTLR) -make -fo $(dir $^) $^ 

BELLESIP_SRC_DIR=$(TOPDIR)/submodules/belle-sip
BELLESIP_BUILD_DIR=$(BELLESIP_SRC_DIR)
prepare-belle-sip: $(BELLESIP_SRC_DIR)/src/belle_sip_message.tokens $(BELLESIP_SRC_DIR)/src/belle_sdp.tokens

prepare-cunit: $(TOPDIR)/submodules/externals/cunit/CUnit/Headers/*.h
	[ -d $(TOPDIR)/submodules/externals/build/cunit/CUnit ] || mkdir $(TOPDIR)/submodules/externals/build/cunit/CUnit
	cp $^ $(TOPDIR)/submodules/externals/build/cunit/CUnit

prepare-liblinphone_tester: $(TOPDIR)/submodules/linphone/tester/*_lrc $(TOPDIR)/submodules/linphone/tester/*_rc
#	[ -d $(TOPDIR)/liblinphone_tester/res/raw ] || mkdir $(TOPDIR)/liblinphone_tester/res/raw
#	cp $^ $(TOPDIR)/liblinphone_tester/res/raw

prepare-sources: prepare-ffmpeg prepare-ilbc prepare-vpx prepare-silk prepare-srtp prepare-mediastreamer2 prepare-antlr3 prepare-belle-sip

LIBLINPHONE_OPTIONS = NDK_DEBUG=$(NDK_DEBUG) LINPHONE_VERSION=$(LINPHONE_VERSION) BUILD_UPNP=$(BUILD_UPNP) BUILD_REMOTE_PROVISIONING=$(BUILD_REMOTE_PROVISIONING) BUILD_X264=$(BUILD_X264) BUILD_AMRNB=$(BUILD_AMRNB) BUILD_AMRWB=$(BUILD_AMRWB) BUILD_GPLV3_ZRTP=$(BUILD_GPLV3_ZRTP) BUILD_SILK=$(BUILD_SILK) BUILD_G729=$(BUILD_G729) BUILD_TUNNEL=$(BUILD_TUNNEL) BUILD_WEBRTC_AECM=$(BUILD_WEBRTC_AECM) BUILD_FOR_X86=$(BUILD_FOR_X86) USE_JAVAH=$(USE_JAVAH) 

generate-libs: prepare-sources javah
	$(NDK_PATH)/ndk-build $(LIBLINPHONE_OPTIONS) -j$(NUMCPUS)

update-project:
	$(SDK_PATH)/android update project --path . --target $(ANDROID_MOST_RECENT_TARGET)
	$(SDK_PATH)/android update project --path liblinphone_tester --target $(ANDROID_MOST_RECENT_TARGET)

liblinphone_tester: prepare-sources prepare-cunit prepare-liblinphone_tester javah
	$(NDK_PATH)/ndk-build -C liblinphone_tester $(LIBLINPHONE_OPTIONS) NDK_DEBUG=1 -j$(NUMCPUS)

javah: 
	ant javah

generate-apk: generate-libs
	ant partial-clean
	echo "version.name=$(LINPHONE_ANDROID_DEBUG_VERSION)" > default.properties
	ant debug

install-apk:
	ant installd

release: update-project
	ant clean
	echo "What is the version name for the release ?"; \
	read version; \
	echo "version.name=$$version" > default.properties
	ant release

run-linphone:
	ant run

run-tests:
	ant partial-clean && \
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone.test
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone
	@cd $(TOPDIR)/tests/ && \
	$(SDK_PATH)/android update test-project --path . -m ../ && \
	ant debug && \
	ant installd && \
	ant test

clean:
	$(NDK_PATH)/ndk-build clean $(LIBLINPHONE_OPTIONS)
	ant clean

.PHONY: clean

generate-sdk: generate-apk
	ant liblinphone-sdk

