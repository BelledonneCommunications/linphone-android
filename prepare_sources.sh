#!/bin/sh

topdir=`pwd`

if [ $# -ne 1 ] 
then
	"Error : path to the ndk as parameter."
	exit 65
fi

cd submodules/externals/ffmpeg
if test -z "`git status | grep neon`" ; then
	echo "Applying patch to ffmpeg"
	cd $topdir
	patch -p0 < ${topdir}/patches/ffmpeg_scalar_product_remove_alignment_hints.patch
fi

cd $topdir/submodules/libilbc-rfc3951 && ./autogen.sh && ./configure && make || ( echo "iLBC prepare stage failed" ; exit 1 )

cd $topdir/submodules/externals/libvpx && ./configure --target=armv7-android-gcc --sdk-path=$1 --enable-error-concealment && make asm_com_offsets.asm || ( echo "VP8 prepare stage failed." ; exit 1 )

cd $topdir/submodules/mssilk && ./autogen.sh && ./configure --host=arm-linux MEDIASTREAMER_CFLAGS=" " MEDIASTREAMER_LIBS=" " && cd sdk && make extract-sources || ( echo "SILK audio plugin prepare state failed." ; exit 1 )

# As a memo, the config.h for zrtpcpp is generated using the command
# cmake  -Denable-ccrtp=false submodules/externals/libzrtpcpp
