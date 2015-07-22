#! /bin/bash
adb logcat -v time > $1 &
echo $! >adb.pid

