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
package org.linphone.ui.main.chat.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.databinding.ChatMessageDeliveryBottomSheetBinding
import org.linphone.ui.main.chat.model.ChatMessageDeliveryModel
import org.linphone.ui.main.chat.model.ChatMessageModel

@UiThread
class MessageDeliveryDialogFragment(
    chatMessageModel: ChatMessageModel
) : BottomSheetDialogFragment() {
    companion object {
        const val TAG = "MessageDeliveryDialogFragment"
    }

    val model = ChatMessageDeliveryModel(chatMessageModel.chatMessage)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = ChatMessageDeliveryBottomSheetBinding.inflate(layoutInflater)
        view.model = model
        return view.root
    }
}
