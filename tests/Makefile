SDK_PATH=$(shell dirname `which android`)
SDK_PLATFORM_TOOLS_PATH=$(shell dirname `which adb`)

clean:
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone.test
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone
	$(SDK_PATH)/android update test-project --path . -m ../

all: clean
	ant debug install

run-basic-tests: all
	ant test
	adb shell run-as org.linphone cat /data/data/org.linphone/files/junit-report.xml 2>/dev/null > linphone-junit-report.xml

run-all-tests: all
	adb shell am instrument -w -e size large org.linphone.test/com.zutubi.android.junitreport.JUnitReportTestRunner
	adb shell run-as org.linphone cat /data/data/org.linphone/files/junit-report.xml 2>/dev/null > linphone-junit-report.xml
