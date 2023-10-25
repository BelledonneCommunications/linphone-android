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
package org.linphone.ui.main.meetings.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Conference
import org.linphone.core.ConferenceInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class MeetingWaitingRoomViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Meeting Waiting Room ViewModel]"
    }

    val subject = MutableLiveData<String>()

    val dateTime = MutableLiveData<String>()

    val selfAvatar = MutableLiveData<ContactAvatarModel>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isVideoAvailable = MutableLiveData<Boolean>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isSwitchCameraAvailable = MutableLiveData<Boolean>()

    val conferenceInfoFoundEvent = MutableLiveData<Event<Boolean>>()

    val conferenceCreatedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var conferenceInfo: ConferenceInfo

    private val coreListener = object : CoreListenerStub() {
        override fun onConferenceStateChanged(
            core: Core,
            conference: Conference,
            state: Conference.State?
        ) {
            Log.i("$TAG Conference state changed: [$state]")
            if (conference.state == Conference.State.Created) {
                conferenceCreatedEvent.postValue(Event(true))
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun findConferenceInfo(uri: String) {
        coreContext.postOnCoreThread { core ->
            val address = Factory.instance().createAddress(uri)
            if (address != null) {
                val found = core.findConferenceInformationFromUri(address)
                if (found != null) {
                    Log.i("$TAG Conference info with SIP URI [$uri] was found")
                    conferenceInfo = found
                    configureConferenceInfo()
                    configureWaitingRoom()
                    conferenceInfoFoundEvent.postValue(Event(true))
                } else {
                    Log.e("$TAG Conference info with SIP URI [$uri] couldn't be found!")
                    conferenceInfoFoundEvent.postValue(Event(false))
                }
            } else {
                Log.e("$TAG Failed to parse SIP URI [$uri] as Address!")
                conferenceInfoFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun setFrontCamera() {
        coreContext.postOnCoreThread { core ->
            for (camera in core.videoDevicesList) {
                if (camera.contains("Front")) {
                    Log.i("$TAG Found front facing camera [$camera], using it")
                    coreContext.core.videoDevice = camera
                    return@postOnCoreThread
                }
            }

            val first = core.videoDevicesList.firstOrNull()
            if (first != null) {
                Log.w("$TAG No front facing camera found, using first one available [$first]")
                coreContext.core.videoDevice = first
            }
        }
    }

    @UiThread
    fun join() {
        coreContext.postOnCoreThread { core ->
            if (::conferenceInfo.isInitialized) {
                val conferenceUri = conferenceInfo.uri
                if (conferenceUri == null) {
                    Log.e("$TAG Conference Info doesn't have a conference SIP URI to call!")
                    return@postOnCoreThread
                }

                val params = core.createCallParams(null)
                params ?: return@postOnCoreThread

                params.isVideoEnabled = isVideoEnabled.value == true
                params.isMicEnabled = isMicrophoneMuted.value == false
                params.account = core.defaultAccount
                coreContext.startCall(conferenceUri, params)
            }
        }
    }

    @UiThread
    fun switchCamera() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Switching camera")
            coreContext.switchCamera()
        }
    }

    @UiThread
    fun toggleVideo() {
        isVideoEnabled.value = isVideoEnabled.value == false
    }

    @UiThread
    fun toggleMuteMicrophone() {
        isMicrophoneMuted.value = isMicrophoneMuted.value == false
    }

    @UiThread
    fun toggleSpeaker() {
        // TODO
    }

    @WorkerThread
    private fun configureConferenceInfo() {
        if (::conferenceInfo.isInitialized) {
            subject.postValue(conferenceInfo.subject)

            val timestamp = conferenceInfo.dateTime
            val duration = conferenceInfo.duration
            val date = TimestampUtils.toString(
                timestamp,
                onlyDate = true,
                shortDate = false,
                hideYear = false
            )
            val startTime = TimestampUtils.timeToString(timestamp)
            val end = timestamp + (duration * 60)
            val endTime = TimestampUtils.timeToString(end)
            dateTime.postValue("$date | $startTime - $endTime")

            val localAddress = coreContext.core.defaultAccount?.params?.identityAddress
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = localAddress
            fakeFriend.name = LinphoneUtils.getDisplayName(localAddress)
            val avatarModel = ContactAvatarModel(fakeFriend)
            selfAvatar.postValue(avatarModel)
        }
    }

    @WorkerThread
    private fun configureWaitingRoom() {
        val core = coreContext.core

        isVideoEnabled.postValue(
            core.isVideoEnabled && core.videoActivationPolicy.automaticallyInitiate
        )
        isSwitchCameraAvailable.postValue(coreContext.showSwitchCameraButton())

        isMicrophoneMuted.postValue(!core.isMicEnabled)

        // TODO: audio routes
    }
}
