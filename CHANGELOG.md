# Change Log
All notable changes to this project will be documented in this file.

Group changes to describe their impact on the project, as follows:

    Added for new features.
    Changed for changes in existing functionality.
    Deprecated for once-stable features removed in upcoming releases.
    Removed for deprecated features removed in this release.
    Fixed for any bug fixes.
    Security to invite users to upgrade in case of vulnerabilities.

## [4.6.0] - Unreleased

### Added
- Reply to chat message feature (with original message preview)
- Swipe action on chat messages to reply / delete
- Voice recordings in chat feature
- Allow video recording in chat file sharing
- Unread messages indicator in chat conversation that separates read & unread messages
- Notify incoming/outgoing calls on bluetooth devices using self-managed connections from telecom manager API (disables SDK audio focus)
- Ask Android to not process what user types in an encrypted chat room to improve privacy, see [IME_FLAG_NO_PERSONALIZED_LEARNING](https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING)
- New video call UI on foldable device like Galaxy Z Fold
- Setting to automatically record all calls
- When using a physical keyboard, use left control + enter keys to send message
- Using CallStyle notifications for calls for devices running Android 12 or newer

### Changed
- UI has been reworked around SlidingPane component to better handle tablets & foldable devices
- No longer scroll to bottom of chat room when new messages are received, a new button shows up to do it and it displays conversation's unread messages count
- Animations have been replaced to use com.google.android.material.transition ones
- Using new [Unified Content API](https://developer.android.com/about/versions/12/features/unified-content-api) to share files from keyboard (or other sources)
- Bumped dependencies, gradle updated from 4.2.2 to 7.0.2
- Target Android SDK version set to 31 (Android	12)
- Splashscreen is using new APIs
- SDK updated to 5.1.0 release

### Fixed
- Chat notifications disappearing when app restarts
- "Infinite backstack", now each view is stored (at most) once in the backstack
- Going back to the dialer when pressing back in a chat room after clicking on a chat message notification
- Missing international prefix / phone number in assistant after granting permission
- Display issue for incoming call notification preventing to use answer/hangup actions on some Xiaomi devices (like Redmi Note 9S)

### Removed
- Launcher Activity has been replaced by [Splash Screen API](https://developer.android.com/reference/kotlin/androidx/core/splashscreen/SplashScreen)
- Dialer will no longer make DTMF sound when pressing digits
- Launcher activity
- Global push notification setting in Network, use the switch in each Account instead

## [4.5.6] - 2021-11-08

### Changed
- SDK updated to 5.0.49

## [4.5.5] - 2021-10-28

### Changed
- SDK updated to 5.0.45

## [4.5.4] - 2021-10-19

### Changed
- SDK updated to 5.0.38

### Fixed
- Side menu not showing the newly configured account until next start

## [4.5.3] - 2021-10-04

### Added
- Russian translation

### Changed
- SDK updated to 5.0.31

### Fixed
- AccountSettingsViewModel leak causing number of REGISTER to grow

## [4.5.2] - 2021-08-27

### Added
- Added a contact cache at app level
- Glide cache cleared on low memory

### Changed
- Fixed encrypted file export when VFS is enabled
- Fixed in-app video player size when VFS is enabled
- Fixed background mode setting
- Fixed proximity sensor during calls
- Fixed missing notification for missed call when call history view is active
- Fixed shortcuts on launcher
- Fixed a few memory leaks
- Fixed various crashes & other issues
- SDK bumped to 5.0.10

## [4.5.1] - 2021-07-15

### Changed
- Bugs & crashes have been fixed
- SDK bumped to 5.0.1

## [4.5.0] - 2021-07-08

This version is a full rewrite of the app in kotlin, using modern Android components like navigation, viewmodel, databinding, coroutines, etc...

### Added

- Using linphone SDK 5.0 API to better handle audio route (see linphone-sdk changelog)
- All files used by the app can now be encrypted for more security (VFS setting)
- In-app file viewers for PDFs, images, videos, sounds and texts
- Ephemeral messages
- Messages can be forwarded between chat rooms
- Numpad can be displayed in outgoing call view if the call has early media
- Can display multiple files in the same chat bubble
- Display video in recordings if available
- "Swipe left to delete" action available on calls history, contacts & chat rooms list
- "Swipe right" to mark a chat room as read
- Android 11 people & conversation compliant
- New animations between fragments and for unread chat messages / missed calls counters (can be disabled)
- Bubble & conversation support for chat message notifications
- Direct share support for chat room shortcuts
- Option to mark messages as read when dismissing the notification
- More settings are available
- Call view can be displayed in full-screen
- Display phone number label (home, work, etc...) in contacts' details

### Changed

- Call history view groups call from the same SIP URI (like linphone-iphone)
- Reworked conference (using new linphone-sdk APIs)
- Route audio to headset / headphones / bluetooth device automatically when available
- Send logs / Reset logs buttons moved from About page to Advanced Settings like iOS
- Improved how Android native contacts are used
- Switched to material design for text input fields & switches
- Launcher shortcuts can be to either contacts or chat rooms
- Improved preview when sharing video files through the chat
- UI changes

### Removed

- "back-to-call" button from dialer & chat views, use notification or overlay (see call settings for in-app/system-wide overlay)
- Don't ask for "Do not disturb settings" permission anymore
- Previous translations, starting again from scratch using Weblate instead of Transifex

### [4.4.0] - 2021-03-29

### Added
- Dedicated notification channel for missed calls

### Changed
- SDK updated to 4.5.0
- Min Android version updated from 21 to 23 (Android 6) due to SDK audio routes feature
- Rely on SDK audio routes feature instead of doing it in the application
- User can now check incoming messages delivery status in group chat rooms
- Asking user to read and accept privacy policy and general terms
- Updated translations
- Various crashes & issues fixed

## [4.3.1] - 2020-09-25

### Fixed
- Added phoneCall foregroundServiceType for Android Q and newer
- Contact sorting when first character has an accent

### Changed
- SDK updated to 4.4.2
- Updated translations

## [4.3.0] - 2020-06-23

### Added
- Forward message between chat rooms

### Changed
- Files from chat messages are now stored in a private space and will be deleted when the message or room will be deleted
- SDK updated to 4.4 version
- Fixed ANRs
- Fixed various issues

## [4.2.3] - 2020-03-03

### Changed
- Fixed various crashes
- Updated SDK to 4.3.3

## [4.2.2] - 2020-02-24

### Changed
- Fixed various issues
- Updated SDK to 4.3.1
- Removed AAudio plugin for now (we have observed quality issues on some popular devices with their latest updates)

## [4.2.1] - 2020-01-13

### Changed
-  Fixed various issues

## [4.2.0] - 2019-12-09

### Added
- Added shortcuts to contacts' latest chat rooms
- Improved device's do not disturb policy compliance
- Added sample application to help developpers getting started with our SDK
- Added picture in picture feature if supported instead of video overlay
- Added camera preview as dialer's background on tablets
- Contact section in the settings
- Using new AAudio & Camera2 frameworks for better performances (if available)
- Android 10 compatibility
- New plugin loader to be compatible with app bundle distribution mode
- Restart service if foreground service setting is on when app is updated
- Change bluetooth volume while in call if BT device connected and used

### Changed
- Improved performances to reduce startup time
- Call statistics are now available for each call & conference
- Added our own devices in LIME encrypted chatrooms' security view
- No longer display incoming call activity from Service, instead use incoming call notification with full screen intent
- Improved reply notification when replying to a chat message from the notification
- License changed from GPLv2 to GPLv3
- Switched from MD5 to SHA-256 as password protection algorithm

## [4.1.0] - 2019-05-03

### Added
- End-to-end encryption for instant messaging, for both one-to-one and group conversations.
- Video H.265 codec support, based on android MediaCodec.
- Enhanced call and IM notifications, so that it is possible to answer, decline, reply or mark as read directly from them.
- Setting to request attachments to be automatically downloaded, unconditionnally or based on their size.
- Possibility to send multiple attachments (images, documents) in a same message.
- Possibility to share multiple images through Linphone from an external application (ex: photo app)
- Rich input from keyboard (images, animated gifs...) when composing messages.
- Rendering of animated gifs in conversations.
- Button to invite contacts to use Linphone by sending them a SMS.
- Possibility to record calls (audio only), and replay them from the "Recordings" menu.
- Remote provisioning from a QR code providing the http(s) url of a provisioning server.
- Option for a dark theme

### Changed
- Compilation procedure is simplified: a binary SDK containing dependencies (liblinphone) is retrieved automatically from a Maven repository.
  Full compilation remains absolutely supported. Please check local README.md for more details.
- Updated translations, mainly French and English.
- Call history view shows last calls for a given contact.
- Improved ergonomy of answer/decline buttons, including accessibility support.
- Enhanced user interface, including new icons, cleanups of unused graphical resources.
- Contact view is faster thanks to an asynchronous fetching.
- Adaptive icon for Android 8+.
- Video overlay now also shows local view.
- Reworked settings view, cleanup of useless settings.
- About section links to full GPLv2 license text.

### Deprecated
- The video rendering method based on GL2JNIView is deprecated in favour of TextureView, which is easier to use.
  Please read [this article](https://wiki.linphone.org/xwiki/wiki/public/view/Lib/Features/Android%20TextureView%20Display/) for more information.

### Fixed
- One to one text conversations mixed up when initiated from differents SIP accounts. 


## [4.0.1] - 2018-06-26

### Fixed
- fix loading of plugins
- fix issue with video stream, not started when receiving an incoming call just after the app is launched
- fix issue with TURN

## [4.0.0] - 2018-06-15

### Added
- Group chat between linphone.org SIP accounts.
- new JAVA/JNI wrapper. This new wrapper is automatically generated from liblinphone C API. It breaks compatibility with previous, hand-made wrapper.
  (more information about new wrapper [here.](https://wiki.linphone.org/xwiki/wiki/public/view/Lib/Linphone%20%28Android%29%20Java%20wrapper/) )

### Deprecated
- hand-made java API in submodules/linphone/java is deprecated. However it is still possible to use it by checking out the 3.4.x branch of linphone-android.

### Fixed
- issue with changing push notification token not passed to library, possibly resulting in a loss of incoming calls.

## [3.3.0] - 2017-10-18

### Added
- Integration with Android O
- New video adaptive bitrate algorithm(More informations [here](https://wiki.linphone.org/xwiki/wiki/public/view/FAQ/How%20does%20adaptive%20bitrate%20algorithm%20work%20%3F/))

### Changed
- Application is no more managing in-call wakelock, it's now managed by the library

### Fixed
- Crashs in new chat view
- Contacts management
- Random crash in chatroom
- Improve chats list loading time

## [3.2.7] - 2017-05-15

### Fixed
- Crash with devices X86 on Android < 5

## [3.2.6] - 2017-04-10

### Added
- Notification of message reading on chat
- New permission to kill linphone app

### Fixed
- Crash with firebase push
- Problems with contacts

## [3.2.5] - 2017-03-06

### Added
- Doze mode(energy saving) button in Network settings

### Changed
- Migrate Linphone build from ANT to gradle
- No pause VOIP Call on incoming GSM call until we off hook this
- Subscription friends list enabled by default only for linphone domain

### Fixed
- Rotation after screen locking
- Contacts background task
- No more asking phone number for non-linphone domain
- Bug with Linphone credential login

## [Unreleased]

### Added
- Lime integration

## [3.2.4] - 2017-01-19

### Fixed
- Some crashs
- Some UI bugs

## [3.2.3] - 2017-01-11

### Fixed
- Somes crashs

### Changed
- Improved performance of contacts loading

## [3.2.2] - 2017-01-04

### Fixed
- Some bug with the download of OpenH264 for Android < 5.1
- Some crashs

### Changed
- Disable AAC codecs

## [3.2.1] - 2016-11-24

### Added
- Open H264 binary download for ARM Android < 5.1

### Fixed
- Crashes for x86 CPU at starting
- Crashes in somes view in cause of bad locale time
- Crashes in contacts view if we don't have permission

## [3.2.0] - 2016-11-10

### Added
- Change your password in your account settings

### Changed
- Media H264 support improved for Android >= 5.1
- Optimize memory footprint and performance of contacts list an IM view

### Fixed
- Crashes Android 6/7 at starting
- Permissions issues
- Layout of tablet views
