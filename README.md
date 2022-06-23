
[![pipeline status](https://gitlab.linphone.org/BC/public/linphone-android/badges/master/pipeline.svg)](https://gitlab.linphone.org/BC/public/linphone-android/commits/master) [![weblate status](https://weblate.linphone.org/widgets/linphone/-/linphone-android/svg-badge.svg)](https://weblate.linphone.org/engage/linphone/?utm_source=widget)

Linphone is an open source softphone for voice and video over IP calling and instant messaging.

It is fully SIP-based, for all calling, presence and IM features.

General description is available from [linphone web site](https://www.linphone.org/technical-corner/linphone).

### How to get it

[<img src="metadata/google-play-badge.png" height="60" alt="Get it on Google Play">](https://play.google.com/store/apps/details?id=org.linphone)[<img src="metadata/f-droid-badge.png" height="60" alt="Get it on F-Droid">](https://f-droid.org/en/packages/org.linphone/)

You can also download APKs signed with our key from [our website](https://download.linphone.org/releases/android/?C=M;O=D).

### License

Copyright © Belledonne Communications

Linphone is dual licensed, and is available either :

 - under a [GNU/GPLv3 license](https://www.gnu.org/licenses/gpl-3.0.en.html), for free (open source). Please make sure that you understand and agree with the terms  of this license before using it (see LICENSE file for details).

 - under a proprietary license, for a fee, to be used in closed source applications. Contact [Belledonne Communications](https://www.linphone.org/contact) for any question about costs and services.

### Documentation

- Supported features and RFCs : https://www.linphone.org/technical-corner/linphone/features

- Linphone public wiki : https://wiki.linphone.org/xwiki/wiki/public/view/Linphone/

- Tutorials : https://gitlab.linphone.org/BC/public/tutorials/-/tree/master/android/kotlin

# What's new

App has been totally rewritten in Kotlin using modern components such as Navigation, Data Binding, View Models, coroutines, etc...  
Check the [CHANGELOG](./CHANGELOG.md) file for a more detailed list.
The first linphone-android release that will be based on this will be 4.5.0, using 5.0.0 SDK.

We're also taking a fresh start regarding translations so less languages will be available for a while.  
If you want to contribute, you are welcome to do so, check the [Translations](#Translations) section below.

org.linphone.legacy flavor (old java wrapper if you didn't migrate your app code to the new one yet) is no longer supported starting 5.0.0 SDK.

The sample project has been removed, we now recommend you to take a look at our [tutorials](https://gitlab.linphone.org/BC/public/tutorials/-/tree/master/android/kotlin).

# Building the app

If you have Android Studio, simply open the project, wait for the gradle synchronization and then build/install the app.  
It will download the linphone library from our Maven repository as an AAR file so you don't have to build anything yourself.

If you don't have Android Studio, you can build and install the app using gradle:
```
./gradlew assembleDebug
```
will compile the APK file (assembleRelease to instead if you want to build a release package), and then
```
./gradlew installDebug
```
to install the generated APK in the previous step (use installRelease instead if you built a release package).

APK files are stored within ```./app/build/outputs/apk/debug/``` and ```./app/build/outputs/apk/release/``` directories.

When building a release AppBundle, use releaseAppBundle target instead of release.   
Also make sure you have a NDK installed and that you have an environment variable named ```ANDROID_NDK_HOME``` that contains the path to the NDK.  
This is to be able to include native libraries symbols into app bundle for the Play Store.

## Building a local SDK

1. Clone the linphone-sdk repository from out gitlab:
```
git clone https://gitlab.linphone.org/BC/public/linphone-sdk.git --recursive
```

2. Follow the instructions in the linphone-sdk/README file to build the SDK.

3. Create or edit the gradle.properties file in $GRADLE_USER_HOME (usually ~/.gradle/) and add the absolute path to your linphone-sdk build directory, for example:
```
LinphoneSdkBuildDir=/home/<username>/linphone-sdk/build/
```

4. Rebuild the app in Android Studio.

## Native debugging

1. Install LLDB from SDK Tools in Android-studio.

2. In Android-studio go to Run->Edit Configurations->Debugger.

3. Select 'Dual' or 'Native' and add the path to linphone-sdk debug libraries (build/libs-debug/ for example).

4. Open native file and put your breakpoint on it.

5. Make sure you are using the debug AAR in the app/build.gradle script and not the release one (to have faster builds by default the release AAR is used even for debug APK flavor).

6. Debug app.

## Known issues

- If you encounter the `couldn't find "libc++_shared.so"` crash when the app starts, simply clean the project in Android Studio (under Build menu) and build again.
Also check you have built the SDK for the right CPU architecture using the `-DLINPHONESDK_ANDROID_ARCHS=armv7,arm64,x86,x86_64` cmake parameter.

- Push notification might not work when app has been started by Android Studio consecutively to an install. Remove the app from the recent activity view and start it again using the launcher icon to resolve this.

## Troubleshooting

### Behavior issue

When submitting an issue on our [Github repository](https://github.com/BelledonneCommunications/linphone-android), please follow the template and attach the matching library logs:

1. To enable them, go to Settings -> Advanced and toggle `Debug Mode`. If they are already enabled, clear them first using the `Reset logs` button on the About page.

2. Then restart the app, reproduce the issue and upload the logs using the `Send logs` button on the advanced settings page.

3. Finally paste the link to the uploaded logs (link is already in the clipboard after a successful upload).

### Native crash

First of all, to be able to get a symbolized stack trace, you need the debug version of our libraries.

If you haven't built the SDK locally (see [building a local SDK](#BuildingalocalSDK)), here's how to get them:

1. Go to our [maven repository](https://download.linphone.org/maven_repository/org/linphone/linphone-sdk-android-debug/), in the linphone-android-debug directory.

2. Download the AAR file with **the exact same version** as the AAR that was used to generate the crash's stacktrace.

3. Extract the AAR somewhere on your computer (it's a simple ZIP file even it's doesn't have the extension). Libraries are stored inside the ```jni``` folder (a directory for each architectured built, usually ```arm64-v8a, armeabi-v7a, x86_64 and x86```).

4. To get consistent with locally built SDK, rename the ```jni``` directory into ```libs-debug```.

Now you need the ```ndk-stack``` tool and possibly ```adb logcat```.

If your computer isn't used for Android development, you can download those tools from [Google website](https://developer.android.com/studio#downloads), in the ```Command line tools only``` section.

Once you have the debug libraries and the proper tools installed, you can use the ```ndk-stack``` tool to symbolize your stacktrace. Note that you also need to know the architecture (armv7, arm64, x86, etc...) of the libraries that were used.

If you know the CPU architecture of your device (most probably arm64 if it's a recent device) you can use the following to get the stacktrace from a device plugged to a computer:
```
adb logcat -d | ndk-stack -sym ./libs-debug/arm64-v8a/
```
If you don't know the CPU architecture, use the following instead:
```
adb logcat -d | ndk-stack -sym ./libs-debug/`adb shell getprop ro.product.cpu.abi | tr -d '\r'` 
```
Warning: This command won't print anything until you reproduce the crash!

## Create an APK with a different package name

Before the 4.1 release, there were a lot of files to edit to change the package name.  
Now, simply edit the app/build.gradle file and change the value returned by method ```getPackageName()```  
The next build will automatically use this value everywhere thanks to ```manifestPlaceholders``` feature of gradle and Android.

You may have already noticed that the app installed by Android Studio has ```org.linphone.debug``` package name.  
If you build the app as release, the package name will be ```org.linphone```.

## Firebase push notifications

Now that Google Cloud Messaging has been deprecated and will be completely removed on April 11th 2019, the only official way of using push notifications is through Firebase.

However to make Firebase push notifications work, the project needs to have a ```app/google-services.json``` file that contains the configuration.  
We have archived our own, so you can build your linphone-android application and still receive push notifications from our free SIP service (sip.linphone.org).
If you delete it, you won't receive any push notification.

If you have your own push server, replace this file by yours.

## Translations

We no longer use transifex for the translation process, instead we have deployed our own instance of [Weblate](https://weblate.linphone.org/).

Due to the full app rewrite we can't re-use previous translations, so we'll be very happy if you want to contribute.

# CONTRIBUTIONS

In order to submit a patch for inclusion in linphone's source code:

1. First make sure your patch applies to latest git sources before submitting: patches made to old versions can't and won't be merged.
2. Fill out and send us an email with the link of pull-request and the [Contributor Agreement](https://linphone.org/sites/default/files/bc-contributor-agreement_0.pdf) for your patch to be included in the git tree.

The goal of this agreement to grant us peaceful exercise of our rights on the linphone source code, while not losing your rights on your contribution.
