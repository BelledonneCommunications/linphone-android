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
antlr_java_prefixes="/usr/share/java /usr/local/share/java /usr/share/java /opt/local/share/java"
antlr_jar="no"
for antlr_java_prefix in ${antlr_java_prefixes}
do
	antlr_jar=${antlr_java_prefix}/antlr.jar
	if [ ! -f ${antlr_jar} ]; then
		antlr_jar="no"	
	else
		break;
	fi
done
if test ${antlr_jar} = "no" ; then
	echo "Could not find antlr.jar. Please install antlr3";
	exit -1;
fi
ANTLR="${JAVA} -jar \"${antlr_jar}\"";

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
