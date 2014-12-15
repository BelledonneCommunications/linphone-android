all: 
	ant debug
	ant installd

run-all-tests: all
	adb shell am instrument -w org.linphone.tester/org.linphone.tester.TestRunner
	adb shell run-as org.linphone.tester cat /data/data/org.linphone.tester/files/junit-report.xml > liblinphone-junit-report.xml
