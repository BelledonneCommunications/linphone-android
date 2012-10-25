NDK_PATH=$(shell dirname `which ndk-build`)
SDK_PATH=$(shell dirname `which android`)
NUMCPUS=$(shell grep -c '^processor' /proc/cpuinfo || echo "4" )
TOPDIR=$(shell pwd)
PATCH_FFMPEG=$(shell cd submodules/externals/ffmpeg && git status | grep neon)
LINPHONE_VERSION=$(shell grep -e '^.C_INIT' submodules/linphone/configure.ac | sed -e 's/.*linphone]\,\[//' |sed -e 's/\].*//' )
KEYSTORE=bc-android.keystore
KEYALIAS=nw8000

all: prepare-sources generate-libs generate-apk install-apk run-linphone

prepare-ffmpeg:
ifeq ($(PATCH_FFMPEG),)
	@patch -p0 < $(TOPDIR)/patches/ffmpeg_scalar_product_remove_alignment_hints.patch
endif

prepare-ilbc:
	@cd $(TOPDIR)/submodules/libilbc-rfc3951 && \
	./autogen.sh && \
	./configure && make \
	|| ( echo "iLBC prepare stage failed" ; exit 1 )

prepare-vpx:
	@cd $(TOPDIR)/submodules/externals/libvpx && \
	./configure --target=armv7-android-gcc --sdk-path=$(NDK_PATH) --enable-error-concealment && \
	make clean && \
	make asm_com_offsets.asm \
	|| ( echo "VP8 prepare stage failed." ; exit 1 )

prepare-silk:
	@cd $(TOPDIR)/submodules/mssilk && \
	./autogen.sh && \
	./configure --host=arm-linux MEDIASTREAMER_CFLAGS=" " MEDIASTREAMER_LIBS=" " && \
	cd sdk && make extract-sources \
	|| ( echo "SILK audio plugin prepare state failed." ; exit 1 )

prepare-srtp:
	@cd $(TOPDIR)/submodules/externals/srtp/ && \
	cp ../build/srtp/config.h . \
	|| ( echo "SRTP prepare state failed." ; exit 1 )

prepare-mediastreamer2:
	@cd $(TOPDIR)/submodules/linphone/mediastreamer2/src/ && \
	eval `cat Makefile.am | grep xxd | grep yuv2rgb.vs | sed 's/$$$$builddir/./'` && \
	eval `cat Makefile.am | grep xxd | grep yuv2rgb.fs | sed 's/$$$$builddir/./'` && \
	if ! [ -e yuv2rgb.vs.h ]; then echo "yuv2rgb.vs.h creation error (do you have 'xxd' application installed ?)"; exit 1; fi && \
	if ! [ -e yuv2rgb.fs.h ]; then echo "yuv2rgb.fs.h creation error (do you have 'xxd' application installed ?)"; exit 1; fi

prepare-sources: prepare-ffmpeg prepare-ilbc prepare-vpx prepare-silk prepare-srtp prepare-mediastreamer2

generate-libs: 
	$(NDK_PATH)/ndk-build LINPHONE_VERSION=$(LINPHONE_VERSION) BUILD_SILK=1 BUILD_AMRNB=full -j$(NUMCPUS)

update-project:
	$(SDK_PATH)/android update project --path .
	echo "key.store=$(KEYSTORE)" > ant.properties
	echo "key.alias=$(KEYALIAS)" >> ant.properties
	touch default.properties

generate-apk:
	ant debug

install-apk: generate-apk
	ant installd

release: update-project
	ant release

run-linphone:
	ant run

clean:
	$(NDK_PATH)/ndk-build clean
	ant clean

.PHONY: clean

