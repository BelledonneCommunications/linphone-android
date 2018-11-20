[![pipeline status](https://gitlab.linphone.org/BC/public/linphone-android/badges/master/pipeline.svg)](https://gitlab.linphone.org/BC/public/linphone-android/commits/master)

Linphone is a free VoIP and video softphone based on the SIP protocol.

# What's new

Now the default way of building linphone-android is to download the AAR SDK in our maven repository.
Compared to previous versions, this project no longer uses submodules developper has to build in order to get a working app.
However, if you wish to use a locally compiled SDK see below how to proceed.

The repository structure has also been cleaned and updated, and changing the package name can now be done in a single step.
This allows developpers to keep a stable version as well as a developpment one on the same device easily.

## Building a local SDK

1. Clone the linphone-sdk repository from out gitlab:
```
git clone https://gitlab.linphone.org/BC/public/linphone-sdk.git --recursive
```

2. Follow the instructions in the linphone-sdk/README file to build the SDK.

3. Edit in the linphone-sdk-android folder of this project the symbolic link (debug or release) to the generated aar:
```
ln -s <path to linphone-sdk>/linphone-sdk/build/linphone-sdk/bin/outputs/aar/linphone-sdk-android-<debug or release>.aar linphone-sdk-android/linphone-sdk-android-<debug or release>.aar
```

4. Rebuild the app in Android Studio.

## Create an apk with a different package name

Before the 4.1 release, there were a lot of files to edit to change the package name.
Now, simply edit the app/build.gradle file and change the value returned by method ```getPackageName()```
The next build will automatically use this value everywhere thanks to ```manifestPlaceholders``` feature of gradle and Android.

You may have already noticed that the app installed by Android Studio has ```org.linphone.debug``` package name.
If you build the app as release, the package name will be ```org.linphone```. 

## Firebase push notifications

Now that Google Cloud Messaging has been deprecated and will be completely removed on April 11th 2019, the only official way of using push notifications is through Firebase.

However to make Firebase push notifications work, the project needs to have a file named app/google-services.json that contains some confidential informations, so you won't find it (it has been added to the .gitignore file).
This means that if you compile this project, you won't have push notification feature working in the app!

To enable them, just add your own ```google-services.json``` in the app folder, edit the ```res/values/non_localizable_custom.xml``` file set your key in ```push_sender_id```. Also ensure ```push_type``` is set to ```firebase```.

# CONTRIBUTIONS

In order to submit a patch for inclusion in linphone's source code:

1.    First make sure your patch applies to latest git sources before submitting: patches made to old versions can't and won't be merged.
2.    Fill out and send us an email with the link of pullrequest and the [Contributor Agreement](http://www.belledonne-communications.com/downloads/Belledonne_communications_CA.pdf) for your patch to be included in the git tree.

The goal of this agreement to grant us peaceful exercise of our rights on the linphone source code, while not losing your rights on your contribution.
