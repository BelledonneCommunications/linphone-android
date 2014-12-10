all: 
	ant debug
	ant installd

run-all-tests: all
	ant test
	adb shell run-as org.linphone.tester cat /data/data/org.linphone.tester/files/junit-report.xml > liblinphone-junit-report.xml
