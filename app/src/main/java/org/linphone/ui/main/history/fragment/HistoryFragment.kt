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
package org.linphone.ui.main.history.fragment

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
import org.linphone.databinding.HistoryFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.SlidingPaneBackPressedCallback

@UiThread
class HistoryFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Calls Fragment]"
    }

    private lateinit var binding: HistoryFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HistoryFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.startCallFragment) {
            // Holds fragment in place while new contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
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

        sharedViewModel.historyReadyEvent.observe(viewLifecycleOwner) {
            it.consume {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                    sharedViewModel.isFirstFragmentReady = true
                }
            }
        }

        sharedViewModel.closeSlidingPaneEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (binding.slidingPaneLayout.isOpen) {
                    Log.i("$TAG Closing sliding pane")
                    binding.slidingPaneLayout.closePane()
                }
            }
        }

        sharedViewModel.openSlidingPaneEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                if (!binding.slidingPaneLayout.isOpen) {
                    Log.i("$TAG Opening sliding pane")
                    binding.slidingPaneLayout.openPane()
                }
            }
        }

        sharedViewModel.showStartCallEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Navigating to start call fragment")
                val action = HistoryFragmentDirections.actionHistoryFragmentToStartCallFragment()
                findNavController().navigate(action)
            }
        }

        sharedViewModel.showCallLogEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { callId ->
                Log.i("$TAG Displaying call log with call ID [$callId]")
                val navController = binding.historyNavContainer.findNavController()
                val action = HistoryContactFragmentDirections.actionGlobalHistoryContactFragment(
                    callId
                )
                navController.navigate(action)
            }
        }

        sharedViewModel.navigateToContactsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.historyFragment) {
                    // To prevent any previously seen call log to show up when navigating back to here later
                    binding.historyNavContainer.findNavController().popBackStack()

                    val action = HistoryFragmentDirections.actionHistoryFragmentToContactsFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToConversationsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.historyFragment) {
                    // To prevent any previously seen call log to show up when navigating back to here later
                    binding.historyNavContainer.findNavController().popBackStack()

                    val action = HistoryFragmentDirections.actionHistoryFragmentToConversationsFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToMeetingsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.historyFragment) {
                    // To prevent any previously seen call log to show up when navigating back to here later
                    binding.historyNavContainer.findNavController().popBackStack()

                    val action = HistoryFragmentDirections.actionHistoryFragmentToMeetingsFragment()
                    findNavController().navigate(action)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.currentlyDisplayedFragment.value = R.id.historyFragment
    }
}
