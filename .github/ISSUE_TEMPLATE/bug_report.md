---
name: Bug report
about: How to create a proper bug report
title: ''
labels: ''
assignees: ''

---

First of all, please say "Hi" or "Hello", it doesn't cost much.


1. **Describe the bug** (mandatory)

A clear and concise description of what the bug is.

Also, if applicable, **can you reproduce it with linphone-android latest release from the Play Store?**

**If the issue is about the SDK (build, issue, etc...) open the ticket in the [linphone-sdk](https://github.com/BelledonneCommunications/linphone-sdk) repository or one of its submodules!**

2. **To Reproduce** (mandatory)

Please detail steps to reproduce the behavior.

3. **Expected behavior** (mandatory)

A clear and concise description of what you expected to happen.

4. **Please complete the following information** (mandatory)

 - Device: [e.g. Samsung Note 20 Ultra]
 - OS: [e.g. Android 12]
 - Version of the App: [e.g. 4.6.11]
 - Version of the SDK: [e.g 5.1.48]
 - Where you did got it from: (Play Store, F-Droid, local build)
 - Please tell us if your Android is a Lineage OS or another variant.

If you are using a SDK that isn't the latest release, please update first as it's likely your issue is already solved.

5. **SDK logs** (mandatory)

Click on "Share logs" in Help -> Troubleshooting view and copy/paste the link here.

It's also explained [in the README](https://github.com/BelledonneCommunications/linphone-android#behavior-issue).

In case of a call issue, please attach logs from both devices!

6. **Adb logcat logs** (mandatory if native crash)

In case of a crash of the app, please also provide the symbolized stack trace of the crash using adb logcat.

Here's the command for a arm64 device: `adb logcat | grep ndk-stack -sym <sdk build directory>/libs-debug/arm64-v8a/`

For more information, please refer to [this section of the README](https://github.com/BelledonneCommunications/linphone-android#native-crash) file.

7. **Screenshots** (optional)

If applicable, add screenshots to help explain your problem.

8. **Additional context** (optional)

Add any other context about the problem here.


Thank you in advance for filling bug reports properly!
