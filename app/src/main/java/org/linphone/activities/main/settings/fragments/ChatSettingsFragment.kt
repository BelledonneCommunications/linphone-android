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
package org.linphone.activities.main.settings.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.main.chat.viewmodels.ChatRoomsListViewModel
import org.linphone.activities.main.settings.viewmodels.ChatSettingsViewModel
import org.linphone.databinding.SettingsChatFragmentBinding
import org.linphone.mediastream.Version
import org.linphone.utils.ShortcutsHelper

class ChatSettingsFragment : GenericSettingFragment<SettingsChatFragmentBinding>() {
    private lateinit var viewModel: ChatSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_chat_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.sharedMainViewModel = sharedViewModel

        viewModel = ViewModelProvider(this)[ChatSettingsViewModel::class.java]
        binding.viewModel = viewModel

        viewModel.launcherShortcutsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { newValue ->
                if (newValue) {
                    ShortcutsHelper.createShortcutsToChatRooms(requireContext())
                } else {
                    ShortcutsHelper.removeShortcuts(requireContext())
                }
            }
        }

        viewModel.goToAndroidNotificationSettingsEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (Build.VERSION.SDK_INT >= Version.API26_O_80) {
                    val i = Intent()
                    i.action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    i.putExtra(
                        Settings.EXTRA_CHANNEL_ID,
                        getString(R.string.notification_channel_chat_id)
                    )
                    i.addCategory(Intent.CATEGORY_DEFAULT)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    startActivity(i)
                }
            }
        }

        viewModel.reloadChatRoomsEvent.observe(viewLifecycleOwner) {
            it.consume {
                reloadChatRooms()
            }
        }
    }

    private fun reloadChatRooms() {
        val listViewModel = requireActivity().run {
            ViewModelProvider(this)[ChatRoomsListViewModel::class.java]
        }
        listViewModel.updateChatRooms()
    }
}
