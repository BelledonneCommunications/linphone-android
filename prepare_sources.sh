#!/bin/sh

topdir=`pwd`

cd submodules/libilbc-rfc3951 && ./autogen.sh && ./configure && make || ( echo "iLBC prepare stage failed" ; exit 1 )

cd $topdir/submodules/externals/build/libvpx && ./asm_conversion.sh && cp *.asm *.h ../../libvpx/ || ( echo "VP8 prepare stage failed." ; exit 1 )


