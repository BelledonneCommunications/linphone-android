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

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.navigateToCallHistory
import org.linphone.activities.main.navigateToChatRooms
import org.linphone.activities.main.navigateToContacts
import org.linphone.activities.main.navigateToDialer
import org.linphone.activities.main.viewmodels.TabsViewModel
import org.linphone.databinding.TabsFragmentBinding

class TabsFragment : GenericFragment<TabsFragmentBinding>(), NavController.OnDestinationChangedListener {
    private lateinit var viewModel: TabsViewModel

    override fun getLayoutId(): Int = R.layout.tabs_fragment

    private val bounceAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(resources.getDimension(R.dimen.tabs_fragment_unread_count_bounce_offset), 0f)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = requireActivity().run {
            ViewModelProvider(this).get(TabsViewModel::class.java)
        }
        binding.viewModel = viewModel

        binding.setHistoryClickListener {
            navigateToCallHistory()
        }

        binding.setContactsClickListener {
            navigateToContacts()
        }

        binding.setDialerClickListener {
            navigateToDialer()
        }

        binding.setChatClickListener {
            navigateToChatRooms()
        }

        bounceAnimator.addUpdateListener {
            val value = it.animatedValue as Float
            binding.historyUnreadCount.translationY = -value
            binding.chatUnreadCount.translationY = -value
        }
        bounceAnimator.interpolator = LinearInterpolator()
        bounceAnimator.duration = 250
        bounceAnimator.repeatMode = ValueAnimator.REVERSE
        bounceAnimator.repeatCount = ValueAnimator.INFINITE
    }

    override fun onStart() {
        super.onStart()
        bounceAnimator.start()
        findNavController().addOnDestinationChangedListener(this)
    }

    override fun onStop() {
        bounceAnimator.pause()
        findNavController().removeOnDestinationChangedListener(this)
        super.onStop()
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        when (destination.id) {
            R.id.masterCallLogsFragment -> binding.motionLayout.transitionToState(R.id.call_history)
            R.id.masterContactsFragment -> binding.motionLayout.transitionToState(R.id.contacts)
            R.id.dialerFragment -> binding.motionLayout.transitionToState(R.id.dialer)
            R.id.masterChatRoomsFragment -> binding.motionLayout.transitionToState(R.id.chat_rooms)
        }
    }
}
