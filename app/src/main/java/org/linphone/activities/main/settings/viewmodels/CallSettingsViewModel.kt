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
package org.linphone.activities.main.settings.viewmodels

import androidx.lifecycle.MutableLiveData
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.core.MediaEncryption
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version
import org.linphone.telecom.TelecomHelper
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class CallSettingsViewModel : GenericSettingsViewModel() {
    val deviceRingtoneListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.ring = if (newValue) null else prefs.defaultRingtonePath
        }
    }
    val deviceRingtone = MutableLiveData<Boolean>()

    val ringtoneListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            if (position == 0) {
                core.ring = null
            } else {
                core.ring = ringtoneValues[position]
            }
        }
    }
    val ringtoneIndex = MutableLiveData<Int>()
    val ringtoneLabels = MutableLiveData<ArrayList<String>>()
    private val ringtoneValues = arrayListOf<String>()
    val showRingtonesList = MutableLiveData<Boolean>()

    val vibrateOnIncomingCallListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isVibrationOnIncomingCallEnabled = newValue
        }
    }
    val vibrateOnIncomingCall = MutableLiveData<Boolean>()

    val encryptionListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            core.mediaEncryption = MediaEncryption.fromInt(encryptionValues[position])
            encryptionIndex.value = position
            if (position == 0) {
                encryptionMandatory.value = false
            }
        }
    }
    val encryptionIndex = MutableLiveData<Int>()
    val encryptionLabels = MutableLiveData<ArrayList<String>>()
    private val encryptionValues = arrayListOf<Int>()

    val encryptionMandatoryListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isMediaEncryptionMandatory = newValue
        }
    }
    val encryptionMandatory = MutableLiveData<Boolean>()

    val useTelecomManagerListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            if (newValue) {
                enableTelecomManagerEvent.value = Event(true)
            } else {
                if (TelecomHelper.exists()) {
                    Log.i("[Call Settings] Removing Telecom Manager account & destroying singleton")
                    TelecomHelper.get().removeAccount()
                    TelecomHelper.get().destroy()
                    TelecomHelper.destroy()

                    Log.w("[Call Settings] Disabling Telecom Manager auto-enable")
                    prefs.manuallyDisabledTelecomManager = true
                }
                prefs.useTelecomManager = newValue
            }
        }
    }
    val useTelecomManager = MutableLiveData<Boolean>()
    val enableTelecomManagerEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }
    val api29OrHigher = MutableLiveData<Boolean>()

    val overlayListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.showCallOverlay = newValue
        }
    }
    val overlay = MutableLiveData<Boolean>()

    val systemWideOverlayListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            if (newValue) systemWideOverlayEnabledEvent.value = Event(true)
            prefs.systemWideCallOverlay = newValue
        }
    }
    val systemWideOverlay = MutableLiveData<Boolean>()
    val systemWideOverlayEnabledEvent = MutableLiveData<Event<Boolean>>()

    val sipInfoDtmfListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.useInfoForDtmf = newValue
        }
    }
    val sipInfoDtmf = MutableLiveData<Boolean>()

    val rfc2833DtmfListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.useRfc2833ForDtmf = newValue
        }
    }
    val rfc2833Dtmf = MutableLiveData<Boolean>()

    val autoStartCallRecordingListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.automaticallyStartCallRecording = newValue
        }
    }
    val autoStartCallRecording = MutableLiveData<Boolean>()

    val remoteCallRecordingListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isRecordAwareEnabled = newValue
        }
    }
    val remoteCallRecording = MutableLiveData<Boolean>()

    val autoStartListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.callRightAway = newValue
        }
    }
    val autoStart = MutableLiveData<Boolean>()

    val autoAnswerListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.autoAnswerEnabled = newValue
        }
    }
    val autoAnswer = MutableLiveData<Boolean>()

    val autoAnswerDelayListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                prefs.autoAnswerDelay = newValue.toInt()
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val autoAnswerDelay = MutableLiveData<Int>()

    val incomingTimeoutListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                core.incTimeout = newValue.toInt()
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val incomingTimeout = MutableLiveData<Int>()

    val voiceMailUriListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            voiceMailUri.value = newValue
            prefs.voiceMailUri = newValue
        }
    }
    val voiceMailUri = MutableLiveData<String>()

    val redirectToVoiceMailIncomingDeclinedCallsListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.redirectDeclinedCallToVoiceMail = newValue
        }
    }
    val redirectToVoiceMailIncomingDeclinedCalls = MutableLiveData<Boolean>()

    val acceptEarlyMediaListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.acceptEarlyMedia = newValue
        }
    }
    val acceptEarlyMedia = MutableLiveData<Boolean>()

    val ringDuringEarlyMediaListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.ringDuringIncomingEarlyMedia = newValue
        }
    }
    val ringDuringEarlyMedia = MutableLiveData<Boolean>()

    val pauseCallsWhenAudioFocusIsLostListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.pauseCallsWhenAudioFocusIsLost = newValue
        }
    }

    val pauseCallsWhenAudioFocusIsLost = MutableLiveData<Boolean>()

    val goToAndroidNotificationSettingsListener = object : SettingListenerStub() {
        override fun onClicked() {
            goToAndroidNotificationSettingsEvent.value = Event(true)
        }
    }
    val goToAndroidNotificationSettingsEvent = MutableLiveData<Event<Boolean>>()

    init {
        initRingtonesList()
        deviceRingtone.value = core.ring == null
        showRingtonesList.value = prefs.showAllRingtones

        vibrateOnIncomingCall.value = core.isVibrationOnIncomingCallEnabled

        initEncryptionList()
        encryptionMandatory.value = core.isMediaEncryptionMandatory

        useTelecomManager.value = prefs.useTelecomManager
        api29OrHigher.value = Version.sdkAboveOrEqual(Version.API29_ANDROID_10)

        overlay.value = prefs.showCallOverlay
        systemWideOverlay.value = prefs.systemWideCallOverlay
        sipInfoDtmf.value = core.useInfoForDtmf
        rfc2833Dtmf.value = core.useRfc2833ForDtmf
        autoStartCallRecording.value = prefs.automaticallyStartCallRecording
        remoteCallRecording.value = core.isRecordAwareEnabled
        autoStart.value = prefs.callRightAway
        autoAnswer.value = prefs.autoAnswerEnabled
        autoAnswerDelay.value = prefs.autoAnswerDelay
        incomingTimeout.value = core.incTimeout
        voiceMailUri.value = prefs.voiceMailUri
        redirectToVoiceMailIncomingDeclinedCalls.value = prefs.redirectDeclinedCallToVoiceMail
        acceptEarlyMedia.value = prefs.acceptEarlyMedia
        ringDuringEarlyMedia.value = core.ringDuringIncomingEarlyMedia
        pauseCallsWhenAudioFocusIsLost.value = prefs.pauseCallsWhenAudioFocusIsLost
    }

    private fun initRingtonesList() {
        val labels = arrayListOf<String>()
        labels.add(AppUtils.getString(R.string.call_settings_device_ringtone_title))
        ringtoneValues.add("")

        val directory = File(prefs.ringtonesPath)
        val files = directory.listFiles()
        for (ringtone in files.orEmpty()) {
            if (ringtone.absolutePath.endsWith(".mkv")) {
                val name = ringtone.name
                    .substringBefore(".")
                    .replace("_", " ")
                    .capitalize(Locale.getDefault())
                labels.add(name)
                ringtoneValues.add(ringtone.absolutePath)
            }
        }

        ringtoneLabels.value = labels
        ringtoneIndex.value = if (core.ring == null) 0 else ringtoneValues.indexOf(core.ring)
    }

    private fun initEncryptionList() {
        val labels = arrayListOf<String>()

        labels.add(prefs.getString(R.string.call_settings_media_encryption_none))
        encryptionValues.add(MediaEncryption.None.toInt())

        if (core.mediaEncryptionSupported(MediaEncryption.SRTP)) {
            labels.add(prefs.getString(R.string.call_settings_media_encryption_srtp))
            encryptionValues.add(MediaEncryption.SRTP.toInt())
        }
        if (core.mediaEncryptionSupported(MediaEncryption.ZRTP)) {
            if (core.postQuantumAvailable) {
                labels.add(prefs.getString(R.string.call_settings_media_encryption_zrtp_post_quantum))
            } else {
                labels.add(prefs.getString(R.string.call_settings_media_encryption_zrtp))
            }
            encryptionValues.add(MediaEncryption.ZRTP.toInt())
        }
        if (core.mediaEncryptionSupported(MediaEncryption.DTLS)) {
            labels.add(prefs.getString(R.string.call_settings_media_encryption_dtls))
            encryptionValues.add(MediaEncryption.DTLS.toInt())
        }

        encryptionLabels.value = labels
        encryptionIndex.value = encryptionValues.indexOf(core.mediaEncryption.toInt())
    }
}
