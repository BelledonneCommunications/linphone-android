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
package org.linphone.ui.main.meetings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.MeetingsFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.SlidingPaneBackPressedCallback

@UiThread
class MeetingsFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Meetings Fragment]"
    }

    private lateinit var binding: MeetingsFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MeetingsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.scheduleMeetingFragment) {
            // Holds fragment in place while new contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.root.doOnPreDraw {
            val slidingPane = binding.slidingPaneLayout
            slidingPane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

            sharedViewModel.isSlidingPaneSlideable.value = slidingPane.isSlideable

            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                SlidingPaneBackPressedCallback(slidingPane)
            )
        }

        sharedViewModel.closeSlidingPaneEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Log.i("$TAG Closing sliding pane")
                binding.slidingPaneLayout.closePane()
            }
        }

        sharedViewModel.openSlidingPaneEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Log.i("$TAG Opening sliding pane")
                binding.slidingPaneLayout.openPane()
            }
        }

        sharedViewModel.showScheduleMeetingEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Navigating to schedule meeting fragment")
                findNavController().navigate(R.id.action_global_scheduleMeetingFragment)
            }
        }

        sharedViewModel.showMeetingEvent.observe(viewLifecycleOwner) {
            it.consume { uri ->
                Log.i("$TAG Navigating to meeting fragment with URI [$uri]")
                val action = MeetingFragmentDirections.actionGlobalMeetingFragment(uri)
                binding.meetingsNavContainer.findNavController().navigate(action)
            }
        }

        sharedViewModel.navigateToContactsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.meetingsFragment) {
                    // To prevent any previously seen meeting to show up when navigating back to here later
                    binding.meetingsNavContainer.findNavController().popBackStack()

                    val action = MeetingsFragmentDirections.actionMeetingsFragmentToContactsFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToCallsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.meetingsFragment) {
                    // To prevent any previously seen meeting to show up when navigating back to here later
                    binding.meetingsNavContainer.findNavController().popBackStack()

                    val action = MeetingsFragmentDirections.actionMeetingsFragmentToHistoryFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToConversationsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.meetingsFragment) {
                    // To prevent any previously seen meeting to show up when navigating back to here later
                    binding.meetingsNavContainer.findNavController().popBackStack()

                    val action = MeetingsFragmentDirections.actionMeetingsFragmentToConversationsFragment()
                    findNavController().navigate(action)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.currentlyDisplayedFragment.value = R.id.meetingsFragment
    }
}
