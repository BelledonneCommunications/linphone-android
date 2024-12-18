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
package org.linphone.ui.main.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.databinding.GenericAddParticipantsFragmentBinding
import org.linphone.ui.main.viewmodel.AddParticipantsViewModel
import org.linphone.utils.Event

@UiThread
class AddParticipantsFragment : GenericAddressPickerFragment() {
    companion object {
        private const val TAG = "[Add Participants Fragment]"
    }

    private lateinit var binding: GenericAddParticipantsFragmentBinding

    override lateinit var viewModel: AddParticipantsViewModel

    private val args: AddParticipantsFragmentArgs by navArgs()

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            Log.d("$TAG ${getFragmentRealClassName()} handleOnBackPressed")
            try {
                if (!goBack()) {
                    Log.d(
                        "$TAG ${getFragmentRealClassName()}'s goBack() method returned false, disabling back pressed callback and trying again"
                    )
                    isEnabled = false
                    try {
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    } catch (ise: IllegalStateException) {
                        Log.w(
                            "$TAG ${getFragmentRealClassName()} Can't go back: $ise"
                        )
                    }
                }
            } catch (ise: IllegalStateException) {
                Log.e(
                    "$TAG ${getFragmentRealClassName()} Can't go back: $ise"
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = GenericAddParticipantsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        try {
            return findNavController().popBackStack()
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Can't go back popping back stack: $ise")
        }
        return false
    }

    override fun onSingleAddressSelected(address: Address, friend: Friend) {
        Log.e("$TAG This shouldn't happen as we should always be in multiple selection mode here!")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[AddParticipantsViewModel::class.java]

        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        setupRecyclerView(binding.contactsList)

        val participants = args.selectedParticipants

        viewModel.modelsList.observe(
            viewLifecycleOwner
        ) {
            if (!participants.isNullOrEmpty() && viewModel.isSelectionEmpty()) {
                Log.i("$TAG Found participants in arguments and selection is currently empty, adding them")
                viewModel.addSelectedParticipants(participants)
            }

            Log.i("$TAG Contacts & suggestions list is ready with [${it.size}] items")
            adapter.submitList(it)

            attachAdapter()

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        viewModel.selectedSipUrisEvent.observe(viewLifecycleOwner) {
            it.consume { list ->
                sharedViewModel.listOfSelectedSipUrisEvent.value = Event(list)
                goBack()
            }
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            val enabled = backPressedCallBackEnabled(slideable)
            onBackPressedCallback.isEnabled = enabled
            Log.d(
                "$TAG ${getFragmentRealClassName()} Our own back press callback is ${if (enabled) "enabled" else "disabled"}"
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        onBackPressedCallback.remove()
    }

    private fun backPressedCallBackEnabled(slideable: Boolean): Boolean {
        // This allow to navigate a SlidingPane child nav graph.
        // This only concerns fragments for which the nav graph is inside a SlidingPane layout.
        // In our case it's all graphs except the main one.
        Log.d(
            "$TAG ${getFragmentRealClassName()} Sliding pane is ${if (slideable) "slideable" else "flat"}"
        )
        return slideable
    }
}
