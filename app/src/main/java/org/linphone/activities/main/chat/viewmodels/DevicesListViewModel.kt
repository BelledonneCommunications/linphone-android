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
package org.linphone.activities.main.chat.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.linphone.activities.main.chat.data.DevicesListGroupData
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog

class DevicesListViewModelFactory(private val chatRoom: ChatRoom) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DevicesListViewModel(chatRoom) as T
    }
}

class DevicesListViewModel(private val chatRoom: ChatRoom) : ViewModel() {
    val participants = MutableLiveData<ArrayList<DevicesListGroupData>>()

    private val listener = object : ChatRoomListenerStub() {
        override fun onParticipantDeviceAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            updateParticipants()
        }

        override fun onParticipantDeviceRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            updateParticipants()
        }

        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            updateParticipants()
        }

        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            updateParticipants()
        }
    }

    init {
        chatRoom.addListener(listener)
    }

    override fun onCleared() {
        participants.value.orEmpty().forEach(DevicesListGroupData::destroy)
        chatRoom.removeListener(listener)

        super.onCleared()
    }

    fun updateParticipants() {
        participants.value.orEmpty().forEach(DevicesListGroupData::destroy)

        val list = arrayListOf<DevicesListGroupData>()
        val me = chatRoom.me
        if (me != null) list.add(DevicesListGroupData(me))
        for (participant in chatRoom.participants) {
            list.add(DevicesListGroupData(participant))
        }

        participants.value = list
    }
}
