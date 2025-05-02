# Change Log
All notable changes to this project will be documented in this file.

Group changes to describe their impact on the project, as follows:

    Added for new features.
    Changed for changes in existing functionality.
    Deprecated for once-stable features removed in upcoming releases.
    Removed for deprecated features removed in this release.
    Fixed for any bug fixes.
    Security to invite users to upgrade in case of vulnerabilities.

## [6.1.0] - Unreleased

### Added
- Added a vu meter for recording volume
- Added a setting for user to choose whether to sort contacts by first name or last name

## [6.0.6] - 2025-05-02

### Added
- Added recover phone account when clicking on "Forgotten password" in the assistant
- Improved message when contacts list is empty depending on the currently selected filter and added a button to open the filter popup menu for users that didn't notice the icon on the top right corner of the screen when contacts list is empty and "SIP contacts only" filter is set.
- Added "Logs collection sharing server URL" setting in developper area
- Added "Disable sending logs to Crashlytics" advanced setting.

### Changed
- Improved VFS message in confirmation dialog
- Moved "Print logs in logcat" and "File sharing server URL" settings to developper area

### Fixed
- Fixed crash when opening a password protected PDF
- Fixed chat room lookup while in 1-1 call, using SDK method for getting chat room from conference
- Fixed newly created contact not being visible in contacts list without reloading it
- Fixed missing event icon for group conversations
- Another attempts at preventing crashes due to In-Call service not being started as foreground before being stopped

## [6.0.5] - 2025-04-18

### Changed
- When calling a SIP URI that looks like a phone number in the username and an IP in the domain, replace the domain with the one of the currently selected account to workaround issue with PBXs using IPs instead of domains in From header
- Improved account creation page UI when push notifications aren't available
- Improved called account display on incoming call screen when more than one account configured
- Updated telecom package from beta to release candidate

### Fixed
- Fixed transfer call view numpad button starting a new call instead of forwarding the current one
- Fixed incoming call not displayed in call history depending on how the From & To headers are formatted (SDK fix)
- Fixed crashes related to foreground service not being started
- Fixed crash due to lateinit property not being initialized before used

## [6.0.4] - 2025-04-11

### Changed
- Third party SIP accounts push notifications will be disabled and setting will be hidden unless if list of supported domains (to prevent issues, specifically when used with UDP transport protocol causing bigger packets getting lost)

### Fixed
- Prevent refresh of views due to contacts changes to happen to frequently at startup
- Prevent crash in Help view if app is built without Firebase

## [6.0.3] - 2025-04-04

### Added
- Show alert when default account is disabled
- Refesh list details when going back from background after one hour or more (when keep app alive using service is enabled)
- Click to copy SIP URI in call history shortcut
- Added developper settings, must click 8 times on version (in Help) to make it appear (E2E encryption for meetings & group calls setting was moved there)
- Circular indicator while search is in progress in contacts lists

### Changed
- Force some default values on notifications channels
- Contacts list filter is now applied to new call / conversation & other contact pickers
- Attach file icon stays visible while typing message in conversation instead of emoji picker icon

### Fixed
- No default account being selected if the default one is removed
- Navigation bar turning orange when opening search bar
- Incoming call showed as video even if video is disabled locally
- Concurrent modification crash in Contacts loader
- Meetings list not properly sorted when CCMP is used
- POST_NOTIFICATIONS permission check on old Android devices

## [6.0.2] - 2025-03-28

### Added
- Show on top bar if FULL_SCREEN_INTENT permission isn't granted, clicking on it sends to the matching settings so user can fix it easily, without it incoming call screen won't be displayed if screen is off
- Ring during incoming early media call setting added back
- Added a floating action button to open dialpad during outgoing early media call

### Changed
- Delete all related call history / conversations / meetings when removing an account
- Delay / use a separated thread for heavy contacts related tasks to ensure call is correctly handled and foreground service is started quickly enough
- Newly created account in app will be kept disabled until SMS code validation is done
- Keep app alive foreground service notification no shows a content message to ease clicking on it to open the app & workaround a crash on some devices
- Automatically show dialpad setting will now also work on new / transfer call while in call as well

### Fixed
- Improved POST_NOTIFICATIONS permission check on Android 13 and newer, should prevent crashes
- Fixed contact lookup if phone number starts by "00" instead of "+"
- Fixed "delete all call history" sometimes not removing all call logs
- Fixed LDAP / remote CardDAV contacts sometimes not displayed in contacts list when doing a search
- Fixed issue where contact filter could be set to only show sip.linphone.org contacts even when third party account was being selected
- Fixed sometimes wrong displayed SIP URI in detailed call history
- Fixed invisible meeting icon in status bar
- Fixed missed call count indicator behavior with some third party providers
- Prevent today indicator & meeting icon in bottom nav bar from blinking / briefly appearing
- Fixed bottom nav bar sometimes being hidden
- Fixed missing share logs server URL when migrating from 5.2 if that value was removed back then
- Other crashes fixed

## [6.0.1] - 2025-03-21

### Added
- Start at boot & auto answer settings added back
- Interface setting to have dialpad automatically opened in start call view
- Replace "+" by "00" and do not apply prefix for calls & chat account settings
- Setting to let user choose whether to record calls using MKV or SMFF format (the later allows to record H265/AV1 video but is a proprietary file format that can't be read outside of Linphone)

### Changed
- Reverted the way of playing incoming call ringone (you may have to configure your own ringtone again), was causing various issues depending on devices/firmwares
- Show all call history entries if only one account is configured (workaround for missing history for now until a proper fix will be done in SDK)

### Fixed
- Issue preventing bluetooth Hearing Aids from working properly (and fixed earpiece/hearing aids icon)
- Prevent Qr Code scanner to use static picture camera
- Prevent user from connecting the same account multiple times
- Quit menu visibility not updated when changing Keep Alive setting
- Participant selection in group when typing "@"
- Recordings order has been reversed to have newest ones at top
- Improved message when network is not reachable due to "Wifi only mode" being enabled
- Various crash & bug fixes

## [6.0.0] - 2025-03-11

6.0.0 release is a complete rework of Linphone Android, with a fully redesigned UI, so it is impossible to list everything here.

### Changed
- Separated threads: Contrary to previous versions, our SDK is now running in it's own thread, meaning it won't freeze the UI anymore in case of heavy work, thus reducing the number of ANR and greatly increasing the fluidity of the app.
- Asymmetrical video : you no longer need to send your own camera feed to receive the one from the remote end of the call, and vice versa.
- Improved multi account: you'll only see history, conversations, meetings etc... related to currently selected account, and you can switch the default account in two clicks.
- Call transfer: Blind & Attended call transfer have been merged into one: during a call, if you initiate a transfer action, either pick another call to do the attended transfer or select a contact from the list (you can input a SIP URI not already in the suggestions list) to start a blind transfer.
- User can only send up to 12 files in a single chat message.
- IMDNs are now only sent to the message sender, preventing huge traffic in large groups, and thus the delivery status icon for received messages is now hidden in groups (as it was in 1-1 conversations).
- Settings: a lot of them are gone, the one that are still there have been reworked to increase user friendliness.
- Default screen (between contacts, call history, conversations & meetings list) will change depending on where you were when the app was paused or killed, and you will return to that last visited screen on the next startup.
- Gradle files have been migrated from Groovy to Kotlin DSL, and dependencies are now in a separated file (libs.versions.toml).
- Account creation no longer allows you to use your phone number as username, but it is still required to provide it to receive activation code by SMS.
- Minimum supported Android OS version is now 9 (API level 28).
- Telecom Manager support is now based on androidx.core.core-telecom package.
- Some settings have changed name and/or section in linphonerc file.

### Added
- Contacts trust: contacts for which all devices have been validated through a ZRTP call with SAS exchange are now highlighted with a blue circle (and with a red one in case of mistrust). That trust is now handled at contact level (instead of conversation level in previous versions).
- Media & documents exchanged in a conversation can be easily found through a dedicated screen.
- A brand new chat message search feature has been added to conversations.
- You can now react to a chat message using any emoji.
- If next message is also a voice recording, playback will automatically start after the currently playing one ends.
- Chat while in call: a shortcut to a conversation screen with the remote.
- Chat while in a conference: if the conference has a text stream enabled, you can chat with the other participants of the conference while it lasts. At the end, you'll find the messages history in the call history (and not in the list of conversations).
- Auto export of media to native gallery even when auto download is enabled (but still not if VFS is enabled nor for ephemeral messages).
- Save / export document & media from ephemeral messages will be disabled, and secure policy that prevents screenshots will be enforced in file viewer even if the setting is disabled.
- Notification showing upload/download of files shared through chat will let user know the progress and keep the app alive during that process.
- Screen sharing in conference: only desktop app starting with 6.0 version is able to start it, but on mobiles you'll be able to see it.
- You can choose whatever ringtone you'd like for incoming calls (in Android notification channel settings).
- Security focus: security & trust is more visible than ever, and unsecure conversations & calls are even more visible than before.
- CardDAV: you can configure as many CardDAV servers you want to synchronize you contacts in Linphone (in addition or in replacement of native addressbook import).
- OpenID: when used with a SSO compliant SIP server (such as Flexisip), we support single-sign-on login.
- MWI support: display and allow to call your voicemail when you have new messages (if supported by your VoIP provider and properly configured in your account params).
- CCMP support: if you configure a CCMP server URL in your accounts params, it will be used when scheduling meetings & to fetch list of meetings you've organized/been invited to.
- Devices list: check on which device your sip.linphone.org account is connected and the last connection date & time (like on subscribe.linphone.org).
- Protobuf dependency to allow logging native crashes stack traces at next app startup.
- Android 15 startup listener, allowing us to log type of start (cold, warm, etc...) and some other useful info.
- Dialer & in-call numpad show letters under the digit.

### Removed
- Dialer: the previous home screen (dialer) has been removed, you'll find it as an input option in the new start call screen.
- Peer-to-peer: a SIP account (sip.linphone.org or other) is now required.
- Contacts: we no longer add contacts created in-app in the native addressbook (WRITE_CONTACTS permission was removed), but we still import them if you grant us the READ_CONTACTS permission.

### Fixed
- No longer trying to play vocal messages & call recordings using bluetooth when connected to an Android Auto car, causing playback issues.
- AAudio driver no longer causes delay when switching between devices (SDK fix).

## [5.2.5] - 2024-05-03

### Changed
- Updated translations

## [5.2.4] - 2024-04-22

### Fixed
- Active speaker video hidden when you are the first one to join a meeting
- Show camera icon instead of microphone for incoming video calls
- SIP URI parsing from native contact due to international prefix being applied when it shouldn't
- Various fixes for broadcast mode

## [5.2.3] - 2024-01-31

### Fixed
- Crash due to OOM for some images sent/received in chat
- Crash while navigating to account settings

### Changed
- Updated translations (Romanian, Polish, Portuguese)

## [5.2.2] - 2024-01-15

### Fixed
- Local conference created my merging audio streams

## [5.2.1] - 2023-12-23

### Fixed
- Crash when Service starts before CoreContext

## [5.2.0] - 2023-12-21

### Added
- Chat messages emoji "reactions"
- Hearing aids should be working the same way bluetooth headset does
- Hardware video codecs (H264, H265) are now used in priority when possible (SDK)
- Broadcast mode for scheduled meetings (hidden)
- Android 14 support

### Changed
- BLUETOOTH_CONNECT permission is no longer required

### Fixed
- Correctly switching to either bottom or back microphone depending on wether the earpiece or the speaker is used, 
and also use the same device for input and output if the one set as output as RECORD capability 
(fixes echo issue while on speakerphone on some devices such as Samsung's)
- Connection status & color when in refreshing state
- Sent content type for files attached to a chat message
- Toggle mute mic while in conference
- Calling right after creating a chat room

## [5.1.4] - 2023-10-20

### Fixed
- Various fixes in the SDK (5.2.110)

### Changed
- Updated translations from Weblate

## [5.1.3] - 2023-09-23

### Fixed
- Core not able to open database due to issue in 5.2.107 SDK from last update
- Incoming call activity and lock screen interaction
- Selected "meeting" filter icon color

## [5.1.2] - 2023-09-22

### Added
- Italian translation completed

### Fixed
- Multiple authentication requested dialogs stacking above each other sometimes
- Downgraded navigation version to try to prevent some crashes reported on the Play Store

## [5.1.1] - 2023-09-06

### Fixed
- Fixed issue in SDK randomly generated password when creating account from app
- Various issues reported on the Play Store

## [5.1.0] - 2023-08-21

### Added
- Showing short term presence for contacts whom publish it + added setting to disable it (enabled by default for sip.linphone.org accounts)
- Confirmation dialog before removing account
- Attended transfer instead of blind transfer if there is more than 1 call
- Last sent message delivery status (IMDN) icon in chat rooms list
- Emoji picker in chat room, and increase size of text if it only contains emojis
- Hidden setting to disable video completely
- Hidden setting to prevent adding / editing / removing native contacts
- Hidden setting to protect settings access using account password
- SIP URI in call can be selected using long press
- Dialog showing up asking for correct account password in case of failed authentication

### Changed
- Switched Account Creator backend from XMLRPC to FlexiAPI, it now requires to be able to receive a push notification
- Email account creation form is now only available if TELEPHONY feature is not available, not related to screen size anymore
- Replaced voice recordings file name by localized placeholder text, like for video conferences invitations
- Decline incoming calls with Busy reason if there is at least another active call
- Open keyboard when replying to a message if no text / file / voice record is pending
- Removed jetifier as it is not needed
- Switched from gradle 7.5 to 8.0, requires JDK 17 (instead of 11)

### Fixed
- Messages not marked as reply in basic chat room if sending more than 1 content
- Chat message video attachment display when failing to get a preview picture

## [5.0.14] - 2023-06-20

### Changed
- SDK update only

## [5.0.13] - 2023-06-15

### Changed
- SDK update only

## [5.0.12] - 2023-05-23

### Fixed
- Crash if notification manager throws an exception
- Video preview not moving if call was started in audio only

## [5.0.11] - 2023-05-09

### Fixed
- Wrong call displayed when hanging up a call while an incoming one is ringing
- Crash related to call history
- Crash due to wrongly format string
- Add/remove missing listener on FriendLists created after Core has been created

### Changed
- Improved GSM call interruption
- Updated translations

## [5.0.11] - 2023-05-09

### Fixed
- Wrong call displayed when hanging up a call while an incoming one is ringing
- Crash related to call history
- Crash due to wrongly format string
- Add/remove missing listener on FriendLists created after Core has been created

### Changed
- Improved GSM call interruption
- Updated translations

## [5.0.10] - 2023-04-04

### Fixed
- Plain copy of encrypted files (when VFS is enabled) not cleaned
- Avatar display issue if contact's "initials" contains more than 1 emoji or an emoji + a character

## [5.0.9] - 2023-03-30

### Fixed
- Admin weren't visible for non admin users in group chat rooms
- Crash when clicking on URI in chat if not matching app is found on Android to handle it
- LIME update threshold wasn't set, causing a request to be made after each REGISTER

### Changed
- Now SDK automatically handles TextureView's listener, removed it from app
- Bumped license year to 2023
- Force remove LIME X3DH server URL for third party accounts

## [5.0.8] - 2023-03-20

### Fixed
- Trying to prevent crash in call history
- Color icon in dark mode in chat for files & replies

### Changed
- Updated translations

## [5.0.7] - 2023-02-27

### Fixed
- Fixed navigating to a contact that doesn't have a native ID, but using it's SIP address instead
- Fixed account creator resolved country name & create button not enabled

### Changed
- Updated translations

## [5.0.6] - 2023-02-17

### Fixed
- Wrong country displayed in assistant after picking it in the list if another country has the same international prefix (such as +1)
- SIP URI clickable pattern missing '~'
- Crash that happens sometimes when CallActivity is destroyed
- Pressing send message button while recording a voice message not sending it
- Missing ephemeral icon next to send message icon
- Headers colors in IMDN details
- Pixel issue in call quality indicator 2 icon

### Changed
- Improved incoming call layout when receiving early-media video
- Hidden "Echo Tester" setting unless in debug mode as it can mislead user and isn't useful for end user

## [5.0.5] - 2023-01-19

### Fixed
- Issue with how replies where added to chat message notification from reply action

## [5.0.4] - 2023-01-18

### Added
- Show a progress bar while importing files to the chat sending area

### Changed
- Prevent keyboard from auto-replacing some user input such as username, breaking SIP URIs unknowingly

### Fixed
- Prevent copy of files that weren't sent in chat to be kept in app local folder

## [5.0.3] - 2023-01-13

### Added
- Voice message recording/playback will use bluetooth/headset/headphones/hearing aid device if available
- Chat message notifications are now compatible with Android Auto

### Changed
- In video conference, when in active speaker layout, currently speaking participant miniature will be hidden
- Attach file, voice recording and send message icons are now a bit bigger
- Updated Firebase BoM, gradle & some dependencies

### Fixed
- ANR happening sometimes during voice message playback

## [5.0.2] - 2023-01-05

### Changed
- Export files to native gallery is now available even if automatically download files setting is enabled

### Fixed
- Makes sure sip.linphone.org accounts have a LIME X3DH server URL for E2E chat messages encryption
- Files not being exported to native gallery sometimes
- Crashes reported by Google Play Store & Crashlytics

## [5.0.1] - 2022-12-16

### Changed
- File transfer progress indication & error status improvements

### Fixed
- Wrong LIME status for participant that has multiple devices
- No longer sends video when switching from audio only to another conference layout
- SIP URI regex pattern to prevent HTTP URLs containing '@' to be handled as SIP URI

## [5.0.0] - 2022-12-06

### Added
- Post Quantum encryption when using ZRTP
- Conference creation with scheduling, video, different layouts, showing who is speaking and who is muted, etc...
- Group calls directly from group chat rooms
- Chat rooms can be individually muted (no notification when receiving a chat message)
- When a message is received wait a short amount of time to check if more are to be received to notify them all at once
- Outgoing call video in early-media if requested by callee
- Image & Video in-app viewers allow for full-screen display
- Display name can be set during assistant when creating / logging in a sip.linphone.org account
- Android 13 support, using new post notifications & media permissions
- Call recordings can be exported
- Setting to prevent international prefix from account to be applied to call & chat
- Themed app icon is now supported for Android 13+

### Changed
- In-call views have been re-designed
- "Media Encryption Mandatory" setting now allows for any media encryption (instead of only the one selected in the above setting previously)
- Improved how call logs are handled to improve performances
- Improved how contact avatars are generated
- 3-dots menu even for basic chat rooms with more options
- Phone numbers & email addresses are now clickable links in chat messages
- Go to call activity when you click on launcher icon if there is at least one active call

### Fixed
- Multiple file download attempt from the same chat bubble at the same time needed app restart to properly download each file
- Call stopped when removing app from recent tasks
- Generated avatars in dark mode
- Call state in self-managed TelecomManager service if it takes longer to be created than the call to be answered
- Show service notification sooner to prevent crash if Core creation takes too long
- Incoming call screen not being showed up to user (& screen staying off) when using app in Samsung secure folder
- One to one chat room creation process waiting indefinitely if chat room already exists
- Contact edition (SIP addresses & phone numbers) not working due to original value being lost in Friend parsing
- Automatically start call recording
- "Blinking" in some views when presence is being received
- Trying to keep the preferred driver (OpenSLES / AAudio) when switching device
- Issues when storing presence in native contacts + potentially duplicated SIP addresses in contact details
- Chat room scroll position lost when going into sub-view
- Trim user input to remove any space at end of string due to keyboard auto completion
- No longer makes requests to our LIME server (end-to-end encryption keys server) for non sip.linphone.org accounts
- Fixed incoming call/notification not ringing if Do not Disturb mode is enabled except for favorite contacts

## [4.6.14] - 2022-09-19

### Fixed
- ANR that happens sometimes when playing voice recording

### Changed
- Improved contact loader by querying only relevant fields

## [4.6.13] - 2022-08-25

### Fixed
- Disable Telecom Manager feature on Android < 10 to prevent crash due to Android 9 OS bug
- Fixed crash due to AAudio's waitForStateChange (SDK fix)

## [4.6.12] - 2022-07-29

### Fixed
- Call notification not being removed if service channel is disabled & background mode is enabled
- Wrong display name in chat notification sometimes
- Removed secure chat button if no LIME server configured or no conference factory URI set
- Disable TelecomManager feature when the device doesn't support it

### Changed
- ContactsLoader have been updated, shouldn't crash anymore

## [4.6.11] - 2022-06-27

### Fixed
- Various crashes due to unhandled exceptions
- Echo canceller calibration not using speaker (SDK fix)

## [4.6.10] - 2022-06-07

### Fixed
- Fixed contact address used instead of identity address when creating a basic chat room from history or contact details
- Fixed call notification still visible after call ended on some devices
- Fixed incoming call activity not displayed on some devices
- Fixed Malaysian dial plan (SDK fix)
- Fixed incoming call ringing even if Do not disturb mode is enabled (SDK fix)

## [4.6.9] - 2022-05-30

### Fixed
- ANR when screen turns OFF/ON while app is in foreground
- Crash due to missing CoreContext instance in TelecomManager service
- One-to-One encrypted chat room creation if it already exists
- Crash if ConnectionService feature isn't supported by the device

### Changed
- Updated translations from Weblate
- Improved audio devices logs

## [4.6.8] - 2022-05-23

### Fixed
- Crash due to missing CoreContext in CoreService
- Crash in BootReceiver if auto start is disabled
- Other crashes

## [4.6.7] - 2022-05-04

### Changed
- Do not start Core in Application, prevents service notification from appearing by itself
- When switching from bluetooth or headset device to earpiece/speaker, also change microphone
- Prevent empty chat bubble by sending only space character(s)

### Fixed
- Phone numbers with non-ASCII labels missing from address book
- Wrong audio device displayed in call statistics
- Various issues from Crashlytics

## [4.6.6] - 2022-04-26

### Changed
- Prevent requests to LIME X3DH & long term presence servers when not using a sip.linphone.org account
- Updated DE & RU translations
- Improved UI on landscape tablets

### Fixed
- Catching exceptions in new ContactsLoader reported on PlayStore
- Missing phone numbers in contacts when label contains a space character (5.1.24 SDK fix)
- Prevent app from starting by itself due to DummySyncService
- Hide chat rooms settings not working properly

## [4.6.5] - 2022-04-11

### Changed
- Only display phone number if it matches SIP address username
- Using new MagicSearch API to improve contacts list performances

### Fixed
- Prevent concurrent exception while loading native address book contacts

## [4.6.4] - 2022-04-06

### Added
- Set video information in CallStyle incoming call notification

### Changed
- Massive rework of how native contacts from address book are handled to improve performances
- Only display phone number from LDAP search result if it matches SIP address' username

### Fixed
- Do not use CallStyle notification on Samsung devices, they are currently displayed badly
- Fixed microphone muted when starting a new call if microphone was muted at the end of the previous one
- Added LDAP contact display name to SIP address
- Prevent read-only 1-1 chat room
- Fixed chat room last updated time not updated sometimes

## [4.6.3] - 2022-03-08

### Added
- Improvements in contacts matching

### Changed
- "Operation in progress" spinner hidden when contacts display/filter takes less than 200ms

### Fixed
- Contacts order when multiple address book contacts share the same number / SIP address
- Wrongly formatted phone numbers not displayed anymore
- Incoming call activity not displayed on LineageOS sometimes
- Various crashes related to Telecom Manager exceptions not being caught

## [4.6.2] - 2022-03-01

### Added
- Request BLUETOOTH_CONNECT permission on Android 12+ devices, if not we won't be notified when a BT device is being connected/disconnected while app is alive.
- LDAP settings if SDK is built with OpenLDAP (requires 5.1.1 or higher linphone-sdk), will add contacts if any
- SIP addresses & phone numbers can be selected in history & contact details view
- Text can be selected in file viewer & config viewer
- Prevent screen to turn off while recording a voice message

### Changed
- Contacts lists now show LDAP contacts if any

### Fixed
- Negative gain in audio settings is allowed again
- STUN server URL setting not enabling it for non sip.linphone.org accounts
- Contacts list header case comparison
- Stop voice recording playback when sending chat message
- Call activity not finishing when hanging up sometimes
- Auto start setting disabled not working if background mode setting was enabled

## [4.6.1] - 2022-02-14

### Fixed
- Quit button not working when background mode was enabled
- Crash when background mode was enabled and service notification channel was disabled
- Crashes while changing audio route
- Crash while fetching contacts
- Crash when rotating the device (SDK fix)

## [4.6.0] - 2022-02-09

### Added
- Reply to chat message feature (with original message preview)
- Swipe action on chat messages to reply / delete
- Voice recordings in chat feature
- Allow video recording in chat file sharing
- Unread messages indicator in chat conversation that separates read & unread messages
- Notify incoming/outgoing calls on bluetooth devices using self-managed connections from telecom manager API (disables SDK audio focus)
- Ask Android to not process what user types in an encrypted chat room to improve privacy, see [IME_FLAG_NO_PERSONALIZED_LEARNING](https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING)
- SIP URIs in chat messages are clickable to easily initiate a call
- New video call UI on foldable device like Galaxy Z Fold
- Setting to automatically record all calls
- When using a physical keyboard, use left control + enter keys to send message
- Using CallStyle notifications for calls for devices running Android 12 or newer
- New fragment explaining generic SIP account limitations contrary to sip.linphone.org SIP accounts
- Link to Weblate added in about page

### Changed
- UI has been reworked around SlidingPane component to better handle tablets & foldable devices
- No longer scroll to bottom of chat room when new messages are received, a new button shows up to do it and it displays conversation's unread messages count
- Animations have been replaced to use com.google.android.material.transition ones
- Using new [Unified Content API](https://developer.android.com/about/versions/12/features/unified-content-api) to share files from keyboard (or other sources)
- Received messages are now trimmed
- Bumped dependencies, gradle updated from 4.2.2 to 7.0.2
- Target Android SDK version set to 31 (Android	12)
- Splashscreen is using new APIs
- SDK updated to 5.1.0 release
- Updated translations

### Fixed
- Chat notifications disappearing when app restarts
- "Infinite backstack", now each view is stored (at most) once in the backstack
- Voice messages / call recordings will be played on headset/headphones instead of speaker, if possible
- Going back to the dialer when pressing back in a chat room after clicking on a chat message notification
- Missing international prefix / phone number in assistant after granting permission
- Display issue for incoming call notification preventing to use answer/hangup actions on some Xiaomi devices (like Redmi Note 9S)
- Missing foreground service notification for background mode

### Removed
- Launcher Activity has been replaced by [Splash Screen API](https://developer.android.com/reference/kotlin/androidx/core/splashscreen/SplashScreen)
- Dialer will no longer make DTMF sound when pressing digits
- Launcher activity
- Global push notification setting in Network, use the switch in each Account instead
- No longer need to monitor device rotation and give information to the Core, it does it by itself

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
