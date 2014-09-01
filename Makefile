
NDK_PATH=$(shell dirname `which ndk-build`)
SDK_PATH=$(shell dirname `which android`)
SDK_PLATFORM_TOOLS_PATH=$(shell dirname `which adb`)
ARM_COMPILER_PATH=`find "$(NDK_PATH)" -name "arm-linux-androideabi-gcc*" -print -quit`
ARM_TOOLCHAIN_PATH=$(shell dirname $(ARM_COMPILER_PATH))/arm-linux-androideabi-
ARM_SYSROOT=$(shell find "${NDK_PATH}" -name arch-arm -print | \
	awk '{n = split($$0,a,"/"); \
	split(a[n-1],b,"-"); \
	print $$0 " " b[2]}' | \
	sort -g -k 2 | \
	awk '{ print $$1 }' | tail -1)
X86_COMPILER_PATH=`find "$(NDK_PATH)" -name "i686-linux-android-gcc*" -print -quit`
X86_TOOLCHAIN_PATH=$(shell dirname $(X86_COMPILER_PATH))/i686-linux-android-
X86_SYSROOT=$(shell find "${NDK_PATH}" -name arch-x86 -print | \
	awk '{n = split($$0,a,"/"); \
	split(a[n-1],b,"-"); \
	print $$0 " " b[2]}' | \
	sort -g -k 2 | \
	awk '{ print $$1 }' | tail -1)
NUMCPUS=$(shell grep -c '^processor' /proc/cpuinfo 2>/dev/null || echo "4" )
TOPDIR=$(shell pwd)
LIBLINPHONE_VERSION=$(shell cd submodules/linphone && git describe --always)
LINPHONE_ANDROID_DEBUG_VERSION=$(shell git describe --always)
BELLESIP_VERSION_SCRIPT:=cat submodules/belle-sip/configure.ac | grep "AC_INIT(" | sed -e "s/.*belle-sip\]//" | sed -e "s/].*//" | sed -e "s/.*\[//"
BELLESIP_VERSION=$(shell $(BELLESIP_VERSION_SCRIPT))
ANDROID_MOST_RECENT_TARGET=$(shell android list target -c | grep android | tail -n1)
SQLITE_VERSION=3071700
SQLITE_BASENAME=sqlite-amalgamation-$(SQLITE_VERSION)
SQLITE_URL=http://www.sqlite.org/2013/$(SQLITE_BASENAME).zip
ENABLE_GPL_THIRD_PARTIES=1

#default options, can be overidden using make OPTION=value .

ifeq ($(ENABLE_GPL_THIRD_PARTIES),1)
BUILD_G729=1
else
#x264 and g729 requires additional licensing agreements.
BUILD_X264=0
BUILD_G729=0
endif

NDK_DEBUG=0
BUILD_VIDEO=1
BUILD_OPENH264=1
BUILD_UPNP=1
BUILD_AMRNB=full # 0, light or full
BUILD_AMRWB=1
BUILD_ZRTP=1
BUILD_SILK=1
BUILD_TUNNEL=0
BUILD_WEBRTC_AECM=1
BUILD_OPUS=1
BUILD_MATROSKA=0
BUILD_WEBRTC_ISAC=1
BUILD_FOR_X86=1
USE_JAVAH=1
BUILD_TLS=1
BUILD_SQLITE=1
BUILD_CONTACT_HEADER=0
BUILD_RTP_MAP=0
BUILD_DONT_CHECK_HEADERS_IN_MESSAGE=0
LIBLINPHONE_EXTENDED_SRC_FILES=
LIBLINPHONE_EXTENDED_C_INCLUDES=
LIBLINPHONE_EXTENDED_CFLAGS=
APP_STL=stlport_static

# Checks
CHECK_MSG=$(shell ./check_tools.sh)
ifneq ($(CHECK_MSG),)
    $(error $(CHECK_MSG))
endif
include check_tools.mk

OPENSSL_DIR=$(shell openssl version -d | sed  "s/OPENSSLDIR: \"\(.*\)\"/\1/")
ifneq ($(shell ls $(OPENSSL_DIR)/certs),)
	HTTPS_CA_DIR=$(OPENSSL_DIR)/certs
else
	HTTPS_CA_DIR=$(OPENSSL_DIR)
endif

all: update-project prepare-sources generate-apk
ifeq ($(ENABLE_GPL_THIRD_PARTIES),1)
	@echo "***************************************************************************"
	@echo "***** CAUTION, this liblinphone SDK is built using 3rd party GPL code *****"
	@echo "*****    Even if you acquired a proprietary license from Belledonne   *****"
	@echo "*****          Communications, this SDK is GPL and GPL only.          *****"
	@echo "*****           To disable 3rd party gpl code, please use:            *****"
	@echo "*****                 $$ make ENABLE_GPL_THIRD_PARTIES=0               *****"
	@echo "***************************************************************************"
else
	@echo
	@echo "*****************************************************************"
	@echo "*****      Linphone SDK without 3rd party GPL software      *****"
	@echo "***** If you acquired a proprietary license from Belledonne *****"
	@echo "*****     Communications, this SDK can be used to create    *****"
	@echo "*****       a proprietary linphone-based application.       *****"
	@echo "*****************************************************************"
endif

install: install-apk run-linphone

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

#ffmpeg
ifeq ($(BUILD_VIDEO),1)
BUILD_FFMPEG_DEPS=$(FFMPEG_SRC_DIR)/non_versioned_soname_patch_applied.txt $(FFMPEG_BUILD_DIR)/arm/libavcodec/libavcodec-linphone-arm.so
ifeq ($(BUILD_FOR_X86), 1)
	BUILD_FFMPEG_DEPS+=$(FFMPEG_BUILD_DIR)/x86/libavcodec/libavcodec-linphone-x86.so
endif
endif
FFMPEG_SRC_DIR=$(TOPDIR)/submodules/externals/ffmpeg
FFMPEG_BUILD_DIR=$(TOPDIR)/submodules/externals/build/ffmpeg
FFMPEG_CONFIGURE_OPTIONS=--target-os=linux --enable-cross-compile --enable-runtime-cpudetect \
	--disable-everything --disable-doc --disable-ffplay --disable-ffmpeg --disable-ffprobe --disable-ffserver \
	--disable-avdevice --disable-avfilter --disable-avformat --disable-swresample --disable-network \
	--enable-decoder=mjpeg --enable-encoder=mjpeg --enable-decoder=mpeg4 --enable-encoder=mpeg4 --enable-decoder=h264 \
	--enable-decoder=h263p --enable-encoder=h263p --enable-decoder=h263 --enable-encoder=h263\
	--disable-static --enable-shared
FFMPEG_ARM_CONFIGURE_OPTIONS=--build-suffix=-linphone-arm --arch=arm --sysroot=$(ARM_SYSROOT) --cross-prefix=$(ARM_TOOLCHAIN_PATH) --enable-pic
FFMPEG_X86_CONFIGURE_OPTIONS=--build-suffix=-linphone-x86 --arch=x86 --sysroot=$(X86_SYSROOT) --cross-prefix=$(X86_TOOLCHAIN_PATH) --disable-mmx --disable-sse2 --disable-ssse3 --extra-cflags='-O3'

$(FFMPEG_SRC_DIR)/non_versioned_soname_patch_applied.txt:
	@patch -p0 < $(TOPDIR)/patches/ffmpeg_non_versioned_soname.patch
	touch $@

$(FFMPEG_BUILD_DIR)/arm/libavcodec/libavcodec-linphone-arm.so:
	mkdir -p $(FFMPEG_BUILD_DIR)/arm && \
	cd $(FFMPEG_BUILD_DIR)/arm && \
	$(FFMPEG_SRC_DIR)/configure $(FFMPEG_CONFIGURE_OPTIONS) $(FFMPEG_ARM_CONFIGURE_OPTIONS) && \
	make -j ${NUMCPUS} \
	|| ( echo "Build of ffmpeg for arm failed." ; exit 1 )

$(FFMPEG_BUILD_DIR)/x86/libavcodec/libavcodec-linphone-x86.so:
	mkdir -p $(FFMPEG_BUILD_DIR)/x86 && \
	cd $(FFMPEG_BUILD_DIR)/x86 && \
	$(FFMPEG_SRC_DIR)/configure $(FFMPEG_CONFIGURE_OPTIONS) $(FFMPEG_X86_CONFIGURE_OPTIONS) && \
	make -j ${NUMCPUS} \
	|| ( echo "Build of ffmpeg for x86 failed." ; exit 1 )

build-ffmpeg: $(BUILD_FFMPEG_DEPS)

clean-ffmpeg:
	rm -rf $(FFMPEG_BUILD_DIR)/arm && \
	rm -rf $(FFMPEG_BUILD_DIR)/x86

#x264
ifeq ($(BUILD_VIDEO),1)
ifeq ($(BUILD_X264), 1)
BUILD_X264_DEPS=$(X264_SRC_DIR)/log2f_fix_patch_applied.txt $(X264_BUILD_DIR)/arm/libx264.a
ifeq ($(BUILD_FOR_X86), 1)
	BUILD_X264_DEPS+=$(X264_BUILD_DIR)/x86/libx264.a
endif
endif

X264_SRC_DIR=$(TOPDIR)/submodules/externals/x264
X264_BUILD_DIR=$(TOPDIR)/submodules/externals/build/x264
X264_CONFIGURE_OPTIONS=
X264_ARM_CONFIGURE_OPTIONS=--host=arm-none-linux-gnueabi --sysroot=$(ARM_SYSROOT) --cross-prefix=$(ARM_TOOLCHAIN_PATH) --enable-pic
X264_X86_CONFIGURE_OPTIONS=--host=i686-linux-gnueabi --sysroot=$(X86_SYSROOT) --cross-prefix=$(X86_TOOLCHAIN_PATH)

$(X264_SRC_DIR)/log2f_fix_patch_applied.txt:
	@patch -p0 < $(TOPDIR)/patches/x264_log2f_fix.patch
	touch $@

$(X264_BUILD_DIR)/arm/libx264.a:
	mkdir -p $(X264_BUILD_DIR)/arm && \
	cd $(X264_SRC_DIR) && \
	$(X264_SRC_DIR)/configure $(X264_CONFIGURE_OPTIONS) $(X264_ARM_CONFIGURE_OPTIONS) && \
	make -j $(NUMCPUS) STRIP= && \
	cp libx264.a $(X264_BUILD_DIR)/arm/libx264.a && \
	make clean \
	|| ( echo "Build of x264 for arm failed." ; exit 1 )

$(X264_BUILD_DIR)/x86/libx264.a:
	mkdir -p $(X264_BUILD_DIR)/x86 && \
	cd $(X264_SRC_DIR) && \
	$(X264_SRC_DIR)/configure $(X264_CONFIGURE_OPTIONS) $(X264_X86_CONFIGURE_OPTIONS) && \
	make -j $(NUMCPUS) STRIP= && \
	cp libx264.a $(X264_BUILD_DIR)/x86/libx264.a && \
	make clean \
	|| ( echo "Build of x264 for x86 failed." ; exit 1 )

endif
build-x264: $(BUILD_X264_DEPS)

clean-x264:
	rm -rf $(X264_BUILD_DIR)/arm && \
	rm -rf $(X264_BUILD_DIR)/x86

#openh264
ifeq ($(BUILD_VIDEO),1)
ifeq ($(BUILD_OPENH264), 1)
BUILD_OPENH264_DEPS=build-openh264-arm
ifeq ($(BUILD_FOR_X86), 1)
	BUILD_OPENH264_DEPS+=build-openh264-x86
endif
endif
endif

OPENH264_SRC_DIR=$(TOPDIR)/submodules/externals/openh264
OPENH264_BUILD_DIR=$(TOPDIR)/submodules/externals/build/openh264
OPENH264_BUILD_DIR_ARM=$(OPENH264_BUILD_DIR)/arm
OPENH264_BUILD_DIR_X86=$(OPENH264_BUILD_DIR)/x86

$(OPENH264_SRC_DIR)/patch.stamp: $(TOPDIR)/patches/openh264-permissive.patch
	cd $(OPENH264_SRC_DIR) && patch -p1 < $(TOPDIR)/patches/openh264-permissive.patch && touch $(OPENH264_SRC_DIR)/patch.stamp

openh264-patch:	$(OPENH264_SRC_DIR)/patch.stamp

openh264-install-headers:
	mkdir -p $(OPENH264_SRC_DIR)/include/wels
	rsync -rvLpgoc --exclude ".git"  $(OPENH264_SRC_DIR)/codec/api/svc/* $(OPENH264_SRC_DIR)/include/wels/.

copy-openh264-x86: openh264-patch openh264-install-headers
	mkdir -p $(OPENH264_BUILD_DIR)
	mkdir -p $(OPENH264_BUILD_DIR_X86)
	cd $(OPENH264_BUILD_DIR_X86) \
	&& rsync -rvLpgoc --exclude ".git"  $(OPENH264_SRC_DIR)/* .

copy-openh264-arm: openh264-patch openh264-install-headers
	mkdir -p $(OPENH264_BUILD_DIR)
	mkdir -p $(OPENH264_BUILD_DIR_ARM)
	cd $(OPENH264_BUILD_DIR_ARM) \
	&& rsync -rvLpgoc --exclude ".git"  $(OPENH264_SRC_DIR)/* .

build-openh264-x86: copy-openh264-x86
	cd $(OPENH264_BUILD_DIR_X86) && \
	make libraries -j $(NUMCPUS) OS=android ARCH=x86 NDKROOT=$(NDK_PATH) TARGET=$(ANDROID_MOST_RECENT_TARGET)

build-openh264-arm: copy-openh264-arm
	cd $(OPENH264_BUILD_DIR_ARM) && \
	make libraries -j $(NUMCPUS) OS=android ARCH=arm NDKROOT=$(NDK_PATH) TARGET=$(ANDROID_MOST_RECENT_TARGET)

build-openh264: $(BUILD_OPENH264_DEPS)

clean-openh264:
	cd $(OPENH264_SRC_DIR) && git clean -dfx && git reset --hard
	rm -rf $(OPENH264_BUILD_DIR_ARM)
	rm -rf $(OPENH264_BUILD_DIR_X86)

#libvpx
ifeq ($(BUILD_VIDEO),1)
BUILD_VPX_DEPS=$(LIBVPX_SRC_DIR)/configure_android_x86_patch_applied.txt $(LIBVPX_BUILD_DIR)/arm/libvpx.a
ifeq ($(BUILD_FOR_X86), 1)
	BUILD_VPX_DEPS+=$(LIBVPX_BUILD_DIR)/x86/libvpx.a
endif
endif
LIBVPX_SRC_DIR=$(TOPDIR)/submodules/externals/libvpx
LIBVPX_BUILD_DIR=$(TOPDIR)/submodules/externals/build/libvpx
LIBVPX_CONFIGURE_OPTIONS=--disable-vp9 --disable-examples --disable-unit-tests --disable-postproc --enable-error-concealment --enable-debug

$(LIBVPX_SRC_DIR)/configure_android_x86_patch_applied.txt:
	@patch -p1 < $(TOPDIR)/patches/libvpx_configure_android_x86.patch
	touch $@

$(LIBVPX_BUILD_DIR)/arm/libvpx.a:
	mkdir -p $(LIBVPX_BUILD_DIR)/arm && \
	cd $(LIBVPX_BUILD_DIR)/arm && \
	$(LIBVPX_SRC_DIR)/configure --target=armv7-android-gcc --sdk-path=$(NDK_PATH) $(LIBVPX_CONFIGURE_OPTIONS) && \
	make -j ${NUMCPUS} \
	|| ( echo "Build of libvpx for arm failed." ; exit 1 )

$(LIBVPX_BUILD_DIR)/x86/libvpx.a:
	mkdir -p $(LIBVPX_BUILD_DIR)/x86 && \
	cd $(LIBVPX_BUILD_DIR)/x86 && \
	$(LIBVPX_SRC_DIR)/configure --target=x86-android-gcc --sdk-path=$(NDK_PATH) $(LIBVPX_CONFIGURE_OPTIONS) && \
	make -j${NUMCPUS} \
	|| ( echo "Build of libvpx for x86 failed." ; exit 1 )

build-vpx: $(BUILD_VPX_DEPS)

clean-vpx:
	rm -rf submodules/externals/build/libvpx/arm && \
	rm -rf submodules/externals/build/libvpx/x86

#libmatroska
ifeq ($(BUILD_VIDEO), 1)
ifeq ($(BUILD_MATROSKA),1)
BUILD_MATROSKA_DEPS=$(LIBEBML2_BUILD_DIR)/arm/libebml2.a $(LIBMATROSKA_BUILD_DIR)/arm/libmatroska2.a
ifeq ($(BUILD_FOR_X86), 1)
BUILD_MATROSKA_DEPS+=$(LIBEBML2_BUILD_DIR)/x86/libebml2.a $(LIBMATROSKA_BUILD_DIR)/x86/libmatroska2.a
endif #BUILD_FOR_X86
BUILD_MATROSKA_DEPS += $(LIBEBML2_BUILD_DIR)/include $(LIBMATROSKA_BUILD_DIR)/include
endif #BUILD_MATROSKA
endif #BUILD_VIDEO
LIBMATROSKA_SRC_DIR=$(TOPDIR)/submodules/externals/libmatroska
LIBMATROSKA_BUILD_DIR=$(TOPDIR)/submodules/externals/build/libmatroska
LIBEBML2_BUILD_DIR=$(TOPDIR)/submodules/externals/build/libebml2
COREMAKE=$(LIBMATROSKA_SRC_DIR)/corec/tools/coremake/coremake

build-matroska: $(BUILD_MATROSKA_DEPS)

$(LIBEBML2_BUILD_DIR)/arm/libebml2.a: $(LIBMATROSKA_SRC_DIR)/release/android_armv7/libebml2.a
	mkdir -p $(LIBEBML2_BUILD_DIR)/arm
	cp $< $@

$(LIBMATROSKA_BUILD_DIR)/arm/libmatroska2.a: $(LIBMATROSKA_SRC_DIR)/release/android_armv7/libmatroska2.a
	mkdir -p $(LIBMATROSKA_BUILD_DIR)/arm
	cp $< $@

$(LIBEBML2_BUILD_DIR)/x86/libebml2.a: $(LIBMATROSKA_SRC_DIR)/release/android_x86/libebml2.a
	mkdir -p $(LIBEBML2_BUILD_DIR)/x86
	cp $< $@

$(LIBMATROSKA_BUILD_DIR)/x86/libmatroska2.a: $(LIBMATROSKA_SRC_DIR)/release/android_x86/libmatroska2.a
	mkdir -p $(LIBMATROSKA_BUILD_DIR)/x86
	cp $< $@

$(LIBMATROSKA_SRC_DIR)/release/android_armv7/libebml2.a: $(LIBMATROSKA_SRC_DIR)/builded.txt

$(LIBMATROSKA_SRC_DIR)/release/android_armv7/libmatroska2.a: $(LIBMATROSKA_SRC_DIR)/builded.txt

$(LIBMATROSKA_SRC_DIR)/release/android_x86/libebml2.a: $(LIBMATROSKA_SRC_DIR)/builded.txt

$(LIBMATROSKA_SRC_DIR)/release/android_x86/libmatroska2.a: $(LIBMATROSKA_SRC_DIR)/builded.txt

$(LIBMATROSKA_SRC_DIR)/builded.txt: $(COREMAKE) $(LIBMATROSKA_SRC_DIR)/configure_config_h.txt $(LIBMATROSKA_SRC_DIR)/fix_coremake.txt
	cd $(LIBMATROSKA_SRC_DIR) ; $(COREMAKE) android_armv7 -f $(LIBMATROSKA_SRC_DIR)/root.proj
	make -C $(LIBMATROSKA_SRC_DIR) ebml2
	make -C $(LIBMATROSKA_SRC_DIR) matroska2
ifeq ($(BUILD_FOR_X86), 1)
	cd $(LIBMATROSKA_SRC_DIR) ; $(COREMAKE) android_x86 -f $(LIBMATROSKA_SRC_DIR)/root.proj
	make -C $(LIBMATROSKA_SRC_DIR) ebml2
	make -C $(LIBMATROSKA_SRC_DIR) matroska2
endif
	touch $@

$(COREMAKE):
	make -C $(LIBMATROSKA_SRC_DIR)/corec/tools/coremake

$(LIBMATROSKA_SRC_DIR)/configure_config_h.txt: $(LIBMATROSKA_BUILD_DIR)/config.h
	cp $(LIBMATROSKA_BUILD_DIR)/config.h $(LIBMATROSKA_SRC_DIR)
	echo "#define COREMAKE_STATIC" >> $(LIBMATROSKA_SRC_DIR)/config.h
	echo "#define COREMAKE_UNICODE" >> $(LIBMATROSKA_SRC_DIR)/config.h
	echo "#define COREMAKE_CONFIG_HELPER" >> $(LIBMATROSKA_SRC_DIR)/config.h
	echo "#define CONFIG_ANDROID_NDK $(NDK_PATH)" >> $(LIBMATROSKA_SRC_DIR)/config.h
	echo "#define CONFIG_ANDROID_VERSION $(ANDROID_MOST_RECENT_TARGET)" >> $(LIBMATROSKA_SRC_DIR)/config.h
	echo "#define CONFIG_ANDROID_PLATFORM linux-x86_64" >> $(LIBMATROSKA_SRC_DIR)/config.h
	touch $@

$(LIBMATROSKA_SRC_DIR)/fix_coremake.txt:
	cd $(LIBMATROSKA_SRC_DIR); patch -p0 < ../build/libmatroska/coremake_fix.patch
	cp $(LIBMATROSKA_BUILD_DIR)/android_x86.build $(LIBMATROSKA_SRC_DIR)/corec/tools/coremake
	touch $@

$(LIBEBML2_BUILD_DIR)/include: $(LIBMATROSKA_SRC_DIR)/libebml2/ebml $(LIBMATROSKA_SRC_DIR)/corec/corec $(LIBMATROSKA_BUILD_DIR)/config.h
	mkdir -p $@
	cp -r $(LIBMATROSKA_SRC_DIR)/libebml2/ebml $(LIBMATROSKA_SRC_DIR)/corec/corec $@
	cp $(LIBMATROSKA_BUILD_DIR)/config.h $(LIBEBML2_BUILD_DIR)/include/corec

$(LIBMATROSKA_BUILD_DIR)/include: $(LIBMATROSKA_SRC_DIR)/libmatroska2/matroska
	mkdir -p $@
	cp -r $(LIBMATROSKA_SRC_DIR)/libmatroska2/matroska $@

clean-matroska:
	rm -rf $(LIBMATROSKA_BUILD_DIR)/{arm,x86,include}
	rm -rf $(LIBEBML2_BUILD_DIR)/{arm,x86,include}
	cd $(LIBMATROSKA_SRC_DIR); $(COREMAKE) clean
	rm -rf $(LIBMATROSKA_SRC_DIR)/builded.txt

#SILK
LIBMSSILK_SRC_DIR=$(TOPDIR)/submodules/mssilk
LIBMSSILK_BUILD_DIR=$(LIBMSSILK_SRC_DIR)
$(LIBMSSILK_SRC_DIR)/configure:
	cd $(LIBMSSILK_SRC_DIR) && ./autogen.sh

$(LIBMSSILK_BUILD_DIR)/Makefile: $(LIBMSSILK_SRC_DIR)/configure
	cd $(LIBMSSILK_BUILD_DIR) && \
	$(LIBMSSILK_SRC_DIR)/configure --without-mediastreamer --host=arm-linux MEDIASTREAMER_CFLAGS=" " MEDIASTREAMER_LIBS=" "

#make sure to update this path if SILK sdk is changed
$(LIBMSSILK_BUILD_DIR)/sdk/SILK_SDK_SRC_v1.0.8/SILK_SDK_SRC_ARM_v1.0.8/src/SKP_Silk_resampler.c: $(LIBMSSILK_BUILD_DIR)/Makefile
	cd $(LIBMSSILK_BUILD_DIR)/sdk && \
	make extract-sources \
	|| ( echo "SILK audio plugin prepare state failed." ; exit 1 )

ifeq ($(BUILD_SILK), 1)
prepare-silk: $(LIBMSSILK_BUILD_DIR)/sdk/SILK_SDK_SRC_v1.0.8/SILK_SDK_SRC_ARM_v1.0.8/src/SKP_Silk_resampler.c
else
prepare-silk:
endif


#srtp
$(TOPDIR)/submodules/externals/srtp/config.h : $(TOPDIR)/submodules/externals/build/srtp/config.h
	@cd $(TOPDIR)/submodules/externals/srtp/ && \
	cp ../build/srtp/config.h . \
	|| ( echo "SRTP prepare state failed." ; exit 1 )

prepare-srtp: $(TOPDIR)/submodules/externals/srtp/config.h

#ms2
prepare-mediastreamer2:
	@cd $(TOPDIR)/submodules/linphone/mediastreamer2/src/ && \
	eval `cat Makefile.am | grep xxd | grep yuv2rgb.vs | sed 's/\$$(abs_builddir)/./'` && \
	eval `cat Makefile.am | grep xxd | grep yuv2rgb.fs | sed 's/\$$(abs_builddir)/./'` && \
	if ! [ -e yuv2rgb.vs.h ]; then echo "yuv2rgb.vs.h creation error (do you have 'xxd' application installed ?)"; exit 1; fi && \
	if ! [ -e yuv2rgb.fs.h ]; then echo "yuv2rgb.fs.h creation error (do you have 'xxd' application installed ?)"; exit 1; fi

#antlr3
ANLTR3_SRC_DIR=$(TOPDIR)/submodules/externals/antlr3/runtime/C/include/
ANTLR3_BUILD_DIR=$(ANTLR3_SRC_DIR)
$(ANLTR3_SRC_DIR)/antlr3config.h: $(TOPDIR)/submodules/externals/build/antlr3/antlr3config.h
	cp $(TOPDIR)/submodules/externals/build/antlr3/antlr3config.h $(ANLTR3_SRC_DIR)
prepare-antlr3: $(ANLTR3_SRC_DIR)/antlr3config.h

%.tokens: %.g
	$(ANTLR) -make -fo $(dir $^) $^

#Belle-sip
BELLESIP_SRC_DIR=$(TOPDIR)/submodules/belle-sip
BELLESIP_BUILD_DIR=$(BELLESIP_SRC_DIR)
prepare-belle-sip: $(BELLESIP_SRC_DIR)/src/grammars/belle_sip_message.tokens $(BELLESIP_SRC_DIR)/src/grammars/belle_sdp.tokens

#CUnit
prepare-cunit: $(TOPDIR)/submodules/externals/cunit/CUnit/Headers/*.h
	[ -d $(TOPDIR)/submodules/externals/build/cunit/CUnit ] || mkdir $(TOPDIR)/submodules/externals/build/cunit/CUnit
	cp $^ $(TOPDIR)/submodules/externals/build/cunit/CUnit

$(TOPDIR)/res/raw/rootca.pem:
	 HTTPS_CA_DIR=$(HTTPS_CA_DIR) $(TOPDIR)/submodules/linphone/scripts/mk-ca-bundle.pl $@

prepare-liblinphone_tester: $(TOPDIR)/submodules/linphone/tester/tester_hosts $(TOPDIR)/res/raw/rootca.pem $(TOPDIR)/submodules/linphone/tester/messages.db
	rm -rf liblinphone_tester/assets/config_files
	mkdir -p liblinphone_tester/assets/config_files
	for file in $^; do \
	cp -rf $$file $(TOPDIR)/liblinphone_tester/assets/config_files/. \
	;done
	cp -rf $(TOPDIR)/submodules/linphone/tester/certificates $(TOPDIR)/liblinphone_tester/assets/config_files
	cp -rf $(TOPDIR)/submodules/linphone/tester/sounds $(TOPDIR)/liblinphone_tester/assets/config_files
	cp -rf $(TOPDIR)/submodules/linphone/tester/images $(TOPDIR)/liblinphone_tester/assets/config_files
	cp -rf $(TOPDIR)/submodules/linphone/tester/rcfiles $(TOPDIR)/liblinphone_tester/assets/config_files


#SQLite3
SQLITE_SRC_DIR=$(TOPDIR)/submodules/externals/sqlite3
SQLITE_BUILD_DIR=$(SQLITE_SRC_DIR)
ifeq ($(BUILD_SQLITE), 1)
prepare-sqlite3: $(SQLITE_BUILD_DIR)/sqlite3.c
else
prepare-sqlite3:
endif

$(SQLITE_BUILD_DIR)/sqlite3.c: $(SQLITE_BASENAME).zip
	unzip -oq "$<" "*/sqlite3.?" -d  $(SQLITE_BUILD_DIR)/
	mv "$(SQLITE_BUILD_DIR)/$(SQLITE_BASENAME)/sqlite3".? $(SQLITE_BUILD_DIR)/
	rmdir "$(SQLITE_BUILD_DIR)/$(SQLITE_BASENAME)/"

$(SQLITE_BASENAME).zip:
	curl -sO $(SQLITE_URL)

#Build targets
prepare-sources: build-ffmpeg build-x264 build-openh264 prepare-ilbc build-vpx build-matroska prepare-silk prepare-srtp prepare-mediastreamer2 prepare-antlr3 prepare-belle-sip $(TOPDIR)/res/raw/rootca.pem prepare-sqlite3


GENERATE_OPTIONS = NDK_DEBUG=$(NDK_DEBUG) BUILD_FOR_X86=$(BUILD_FOR_X86) \
	BUILD_AMRNB=$(BUILD_AMRNB) BUILD_AMRWB=$(BUILD_AMRWB) BUILD_SILK=$(BUILD_SILK) BUILD_G729=$(BUILD_G729) BUILD_OPUS=$(BUILD_OPUS) \
	BUILD_VIDEO=$(BUILD_VIDEO) BUILD_X264=$(BUILD_X264) BUILD_OPENH264=$(BUILD_OPENH264) BUILD_MATROSKA=$(BUILD_MATROSKA) \
	BUILD_UPNP=$(BUILD_UPNP) BUILD_ZRTP=$(BUILD_ZRTP) BUILD_WEBRTC_AECM=$(BUILD_WEBRTC_AECM) BUILD_WEBRTC_ISAC=$(BUILD_WEBRTC_ISAC)


LIBLINPHONE_OPTIONS = $(GENERATE_OPTIONS) \
	LIBLINPHONE_VERSION=$(LIBLINPHONE_VERSION) BELLESIP_VERSION=$(BELLESIP_VERSION) USE_JAVAH=$(USE_JAVAH) \
	BUILD_TUNNEL=$(BUILD_TUNNEL) BUILD_TLS=$(BUILD_TLS) BUILD_SQLITE=$(BUILD_SQLITE) \
	BUILD_CONTACT_HEADER=$(BUILD_CONTACT_HEADER) BUILD_RTP_MAP=$(BUILD_RTP_MAP) \
	LIBLINPHONE_EXTENDED_SRC_FILES="$(LIBLINPHONE_EXTENDED_SRC_FILES)" \
	LIBLINPHONE_EXTENDED_C_INCLUDES="$(LIBLINPHONE_EXTENDED_C_INCLUDES)" \
	LIBLINPHONE_EXTENDED_CFLAGS="$(LIBLINPHONE_EXTENDED_CFLAGS)" \
	APP_STL="$(APP_STL)" \
	BUILD_DONT_CHECK_HEADERS_IN_MESSAGE=$(BUILD_DONT_CHECK_HEADERS_IN_MESSAGE)

MEDIASTREAMER2_OPTIONS = $(GENERATE_OPTIONS) BUILD_MEDIASTREAMER2_SDK=1


generate-libs: prepare-sources javah
	$(NDK_PATH)/ndk-build $(LIBLINPHONE_OPTIONS) -j$(NUMCPUS) TARGET_PLATFORM=android-14

generate-mediastreamer2-libs: prepare-sources
	@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \
	$(NDK_PATH)/ndk-build $(MEDIASTREAMER2_OPTIONS) -j$(NUMCPUS) TARGET_PLATFORM=android-14

update-project:
	$(SDK_PATH)/android update project --path . --target $(ANDROID_MOST_RECENT_TARGET)
	$(SDK_PATH)/android update project --path liblinphone_tester --target $(ANDROID_MOST_RECENT_TARGET)

update-mediastreamer2-project:
	@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \
	$(SDK_PATH)/android update project --path . --target $(ANDROID_MOST_RECENT_TARGET)

liblinphone_tester: prepare-sources prepare-cunit prepare-liblinphone_tester javah
	$(NDK_PATH)/ndk-build -C liblinphone_tester $(LIBLINPHONE_OPTIONS) -j$(NUMCPUS) TARGET_PLATFORM=android-14

javah:
	ant javah

generate-apk: generate-libs
	ant partial-clean
	echo "version.name=$(LINPHONE_ANDROID_DEBUG_VERSION)" > default.properties
	ant debug

generate-mediastreamer2-apk: generate-mediastreamer2-libs
	@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \
	ant partial-clean && \
	echo "version.name=$(LINPHONE_ANDROID_DEBUG_VERSION)" > default.properties && \
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

run-basic-tests:
	ant partial-clean
	$(MAKE) -C tests run-basic-tests

run-all-tests:
	ant partial-clean
	$(MAKE) -C tests run-all-tests

clean-ndk-build:
	$(NDK_PATH)/ndk-build clean $(LIBLINPHONE_OPTIONS)
	ant clean
	@if [ -f $(TOPDIR)/submodules/linphone/mediastreamer2/java/project.properties ]; then \
	  cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && ant clean; \
	fi

clean: clean-ndk-build
	ant clean

veryclean: clean clean-ffmpeg clean-x264 clean-openh264 clean-vpx clean-matroska

.PHONY: clean install-apk run-linphone

generate-sdk: liblinphone-android-sdk

liblinphone-android-sdk: generate-apk
	ant liblinphone-android-sdk

linphone-android-sdk: generate-apk
	ant linphone-android-sdk

mediastreamer2-sdk: update-mediastreamer2-project generate-mediastreamer2-apk
	@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \
	ant mediastreamer2-sdk
