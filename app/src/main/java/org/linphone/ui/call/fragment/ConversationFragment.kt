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
package org.linphone.ui.call.fragment

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.fragment.ConversationFragment

class ConversationFragment : ConversationFragment() {
    companion object {
        private const val TAG = "[In-call Conversation Fragment]"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.i("$TAG Creating an in-call ConversationFragment")
        sendMessageViewModel.isInCallConversation.value = true
        viewModel.isInCallConversation.value = true

        binding.setBackClickListener {
            findNavController().popBackStack()
        }
    }
}
