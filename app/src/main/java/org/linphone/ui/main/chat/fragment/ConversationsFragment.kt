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
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.Event
import org.linphone.utils.SlidingPaneBackPressedCallback

@UiThread
class ConversationsFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Conversations Fragment]"
    }

    private lateinit var binding: ChatFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (
            findNavController().currentDestination?.id == R.id.startConversationFragment ||
            findNavController().currentDestination?.id == R.id.meetingWaitingRoomFragment
        ) {
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

        sharedViewModel.conversationsReadyEvent.observe(viewLifecycleOwner) {
            it.consume {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                    sharedViewModel.isFirstFragmentReady = true
                }
            }
        }

        val args = arguments
        if (args != null) {
            val localSipUri = args.getString("LocalSipUri")
            val remoteSipUri = args.getString("RemoteSipUri")
            if (localSipUri != null && remoteSipUri != null) {
                Log.i("$TAG Found local [$localSipUri] & remote [$remoteSipUri] URIs in arguments")
                val pair = Pair(localSipUri, remoteSipUri)
                sharedViewModel.showConversationEvent.value = Event(pair)
                args.clear()
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

        sharedViewModel.showStartConversationEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.conversationsFragment) {
                    Log.i("$TAG Navigating to start conversation fragment")
                    val action =
                        ConversationsFragmentDirections.actionConversationsFragmentToStartConversationFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.showConversationEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val localSipUri = pair.first
                val remoteSipUri = pair.second
                Log.i(
                    "$TAG Navigating to conversation fragment with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
                )
                val action = ConversationFragmentDirections.actionGlobalConversationFragment(
                    localSipUri,
                    remoteSipUri
                )
                binding.chatNavContainer.findNavController().navigate(action)
            }
        }

        sharedViewModel.goToMeetingWaitingRoomEvent.observe(viewLifecycleOwner) {
            it.consume { uri ->
                if (findNavController().currentDestination?.id == R.id.conversationsFragment) {
                    Log.i("$TAG Navigating to meeting waiting room fragment with URI [$uri]")
                    val action =
                        ConversationsFragmentDirections.actionConversationsFragmentToMeetingWaitingRoomFragment(
                            uri
                        )
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToContactsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.conversationsFragment) {
                    // To prevent any previously seen conversation to show up when navigating back to here later
                    binding.chatNavContainer.findNavController().popBackStack()

                    val action = ConversationsFragmentDirections.actionConversationsFragmentToContactsFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToHistoryEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.conversationsFragment) {
                    // To prevent any previously seen conversation to show up when navigating back to here later
                    binding.chatNavContainer.findNavController().popBackStack()

                    val action = ConversationsFragmentDirections.actionConversationsFragmentToHistoryFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.navigateToMeetingsEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.conversationsFragment) {
                    // To prevent any previously seen conversation to show up when navigating back to here later
                    binding.chatNavContainer.findNavController().popBackStack()

                    val action = ConversationsFragmentDirections.actionConversationsFragmentToMeetingsFragment()
                    findNavController().navigate(action)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        sharedViewModel.currentlyDisplayedFragment.value = R.id.conversationsFragment
    }
}
