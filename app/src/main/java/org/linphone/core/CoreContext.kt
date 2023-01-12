/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.core

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Pair
import android.view.*
import android.webkit.MimeTypeMap
import androidx.lifecycle.*
import androidx.loader.app.LoaderManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.text.Collator
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.abs
import kotlinx.coroutines.*
import org.linphone.BuildConfig
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.compatibility.PhoneStateInterface
import org.linphone.contact.ContactLoader
import org.linphone.contact.ContactsManager
import org.linphone.contact.getContactForPhoneNumberOrAddress
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version
import org.linphone.notifications.NotificationsManager
import org.linphone.telecom.TelecomHelper
import org.linphone.utils.*
import org.linphone.utils.Event

class CoreContext(
    val context: Context,
    coreConfig: Config,
    service: CoreService? = null,
    useAutoStartDescription: Boolean = false
) :
    LifecycleOwner, ViewModelStoreOwner {
    private val _lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle {
        return _lifecycleRegistry
    }

    private val _viewModelStore = ViewModelStore()
    override fun getViewModelStore(): ViewModelStore {
        return _viewModelStore
    }

    private val contactLoader = ContactLoader()

    private val collator: Collator = Collator.getInstance()

    var stopped = false
    val core: Core
    val handler: Handler = Handler(Looper.getMainLooper())

    var screenWidth: Float = 0f
    var screenHeight: Float = 0f

    val appVersion: String by lazy {
        val appVersion = BuildConfig.VERSION_NAME
        val appBranch = context.getString(R.string.linphone_app_branch)
        val appBuildType = BuildConfig.BUILD_TYPE
        "$appVersion ($appBranch, $appBuildType)"
    }

    val sdkVersion: String by lazy {
        val sdkVersion = context.getString(R.string.linphone_sdk_version)
        val sdkBranch = context.getString(R.string.linphone_sdk_branch)
        val sdkBuildType = org.linphone.core.BuildConfig.BUILD_TYPE
        "$sdkVersion ($sdkBranch, $sdkBuildType)"
    }

    val contactsManager: ContactsManager by lazy {
        ContactsManager(context)
    }

    val notificationsManager: NotificationsManager by lazy {
        NotificationsManager(context)
    }

    val callErrorMessageResourceId: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val loggingService = Factory.instance().loggingService

    private var overlayX = 0f
    private var overlayY = 0f
    private var callOverlay: View? = null
    private var previousCallState = Call.State.Idle
    private lateinit var phoneStateListener: PhoneStateInterface

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onGlobalStateChanged(core: Core, state: GlobalState, message: String) {
            Log.i("[Context] Global state changed [$state]")
            if (state == GlobalState.On) {
                fetchContacts()
            }
        }

        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            Log.i("[Context] Account [${account.params.identityAddress?.asStringUriOnly()}] registration state changed [$state]")
            if (state == RegistrationState.Ok && account == core.defaultAccount) {
                notificationsManager.stopForegroundNotificationIfPossible()
            }
        }

        override fun onPushNotificationReceived(core: Core, payload: String?) {
            Log.i("[Context] Push notification received: $payload")
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            Log.i("[Context] Call state changed [$state]")
            if (state == Call.State.IncomingReceived || state == Call.State.IncomingEarlyMedia) {
                if (declineCallDueToGsmActiveCall()) {
                    call.decline(Reason.Busy)
                    return
                }

                // Starting SDK 24 (Android 7.0) we rely on the fullscreen intent of the call incoming notification
                if (Version.sdkStrictlyBelow(Version.API24_NOUGAT_70)) {
                    onIncomingReceived()
                }

                if (corePreferences.autoAnswerEnabled) {
                    val autoAnswerDelay = corePreferences.autoAnswerDelay
                    if (autoAnswerDelay == 0) {
                        Log.w("[Context] Auto answering call immediately")
                        answerCall(call)
                    } else {
                        Log.i("[Context] Scheduling auto answering in $autoAnswerDelay milliseconds")
                        handler.postDelayed(
                            {
                                Log.w("[Context] Auto answering call")
                                answerCall(call)
                            },
                            autoAnswerDelay.toLong()
                        )
                    }
                }
            } else if (state == Call.State.OutgoingProgress) {
                val conferenceInfo = core.findConferenceInformationFromUri(call.remoteAddress)
                // Do not show outgoing call view for conference calls, wait for connected state
                if (conferenceInfo == null) {
                    onOutgoingStarted()
                }

                if (core.callsNb == 1 && corePreferences.routeAudioToBluetoothIfAvailable) {
                    AudioRouteUtils.routeAudioToBluetooth(call)
                }
            } else if (state == Call.State.Connected) {
                onCallStarted()
            } else if (state == Call.State.StreamsRunning) {
                if (previousCallState == Call.State.Connected) {
                    // Do not automatically route audio to bluetooth after first call
                    if (core.callsNb == 1) {
                        // Only try to route bluetooth / headphone / headset when the call is in StreamsRunning for the first time
                        Log.i("[Context] First call going into StreamsRunning state for the first time, trying to route audio to headset or bluetooth if available")
                        if (AudioRouteUtils.isHeadsetAudioRouteAvailable()) {
                            AudioRouteUtils.routeAudioToHeadset(call)
                        } else if (corePreferences.routeAudioToBluetoothIfAvailable && AudioRouteUtils.isBluetoothAudioRouteAvailable()) {
                            AudioRouteUtils.routeAudioToBluetooth(call)
                        }
                    }

                    // Only start call recording when the call is in StreamsRunning for the first time
                    if (corePreferences.automaticallyStartCallRecording && !call.params.isRecording) {
                        if (call.conference == null) { // TODO: FIXME: We disabled conference recording for now
                            Log.i("[Context] We were asked to start the call recording automatically")
                            call.startRecording()
                        }
                    }
                }
            } else if (state == Call.State.End || state == Call.State.Error || state == Call.State.Released) {
                if (state == Call.State.Error) {
                    Log.w("[Context] Call error reason is ${call.errorInfo.protocolCode} / ${call.errorInfo.reason} / ${call.errorInfo.phrase}")
                    val toastMessage = when (call.errorInfo.reason) {
                        Reason.Busy -> context.getString(R.string.call_error_user_busy)
                        Reason.IOError -> context.getString(R.string.call_error_io_error)
                        Reason.NotAcceptable -> context.getString(R.string.call_error_incompatible_media_params)
                        Reason.NotFound -> context.getString(R.string.call_error_user_not_found)
                        Reason.ServerTimeout -> context.getString(R.string.call_error_server_timeout)
                        Reason.TemporarilyUnavailable -> context.getString(R.string.call_error_temporarily_unavailable)
                        else -> context.getString(R.string.call_error_generic).format("${call.errorInfo.protocolCode} / ${call.errorInfo.phrase}")
                    }
                    callErrorMessageResourceId.value = Event(toastMessage)
                } else if (state == Call.State.End &&
                    call.dir == Call.Dir.Outgoing &&
                    call.errorInfo.reason == Reason.Declined &&
                    core.callsNb == 0
                ) {
                    Log.i("[Context] Call has been declined")
                    val toastMessage = context.getString(R.string.call_error_declined)
                    callErrorMessageResourceId.value = Event(toastMessage)
                }
            }

            previousCallState = state
        }

        override fun onLastCallEnded(core: Core) {
            Log.i("[Context] Last call has ended")
            removeCallOverlay()
            if (!core.isMicEnabled) {
                Log.w("[Context] Mic was muted in Core, enabling it back for next call")
                core.isMicEnabled = true
            }
        }

        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            for (message in messages) {
                exportFileInMessage(message)
            }
        }
    }

    private val loggingServiceListener = object : LoggingServiceListenerStub() {
        override fun onLogMessageWritten(
            logService: LoggingService,
            domain: String,
            level: LogLevel,
            message: String
        ) {
            if (corePreferences.logcatLogsOutput) {
                when (level) {
                    LogLevel.Error -> android.util.Log.e(domain, message)
                    LogLevel.Warning -> android.util.Log.w(domain, message)
                    LogLevel.Message -> android.util.Log.i(domain, message)
                    LogLevel.Fatal -> android.util.Log.wtf(domain, message)
                    else -> android.util.Log.d(domain, message)
                }
            }
            FirebaseCrashlytics.getInstance().log("[$domain] [${level.name}] $message")
        }
    }

    init {
        if (context.resources.getBoolean(R.bool.crashlytics_enabled)) {
            loggingService.addListener(loggingServiceListener)
            Log.i("[Context] Crashlytics enabled, register logging service listener")
        }

        _lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED

        Log.i("=========================================")
        Log.i("==== Linphone-android information dump ====")
        Log.i("VERSION=${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE}")
        Log.i("PACKAGE=${BuildConfig.APPLICATION_ID}")
        Log.i("BUILD TYPE=${BuildConfig.BUILD_TYPE}")
        Log.i("=========================================")

        if (service != null) {
            Log.i("[Context] Starting foreground service")
            notificationsManager.startForeground(service, useAutoStartDescription)
        }

        core = Factory.instance().createCoreWithConfig(coreConfig, context)

        stopped = false
        _lifecycleRegistry.currentState = Lifecycle.State.CREATED
        Log.i("[Context] Ready")
    }

    fun start() {
        Log.i("[Context] Starting")

        core.addListener(listener)

        // CoreContext listener must be added first!
        if (Version.sdkAboveOrEqual(Version.API26_O_80) && corePreferences.useTelecomManager) {
            if (Compatibility.hasTelecomManagerPermissions(context)) {
                Log.i("[Context] Creating Telecom Helper, disabling audio focus requests in AudioHelper")
                core.config.setBool("audio", "android_disable_audio_focus_requests", true)
                val telecomHelper = TelecomHelper.required(context)
                Log.i("[Context] Telecom Helper created, account is ${if (telecomHelper.isAccountEnabled()) "enabled" else "disabled"}")
            } else {
                Log.w("[Context] Can't create Telecom Helper, permissions have been revoked")
                corePreferences.useTelecomManager = false
            }
        }

        configureCore()

        core.start()
        _lifecycleRegistry.currentState = Lifecycle.State.STARTED

        initPhoneStateListener()

        notificationsManager.onCoreReady()

        collator.strength = Collator.NO_DECOMPOSITION

        if (corePreferences.vfsEnabled) {
            FileUtils.clearExistingPlainFiles()
        }

        if (corePreferences.keepServiceAlive) {
            Log.i("[Context] Background mode setting is enabled, starting Service")
            notificationsManager.startForeground()
        }

        _lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        Log.i("[Context] Started")
    }

    fun stop() {
        Log.i("[Context] Stopping")
        coroutineScope.cancel()

        if (::phoneStateListener.isInitialized) {
            phoneStateListener.destroy()
        }
        notificationsManager.destroy()
        contactsManager.destroy()
        if (TelecomHelper.exists()) {
            Log.i("[Context] Destroying telecom helper")
            TelecomHelper.get().destroy()
            TelecomHelper.destroy()
        }

        core.stop()
        core.removeListener(listener)
        stopped = true
        _lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        loggingService.removeListener(loggingServiceListener)
    }

    private fun configureCore() {
        Log.i("[Context] Configuring Core")

        core.staticPicture = corePreferences.staticPicturePath

        // Migration code
        if (core.config.getBool("app", "incoming_call_vibration", true)) {
            core.isVibrationOnIncomingCallEnabled = true
            core.config.setBool("app", "incoming_call_vibration", false)
        }

        if (core.config.getInt("misc", "conference_layout", 1) > 1) {
            core.config.setInt("misc", "conference_layout", 1)
        }

        // Now LIME server URL is set on accounts
        val limeServerUrl = core.limeX3DhServerUrl.orEmpty()
        if (limeServerUrl.isNotEmpty()) {
            Log.w("[Context] Removing LIME X3DH server URL from Core config")
            core.limeX3DhServerUrl = null
        }

        // Disable Telecom Manager on Android < 10 to prevent crash due to OS bug in Android 9
        if (Version.sdkStrictlyBelow(Version.API29_ANDROID_10)) {
            if (corePreferences.useTelecomManager) {
                Log.w("[Context] Android < 10 detected, disabling telecom manager to prevent crash due to OS bug")
            }
            corePreferences.useTelecomManager = false
            corePreferences.manuallyDisabledTelecomManager = true
        }

        initUserCertificates()

        computeUserAgent()

        for (account in core.accountList) {
            if (account.params.identityAddress?.domain == corePreferences.defaultDomain) {
                var paramsChanged = false
                val params = account.params.clone()

                // Ensure conference factory URI is set on sip.linphone.org proxy configs
                if (account.params.conferenceFactoryUri == null) {
                    val uri = corePreferences.conferenceServerUri
                    Log.i("[Context] Setting conference factory on proxy config ${params.identityAddress?.asString()} to default value: $uri")
                    params.conferenceFactoryUri = uri
                    paramsChanged = true
                }

                // Ensure audio/video conference factory URI is set on sip.linphone.org proxy configs
                if (account.params.audioVideoConferenceFactoryAddress == null) {
                    val uri = corePreferences.audioVideoConferenceServerUri
                    val address = core.interpretUrl(uri, false)
                    if (address != null) {
                        Log.i("[Context] Setting audio/video conference factory on proxy config ${params.identityAddress?.asString()} to default value: $uri")
                        params.audioVideoConferenceFactoryAddress = address
                        paramsChanged = true
                    } else {
                        Log.e("[Context] Failed to parse audio/video conference factory URI: $uri")
                    }
                }

                // Enable Bundle mode by default
                if (!account.params.isRtpBundleEnabled) {
                    Log.i("[Context] Enabling RTP bundle mode on proxy config ${params.identityAddress?.asString()}")
                    params.isRtpBundleEnabled = true
                    paramsChanged = true
                }

                // Ensure we allow CPIM messages in basic chat rooms
                if (!account.params.isCpimInBasicChatRoomEnabled) {
                    params.isCpimInBasicChatRoomEnabled = true
                    paramsChanged = true
                    Log.i("[Context] CPIM allowed in basic chat rooms for account ${params.identityAddress?.asString()}")
                }

                if (account.params.limeServerUrl.isNullOrEmpty()) {
                    if (limeServerUrl.isNotEmpty()) {
                        params.limeServerUrl = limeServerUrl
                        paramsChanged = true
                        Log.i("[Context] Moving Core's LIME X3DH server URL [$limeServerUrl] on account ${params.identityAddress?.asString()}")
                    } else {
                        params.limeServerUrl = corePreferences.limeServerUrl
                        paramsChanged = true
                        Log.w("[Context] Linphone account [${params.identityAddress?.asString()}] didn't have a LIME X3DH server URL, setting one: ${corePreferences.limeServerUrl}")
                    }
                }

                if (paramsChanged) {
                    Log.i("[Context] Account params have been updated, apply changes")
                    account.params = params
                }
            }
        }

        Log.i("[Context] Core configured")
    }

    private fun computeUserAgent() {
        val deviceName: String = corePreferences.deviceName
        val appName: String = context.resources.getString(R.string.user_agent_app_name)
        val androidVersion = BuildConfig.VERSION_NAME
        val userAgent = "$appName/$androidVersion ($deviceName) LinphoneSDK"
        val sdkVersion = context.getString(R.string.linphone_sdk_version)
        val sdkBranch = context.getString(R.string.linphone_sdk_branch)
        val sdkUserAgent = "$sdkVersion ($sdkBranch)"
        core.setUserAgent(userAgent, sdkUserAgent)
    }

    private fun initUserCertificates() {
        val userCertsPath = corePreferences.userCertificatesPath
        val f = File(userCertsPath)
        if (!f.exists()) {
            if (!f.mkdir()) {
                Log.e("[Context] $userCertsPath can't be created.")
            }
        }
        core.userCertificatesPath = userCertsPath
    }

    fun fetchContacts() {
        if (corePreferences.enableNativeAddressBookIntegration) {
            if (PermissionHelper.required(context).hasReadContactsPermission()) {
                Log.i("[Context] Init contacts loader")
                val manager = LoaderManager.getInstance(this@CoreContext)
                manager.restartLoader(0, null, contactLoader)
            }
        }
    }

    fun newAccountConfigured(isLinphoneAccount: Boolean) {
        Log.i("[Context] A new ${if (isLinphoneAccount) AppUtils.getString(R.string.app_name) else "third-party"} account has been configured")

        if (isLinphoneAccount) {
            core.config.setString("sip", "rls_uri", corePreferences.defaultRlsUri)
            val rlsAddress = core.interpretUrl(corePreferences.defaultRlsUri, false)
            if (rlsAddress != null) {
                for (friendList in core.friendsLists) {
                    friendList.rlsAddress = rlsAddress
                }
            }
            if (core.mediaEncryption == MediaEncryption.None) {
                Log.i("[Context] Enabling SRTP media encryption instead of None")
                core.mediaEncryption = MediaEncryption.SRTP
            }
        } else {
            Log.i("[Context] Background mode with foreground service automatically enabled")
            corePreferences.keepServiceAlive = true
            notificationsManager.startForeground()
        }

        contactsManager.updateLocalContacts()
    }

    /* Call related functions */

    fun initPhoneStateListener() {
        if (PermissionHelper.required(context).hasReadPhoneStatePermission()) {
            try {
                phoneStateListener =
                    Compatibility.createPhoneListener(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
            } catch (exception: SecurityException) {
                val hasReadPhoneStatePermission =
                    PermissionHelper.get().hasReadPhoneStateOrPhoneNumbersPermission()
                Log.e("[Context] Failed to create phone state listener: $exception, READ_PHONE_STATE permission status is $hasReadPhoneStatePermission")
            }
        } else {
            Log.w("[Context] Can't create phone state listener, READ_PHONE_STATE permission isn't granted")
        }
    }

    fun declineCallDueToGsmActiveCall(): Boolean {
        if (!corePreferences.useTelecomManager) { // Can't use the following call with Telecom Manager API as it will "fake" GSM calls
            var gsmCallActive = false
            if (::phoneStateListener.isInitialized) {
                gsmCallActive = phoneStateListener.isInCall()
            }

            if (gsmCallActive) {
                Log.w("[Context] Refusing the call with reason busy because a GSM call is active")
                return true
            }
        } else {
            if (TelecomHelper.exists()) {
                if (!TelecomHelper.get().isIncomingCallPermitted() ||
                    TelecomHelper.get().isInManagedCall()
                ) {
                    Log.w("[Context] Refusing the call with reason busy because Telecom Manager will reject the call")
                    return true
                }
            } else {
                Log.e("[Context] Telecom Manager singleton wasn't created!")
            }
        }
        return false
    }

    fun videoUpdateRequestTimedOut(call: Call) {
        coroutineScope.launch {
            Log.w("[Context] 30 seconds have passed, declining video request")
            answerCallVideoUpdateRequest(call, false)
        }
    }

    fun answerCallVideoUpdateRequest(call: Call, accept: Boolean) {
        val params = core.createCallParams(call)

        if (accept) {
            params?.isVideoEnabled = true
            core.isVideoCaptureEnabled = true
            core.isVideoDisplayEnabled = true
        } else {
            params?.isVideoEnabled = false
        }

        call.acceptUpdate(params)
    }

    fun answerCall(call: Call) {
        Log.i("[Context] Answering call $call")
        val params = core.createCallParams(call)
        if (params == null) {
            Log.w("[Context] Answering call without params!")
            call.accept()
            return
        }

        params.recordFile = LinphoneUtils.getRecordingFilePathForAddress(call.remoteAddress)

        if (LinphoneUtils.checkIfNetworkHasLowBandwidth(context)) {
            Log.w("[Context] Enabling low bandwidth mode!")
            params.isLowBandwidthEnabled = true
        }

        if (call.callLog.wasConference()) {
            // Prevent incoming group call to start in audio only layout
            // Do the same as the conference waiting room
            params.isVideoEnabled = true
            params.videoDirection = if (core.videoActivationPolicy.automaticallyInitiate) MediaDirection.SendRecv else MediaDirection.RecvOnly
            Log.i("[Context] Enabling video on call params to prevent audio-only layout when answering")
        }

        call.acceptWithParams(params)
    }

    fun declineCall(call: Call) {
        val voiceMailUri = corePreferences.voiceMailUri
        if (voiceMailUri != null && corePreferences.redirectDeclinedCallToVoiceMail) {
            val voiceMailAddress = core.interpretUrl(voiceMailUri, false)
            if (voiceMailAddress != null) {
                Log.i("[Context] Redirecting call $call to voice mail URI: $voiceMailUri")
                call.redirectTo(voiceMailAddress)
            }
        } else {
            Log.i("[Context] Declining call $call")
            call.decline(Reason.Declined)
        }
    }

    fun terminateCall(call: Call) {
        Log.i("[Context] Terminating call $call")
        call.terminate()
    }

    fun transferCallTo(addressToCall: String): Boolean {
        val currentCall = core.currentCall ?: core.calls.firstOrNull()
        if (currentCall == null) {
            Log.e("[Context] Couldn't find a call to transfer")
        } else {
            val address = core.interpretUrl(addressToCall, LinphoneUtils.applyInternationalPrefix())
            if (address != null) {
                Log.i("[Context] Transferring current call to $addressToCall")
                currentCall.transferTo(address)
                return true
            }
        }
        return false
    }

    fun startCall(to: String) {
        var stringAddress = to.trim()
        if (android.util.Patterns.PHONE.matcher(to).matches()) {
            val contact = contactsManager.findContactByPhoneNumber(to)
            val alias = contact?.getContactForPhoneNumberOrAddress(to)
            if (alias != null) {
                Log.i("[Context] Found matching alias $alias for phone number $to, using it")
                stringAddress = alias
            }
        }

        val address: Address? = core.interpretUrl(stringAddress, LinphoneUtils.applyInternationalPrefix())
        if (address == null) {
            Log.e("[Context] Failed to parse $stringAddress, abort outgoing call")
            callErrorMessageResourceId.value = Event(context.getString(R.string.call_error_network_unreachable))
            return
        }

        startCall(address)
    }

    fun startCall(
        address: Address,
        callParams: CallParams? = null,
        forceZRTP: Boolean = false,
        localAddress: Address? = null
    ) {
        if (!core.isNetworkReachable) {
            Log.e("[Context] Network unreachable, abort outgoing call")
            callErrorMessageResourceId.value = Event(context.getString(R.string.call_error_network_unreachable))
            return
        }

        val params = callParams ?: core.createCallParams(null)
        if (params == null) {
            val call = core.inviteAddress(address)
            Log.w("[Context] Starting call $call without params")
            return
        }

        if (forceZRTP) {
            params.mediaEncryption = MediaEncryption.ZRTP
        }
        if (LinphoneUtils.checkIfNetworkHasLowBandwidth(context)) {
            Log.w("[Context] Enabling low bandwidth mode!")
            params.isLowBandwidthEnabled = true
        }
        params.recordFile = LinphoneUtils.getRecordingFilePathForAddress(address)

        if (localAddress != null) {
            val account = core.accountList.find { account ->
                account.params.identityAddress?.weakEqual(localAddress) ?: false
            }
            if (account != null) {
                params.account = account
                Log.i("[Context] Using account matching address ${localAddress.asStringUriOnly()} as From")
            } else {
                Log.e("[Context] Failed to find account matching address ${localAddress.asStringUriOnly()}")
            }
        }

        if (corePreferences.sendEarlyMedia) {
            params.isEarlyMediaSendingEnabled = true
        }

        val call = core.inviteAddressWithParams(address, params)
        Log.i("[Context] Starting call $call")
    }

    fun switchCamera() {
        val currentDevice = core.videoDevice
        Log.i("[Context] Current camera device is $currentDevice")

        for (camera in core.videoDevicesList) {
            if (camera != currentDevice && camera != "StaticImage: Static picture") {
                Log.i("[Context] New camera device will be $camera")
                core.videoDevice = camera
                break
            }
        }

        val conference = core.conference
        if (conference == null || !conference.isIn) {
            val call = core.currentCall
            if (call == null) {
                Log.w("[Context] Switching camera while not in call")
                return
            }
            call.update(null)
        }
    }

    fun showSwitchCameraButton(): Boolean {
        return core.videoDevicesList.size > 2 // Count StaticImage camera
    }

    fun createCallOverlay() {
        if (!corePreferences.showCallOverlay || !corePreferences.systemWideCallOverlay || callOverlay != null) {
            return
        }

        if (overlayY == 0f) overlayY = AppUtils.pixelsToDp(40f)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // WRAP_CONTENT doesn't work well on some launchers...
        val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
            AppUtils.getDimension(R.dimen.call_overlay_size).toInt(),
            AppUtils.getDimension(R.dimen.call_overlay_size).toInt(),
            Compatibility.getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.x = overlayX.toInt()
        params.y = overlayY.toInt()
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        val overlay = LayoutInflater.from(context).inflate(R.layout.call_overlay, null)

        var initX = overlayX
        var initY = overlayY
        overlay.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x - event.rawX
                    initY = params.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = (event.rawX + initX).toInt()
                    val y = (event.rawY + initY).toInt()

                    params.x = x
                    params.y = y
                    windowManager.updateViewLayout(overlay, params)
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(overlayX - params.x) < CorePreferences.OVERLAY_CLICK_SENSITIVITY &&
                        abs(overlayY - params.y) < CorePreferences.OVERLAY_CLICK_SENSITIVITY
                    ) {
                        view.performClick()
                    }
                    overlayX = params.x.toFloat()
                    overlayY = params.y.toFloat()
                }
                else -> return@setOnTouchListener false
            }
            true
        }
        overlay.setOnClickListener {
            onCallOverlayClick()
        }

        try {
            windowManager.addView(overlay, params)
            callOverlay = overlay
        } catch (e: Exception) {
            Log.e("[Context] Failed to add overlay in windowManager: $e")
        }
    }

    fun onCallOverlayClick() {
        val call = core.currentCall ?: core.calls.firstOrNull()
        if (call != null) {
            Log.i("[Context] Overlay clicked, go back to call view")
            when (call.state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> onIncomingReceived()
                Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> onOutgoingStarted()
                else -> onCallStarted()
            }
        } else {
            Log.e("[Context] Couldn't find call, why is the overlay clicked?!")
        }
    }

    fun removeCallOverlay() {
        if (callOverlay != null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(callOverlay)
            callOverlay = null
        }
    }

    /* Coroutine related */

    private fun exportFileInMessage(message: ChatMessage) {
        // Only do it if auto download feature isn't disabled, otherwise it's done in the user-initiated download process
        if (core.maxSizeForAutoDownloadIncomingFiles != -1) {
            var hasFile = false
            for (content in message.contents) {
                if (content.isFile) {
                    hasFile = true
                    break
                }
            }
            if (hasFile) {
                exportFilesInMessageToMediaStore(message)
            }
        }
    }

    private fun exportFilesInMessageToMediaStore(message: ChatMessage) {
        if (message.isEphemeral) {
            Log.w("[Context] Do not make ephemeral file(s) public")
            return
        }
        if (corePreferences.vfsEnabled) {
            Log.w("[Context] Do not make received file(s) public when VFS is enabled")
            return
        }
        if (!corePreferences.makePublicMediaFilesDownloaded) {
            Log.w("[Context] Making received files public setting disabled")
            return
        }

        if (PermissionHelper.get().hasWriteExternalStoragePermission()) {
            for (content in message.contents) {
                if (content.isFile && content.filePath != null && content.userData == null) {
                    Log.i("[Context] Trying to export file [${content.name}] to MediaStore")
                    addContentToMediaStore(content)
                }
            }
        } else {
            Log.e("[Context] Can't make file public, app doesn't have WRITE_EXTERNAL_STORAGE permission")
        }
    }

    fun addContentToMediaStore(content: Content) {
        if (corePreferences.vfsEnabled) {
            Log.w("[Context] Do not make received file(s) public when VFS is enabled")
            return
        }
        if (!corePreferences.makePublicMediaFilesDownloaded) {
            Log.w("[Context] Making received files public setting disabled")
            return
        }

        if (PermissionHelper.get().hasWriteExternalStoragePermission()) {
            coroutineScope.launch {
                val filePath = content.filePath.orEmpty()
                Log.i("[Context] Trying to export file [$filePath] through Media Store API")

                val extension = FileUtils.getExtensionFromFileName(filePath)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                when {
                    FileUtils.isMimeImage(mime) -> {
                        if (Compatibility.addImageToMediaStore(context, content)) {
                            Log.i("[Context] Successfully exported image [${content.name}] to Media Store")
                        } else {
                            Log.e("[Context] Something went wrong while copying file to Media Store...")
                        }
                    }
                    FileUtils.isMimeVideo(mime) -> {
                        if (Compatibility.addVideoToMediaStore(context, content)) {
                            Log.i("[Context] Successfully exported video [${content.name}] to Media Store")
                        } else {
                            Log.e("[Context] Something went wrong while copying file to Media Store...")
                        }
                    }
                    FileUtils.isMimeAudio(mime) -> {
                        if (Compatibility.addAudioToMediaStore(context, content)) {
                            Log.i("[Context] Successfully exported audio [${content.name}] to Media Store")
                        } else {
                            Log.e("[Context] Something went wrong while copying file to Media Store...")
                        }
                    }
                    else -> {
                        Log.w("[Context] File [$filePath] isn't either an image, an audio file or a video [${content.type}/${content.subtype}], can't add it to the Media Store")
                    }
                }
            }
        }
    }

    fun checkIfForegroundServiceNotificationCanBeRemovedAfterDelay(delayInMs: Long) {
        coroutineScope.launch {
            withContext(Dispatchers.Default) {
                delay(delayInMs)
                withContext(Dispatchers.Main) {
                    if (core.defaultAccount != null && core.defaultAccount?.state == RegistrationState.Ok) {
                        Log.i("[Context] Default account is registered, cancel foreground service notification if possible")
                        notificationsManager.stopForegroundNotificationIfPossible()
                    }
                }
            }
        }
    }

    /* Start call related activities */

    private fun onIncomingReceived() {
        if (corePreferences.preventInterfaceFromShowingUp) {
            Log.w("[Context] We were asked to not show the incoming call screen")
            return
        }

        Log.i("[Context] Starting IncomingCallActivity")
        val intent = Intent(context, org.linphone.activities.voip.CallActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
    }

    private fun onOutgoingStarted() {
        if (corePreferences.preventInterfaceFromShowingUp) {
            Log.w("[Context] We were asked to not show the outgoing call screen")
            return
        }

        Log.i("[Context] Starting OutgoingCallActivity")
        val intent = Intent(context, org.linphone.activities.voip.CallActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
    }

    fun onCallStarted() {
        if (corePreferences.preventInterfaceFromShowingUp) {
            Log.w("[Context] We were asked to not show the call screen")
            return
        }

        Log.i("[Context] Starting CallActivity")
        val intent = Intent(context, org.linphone.activities.voip.CallActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
    }

    /* VFS */

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val ALIAS = "vfs"
        private const val LINPHONE_VFS_ENCRYPTION_AES256GCM128_SHA256 = 2
        private const val VFS_IV = "vfsiv"
        private const val VFS_KEY = "vfskey"

        @Throws(java.lang.Exception::class)
        private fun generateSecretKey() {
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            keyGenerator.generateKey()
        }

        @Throws(java.lang.Exception::class)
        private fun getSecretKey(): SecretKey? {
            val ks = KeyStore.getInstance(ANDROID_KEY_STORE)
            ks.load(null)
            val entry = ks.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        @Throws(java.lang.Exception::class)
        fun generateToken(): String {
            return sha512(UUID.randomUUID().toString())
        }

        @Throws(java.lang.Exception::class)
        private fun encryptData(textToEncrypt: String): Pair<ByteArray, ByteArray> {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            return Pair<ByteArray, ByteArray>(
                iv,
                cipher.doFinal(textToEncrypt.toByteArray(StandardCharsets.UTF_8))
            )
        }

        @Throws(java.lang.Exception::class)
        private fun decryptData(encrypted: String?, encryptionIv: ByteArray): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, encryptionIv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val encryptedData = Base64.decode(encrypted, Base64.DEFAULT)
            return String(cipher.doFinal(encryptedData), StandardCharsets.UTF_8)
        }

        @Throws(java.lang.Exception::class)
        fun encryptToken(string_to_encrypt: String): Pair<String?, String?> {
            val encryptedData = encryptData(string_to_encrypt)
            return Pair<String?, String?>(
                Base64.encodeToString(encryptedData.first, Base64.DEFAULT),
                Base64.encodeToString(encryptedData.second, Base64.DEFAULT)
            )
        }

        @Throws(java.lang.Exception::class)
        fun sha512(input: String): String {
            val md = MessageDigest.getInstance("SHA-512")
            val messageDigest = md.digest(input.toByteArray())
            val no = BigInteger(1, messageDigest)
            var hashtext = no.toString(16)
            while (hashtext.length < 32) {
                hashtext = "0$hashtext"
            }
            return hashtext
        }

        @Throws(java.lang.Exception::class)
        fun getVfsKey(sharedPreferences: SharedPreferences): String {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            return decryptData(
                sharedPreferences.getString(VFS_KEY, null),
                Base64.decode(sharedPreferences.getString(VFS_IV, null), Base64.DEFAULT)
            )
        }

        fun activateVFS() {
            try {
                Log.i("[Context] Activating VFS")
                val preferences = corePreferences.encryptedSharedPreferences
                if (preferences == null) {
                    Log.e("[Context] Can't get encrypted SharedPreferences, can't init VFS")
                    return
                }

                if (preferences.getString(VFS_IV, null) == null) {
                    generateSecretKey()
                    encryptToken(generateToken()).let { data ->
                        preferences
                            .edit()
                            .putString(VFS_IV, data.first)
                            .putString(VFS_KEY, data.second)
                            .commit()
                    }
                }
                Factory.instance().setVfsEncryption(
                    LINPHONE_VFS_ENCRYPTION_AES256GCM128_SHA256,
                    getVfsKey(preferences).toByteArray().copyOfRange(0, 32),
                    32
                )

                Log.i("[Context] VFS activated")
            } catch (e: Exception) {
                Log.f("[Context] Unable to activate VFS encryption: $e")
            }
        }
    }
}
