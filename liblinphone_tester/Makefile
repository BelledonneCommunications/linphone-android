# Ensure that adb is killed before running anything. This is needed in case
# the previous execution crashed (while adb still running) because adb will not be able to list atached devices
# until any adb is running
clean:
	killall adb 2>/dev/null || true
	adb start-server

all: clean
	ant debug install

run-all-tests: all
	ant test
