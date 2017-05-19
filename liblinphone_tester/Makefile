all: copy-files
	../gradlew clean
	../gradlew uninstallDebug

run-all-tests: copy-files
	../gradlew :installDebugAndroidTest && \
	../gradlew :installDebug && \
	../gradlew :grantDebugPermissions -i && \
	../gradlew :connectedAndroidTest -i \

copy-libs:
	mkdir -p assets && \
	mkdir -p assets/config_files && \
	rm -rf libs/arm64-v8a
	if test -d "../liblinphone-sdk/android-arm64"; then \
		mkdir -p libs/arm64-v8a && \
		cp -f ../liblinphone-sdk/android-arm64/lib/*.so libs/arm64-v8a && \
		cp -f ../liblinphone-sdk/android-arm64/lib/mediastreamer/plugins/*.so libs/arm64-v8a && \
		cp -f ../liblinphone-sdk/android-arm64/share/linphone/rootca.pem assets/config_files; \
	fi
	if test -f "../liblinphone-sdk/android-arm64/bin/gdbserver"; then \
		cp -f ../liblinphone-sdk/android-arm64/bin/gdbserver libs/arm64-v8a && \
		cp -f ../liblinphone-sdk/android-arm64/bin/gdb.setup libs/arm64-v8a; \
	fi
	rm -rf libs/armeabi-v7a
	if test -d "../liblinphone-sdk/android-armv7"; then \
		mkdir -p libs/armeabi-v7a && \
		cp -f ../liblinphone-sdk/android-armv7/lib/*.so libs/armeabi-v7a && \
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
		cp -f ../liblinphone-sdk/android-x86/lib/*.so libs/x86 && \
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
