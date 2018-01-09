Linphone is a free VoIP and video softphone based on the SIP protocol.

# COMPILATION INSTRUCTIONS

## To build liblinphone for Android, you must:

1. Download the Android sdk (API 26.0.1 at max) with platform-tools and tools updated to latest revision, then add both 'tools' and 'platform-tools' folders in your path and the android-sdk folder to ANDROID_HOME environment variable.

2. Download the Android ndk (version r11c or 15) from google and add it to your path (no symlink !!!) and ANDROID_NDK environment variable.

3. Install _yasm_, _nasm_ (For OpenH224 support only), _python_, _pkg_config_ and _cmake(>=3.7)_.
  * On 64 bits linux systems you'll need the _ia32-libs_ package.
  * With the latest Debian (multiarch), you need this:
    * `dpkg --add-architecture i386`
    * `aptitude update`
    * `aptitude install libstdc++6:i386 libgcc1:i386 zlib1g:i386 libncurses5:i386`

4. Run `./prepare.py` in the top level directory. This will configure the build and generate a Makefile in the top level directory. Some options can be passed to choose what you want to include in the build and the platforms for which you want to build. Use `./prepare.py --help` to see what these options are.

5. Run the Makefile script in the top level directory, `make`.

6. _(optional)_ To install the generated apk into a plugged device, run	`make install`.

7. _(optional)_ To generate a liblinphone SDK zip containing a full jar and native libraries, run `make liblinphone-android-sdk`

8. _(optional)_ To generate a libmediastreamer2 SDK zip containing a full jar and native libraries, run `make mediastreamer2-sdk`

9. _(optional)_ To generate a signed apk to publish on the Google Play, run `make release`. Make sure you filled the gradle.properties values for version.name, store file, store password, key alias and key password to correctly sign the generated apk:
  * RELEASE_STORE_FILE=""
  * RELEASE_STORE_PASSWORD=
  * RELEASE_KEY_ALIAS=
  * RELEASE_KEY_PASSWORD=

  If you don't, the passwords will be asked at the signing phase.

10. _(optional)_ Once you compiled the libraries succesfully with 'make', you can reduce the compilation time using 'make quick': it will only generate a new APK from java files.

## To run the tutorials:

1. Open the _res/values/non_localizable_custom.xml_ file and change the *show_tutorials_instead_of_app* to true.

2. Compile again using `make` and `make install`.

3. **Don't forget to put it back to false to run the linphone application normally.**

## To create an apk with a different package name

You need to edit the build.gradle file:

1. look for the function named "getPackageName()" and change it value accordingly
2. also update the values in the AndroidManifest file where the comment <!-- Change package ! --> appears
3. change the package name also in the files: res/xml/syncadapter.xml, res/xml/contacts.xml and res/values/non_localizable_custom where <!-- Change package ! --> appears
4. run again the Makefile script by calling "make"

## To run the liblinphone test suite on android

Simply run `make liblinphone_tester`. This will be build everything, generate an apk, and install it on the connected device if any.

You can speed up the compilation by using ccache (compiler cache, see [ccache.samba.org](https://ccache.samba.org/)). Give the *"-DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache"* options to the *prepare.py* script.

# PUSH NOTIFICATION

## Firebase

To enable firebase in Linphone, just add your 'google-service.json' in project root, add your key at 'push_sender_id' and add 'firebase' at 'push_type' in 'res/values/non_localizable_custom.xml'
Be sure to have all services for Firebase in your 'AndroidManifest.xml'

## Google

To enable google push in Linphone, remove 'google-service.json' file if it exist, add your key at 'push_sender_id' and add 'google' at 'push_type' in 'res/values/non_localizable_custom.xml'
Be sure to have every permissions and services for GCM in your 'AndroidManifest.xml'

# TROUBLESHOOTING

If you encounter the following issue:

```
E/dalvikvm( 2465): dlopen("/data/app-lib/org.linphone-1/liblinphone-armeabi-v7a.so") failed:
Cannot load library: soinfo_relocate(linker.cpp:975): cannot locate symbol "rand" referenced
by "liblinphone-armeabi-v7a.so"
```

It's because you have installed the android-21 platform (which is chosen automatically because it's the most recent) and you deployed the apk on a android < 5 device.

To fix this, in the Makefile, force *ANDROID_MOST_RECENT_TARGET=android-19*.

If you encounter troubles with the make clean target and you are using the 8e android ndk, the solution can be found [here](https://groups.google.com/forum/?fromgroups=#!topic/android-ndk/3wIbb-h3nDU).

If you built the app using eclipse, ensure you ran at least once the make command (see above steps 0 to 3) ! Else you'll have this exceptions:

```
FATAL EXCEPTION: main
java.lang.ExceptionInInitializerError
...
Caused by: java.lang.UnsatisfiedLinkError: Couldn't load linphone-armeabi-v7a: findLibrary
returned null
```

# BUILD OPTIONS

The build options are to be passed to the *prepare.py* script. For example to enable the x264 encoder give the *"-DENABLE_X264=YES"* to *prepare.py*.

The available options can be listed with the `./prepare.py --list-features`

# CONTRIBUTIONS

In order to submit a patch for inclusion in linphone's source code:

1.    First make sure your patch applies to latest git sources before submitting: patches made to old versions can't be merged.
2.    Fill out and send us an email with the link of pullrequest and the [Contributor Agreement](http://www.belledonne-communications.com/downloads/Belledonne_communications_CA.pdf) for your patch to be included in the git tree. The goal of this agreement to grant us peaceful exercise of our rights on the linphone source code, while not losing your rights on your contribution.

