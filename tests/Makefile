SDK_PATH=$(shell dirname `which android`)
SDK_PLATFORM_TOOLS_PATH=$(shell dirname `which adb`)
ANT_SILENT=1

ifeq ($(ANT_SILENT), 1)
	ANT=ant -e -S
else
	ANT=ant -e
endif

clean:
	killall adb
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone.test
	$(SDK_PLATFORM_TOOLS_PATH)/adb uninstall org.linphone
	$(SDK_PATH)/android update test-project --path . -m ../

all: clean
	$(ANT) debug install

run-basic-tests: all
	ant -e test -Dtest.size=small
	if [ ! -s linphone-junit-report-small.xml ]; then exit 1; fi

run-all-tests: all
	ant -e test -Dtest.size=large
	if [ ! -s linphone-junit-report-large.xml ]; then exit 1; fi
