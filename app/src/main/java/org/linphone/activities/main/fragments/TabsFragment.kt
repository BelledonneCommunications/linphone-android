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
package org.linphone.activities.main.fragments

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.viewmodels.TabsViewModel
import org.linphone.databinding.TabsFragmentBinding

class TabsFragment : GenericFragment<TabsFragmentBinding>(), NavController.OnDestinationChangedListener {
    private lateinit var viewModel: TabsViewModel

    override fun getLayoutId(): Int = R.layout.tabs_fragment

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = activity?.run {
            ViewModelProvider(this).get(TabsViewModel::class.java)
        } ?: throw Exception("Invalid Activity")
        binding.viewModel = viewModel

        binding.setHistoryClickListener {
            when (findNavController().currentDestination?.id) {
                R.id.masterContactsFragment -> findNavController().navigate(R.id.action_masterContactsFragment_to_masterCallLogsFragment)
                R.id.dialerFragment -> findNavController().navigate(R.id.action_dialerFragment_to_masterCallLogsFragment)
                R.id.masterChatRoomsFragment -> findNavController().navigate(R.id.action_masterChatRoomsFragment_to_masterCallLogsFragment)
            }
        }

        binding.setContactsClickListener {
            when (findNavController().currentDestination?.id) {
                R.id.masterCallLogsFragment -> findNavController().navigate(R.id.action_masterCallLogsFragment_to_masterContactsFragment)
                R.id.dialerFragment -> findNavController().navigate(R.id.action_dialerFragment_to_masterContactsFragment)
                R.id.masterChatRoomsFragment -> findNavController().navigate(R.id.action_masterChatRoomsFragment_to_masterContactsFragment)
            }
        }

        binding.setDialerClickListener {
            when (findNavController().currentDestination?.id) {
                R.id.masterCallLogsFragment -> findNavController().navigate(R.id.action_masterCallLogsFragment_to_dialerFragment)
                R.id.masterContactsFragment -> findNavController().navigate(R.id.action_masterContactsFragment_to_dialerFragment)
                R.id.masterChatRoomsFragment -> findNavController().navigate(R.id.action_masterChatRoomsFragment_to_dialerFragment)
            }
        }

        binding.setChatClickListener {
            when (findNavController().currentDestination?.id) {
                R.id.masterCallLogsFragment -> findNavController().navigate(R.id.action_masterCallLogsFragment_to_masterChatRoomsFragment)
                R.id.masterContactsFragment -> findNavController().navigate(R.id.action_masterContactsFragment_to_masterChatRoomsFragment)
                R.id.dialerFragment -> findNavController().navigate(R.id.action_dialerFragment_to_masterChatRoomsFragment)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        findNavController().addOnDestinationChangedListener(this)
    }

    override fun onStop() {
        findNavController().removeOnDestinationChangedListener(this)
        super.onStop()
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        viewModel.updateTabSelection(destination.id)
    }
}
