/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.emoji2.text.EmojiCompat
import java.util.*
import org.linphone.BuildConfig
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.contacts.ContactsManager
import org.linphone.core.tools.Log
import org.linphone.ui.voip.VoipActivity

class CoreContext(val context: Context) : HandlerThread("Core Thread") {
    lateinit var core: Core

    lateinit var emojiCompat: EmojiCompat

    val contactsManager = ContactsManager()

    private val mainThread = Handler(Looper.getMainLooper())

    @SuppressLint("HandlerLeak")
    private lateinit var coreThread: Handler

    private val coreListener = object : CoreListenerStub() {
        override fun onGlobalStateChanged(core: Core, state: GlobalState, message: String) {
            Log.i("[Context] Global state changed: $state")
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.i("[Context] Call state changed [$state]")
            if (state == Call.State.OutgoingProgress) {
                showCallActivity()
            } else if (state == Call.State.IncomingReceived) {
                // TODO FIXME : remove when full screen intent notification
                showCallActivity()
            }
        }
    }

    init {
        EmojiCompat.init(context)
        emojiCompat = EmojiCompat.get()
    }

    override fun run() {
        Looper.prepare()

        val looper = Looper.myLooper() ?: return
        coreThread = Handler(looper)

        core = Factory.instance().createCoreWithConfig(corePreferences.config, context)

        core.isAutoIterateEnabled = false
        core.addListener(coreListener)

        val timer = Timer("Linphone core.iterate() scheduler")
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    coreThread.post {
                        core.iterate()
                    }
                }
            },
            0,
            50
        )

        computeUserAgent()
        core.start()

        contactsManager.onCoreStarted()

        Looper.loop()
    }

    override fun destroy() {
        core.stop()
        contactsManager.onCoreStopped()

        quitSafely()
    }

    fun isReady(): Boolean {
        return ::core.isInitialized
    }

    fun postOnCoreThread(lambda: (core: Core) -> Unit) {
        coreThread.post {
            lambda.invoke(core)
        }
    }

    fun postOnMainThread(lambda: () -> Unit) {
        mainThread.post {
            lambda.invoke()
        }
    }

    fun startCall(
        address: Address,
        callParams: CallParams? = null,
        forceZRTP: Boolean = false,
        localAddress: Address? = null
    ) {
        if (!core.isNetworkReachable) {
            Log.e("[Context] Network unreachable, abort outgoing call")
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
        /*if (LinphoneUtils.checkIfNetworkHasLowBandwidth(context)) {
            Log.w("[Context] Enabling low bandwidth mode!")
            params.isLowBandwidthEnabled = true
        }
        params.recordFile = LinphoneUtils.getRecordingFilePathForAddress(address)*/

        if (localAddress != null) {
            val account = core.accountList.find { account ->
                account.params.identityAddress?.weakEqual(localAddress) ?: false
            }
            if (account != null) {
                params.account = account
                Log.i(
                    "[Context] Using account matching address ${localAddress.asStringUriOnly()} as From"
                )
            } else {
                Log.e(
                    "[Context] Failed to find account matching address ${localAddress.asStringUriOnly()}"
                )
            }
        }

        /*if (corePreferences.sendEarlyMedia) {
            params.isEarlyMediaSendingEnabled = true
        }*/

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

        val call = core.currentCall
        if (call == null) {
            Log.w("[Context] Switching camera while not in call")
            return
        }
        call.update(null)
    }

    fun showSwitchCameraButton(): Boolean {
        return core.videoDevicesList.size > 2 // Count StaticImage camera
    }

    private fun showCallActivity() {
        Log.i("[Context] Starting VoIP activity")
        val intent = Intent(context, VoipActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        context.startActivity(intent)
    }

    private fun computeUserAgent() {
        // TODO FIXME
        val deviceName: String = "Linphone6"
        val appName: String = "Linphone Android"
        val androidVersion = BuildConfig.VERSION_NAME
        val userAgent = "$appName/$androidVersion ($deviceName) LinphoneSDK"
        val sdkVersion = context.getString(org.linphone.core.R.string.linphone_sdk_version)
        val sdkBranch = context.getString(org.linphone.core.R.string.linphone_sdk_branch)
        val sdkUserAgent = "$sdkVersion ($sdkBranch)"
        core.setUserAgent(userAgent, sdkUserAgent)
    }
}
