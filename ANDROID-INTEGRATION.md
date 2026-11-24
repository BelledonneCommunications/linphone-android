# Android integration (fork of linphone-android)

This fork makes Linphone a first-class calling and messaging app on Android 17 (API 37). It still runs on
everything upstream supports (minSdk 28); each feature says what it needs. It works with any SIP server. It is
also fully compatible with the MU300 SIP gateway (mu300-voip), where phone numbers are dialled through the
router's modem and SMS arrive as SIP MESSAGE.

Every feature has its own switch under **Settings → Android integration**.

## Calls

Android offers two kinds of calling apps, and a call is always one or the other:

| | Managed (system phone app) | Self-managed (Linphone's own call screen) |
|---|---|---|
| API | `ConnectionService` and a `PhoneAccount` with `CAPABILITY_CALL_PROVIDER` | Jetpack Core-Telecom: `CallsManager`, `CallControlScope` |
| Dialling | the system dialer, the "call with" picker, contacts, the call log | Linphone's UI |
| Call screen | the system in-call UI (the default dialer's `InCallService`) | Linphone's call activity |
| Ringing | the system ringer, with the system ringtone and vibration | Linphone |
| Call history | the system call log, written by Telecom | Android 17 unified call history (`isLogExcluded = false`, `ACTION_CALL_BACK`) |
| Bluetooth, car, watch | through the system phone app | through Telecom's call control |
| Needs | the user to enable the calling account once (Settings → Calls → Calling accounts) | nothing |

**Mode** (setting `Call integration`):
- **Automatic** (default): calls are managed whenever the account's calling account is enabled, and self-managed
  otherwise.
- **System phone app**: calls are always managed. Without an enabled calling account, the call fails with a
  prompt to enable it.
- **Linphone call screen**: upstream behaviour (self-managed).

### Managed calls

- **One `PhoneAccount` per SIP account.** The handle is `TelecomConnectionService` plus an id derived from the
  account's identity.
  - Capabilities: `CAPABILITY_CALL_PROVIDER`, video when enabled, and `CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS`
    with `CAPABILITY_VOICE_CALLING_AVAILABLE` following the SIP registration.
  - Extras: `EXTRA_ALWAYS_USE_VOIP_AUDIO_MODE`.
  - URI schemes: `tel`, so normal phone numbers can be dialled (a gateway such as the MU300 turns them into mobile
    calls), and `sip`.
- **Outgoing** from the system dialer: `onCreateOutgoingConnection` starts the Linphone call on that account.
  From Linphone's own UI: `TelecomManager.placeCall` with the account handle, so the call still runs in the
  system UI.
- **Incoming:** Linphone's `IncomingReceived` becomes `TelecomManager.addNewIncomingCall`, and
  `onCreateIncomingConnection` binds the `Connection` to the Linphone call by its SIP Call-ID. Linphone itself
  neither rings nor shows anything.
- **From the system call screen to Linphone:**
  - answer → `accept` (video when asked); reject → `decline`;
  - reject with a quick reply → decline, and send the text as a chat message (on the MU300: an SMS);
  - hold/unhold → `pause`/`resume`; DTMF → `sendDtmf`;
  - transfer → `transferTo`; consultative transfer → `transferToAnother`; deflect → `redirectTo` (302);
  - mute and audio route: the route Telecom picks is applied to Linphone's output device.
- **From Linphone to the system call screen:**
  - dialing; local ringback only when the network sends none (`setRingbackRequested`); active; on hold;
  - disconnected, with a cause, label and tone that match the SIP status;
  - `PROPERTY_HIGH_DEF_AUDIO` for wideband codecs, `PROPERTY_WIFI` on Wi-Fi;
  - status hints: "via <account>".

### Self-managed calls

These are upstream's Core-Telecom calls, plus Android 17's unified call history when it is enabled:
`isLogExcluded = false`, and an activity for `TelecomManager.ACTION_CALL_BACK` that dials the call identified by
`EXTRA_UUID` again.

### Redirecting phone calls (`ROLE_CALL_REDIRECTION`)

A `CallRedirectionService` can send calls dialled in the system dialer over a SIP account instead of the SIM.
- Modes: off / always / ask (the system's own confirmation, `confirmFirst`) / only on selected Wi-Fi networks /
  only while the account is registered.
- Short codes, `*`/`#` codes and listed prefixes are never redirected. Emergency calls never reach the service.

## Messages

- **Stage 1:** "Message via <account>" (`SENDTO sms:`/`smsto:`, the share sheet, contact actions) opens a
  Linphone conversation. Notifications offer direct reply.
- **Stage 2:** the default SMS app (`ROLE_SMS`), handling the SIM's SMS and MMS as well.

## Contacts

- A "Linphone" account (`AccountAuthenticator` and `SyncAdapter`) publishes Linphone's own contact lists
  (CardDAV, LDAP, remote lists) to the system contacts.
- Each contact gets data rows with the actions "Call via <account>" and "Message via <account>", which the
  contacts app and the dialer show.
