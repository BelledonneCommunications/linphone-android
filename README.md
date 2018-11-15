[![pipeline status](https://gitlab.linphone.org/BC/public/linphone-android/badges/master/pipeline.svg)](https://gitlab.linphone.org/BC/public/linphone-android/commits/master)

Linphone is a free VoIP and video softphone based on the SIP protocol.

# Getting Started

Now the default way of building linphone-android is to download the AAR SDK in our maven repository.
However, if you wish to use a locally compiled SDK here's how to proceed.

## Building a local SDK

1. Update the submodules of this project (if not done yet) using the following command:
```
git submodule update --recursive --init
```
After that the folder linphone-sdk will now contain all the modules required to build our SDK.

2. Follow the instructions in the linphone-sdk/README file to build the SDK.

3. Rebuild the app in Android Studio.

## Create an apk with a different package name

Before the 4.1 release, there were a lot of files to edit to change the package name.
Now, simply edit the app/build.gradle file and change the value returned by method ```getPackageName()```
The next build will automatically use this value everywhere thanks to ```manifestPlaceholders``` feature of gradle and Android.

You may have already noticed that the app installed by Android Studio has ```org.linphone.debug``` package name.
If you build the app as release, the package name will be ```org.linphone```. 
This allows developpers to keep a stable version as well as a developpment one on the same device easily.

## Firebase push notifications

Now that Google Cloud Messaging has been deprecated and will be completely removed on April 11th 2019, the only official way of using push notifications is through Firebase.
However to make Firebase push notifications work, the project needs to have a file named app/google-services.json that contains some confidential informations, so you won't find it (it has been added to the .gitignore file).
This means that if you compile this project, you won't have push notification feature working in the app!

To enable them, just add your own 'google-services.json' in the app folder, add your key at 'push_sender_id' and 'firebase' at 'push_type' in 'res/values/non_localizable_custom.xml'.

# CONTRIBUTIONS

In order to submit a patch for inclusion in linphone's source code:

1.    First make sure your patch applies to latest git sources before submitting: patches made to old versions can't and won't be merged.
2.    Fill out and send us an email with the link of pullrequest and the [Contributor Agreement](http://www.belledonne-communications.com/downloads/Belledonne_communications_CA.pdf) for your patch to be included in the git tree.
The goal of this agreement to grant us peaceful exercise of our rights on the linphone source code, while not losing your rights on your contribution.