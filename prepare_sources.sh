#!/bin/sh

topdir=`pwd`

cd submodules/externals/ffmpeg
if test -z "`git status | grep neon`" ; then
	echo "Applying patch to ffmpeg"
	cd $topdir
	patch -p0 < ${topdir}/patches/ffmpeg_scalar_product_remove_alignment_hints.patch
fi
cd $topdir

cd submodules/libilbc-rfc3951 && ./autogen.sh && ./configure && make || ( echo "iLBC prepare stage failed" ; exit 1 )

cd $topdir/submodules/externals/build/libvpx && ./asm_conversion.sh && cp *.asm *.h ../../libvpx/ || ( echo "VP8 prepare stage failed." ; exit 1 )


