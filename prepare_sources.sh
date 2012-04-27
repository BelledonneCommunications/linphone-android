#!/bin/sh

topdir=`pwd`

if test -z "$1" ; then
	ndk_build_path=`which ndk-build`
	if test "$?" != "0" ; then
		echo "ndk-build not found in path. Please specify path to android NDK as first argument of $0."
		exit 127
	fi
	NDK_PATH=`dirname $ndk_build_path`
	if test -z "NDK_PATH" ; then
		echo "Path to Android NDK not set, please specify it as first argument of $0."
		exit 127
	fi
else
	NDK_PATH=$1
fi

echo "using $NDK_PATH as android NDK"

cd submodules/externals/ffmpeg
if test -z "`git status | grep neon`" ; then
	echo "Applying patch to ffmpeg"
	cd $topdir
	patch -p0 < ${topdir}/patches/ffmpeg_scalar_product_remove_alignment_hints.patch
fi

cd $topdir/submodules/libilbc-rfc3951 && ./autogen.sh && ./configure && make || ( echo "iLBC prepare stage failed" ; exit 1 )

cd $topdir/submodules/externals/libvpx && ./configure --target=armv7-android-gcc --sdk-path=$NDK_PATH --enable-error-concealment && make clean && make asm_com_offsets.asm || ( echo "VP8 prepare stage failed." ; exit 1 )

cd $topdir/submodules/mssilk && ./autogen.sh && ./configure --host=arm-linux MEDIASTREAMER_CFLAGS=" " MEDIASTREAMER_LIBS=" " && cd sdk && make extract-sources || ( echo "SILK audio plugin prepare state failed." ; exit 1 )

cd $topdir/submodules/externals/srtp/ && cp ../build/srtp/config.h . || ( echo "SRTP prepare state failed." ; exit 1 )

cd $topdir/submodules/linphone/mediastreamer2/src/
# extract rules to build shader files
vs_rule=`cat Makefile.am | grep xxd | grep yuv2rgb.vs | sed 's/$$builddir/./'`
fs_rule=`cat Makefile.am | grep xxd | grep yuv2rgb.fs | sed 's/$$builddir/./'`
eval $vs_rule
# verify vs file creation
if ! [ -e yuv2rgb.vs.h ]
then
	echo "yuv2rgb.vs.h creation error (do you have 'xxd' application installed ?)"; cd -; exit 1
fi
eval $fs_rule
# verify fs file creation
if ! [ -e yuv2rgb.fs.h ]
then
	echo "yuv2rgb.fs.h creation error (do you have 'xxd' application installed ?)"; cd -; exit 1
fi
cd $topdir

# As a memo, the config.h for zrtpcpp is generated using the command
# cmake  -Denable-ccrtp=false submodules/externals/libzrtpcpp

