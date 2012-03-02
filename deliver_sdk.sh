#!/bin/sh

copy()
{
  todir="$2/`dirname $1`"
  echo "Copying $1 to $2/$1"
  mkdir -p $todir
  cp -r $1 $todir
}

androidize()
{
D=$1
mkdir -p $D/gen

# Add sources to eclipse .classpath
cat > $D/.classpath <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
        <classpathentry kind="src" path="src"/>
        <classpathentry kind="src" path="gen"/>
        <classpathentry kind="src" path="submodules/linphone/coreapi/help/java"/>
        <classpathentry kind="src" path="submodules/linphone/java/common"/>
        <classpathentry kind="src" path="submodules/linphone/java/j2se"/>
        <classpathentry kind="src" path="submodules/linphone/mediastreamer2/java/src"/>
        <classpathentry excluding="org/linphone/mediastream/MediastreamerActivity.java" kind="src" path="submodules/linphone/mediastreamer2/java/src"/>
        <classpathentry kind="con" path="com.android.ide.eclipse.adt.ANDROID_FRAMEWORK"/>
        <classpathentry kind="output" path="bin"/>
</classpath>
EOF

# Fix package name
(
cd $D
grep -R "org.linphone.R" . -l  | grep java | xargs sed -i 's/org\.linphone\.R/org\.linphone\.sdk\.R/g'
)

# Create a basic AndroidManifest.xml
cat > $D/AndroidManifest.xml <<EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
      package="org.linphone" android:versionCode="1" android:versionName="1.0">
    <application>
    
    
        <activity android:name="org.linphone.core.tutorials.TestVideoActivity"
                  android:label="Video test"
                  android:theme="@android:style/Theme.NoTitleBar"
                  android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
         </activity>
         <activity android:name="org.linphone.core.tutorials.TutorialHelloWorldActivity"
                  android:label="Hello World"
                  android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
         </activity>
         <activity android:name="org.linphone.core.tutorials.TutorialRegistrationActivity"
                  android:label="Registration"
                  android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
         </activity>
         <activity android:name="org.linphone.core.tutorials.TutorialBuddyStatusActivity"
                  android:label="Buddy status"
                  android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
         </activity>
         <activity android:name="org.linphone.core.tutorials.TutorialChatRoomActivity"
                  android:label="Chat Room"
                  android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
         </activity>
    </application>


    
<uses-permission android:name="android.permission.INTERNET"></uses-permission>
<uses-permission android:name="android.permission.RECORD_AUDIO"></uses-permission>
<uses-permission android:name="android.permission.READ_CONTACTS"/>
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.PROCESS_OUTGOING_CALLS"></uses-permission>
<uses-permission android:name="android.permission.CALL_PHONE"></uses-permission>
<uses-permission android:name="android.permission.BOOT_COMPLETED"></uses-permission>
<uses-permission android:name="android.permission.VIBRATE"></uses-permission>
<uses-permission android:name="android.permission.CAMERA" />

<supports-screens android:smallScreens="true" android:normalScreens="true" android:largeScreens="true" android:anyDensity="true"/>
</manifest>
EOF

cat > $D/default.properties <<EOF
split.density=false
target=android-9
EOF

mkdir -p $D/.settings
cat > $D/.settings/org.eclipse.jdt.core.prefs <<EOF
eclipse.preferences.version=1
org.eclipse.jdt.core.compiler.codegen.inlineJsrBytecode=enabled
org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.5
org.eclipse.jdt.core.compiler.codegen.unusedLocal=preserve
org.eclipse.jdt.core.compiler.compliance=1.5
org.eclipse.jdt.core.compiler.debug.lineNumber=generate
org.eclipse.jdt.core.compiler.debug.localVariable=generate
org.eclipse.jdt.core.compiler.debug.sourceFile=generate
org.eclipse.jdt.core.compiler.problem.assertIdentifier=error
org.eclipse.jdt.core.compiler.problem.enumIdentifier=error
org.eclipse.jdt.core.compiler.source=1.5
EOF

}

DBASE="liblinphone-android-sdk"
D="../$DBASE"
rm -rf $D.zip $D
copy submodules/linphone/coreapi/help/java $D
copy libs $D
copy src/org/linphone/core $D
copy submodules/linphone/java/j2se $D
copy submodules/linphone/java/common $D
copy submodules/linphone/mediastreamer2/java/src $D
copy res/layout/hello_world.xml $D
copy res/layout/videotest.xml $D

androidize $D

echo "Creating zip $D"
(
cd ..
zip -rq liblinphone-android-sdk.zip $DBASE
rm -rf $DBASE
)

DBASE="liblinphone-android-javadoc"
D="../$DBASE.zip"
echo "Generating javadoc to $D"
javadoc -quiet -d $DBASE  src/org/linphone/*.java \
src/org/linphone/ui/*.java \
src/org/linphone/core/*.java \
src/org/linphone/core/video/*.java \
src/org/linphone/core/tutorials/* \
submodules/linphone/java/common/org/linphone/core/*.java \
submodules/linphone/java/j2se/org/linphone/core/*.java \
submodules/linphone/mediastreamer2/java/src/org/linphone/mediastream/*.java \
submodules/linphone/mediastreamer2/java/src/org/linphone/mediastream/video/capture/*.java \
submodules/linphone/mediastreamer2/java/src/org/linphone/mediastream/video/display/*.java
rm -rf $D
zip -rq $D $DBASE
rm -rf $DBASE
