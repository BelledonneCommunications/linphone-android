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
package org.linphone.activities.main.chat.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.main.chat.viewmodels.DevicesListViewModel
import org.linphone.activities.main.chat.viewmodels.DevicesListViewModelFactory
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatRoomDevicesFragmentBinding

class DevicesFragment : SecureFragment<ChatRoomDevicesFragmentBinding>() {
    private lateinit var listViewModel: DevicesListViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun getLayoutId(): Int = R.layout.chat_room_devices_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        val chatRoom = sharedViewModel.selectedChatRoom.value
        if (chatRoom == null) {
            Log.e("[Devices] Chat room is null, aborting!")
            // (activity as MainActivity).showSnackBar(R.string.error)
            findNavController().navigateUp()
            return
        }

        isSecure = chatRoom.currentParams.isEncryptionEnabled

        listViewModel = ViewModelProvider(
            this,
            DevicesListViewModelFactory(chatRoom)
        )[DevicesListViewModel::class.java]
        binding.viewModel = listViewModel

        binding.setBackClickListener {
            goBack()
        }
    }

    override fun onResume() {
        super.onResume()

        listViewModel.updateParticipants()
    }
}
