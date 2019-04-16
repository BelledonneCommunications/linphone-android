[![pipeline status](https://gitlab.linphone.org/BC/public/linphone-sdk/badges/master/pipeline.svg)](https://gitlab.linphone.org/BC/public/linphone-android/commits/master)

Linphone is a free VoIP and video softphone based on the SIP protocol.

# What's new

Now the default way of building linphone-android is to download the AAR SDK in our maven repository.
Compared to previous versions, this project no longer uses submodules developper has to build in order to get a working app.
However, if you wish to use a locally compiled SDK see below how to proceed.

We offer different flavors for the SDK in our maven repository: org.linphone.no-video (a build without video) and org.linphone.legacy (old java wrapper if you didn't migrate your app code to the new one yet).

The repository structure has also been cleaned and updated, and changing the package name can now be done in a single step.
This allows developpers to keep a stable version as well as a developpment one on the same device easily.

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

## Building a local SDK

1. Clone the linphone-sdk repository from out gitlab:
```
git clone https://gitlab.linphone.org/BC/public/linphone-sdk.git --recursive
```

2. Follow the instructions in the linphone-sdk/README file to build the SDK.

3. Edit in the linphone-sdk-android folder of this project the symbolic link (debug and/or release) to the generated AAR.
We recommend to at least create the link for the release AAR that can be used for debug APK flavor because it is smaller and will reduce the time required to install the APK.
```
ln -s <path to linphone-sdk>/linphone-sdk/build/linphone-sdk/bin/outputs/aar/linphone-sdk-android-release.aar linphone-sdk-android/linphone-sdk-android-release.aar
ln -s <path to linphone-sdk>/linphone-sdk/build/linphone-sdk/bin/outputs/aar/linphone-sdk-android-debug.aar linphone-sdk-android/linphone-sdk-android-debug.aar
```

4. Rebuild the app in Android Studio.

## Native debugging

1. Install LLDB from SDK Tools in Android-studio.

2. In Android-studio go to Run->Edit Configurations->Debugger.

3. Select 'Dual' or 'Native' and add the path to linphone-sdk libraries.

4. Open native file and put your breakpoint on it.

5. Make sure you are using the debug AAR in the app/build.gradle script and not the release one (to have faster builds by default the release AAR is used even for debug APK flavor).

6. Debug app.

## Create an APK with a different package name

Before the 4.1 release, there were a lot of files to edit to change the package name.
Now, simply edit the app/build.gradle file and change the value returned by method ```getPackageName()```
The next build will automatically use this value everywhere thanks to ```manifestPlaceholders``` feature of gradle and Android.

You may have already noticed that the app installed by Android Studio has ```org.linphone.debug``` package name.
If you build the app as release, the package name will be ```org.linphone```. 

## Firebase push notifications

Now that Google Cloud Messaging has been deprecated and will be completely removed on April 11th 2019, the only official way of using push notifications is through Firebase.

However to make Firebase push notifications work, the project needs to have a file named app/google-services.json that contains some confidential informations, so you won't find it (it has been added to the .gitignore file).
This means that if you compile this project, you won't have push notification feature working in the app!

To enable them, just add your own ```google-services.json``` in the app folder.

## Translations

We use transifex so the community can translate the strings of the app in their own language.

Note for developpers: here's how to push/pull string resources to/from transifex:
```
tx pull -af
```
to update local translations with latest transifex changes
```
tx push -s -f --no-interactive
```
to push new strings to transifex so they can be translated.

# CONTRIBUTIONS

In order to submit a patch for inclusion in linphone's source code:

1.    First make sure your patch applies to latest git sources before submitting: patches made to old versions can't and won't be merged.
2.    Fill out and send us an email with the link of pullrequest and the [Contributor Agreement](http://www.belledonne-communications.com/downloads/Belledonne_communications_CA.pdf) for your patch to be included in the git tree.

The goal of this agreement to grant us peaceful exercise of our rights on the linphone source code, while not losing your rights on your contribution.
