/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.conference.viewmodels

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.conference.data.ConferenceSchedulingParticipantData
import org.linphone.activities.main.conference.data.Duration
import org.linphone.activities.main.conference.data.TimeZoneData
import org.linphone.contact.ContactsSelectionViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.TimestampUtils

class ConferenceSchedulingViewModel : ContactsSelectionViewModel() {
    val subject = MutableLiveData<String>()
    val description = MutableLiveData<String>()

    val scheduleForLater = MutableLiveData<Boolean>()

    val formattedDate = MutableLiveData<String>()
    val formattedTime = MutableLiveData<String>()

    val isEncrypted = MutableLiveData<Boolean>()

    val sendInviteViaChat = MutableLiveData<Boolean>()
    val sendInviteViaEmail = MutableLiveData<Boolean>()

    val participantsData = MutableLiveData<List<ConferenceSchedulingParticipantData>>()

    val address = MutableLiveData<Address>()

    val conferenceCreationInProgress = MutableLiveData<Boolean>()

    val conferenceCreationCompletedEvent: MutableLiveData<Event<Pair<String, String?>>> by lazy {
        MutableLiveData<Event<Pair<String, String?>>>()
    }

    val continueEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    var timeZone = MutableLiveData<TimeZoneData>()
    val timeZones: List<TimeZoneData> = computeTimeZonesList()

    var duration = MutableLiveData<Duration>()
    val durationList: List<Duration> = computeDurationList()

    var dateTimestamp: Long = System.currentTimeMillis()
    var hour: Int = 0
    var minutes: Int = 0

    private val conferenceScheduler = coreContext.core.createConferenceScheduler()

    private val chatRoomListener = object : ChatRoomListenerStub() {
        override fun onStateChanged(room: ChatRoom, state: ChatRoom.State) {
            if (state == ChatRoom.State.Created) {
                Log.i("[Conference Creation] Chat room created")
                room.removeListener(this)
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("[Conference Creation] Group chat room creation has failed !")
                room.removeListener(this)
            }
        }
    }

    private val listener = object : ConferenceSchedulerListenerStub() {
        override fun onStateChanged(
            conferenceScheduler: ConferenceScheduler,
            state: ConferenceSchedulerState
        ) {
            Log.i("[Conference Creation] Conference scheduler state is $state")
            if (state == ConferenceSchedulerState.Ready) {
                val conferenceAddress = conferenceScheduler.info?.uri
                Log.i("[Conference Creation] Conference info created, address will be ${conferenceAddress?.asStringUriOnly()}")
                conferenceAddress ?: return

                address.value = conferenceAddress!!

                if (sendInviteViaChat.value == true) {
                    // Send conference info even when conf is not scheduled for later
                    // as the conference server doesn't invite participants automatically
                    val chatRoomParams = coreContext.core.createDefaultChatRoomParams()
                    chatRoomParams.backend = ChatRoomBackend.FlexisipChat
                    chatRoomParams.isGroupEnabled = false
                    chatRoomParams.isEncryptionEnabled = true
                    chatRoomParams.subject = subject.value
                    conferenceScheduler.sendInvitations(chatRoomParams)
                } else {
                    conferenceCreationInProgress.value = false
                    conferenceCreationCompletedEvent.value = Event(Pair(conferenceAddress.asStringUriOnly(), conferenceScheduler.info?.subject))
                }
            }
        }

        override fun onInvitationsSent(
            conferenceScheduler: ConferenceScheduler,
            failedInvitations: Array<out Address>?
        ) {
            Log.i("[Conference Creation] Conference information successfully sent to all participants")
            conferenceCreationInProgress.value = false

            if (failedInvitations?.isNotEmpty() == true) {
                for (address in failedInvitations) {
                    Log.e("[Conference Creation] Conference information wasn't sent to participant ${address.asStringUriOnly()}")
                }
                onMessageToNotifyEvent.value = Event(R.string.conference_schedule_info_not_sent_to_participant)
            }

            val conferenceAddress = conferenceScheduler.info?.uri
            if (conferenceAddress == null) {
                Log.e("[Conference Creation] Conference address is null!")
            } else {
                conferenceCreationCompletedEvent.value = Event(Pair(conferenceAddress.asStringUriOnly(), conferenceScheduler.info?.subject))
            }
        }
    }

    init {
        sipContactsSelected.value = true

        subject.value = ""
        scheduleForLater.value = false
        isEncrypted.value = false
        sendInviteViaChat.value = true
        sendInviteViaEmail.value = false

        timeZone.value = timeZones.find {
            it.id == TimeZone.getDefault().id
        }
        duration.value = durationList.find {
            it.value == 3600
        }

        continueEnabled.value = false
        continueEnabled.addSource(subject) {
            continueEnabled.value = allMandatoryFieldsFilled()
        }
        continueEnabled.addSource(scheduleForLater) {
            continueEnabled.value = allMandatoryFieldsFilled()
        }
        continueEnabled.addSource(formattedDate) {
            continueEnabled.value = allMandatoryFieldsFilled()
        }
        continueEnabled.addSource(formattedTime) {
            continueEnabled.value = allMandatoryFieldsFilled()
        }

        conferenceScheduler.addListener(listener)
    }

    override fun onCleared() {
        conferenceScheduler.removeListener(listener)
        participantsData.value.orEmpty().forEach(ConferenceSchedulingParticipantData::destroy)

        super.onCleared()
    }

    fun toggleSchedule() {
        scheduleForLater.value = scheduleForLater.value == false
    }

    fun setDate(d: Long) {
        dateTimestamp = d
        formattedDate.value = TimestampUtils.dateToString(dateTimestamp, false)
    }

    fun setTime(h: Int, m: Int) {
        hour = h
        minutes = m
        formattedTime.value = TimestampUtils.timeToString(hour, minutes)
    }

    fun updateEncryption(enable: Boolean) {
        isEncrypted.value = enable
    }

    fun computeParticipantsData() {
        participantsData.value.orEmpty().forEach(ConferenceSchedulingParticipantData::destroy)
        val list = arrayListOf<ConferenceSchedulingParticipantData>()

        for (address in selectedAddresses.value.orEmpty()) {
            val data = ConferenceSchedulingParticipantData(address, isEncrypted.value == true)
            list.add(data)
        }

        participantsData.value = list
    }

    fun createConference() {
        val participantsCount = selectedAddresses.value.orEmpty().size
        if (participantsCount == 0) {
            Log.e("[Conference Creation] Couldn't create conference without any participant!")
            return
        }

        conferenceCreationInProgress.value = true
        val core = coreContext.core
        val participants = arrayOfNulls<Address>(selectedAddresses.value.orEmpty().size)
        selectedAddresses.value?.toArray(participants)
        val localAddress = core.defaultAccount?.params?.identityAddress

        // TODO: Temporary workaround for chat room, to be removed once we can get matching chat room from conference
        /*val chatRoomParams = core.createDefaultChatRoomParams()
        chatRoomParams.backend = ChatRoomBackend.FlexisipChat
        chatRoomParams.enableGroup(true)
        chatRoomParams.subject = subject.value
        val chatRoom = core.createChatRoom(chatRoomParams, localAddress, participants)
        if (chatRoom == null) {
            Log.e("[Conference Creation] Failed to create a chat room with same subject & participants as for conference")
        } else {
            Log.i("[Conference Creation] Creating chat room with same subject [${subject.value}] & participants as for conference")
            chatRoom.addListener(chatRoomListener)
        }*/
        // END OF TODO

        val conferenceInfo = Factory.instance().createConferenceInfo()
        conferenceInfo.organizer = localAddress
        conferenceInfo.subject = subject.value
        conferenceInfo.description = description.value
        conferenceInfo.setParticipants(participants)
        if (scheduleForLater.value == true) {
            val startTime = getConferenceStartTimestamp()
            conferenceInfo.dateTime = startTime
            val duration = duration.value?.value ?: 0
            conferenceInfo.duration = duration
        }
        conferenceScheduler.info = conferenceInfo // Will trigger the conference creation automatically
    }

    private fun computeTimeZonesList(): List<TimeZoneData> {
        return TimeZone.getAvailableIDs().map { id -> TimeZoneData(TimeZone.getTimeZone(id)) }.toList().sorted()
    }

    private fun computeDurationList(): List<Duration> {
        // Duration value is in minutes as according to conferenceInfo.setDuration() doc
        return arrayListOf(Duration(30, "30min"), Duration(60, "1h"), Duration(120, "2h"))
    }

    private fun allMandatoryFieldsFilled(): Boolean {
        return !subject.value.isNullOrEmpty() &&
            (
                scheduleForLater.value == false ||
                    (
                        !formattedDate.value.isNullOrEmpty() &&
                            !formattedTime.value.isNullOrEmpty()
                        )
                )
    }

    private fun getConferenceStartTimestamp(): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZone.value?.id ?: TimeZone.getDefault().id))
        calendar.timeInMillis = dateTimestamp
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minutes)
        return calendar.timeInMillis / 1000 // Linphone expects a time_t (so in seconds)
    }
}
