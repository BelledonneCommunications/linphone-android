#!/bin/sh

rm ../liblinphone-android-sdk.zip
zip -r ../liblinphone-android-sdk.zip submodules/linphone/coreapi/help/java libs src/org/linphone/core submodules/linphone/java/j2se/ submodules/linphone/java/common res/layout/hello_world.xml res/layout/videotest.xml src/org/linphone/core/ src/org/linphone/Hacks.java

javadoc -d liblinphone-android-javadoc "org.linphone" src/org/linphone/*.java src/org/linphone/core/*.java src/org/linphone/core/tutorials/* src/org/linphone/component/* submodules/linphone/java/common/org/linphone/core/*.java submodules/linphone/java/j2se/org/linphone/core/*.java
rm ../liblinphone-android-javadoc.zip
zip -r ../liblinphone-android-javadoc.zip liblinphone-android-javadoc
rm -rf liblinphone-android-javadoc
