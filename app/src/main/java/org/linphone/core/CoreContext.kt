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
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.*
import androidx.lifecycle.MutableLiveData
import java.io.File
import kotlin.math.abs
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.call.CallActivity
import org.linphone.activities.call.IncomingCallActivity
import org.linphone.activities.call.OutgoingCallActivity
import org.linphone.compatibility.Compatibility
import org.linphone.contact.Contact
import org.linphone.contact.ContactsManager
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version
import org.linphone.notifications.NotificationsManager
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CoreContext(val context: Context, coreConfig: Config) {
    var stopped = false
    val core: Core
    val handler: Handler = Handler(Looper.getMainLooper())

    val appVersion: String by lazy {
        "${org.linphone.BuildConfig.VERSION_NAME} (${org.linphone.BuildConfig.BUILD_TYPE})"
    }

    val sdkVersion: String by lazy {
        val sdkVersion = context.getString(R.string.linphone_sdk_version)
        val sdkBranch = context.getString(R.string.linphone_sdk_branch)
        "$sdkVersion ($sdkBranch)"
    }

    val contactsManager: ContactsManager by lazy {
        ContactsManager(context)
    }
    val notificationsManager: NotificationsManager by lazy {
        NotificationsManager(context)
    }

    val callErrorMessageResourceId: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }

    private var gsmCallActive = false
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            gsmCallActive = when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    Log.i("[Context] Phone state is off hook")
                    true
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.i("[Context] Phone state is ringing")
                    true
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    Log.i("[Context] Phone state is idle")
                    false
                }
                else -> {
                    Log.w("[Context] Phone state is unexpected: $state")
                    false
                }
            }
        }
    }

    private var overlayX = 0f
    private var overlayY = 0f
    private var callOverlay: View? = null
    private var isVibrating = false

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onGlobalStateChanged(core: Core, state: GlobalState, message: String) {
            Log.i("[Context] Global state changed [$state]")
            if (state == GlobalState.On) {
                contactsManager.fetchContactsAsync()
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            Log.i("[Context] Call state changed [$state]")
            if (state == Call.State.IncomingReceived || state == Call.State.IncomingEarlyMedia) {
                if (gsmCallActive) {
                    Log.w("[Context] Refusing the call with reason busy because a GSM call is active")
                    call.decline(Reason.Busy)
                    return
                }

                if (core.callsNb == 1 && corePreferences.vibrateWhileIncomingCall) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    if ((audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE ||
                                audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL)) {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        if (vibrator.hasVibrator()) {
                            Log.i("[Context] Starting incoming call vibration")
                            Compatibility.vibrate(vibrator)
                            isVibrating = true
                        }
                    }
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
                        val mainThreadHandler = Handler(Looper.getMainLooper())
                        mainThreadHandler.postDelayed({
                            Log.w("[Context] Auto answering call")
                            answerCall(call)
                        }, autoAnswerDelay.toLong())
                    }
                }
            } else if (state == Call.State.OutgoingInit) {
                onOutgoingStarted()
            } else if (state == Call.State.Connected) {
                if (isVibrating) {
                    Log.i("[Context] Stopping vibration")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.cancel()
                    isVibrating = false
                }

                onCallStarted()
            } else if (state == Call.State.End || state == Call.State.Error || state == Call.State.Released) {
                if (core.callsNb == 0) {
                    if (isVibrating) {
                        Log.i("[Context] Stopping vibration")
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        vibrator.cancel()
                        isVibrating = false
                    }

                    removeCallOverlay()
                }

                if (state == Call.State.Error) {
                    Log.w("[Context] Call error reason is ${call.errorInfo.reason}")
                    val id = when (call.errorInfo.reason) {
                        Reason.Busy -> R.string.call_error_user_busy
                        Reason.IOError -> R.string.call_error_io_error
                        Reason.NotAcceptable -> R.string.call_error_incompatible_media_params
                        Reason.NotFound -> R.string.call_error_user_not_found
                        else -> R.string.call_error_unknown
                    }
                    callErrorMessageResourceId.value = Event(id)
                } else if (state == Call.State.End && call.errorInfo.reason == Reason.Declined) {
                    Log.i("[Context] Call has been declined")
                    val id = R.string.call_error_declined
                    callErrorMessageResourceId.value = Event(id)
                }
            }
        }
    }

    init {
        core = Factory.instance().createCoreWithConfig(coreConfig, context)
        stopped = false
        Log.i("[Context] Ready")
    }

    fun start(isPush: Boolean = false) {
        Log.i("[Context] Starting")

        notificationsManager.onCoreReady()

        core.addListener(listener)
        if (isPush) {
            Log.i("[Context] Push received, assume in background")
            core.enterBackground()
        }
        core.config.setBool("net", "use_legacy_push_notification_params", true)
        core.start()

        configureCore()

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        Log.i("[Context] Registering phone state listener")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    fun stop() {
        Log.i("[Context] Stopping")

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        Log.i("[Context] Unregistering phone state listener")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)

        notificationsManager.destroy()
        contactsManager.destroy()

        core.stop()
        core.removeListener(listener)
        stopped = true
    }

    private fun configureCore() {
        Log.i("[Context] Configuring Core")

        core.zrtpSecretsFile = context.filesDir.absolutePath + "/zrtp_secrets"
        core.callLogsDatabasePath = context.filesDir.absolutePath + "/linphone-log-history.db"

        initUserCertificates()

        computeUserAgent()

        for (lpc in core.proxyConfigList) {
            if (lpc.identityAddress?.domain == corePreferences.defaultDomain) {
                // Ensure conference URI is set on sip.linphone.org proxy configs
                if (lpc.conferenceFactoryUri == null) {
                    lpc.edit()
                    val uri = corePreferences.conferenceServerUri
                    Log.i("[Context] Setting conference factory on proxy config ${lpc.identityAddress?.asString()} to default value: $uri")
                    lpc.conferenceFactoryUri = uri
                    lpc.done()
                }

                // Ensure LIME server URL is set if at least one sip.linphone.org proxy
                if (core.limeX3DhAvailable()) {
                    var url: String? = core.limeX3DhServerUrl
                    if (url == null || url.isEmpty()) {
                        url = corePreferences.limeX3dhServerUrl
                        Log.i("[Context] Setting LIME X3Dh server url to default value: $url")
                        core.limeX3DhServerUrl = url
                    }
                }
            }
        }

        Log.i("[Context] Core configured")
    }

    private fun computeUserAgent() {
        val deviceName: String = corePreferences.deviceName
        val appName: String = context.resources.getString(R.string.app_name)
        val androidVersion = org.linphone.BuildConfig.VERSION_NAME
        val userAgent = "$appName/$androidVersion ($deviceName) LinphoneSDK"
        val sdkVersion = context.getString(R.string.linphone_sdk_version)
        val sdkBranch = context.getString(R.string.linphone_sdk_branch)
        val sdkUserAgent = "$sdkVersion ($sdkBranch)"
        core.setUserAgent(userAgent, sdkUserAgent)
    }

    private fun initUserCertificates() {
        val userCertsPath = context.filesDir.absolutePath + "/user-certs"
        val f = File(userCertsPath)
        if (!f.exists()) {
            if (!f.mkdir()) {
                Log.e("[Context] $userCertsPath can't be created.")
            }
        }
        core.userCertificatesPath = userCertsPath
    }

    /* Call related functions */

    fun answerCallUpdateRequest(call: Call, accept: Boolean) {
        val params = core.createCallParams(call)

        if (accept) {
            params?.enableVideo(true)
            core.enableVideoCapture(true)
            core.enableVideoDisplay(true)
        }

        call.acceptUpdate(params)
    }

    fun answerCall(call: Call) {
        Log.i("[Context] Answering call $call")
        val params = core.createCallParams(call)
        params?.recordFile = LinphoneUtils.getRecordingFilePathForAddress(call.remoteAddress)
        call.acceptWithParams(params)
    }

    fun declineCall(call: Call) {
        val voiceMailUri = corePreferences.voiceMailUri
        if (voiceMailUri != null && corePreferences.redirectDeclinedCallToVoiceMail) {
            Log.i("[Context] Redirecting call $call to voice mail")
            call.redirect(voiceMailUri)
        } else {
            Log.i("[Context] Declining call $call")
            call.decline(Reason.Declined)
        }
    }

    fun terminateCall(call: Call) {
        Log.i("[Context] Terminating call $call")
        call.terminate()
    }

    fun transferCallTo(addressToCall: String) {
        val currentCall = core.currentCall ?: core.calls.first()
        if (currentCall == null) {
            Log.e("[Context] Couldn't find a call to transfer")
        } else {
            Log.i("[Context] Transferring current call to $addressToCall")
            currentCall.transfer(addressToCall)
        }
    }

    fun startCall(to: String) {
        var stringAddress = to
        if (android.util.Patterns.PHONE.matcher(to).matches()) {
            val contact: Contact? = contactsManager.findContactByPhoneNumber(to)
            val alias = contact?.getContactForPhoneNumberOrAddress(to)
            if (alias != null) {
                Log.i("[Context] Found matching alias $alias for phone number $to, using it")
                stringAddress = alias
            }
        }

        val address: Address? = core.interpretUrl(stringAddress)
        if (address == null) {
            Log.e("[Context] Failed to parse $stringAddress, abort outgoing call")
            callErrorMessageResourceId.value = Event(R.string.call_error_network_unreachable)
            return
        }

        startCall(address)
    }

    fun startCall(address: Address, forceZRTP: Boolean = false) {
        if (!core.isNetworkReachable) {
            Log.e("[Context] Network unreachable, abort outgoing call")
            callErrorMessageResourceId.value = Event(R.string.call_error_network_unreachable)
            return
        }

        val params = core.createCallParams(null)
        if (forceZRTP) {
            params?.mediaEncryption = MediaEncryption.ZRTP
        }
        params?.recordFile = LinphoneUtils.getRecordingFilePathForAddress(address)

        val call = if (params != null) {
            core.inviteAddressWithParams(address, params)
        } else {
            core.inviteAddress(address)
        }
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

        if (core.conference != null && core.isInConference) {
            // Nothing to do
        } else {
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

    fun isVideoCallOrConferenceActive(): Boolean {
        val conference = core.conference
        return if (conference != null && core.isInConference) {
            conference.currentParams.videoEnabled()
        } else {
            core.currentCall?.currentParams?.videoEnabled() ?: false
        }
    }

    fun createCallOverlay() {
        if (!corePreferences.showCallOverlay || callOverlay != null) {
            return
        }

        if (overlayY == 0f) overlayY = AppUtils.pixelsToDp(40f)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params: WindowManager.LayoutParams = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            Compatibility.getOverlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
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
                    if (abs(overlayX - params.x) < 5 && abs(overlayY - params.y) < 5) {
                        view.performClick()
                    }
                    overlayX = params.x.toFloat()
                    overlayY = params.y.toFloat()
                }
                else -> false
            }
            true
        }
        overlay.setOnClickListener {
            Log.i("[Context] Overlay clicked, go back to call view")
            onCallStarted()
        }

        callOverlay = overlay
        windowManager.addView(overlay, params)
    }

    fun removeCallOverlay() {
        if (callOverlay != null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(callOverlay)
            callOverlay = null
        }
    }

    /* Start call related activities */

    private fun onIncomingReceived() {
        Log.i("[Context] Starting IncomingCallActivity")
        val intent = Intent(context, IncomingCallActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun onOutgoingStarted() {
        Log.i("[Context] Starting OutgoingCallActivity")
        val intent = Intent(context, OutgoingCallActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun onCallStarted() {
        Log.i("[Context] Starting CallActivity")
        val intent = Intent(context, CallActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
    }
}
