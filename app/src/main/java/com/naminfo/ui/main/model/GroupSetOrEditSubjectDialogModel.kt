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
package com.naminfo.ui.main.model

import androidx.annotation.UiThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.naminfo.utils.Event

class GroupSetOrEditSubjectDialogModel
    @UiThread
    constructor(
    initialSubject: String,
    val isGroupConversation: Boolean
) {
    val isEdit = initialSubject.isNotEmpty()

    val subject = MutableLiveData<String>()

    val dismissEvent = MutableLiveData<Event<Boolean>>()

    val confirmEvent = MutableLiveData<Event<String>>()

    val emptySubject = MediatorLiveData<Boolean>()

    init {
        emptySubject.addSource(subject) { subject ->
            emptySubject.value = subject.isEmpty()
        }
        subject.value = initialSubject
    }

    @UiThread
    fun dismiss() {
        dismissEvent.value = Event(true)
    }

    @UiThread
    fun confirm() {
        val newSubject = subject.value.orEmpty()
        emptySubject.value = newSubject.isEmpty()
        confirmEvent.value = Event(newSubject)
    }
}
