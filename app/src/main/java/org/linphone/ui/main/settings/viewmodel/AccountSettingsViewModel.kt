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
package org.linphone.ui.main.settings.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.AVPFMode
import org.linphone.core.Account
import org.linphone.core.AuthInfo
import org.linphone.core.Factory
import org.linphone.core.NatPolicy
import org.linphone.core.TransportType
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event

class AccountSettingsViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Account Settings ViewModel]"
    }

    val expandAdvancedSettings = MutableLiveData<Boolean>()

    val expandNatPolicySettings = MutableLiveData<Boolean>()

    val isDomainInPushNotificationCompatibleList = MutableLiveData<Boolean>()

    val pushNotificationsAvailable = MutableLiveData<Boolean>()

    val pushNotificationsEnabled = MutableLiveData<Boolean>()

    val imEncryptionMandatoryAvailable = MediatorLiveData<Boolean>()

    val imEncryptionMandatory = MutableLiveData<Boolean>()

    val availableTransports = arrayListOf<String>()

    val selectedTransport = MutableLiveData<TransportType>()

    val sipProxyServer = MutableLiveData<String>()

    val outboundProxyEnabled = MutableLiveData<Boolean>()

    val stunServer = MutableLiveData<String>()

    val iceEnabled = MutableLiveData<Boolean>()

    val turnEnabled = MutableLiveData<Boolean>()

    val turnUsername = MutableLiveData<String>()

    val turnPassword = MutableLiveData<String>()

    val showTurnPassword = MutableLiveData<Boolean>()

    val avpfEnabled = MutableLiveData<Boolean>()

    val expire = MutableLiveData<String>()

    val conferenceFactoryUri = MutableLiveData<String>()

    val audioVideoConferenceFactoryUri = MutableLiveData<String>()

    val ccmpServerUrl = MutableLiveData<String>()

    val limeServerUrl = MutableLiveData<String>()

    val bundleModeEnabled = MutableLiveData<Boolean>()

    val mwiUri = MutableLiveData<String>()
    val voicemailUri = MutableLiveData<String>()

    val applyPrefix = MutableLiveData<Boolean>()
    val replacePlusBy00 = MutableLiveData<Boolean>()

    val cpimInBasicChatRooms = MutableLiveData<Boolean>()

    val accountFoundEvent = MutableLiveData<Event<Boolean>>()

    val showDeveloperSettings = MutableLiveData<Boolean>()

    val limeAlgorithms = MutableLiveData<String>()

    private lateinit var account: Account
    private lateinit var natPolicy: NatPolicy
    private lateinit var natPolicyAuthInfo: AuthInfo

    init {
        expandAdvancedSettings.value = false
        expandNatPolicySettings.value = false
        showTurnPassword.value = false

        availableTransports.add(TransportType.Udp.name.uppercase(Locale.getDefault()))
        availableTransports.add(TransportType.Tcp.name.uppercase(Locale.getDefault()))
        availableTransports.add(TransportType.Tls.name.uppercase(Locale.getDefault()))

        imEncryptionMandatoryAvailable.addSource(limeServerUrl) {
            imEncryptionMandatoryAvailable.value = isImEncryptionMandatoryAvailable()
        }
        imEncryptionMandatoryAvailable.addSource(conferenceFactoryUri) {
            imEncryptionMandatoryAvailable.value = isImEncryptionMandatoryAvailable()
        }

        showDeveloperSettings.postValue(corePreferences.showDeveloperSettings)
    }

    @UiThread
    fun findAccountMatchingIdentity(identity: String) {
        coreContext.postOnCoreThread { core ->
            val found = core.accountList.find {
                it.params.identityAddress?.asStringUriOnly() == identity
            }
            if (found != null) {
                Log.i("$TAG Found matching account [$found]")
                account = found

                val params = account.params
                val pushAvailableForDomain = params.identityAddress?.domain in corePreferences.pushNotificationCompatibleDomains
                isDomainInPushNotificationCompatibleList.postValue(pushAvailableForDomain)
                if (pushAvailableForDomain) {
                    pushNotificationsAvailable.postValue(core.isPushNotificationAvailable)
                    pushNotificationsEnabled.postValue(
                        core.isPushNotificationAvailable && params.pushNotificationAllowed
                    )
                } else {
                    Log.w("$TAG Account isn't on default domain [${corePreferences.defaultDomain}], do not show push notification settings")
                }

                imEncryptionMandatory.postValue(params.instantMessagingEncryptionMandatory)

                val transportType = params.serverAddress?.transport ?: TransportType.Tls
                selectedTransport.postValue(transportType)

                sipProxyServer.postValue(params.serverAddress?.asStringUriOnly())
                outboundProxyEnabled.postValue(params.isOutboundProxyEnabled)

                natPolicy = params.natPolicy ?: core.createNatPolicy()
                stunServer.postValue(natPolicy.stunServer)
                iceEnabled.postValue(natPolicy.isIceEnabled)
                turnEnabled.postValue(natPolicy.isTurnEnabled)

                val turnStunUsername = natPolicy.stunServerUsername.orEmpty()
                turnUsername.postValue(turnStunUsername)
                if (turnStunUsername.isNotEmpty()) {
                    val authInfo = core.findAuthInfo(null, turnStunUsername, null)
                    if (authInfo == null) {
                        Log.w("$TAG TURN username not empty but unable to find matching auth info!")
                    } else {
                        natPolicyAuthInfo = authInfo
                        turnPassword.postValue(authInfo.password.orEmpty())
                    }
                }

                avpfEnabled.postValue(account.isAvpfEnabled)

                bundleModeEnabled.postValue(params.isRtpBundleEnabled)

                cpimInBasicChatRooms.postValue(params.isCpimInBasicChatRoomEnabled)

                mwiUri.postValue(params.mwiServerAddress?.asStringUriOnly().orEmpty())
                voicemailUri.postValue(params.voicemailAddress?.asStringUriOnly().orEmpty())

                applyPrefix.postValue(params.useInternationalPrefixForCallsAndChats)
                replacePlusBy00.postValue(params.isDialEscapePlusEnabled)

                expire.postValue(params.expires.toString())

                conferenceFactoryUri.postValue(params.conferenceFactoryAddress?.asStringUriOnly())

                audioVideoConferenceFactoryUri.postValue(
                    params.audioVideoConferenceFactoryAddress?.asStringUriOnly()
                )

                ccmpServerUrl.postValue(params.ccmpServerUrl)

                limeServerUrl.postValue(params.limeServerUrl)

                limeAlgorithms.postValue(params.limeAlgo)

                accountFoundEvent.postValue(Event(true))
            } else {
                Log.e("$TAG Failed to find account matching identity [$identity]")
                accountFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun saveChanges() {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Saving changes...")

            if (::account.isInitialized) {
                val newParams = account.params.clone()

                newParams.pushNotificationAllowed = core.isPushNotificationAvailable && pushNotificationsEnabled.value == true

                newParams.instantMessagingEncryptionMandatory = imEncryptionMandatory.value == true

                val server = sipProxyServer.value.orEmpty()
                if (server.isNotEmpty()) {
                    val serverAddress = core.interpretUrl(server, false)
                    if (serverAddress != null) {
                        serverAddress.transport = selectedTransport.value
                        newParams.serverAddress = serverAddress
                    }
                }
                newParams.isOutboundProxyEnabled = outboundProxyEnabled.value == true

                if (::natPolicy.isInitialized) {
                    Log.i("$TAG Also applying changes to NAT policy")
                    natPolicy.stunServer = stunServer.value.orEmpty().trim()
                    natPolicy.isStunEnabled = stunServer.value.orEmpty().isNotEmpty()
                    natPolicy.isIceEnabled = iceEnabled.value == true
                    natPolicy.isTurnEnabled = turnEnabled.value == true
                    val stunTurnUsername = turnUsername.value.orEmpty().trim()
                    natPolicy.stunServerUsername = stunTurnUsername
                    newParams.natPolicy = natPolicy

                    if (::natPolicyAuthInfo.isInitialized) {
                        if (stunTurnUsername.isEmpty()) {
                            Log.i(
                                "$TAG NAT policy TURN username is now empty, removing existing auth info"
                            )
                            core.removeAuthInfo(natPolicyAuthInfo)
                        } else {
                            Log.i("$TAG Found NAT policy auth info, updating it")
                            natPolicyAuthInfo.username = stunTurnUsername
                            natPolicyAuthInfo.password = turnPassword.value.orEmpty()
                        }
                    } else if (stunTurnUsername.isNotEmpty()) {
                        Log.i("$TAG No NAT policy auth info found, creating it with")
                        val authInfo = Factory.instance().createAuthInfo(
                            stunTurnUsername,
                            null,
                            turnPassword.value.orEmpty(),
                            null,
                            null,
                            null
                        )
                        core.addAuthInfo(authInfo)
                    }
                }

                newParams.avpfMode = if (avpfEnabled.value == true) AVPFMode.Enabled else AVPFMode.Disabled

                newParams.isRtpBundleEnabled = bundleModeEnabled.value == true

                newParams.isCpimInBasicChatRoomEnabled = cpimInBasicChatRooms.value == true

                val mwi = mwiUri.value.orEmpty().trim()
                if (mwi.isNotEmpty()) {
                    val mwiAddress = core.interpretUrl(mwi, false)
                    newParams.mwiServerAddress = mwiAddress
                } else {
                    newParams.mwiServerAddress = null
                }

                val voicemail = voicemailUri.value.orEmpty().trim()
                if (voicemail.isNotEmpty()) {
                    val voicemailAddress = core.interpretUrl(voicemail, false)
                    newParams.voicemailAddress = voicemailAddress
                } else {
                    newParams.voicemailAddress = null
                }

                val expire = expire.value.orEmpty()
                val expireInt = if (expire.isEmpty()) {
                    31536000
                } else {
                    try {
                        expire.toInt()
                    } catch (_: NumberFormatException) {
                        31536000
                    }
                }
                newParams.expires = expireInt

                val conferenceUri = conferenceFactoryUri.value.orEmpty()
                if (conferenceUri.isNotEmpty()) {
                    val conferenceFactoryAddress = core.interpretUrl(conferenceUri, false)
                    newParams.conferenceFactoryAddress = conferenceFactoryAddress
                } else {
                    newParams.conferenceFactoryAddress = null
                }

                val audioVideoConferenceUri = audioVideoConferenceFactoryUri.value.orEmpty()
                if (audioVideoConferenceUri.isNotEmpty()) {
                    val audioVideoConferenceFactoryAddress = core.interpretUrl(
                        audioVideoConferenceUri,
                        false
                    )
                    newParams.audioVideoConferenceFactoryAddress =
                        audioVideoConferenceFactoryAddress
                } else {
                    newParams.audioVideoConferenceFactoryAddress = null
                }

                newParams.ccmpServerUrl = ccmpServerUrl.value.orEmpty().trim()
                newParams.limeServerUrl = limeServerUrl.value.orEmpty().trim()
                newParams.limeAlgo = limeAlgorithms.value.orEmpty().trim()

                newParams.useInternationalPrefixForCallsAndChats = applyPrefix.value == true
                newParams.isDialEscapePlusEnabled = replacePlusBy00.value == true

                account.params = newParams
                Log.i("$TAG Changes have been saved")
            }
        }
    }

    @UiThread
    fun isImEncryptionMandatoryAvailable(): Boolean {
        return limeServerUrl.value.orEmpty().isNotEmpty() && conferenceFactoryUri.value.orEmpty().isNotEmpty()
    }

    @UiThread
    fun toggleNatPolicySettingsExpand() {
        expandNatPolicySettings.value = expandNatPolicySettings.value == false
    }

    @UiThread
    fun toggleAdvancedSettingsExpand() {
        expandAdvancedSettings.value = expandAdvancedSettings.value == false
    }

    @UiThread
    fun updateAccountPassword(newPassword: String) {
        coreContext.postOnCoreThread { core ->
            if (::account.isInitialized) {
                val authInfo = account.findAuthInfo()
                if (authInfo != null) {
                    Log.i(
                        "$TAG Updating password for username [${authInfo.username}] using auth info [$authInfo]"
                    )
                    authInfo.password = newPassword
                    core.addAuthInfo(authInfo)
                    core.refreshRegisters()
                } else {
                    Log.w(
                        "$TAG Failed to find auth info for account [${account.params.identityAddress?.asStringUriOnly()}]"
                    )
                }
            }
        }
    }

    @UiThread
    fun toggleShowTurnPassword() {
        showTurnPassword.value = showTurnPassword.value == false
    }
}
