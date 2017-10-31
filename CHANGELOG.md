# Change Log
All notable changes to this project will be documented in this file.

Group changes to describe their impact on the project, as follows:

    Added for new features.
    Changed for changes in existing functionality.
    Deprecated for once-stable features removed in upcoming releases.é
    Removed for deprecated features removed in this release.
    Fixed for any bug fixes.
    Security to invite users to upgrade in case of vulnerabilities.

## [Incomming]

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
