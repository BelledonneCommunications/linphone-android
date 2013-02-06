#!/bin/bash

rm -f check_tools.mk
touch check_tools.mk

# Check java
JAVA=\"$(which java)\"
if [ -z ${JAVA} ]; then 
	echo "Could not find java. Please install java";
	exit -1;
fi

# Check antlr
ANTLR="${JAVA} -jar \"submodules/externals/antlr3/antlr-3.4-complete.jar\"";

# Check NDK
NDK=$(which ndk-build)
if [ -z ${NDK} ]; then 
	echo "Could not find ndk-build. Please install android ndk";
	exit -1;
fi

# Check SDK
SDK=$(which android)
if [ -z ${SDK} ]; then 
	echo "Could not find android. Please install android sdk";
	exit -1;
fi

SDK_PLATFORM_TOOLS=$(which adb)
if [ -z ${SDK_PLATFORM_TOOLS} ]; then 
	echo "Could not find adb. Please install android sdk platform tools";
	exit -1;
fi

echo JAVA=${JAVA} >> check_tools.mk
echo ANTLR=${ANTLR} >> check_tools.mk
