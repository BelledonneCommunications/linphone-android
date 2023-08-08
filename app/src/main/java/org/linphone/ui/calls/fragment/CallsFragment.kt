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
package org.linphone.ui.calls.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.R
import org.linphone.databinding.CallsFragmentBinding
import org.linphone.ui.fragment.GenericFragment
import org.linphone.utils.SlidingPaneBackPressedCallback

class CallsFragment : GenericFragment() {
    private lateinit var binding: CallsFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallsFragmentBinding.inflate(layoutInflater)
        return binding.root
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
                binding.slidingPaneLayout.closePane()
            }
        }

        sharedViewModel.openSlidingPaneEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                binding.slidingPaneLayout.openPane()
            }
        }

        sharedViewModel.navigateToConversationsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.callsFragment) {
                    // To prevent any previously seen contact to show up when navigating back to here later
                    binding.callsRightNavContainer.findNavController().popBackStack()

                    val action =
                        CallsFragmentDirections.actionCallsFragmentToConversationsFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToContactsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.callsFragment) {
                    // To prevent any previously seen contact to show up when navigating back to here later
                    binding.callsRightNavContainer.findNavController().popBackStack()

                    val action = CallsFragmentDirections.actionCallsFragmentToContactsFragment()
                    findNavController().navigate(action)
                }
            }
        }
    }
}
