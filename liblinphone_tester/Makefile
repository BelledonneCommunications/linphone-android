ANDROID_MOST_RECENT_TARGET=$(shell android list target -c | grep -E 'android-[0-9]+' | tail -n1)

all: update-project copy-files
	ant debug install

run-all-tests: all
	ant test

update-project:
	android update project --path . --target $(ANDROID_MOST_RECENT_TARGET)

copy-libs:
	rm -rf libs/armeabi
	mkdir -p assets/config_files
	if test -d "../liblinphone-sdk/android-arm"; then \
		mkdir -p libs/armeabi && \
		cp -f ../liblinphone-sdk/android-arm/lib/lib*-armeabi.so libs/armeabi && \
		cp -f ../liblinphone-sdk/android-arm/lib/mediastreamer/plugins/*.so libs/armeabi && \
		cp -f ../liblinphone-sdk/android-arm/share/linphone/rootca.pem assets/config_files; \
	fi
	if test -f "../liblinphone-sdk/android-arm/bin/gdbserver"; then \
		cp -f ../liblinphone-sdk/android-arm/bin/gdbserver libs/armeabi && \
		cp -f ../liblinphone-sdk/android-arm/bin/gdb.setup libs/armeabi; \
	fi
	rm -rf libs/armeabi-v7a
	if test -d "../liblinphone-sdk/android-armv7"; then \
		mkdir -p libs/armeabi-v7a && \
		cp -f ../liblinphone-sdk/android-armv7/lib/lib*-armeabi-v7a.so libs/armeabi-v7a && \
		cp -f ../liblinphone-sdk/android-armv7/lib/mediastreamer/plugins/*.so libs/armeabi-v7a && \
		cp -f ../liblinphone-sdk/android-armv7/share/linphone/rootca.pem assets/config_files; \
	fi
	if test -f "../liblinphone-sdk/android-armv7/bin/gdbserver"; then \
		cp -f ../liblinphone-sdk/android-armv7/bin/gdbserver libs/armeabi-v7a && \
		cp -f ../liblinphone-sdk/android-armv7/bin/gdb.setup libs/armeabi-v7a; \
	fi
	rm -rf libs/x86
	if test -d "../liblinphone-sdk/android-x86"; then \
		mkdir -p libs/x86 && \
		cp -f ../liblinphone-sdk/android-x86/lib/lib*-x86.so libs/x86 && \
		cp -f ../liblinphone-sdk/android-x86/lib/mediastreamer/plugins/*.so libs/x86 && \
		cp -f ../liblinphone-sdk/android-x86/share/linphone/rootca.pem assets/config_files; \
	fi
	if test -f "../liblinphone-sdk/android-x86/bin/gdbserver"; then \
		cp -f ../liblinphone-sdk/android-x86/bin/gdbserver libs/x86 && \
		cp -f ../liblinphone-sdk/android-x86/bin/gdb.setup libs/x86; \
	fi

copy-files: ../submodules/linphone/tester/tester_hosts ../submodules/linphone/tester/messages.db
	$(MAKE) copy-libs
	rm -rf assets/config_files
	mkdir -p assets/config_files
	for file in $^; do \
		cp -rf $$file assets/config_files/. ; \
	done
	cp -rf ../submodules/linphone/tester/certificates assets/config_files
	cp -rf ../submodules/linphone/tester/images assets/config_files
	cp -rf ../submodules/linphone/tester/rcfiles assets/config_files
	cp -rf ../submodules/linphone/tester/sounds assets/config_files
	cp -rf ../submodules/linphone/tester/vcards assets/config_files
