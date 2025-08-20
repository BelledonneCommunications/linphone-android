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
package org.linphone.activities.main.history.viewmodels

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.history.data.GroupedCallLogData
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.models.callhistory.CallHistoryItemViewModel
import org.linphone.services.CallHistoryService
import org.linphone.services.TransferService
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.Log

class CallLogsListViewModel : ViewModel() {
    val callHistoryService = CallHistoryService.getInstance(coreContext.context)

    val callLogs = MutableLiveData<List<GroupedCallLogData>>()

    val filter = MutableLiveData<CallLogsFilter>()

    val showConferencesFilter = MutableLiveData<Boolean>()

    val isContextMenuOpen = MutableLiveData<Boolean>()

    val contactsUpdatedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val makeCallEvent: MutableLiveData<Event<CallHistoryItemViewModel>> by lazy {
        MutableLiveData<Event<CallHistoryItemViewModel>>()
    }

    val playRecordingEvent: MutableLiveData<Event<CallHistoryItemViewModel>> by lazy {
        MutableLiveData<Event<CallHistoryItemViewModel>>()
    }

    val trasferState = TransferService.getInstance().transferState

    val hasPlaybackPermission = MutableLiveData<Boolean>(false)

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallLogUpdated(core: Core, log: CallLog) {
            updateCallLogs()
        }
    }

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Call Logs] Contacts have changed")
            contactsUpdatedEvent.value = Event(true)
        }
    }

    init {
        filter.value = CallLogsFilter.ALL
        updateCallLogs()

        showConferencesFilter.value = LinphoneUtils.isRemoteConferencingAvailable()

        coreContext.core.addListener(listener)
        coreContext.contactsManager.addListener(contactsUpdatedListener)

        val callHistorySubscription = callHistoryService.formattedHistory.subscribe(
            { updateCallLogs() },
            { error -> Log.e(error, "Failed to update call history.") }
        )
    }

    override fun onCleared() {
        callLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)

        coreContext.contactsManager.removeListener(contactsUpdatedListener)
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun showAllCallLogs() {
        filter.value = CallLogsFilter.ALL
        updateCallLogs()
    }

    fun showOnlyMissedCallLogs() {
        filter.value = CallLogsFilter.MISSED
        updateCallLogs()
    }

    fun showOnlyConferenceCallLogs() {
        filter.value = CallLogsFilter.CONFERENCE
        updateCallLogs()
    }

    fun deleteCallLogGroup(callLog: GroupedCallLogData?) {
        if (callLog != null) {
            for (log in callLog.callLogs) {
                coreContext.core.removeCallLog(log)
            }
        }

        updateCallLogs()
    }

    fun deleteCallLogGroups(listToDelete: ArrayList<GroupedCallLogData>) {
        for (callLog in listToDelete) {
            for (log in callLog.callLogs) {
                coreContext.core.removeCallLog(log)
            }
        }

        updateCallLogs()
    }

    private fun computeCallLogs(callLogs: List<CallLog>, missed: Boolean, conference: Boolean): ArrayList<GroupedCallLogData> {
        val list = arrayListOf<GroupedCallLogData>()

        for (callLog in callLogs) {
            if (missed) {
                if (LinphoneUtils.isCallLogMissed(callLog)) {
                    list.add(GroupedCallLogData(callLog))
                }
            } else {
                list.add(GroupedCallLogData(callLog))
            }
        }

        return list
    }

    @SuppressLint("CheckResult")
    private fun updateCallLogs() {
        callHistoryService.formattedHistory.subscribe { formattedHistory ->
            callLogs.value.orEmpty().forEach(GroupedCallLogData::destroy)

            val updatedCallLogs = when (filter.value) {
                CallLogsFilter.MISSED -> computeCallLogs(
                    formattedHistory,
                    missed = true,
                    conference = false
                )
                CallLogsFilter.CONFERENCE -> computeCallLogs(
                    formattedHistory,
                    missed = false,
                    conference = true
                )
                else -> computeCallLogs(formattedHistory, missed = false, conference = false)
            }

            callLogs.postValue(updatedCallLogs)
        }.dispose()
    }

    val contextMenuTranslateY = MutableLiveData<Float>()
    private val contextMenuAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(
            AppUtils.getDimension(R.dimen.voip_call_extra_buttons_translate_y),
            0f
        ).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                contextMenuTranslateY.value = value
            }
            duration = if (corePreferences.enableAnimations) 500 else 0
        }
    }

    fun showContextMenu(call: CallHistoryItemViewModel) {
        if (call.canCallBack || (call.call.hasRecording && hasPlaybackPermission.value == true)) {
            contextMenuAnimator.start()
            isContextMenuOpen.value = true
        }
    }

    fun hideContextMenu(skipAnimation: Boolean) {
        // Animation must be skipped when called from Fragment's onPause() !
        if (skipAnimation) {
            contextMenuTranslateY.value = AppUtils.getDimension(
                R.dimen.voip_call_extra_buttons_translate_y
            )
        } else {
            contextMenuAnimator.reverse()
        }
        isContextMenuOpen.value = false
    }

    fun makeCall(call: CallHistoryItemViewModel) {
        hideContextMenu(false)
        makeCallEvent.value = Event(call)
    }

    fun playRecording(call: CallHistoryItemViewModel) {
        hideContextMenu(false)
        playRecordingEvent.value = Event(call)
    }
}

enum class CallLogsFilter {
    ALL,
    MISSED,
    CONFERENCE
}
