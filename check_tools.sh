#!/bin/sh

rm -f check_tools.mk
touch check_tools.mk

error_on_quit=0

check_installed() {
	if [ -z "$(which $1)" ]; then
		echo "Could not find $1. Please install $2."
		error_on_quit=1
		return 1
	fi
	return 0
}


check_installed "java" "it"
check_installed "ant" "it"
check_installed "yasm" "it"
check_installed "nasm" "it"
check_installed "ndk-build" "android NDK"
if check_installed "android" "android SDK"; then
	check_installed "adb" "android SDK platform tools"
	# check that at least one target is installed
	if [ -z "$(android list target -c)" ]; then
		echo "Install at least one android target in android SDK"
		error_on_quit=1
	fi
fi

if [ $error_on_quit = 0 ]; then
	echo "JAVA=\"$(which java)\"" >> check_tools.mk
	echo "ANTLR=\"$(which java)\" -jar \"submodules/externals/antlr3/antlr-3.2.jar\"" >> check_tools.mk
fi

exit $error_on_quit
