SDK_PATH=$(shell dirname `which android`)
SDK_PLATFORM_TOOLS_PATH=$(shell dirname `which adb`)

run-basic-tests:
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone.test
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone
	$(SDK_PATH)/android update test-project --path . -m ../
	ant debug
	ant installd
	adb shell am instrument -w -e size small org.linphone.test/android.test.InstrumentationTestRunner

run-all-tests:
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone.test
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone
	$(SDK_PATH)/android update test-project --path . -m ../     
	ant debug     
	ant installd
	adb shell am instrument -w -e size large org.linphone.test/android.test.InstrumentationTestRunner
