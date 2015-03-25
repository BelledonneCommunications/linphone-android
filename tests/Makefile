SDK_PATH=$(shell dirname `which android`)
SDK_PLATFORM_TOOLS_PATH=$(shell dirname `which adb`)
ANT_SILENT=1

ifeq ($(ANT_SILENT), 1)
	ANT=ant -e -S
else
	ANT=ant -e
endif

# Ensure that adb is killed before running anything. This is needed in case
# the previous execution crashed (while adb still running) because adb will not be able to list atached devices
# until any adb is running
clean:
	killall adb 2>/dev/null || true
	$(SDK_PLATFORM_TOOLS_PATH)/adb start-server
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
