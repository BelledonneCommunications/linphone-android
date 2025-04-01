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
package org.linphone.activities.main.viewmodels

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.*
import org.linphone.services.CallHistoryService
import org.linphone.utils.AppUtils
import org.linphone.utils.Log

class TabsViewModel : ViewModel() {
    private val callHistoryService = CallHistoryService.getInstance(coreContext.context)

    val unreadMessagesCount = MutableLiveData<Int>()
    val unreadVoicemailsCount = MutableLiveData<Int>()
    val missedCallsCount = MutableLiveData<Int>()

    val leftAnchor = MutableLiveData<Float>()
    val middleAnchor = MutableLiveData<Float>()
    val rightAnchor = MutableLiveData<Float>()

    val historyMissedCountTranslateY = MutableLiveData<Float>()
    val chatUnreadCountTranslateY = MutableLiveData<Float>()
    val voicemailUnreadCountTranslateY = MutableLiveData<Float>()

    private var missedCallCountSubscription: Disposable? = null

    init {
        missedCallCountSubscription = callHistoryService.missedCallCount.subscribe { c ->
            missedCallsCount.postValue(c)
        }
    }

    private val bounceAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(
            AppUtils.getDimension(R.dimen.tabs_fragment_unread_count_bounce_offset),
            0f
        ).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                historyMissedCountTranslateY.value = -value
                chatUnreadCountTranslateY.value = -value
                voicemailUnreadCountTranslateY.value = -value
            }
            interpolator = LinearInterpolator()
            duration = 250
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
    }

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            if (state == Call.State.End || state == Call.State.Error) {
                updateMissedCallCount()
            }
        }

        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            updateUnreadChatCount()
        }

        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            updateUnreadChatCount()
        }

        override fun onChatRoomStateChanged(core: Core, chatRoom: ChatRoom, state: ChatRoom.State) {
            if (state == ChatRoom.State.Deleted) {
                updateUnreadChatCount()
            }
        }

        override fun onNotifyReceived(
            core: Core,
            event: Event,
            notifiedEvent: String,
            body: Content?
        ) {
            if (body?.type == "application" && body.subtype == "simple-message-summary" && body.size > 0) {
                val data = body.utf8Text?.lowercase(Locale.getDefault())
                val voiceMail = data?.split("voice-message: ")
                if ((voiceMail?.size ?: 0) >= 2) {
                    val toParse = voiceMail!![1].split("/", limit = 0)
                    try {
                        val unreadCount: Int = toParse[0].toInt()
                        unreadVoicemailsCount.value = unreadCount
                    } catch (nfe: NumberFormatException) {
                        Log.e("[Status Fragment] $nfe")
                    }
                }
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        leftAnchor.value = 0.25F
        middleAnchor.value = 0.5F
        rightAnchor.value = 0.75F

        updateUnreadChatCount()
        updateMissedCallCount()

        if (corePreferences.enableAnimations) bounceAnimator.start()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun updateMissedCallCount() {
        // missedCallsCount.value = coreContext.core.missedCallsCount //NOTE - now handled by MissedCallCountSubscription
    }

    fun updateUnreadChatCount() {
        unreadMessagesCount.value = if (corePreferences.disableChat) 0 else coreContext.core.unreadChatMessageCountFromActiveLocals
    }

    fun dialVoicemail() {
        coreContext.dialVoicemail()
    }
}
