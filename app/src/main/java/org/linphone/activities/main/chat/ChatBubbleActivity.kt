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
package org.linphone.activities.main.chat

import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatBubbleActivityBinding

class ChatBubbleActivity : GenericActivity() {
    private lateinit var binding: ChatBubbleActivityBinding
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.chat_bubble_activity)
        binding.lifecycleOwner = this

        sharedViewModel = ViewModelProvider(this).get(SharedMainViewModel::class.java)

        val localSipUri = intent?.extras?.getString("LocalSipUri")
        val remoteSipUri = intent?.extras?.getString("RemoteSipUri")
        if (localSipUri != null && remoteSipUri != null) {
            Log.i("[Chat Bubble] Found local [$localSipUri] & remote [$remoteSipUri] addresses in arguments")
            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            val chatRoom = LinphoneApplication.coreContext.core.getChatRoom(remoteSipAddress, localAddress)
            if (chatRoom != null) {
                Log.i("[Chat Bubble] Found matching chat room $chatRoom")
                chatRoom.markAsRead()
                sharedViewModel.selectedChatRoom.value = chatRoom
            }
        }
    }
}
