/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package org.linphone.ui.main.chat.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatBubbleEmojiPickerBottomSheetBinding
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.utils.Event

class ChatMessageLongPressViewModel : GenericViewModel() {
    companion object {
        const val TAG = "[Chat Message LongPress ViewModel]"
    }

    val visible = MutableLiveData<Boolean>()

    val hideForward = MutableLiveData<Boolean>()

    val hideCopyTextToClipboard = MutableLiveData<Boolean>()

    val horizontalBias = MutableLiveData<Float>()

    val isChatRoomReadOnly = MutableLiveData<Boolean>()

    val messageModel = MutableLiveData<MessageModel>()

    val isMessageOutgoing = MutableLiveData<Boolean>()

    val isMessageInError = MutableLiveData<Boolean>()

    val showImdnInfoEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val replyToMessageEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val forwardMessageEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val deleteMessageEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val onDismissedEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var emojiBottomSheetBehavior: BottomSheetBehavior<View>

    init {
        visible.value = false
    }

    @UiThread
    fun setupEmojiPicker(bottomSheet: ChatBubbleEmojiPickerBottomSheetBinding) {
        emojiBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet.root)
        emojiBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        emojiBottomSheetBehavior.skipCollapsed = true
    }

    @UiThread
    fun setMessage(model: MessageModel) {
        hideCopyTextToClipboard.value = model.text.value.isNullOrEmpty()
        isChatRoomReadOnly.value = model.chatRoomIsReadOnly
        isMessageOutgoing.value = model.isOutgoing
        isMessageInError.value = model.isInError.value == true
        horizontalBias.value = if (model.isOutgoing) 1f else 0f
        messageModel.value = model

        emojiBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    @UiThread
    fun dismiss() {
        onDismissedEvent.value = Event(true)
        emojiBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        visible.value = false
    }

    @UiThread
    fun resend() {
        Log.i("$TAG Re-sending message in error state")
        messageModel.value?.resend()
        dismiss()
    }

    @UiThread
    fun showInfo() {
        showImdnInfoEvent.value = Event(true)
        dismiss()
    }

    @UiThread
    fun reply() {
        Log.i("$TAG Replying to message")
        replyToMessageEvent.value = Event(true)
        dismiss()
    }

    @UiThread
    fun copyClickListener() {
        Log.i("$TAG Copying message text into clipboard")

        val text = messageModel.value?.text?.value?.toString()
        val clipboard = coreContext.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = "Message"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

        dismiss()
    }

    @UiThread
    fun forwardClickListener() {
        Log.i("$TAG Forwarding message")
        forwardMessageEvent.value = Event(true)
        dismiss()
    }

    @UiThread
    fun deleteClickListener() {
        Log.i("$TAG Deleting message")
        deleteMessageEvent.value = Event(true)
        dismiss()
    }

    @UiThread
    fun pickEmoji() {
        Log.i("$TAG Opening emoji-picker for reaction")
        emojiBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
