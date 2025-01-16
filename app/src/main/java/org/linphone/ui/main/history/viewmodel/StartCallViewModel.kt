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
package org.linphone.ui.main.history.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.MediaDirection
import org.linphone.core.tools.Log
import org.linphone.ui.main.history.model.NumpadModel
import org.linphone.ui.main.viewmodel.AddressSelectionViewModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class StartCallViewModel
    @UiThread
    constructor() : AddressSelectionViewModel() {
    companion object {
        private const val TAG = "[Start Call ViewModel]"
    }

    val title = MutableLiveData<String>()

    val numpadModel: NumpadModel

    val hideGroupCallButton = MutableLiveData<Boolean>()

    val isNumpadVisible = MutableLiveData<Boolean>()

    val startGroupCallButtonEnabled = MediatorLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val operationInProgress = MutableLiveData<Boolean>()

    val appendDigitToSearchBarEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val removedCharacterAtCurrentPositionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val clearSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val requestKeyboardVisibilityChangedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val leaveFragmentEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    init {
        isNumpadVisible.value = false
        numpadModel = NumpadModel(
            false,
            { digit -> // onDigitClicked
                appendDigitToSearchBarEvent.value = Event(digit)
                // Don't do that, cursor will stay at start
                // searchFilter.value = "${searchFilter.value.orEmpty()}$digit"
            },
            { // onVoicemailClicked
                coreContext.postOnCoreThread { core ->
                    val account = LinphoneUtils.getDefaultAccount()
                    val voicemailAddress = account?.params?.voicemailAddress
                    if (voicemailAddress != null) {
                        Log.i("$TAG Calling voicemail URI [${voicemailAddress.asStringUriOnly()}]")
                        coreContext.startCall(voicemailAddress)
                    } else {
                        Log.w("$TAG No voicemail URI configured for current account, nothing to do")
                    }
                }
            },
            { // OnBackspaceClicked
                removedCharacterAtCurrentPositionEvent.value = Event(true)
            },
            { // OnCallClicked
                val suggestion = searchFilter.value.orEmpty()
                if (suggestion.isNotEmpty()) {
                    Log.i("$TAG Using numpad dial button to call [$suggestion]")
                    coreContext.postOnCoreThread { core ->
                        val address = core.interpretUrl(
                            suggestion,
                            LinphoneUtils.applyInternationalPrefix()
                        )
                        if (address != null) {
                            Log.i("$TAG Calling [${address.asStringUriOnly()}]")
                            coreContext.startAudioCall(address)
                            leaveFragmentEvent.postValue(Event(true))
                        } else {
                            Log.e("$TAG Failed to parse [$suggestion] as SIP address")
                        }
                    }
                }
            },
            { // OnClearInput
                clearSearchBarEvent.value = Event(true)
            }
        )

        startGroupCallButtonEnabled.value = false
        startGroupCallButtonEnabled.addSource(selection) {
            startGroupCallButtonEnabled.value = it.isNotEmpty()
        }

        updateGroupCallButtonVisibility()
    }

    @UiThread
    fun updateGroupCallButtonVisibility() {
        coreContext.postOnCoreThread { core ->
            val hideGroupCall = corePreferences.disableMeetings || !LinphoneUtils.isRemoteConferencingAvailable(
                core
            )
            hideGroupCallButton.postValue(hideGroupCall)
        }
    }

    @UiThread
    fun switchBetweenKeyboardAndNumpad() {
        val showKeyboard = isNumpadVisible.value == true
        requestKeyboardVisibilityChangedEvent.value = Event(showKeyboard)
        viewModelScope.launch {
            delay(100)
            isNumpadVisible.value = !showKeyboard
        }
    }

    @UiThread
    fun hideNumpad() {
        isNumpadVisible.value = false
    }

    @UiThread
    fun createGroupCall() {
        coreContext.postOnCoreThread { core ->
            val account = core.defaultAccount
            if (account == null) {
                Log.e(
                    "$TAG No default account found, can't create group call!"
                )
                return@postOnCoreThread
            }
            operationInProgress.postValue(true)

            Log.i("$TAG Creating group call with subject ${subject.value}")
            val conference = LinphoneUtils.createGroupCall(account, subject.value.orEmpty())
            if (conference == null) {
                Log.e("$TAG Failed to create group call!")
                showRedToastEvent.postValue(
                    Event(
                        Pair(
                            R.string.conference_failed_to_create_group_call_toast,
                            R.drawable.warning_circle
                        )
                    )
                )
                return@postOnCoreThread
            }

            val callParams = core.createCallParams(null)
            callParams?.isVideoEnabled = true
            callParams?.videoDirection = MediaDirection.RecvOnly

            val participants = arrayOfNulls<Address>(selection.value.orEmpty().size)
            var index = 0
            for (participant in selection.value.orEmpty()) {
                participants[index] = participant.address
                index += 1
            }
            Log.i(
                "$TAG Inviting ${participants.size} participant(s) into newly created conference"
            )
            if (conference.inviteParticipants(participants, callParams) != 0) {
                Log.e("$TAG Failed to invite participants into group call!")
                showRedToastEvent.postValue(
                    Event(
                        Pair(
                            R.string.conference_failed_to_create_group_call_toast,
                            R.drawable.warning_circle
                        )
                    )
                )
            }
            operationInProgress.postValue(false)
        }
    }
}
