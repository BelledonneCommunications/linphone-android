/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package org.linphone.ui.call.conference.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.core.tools.Log
import org.linphone.databinding.GenericAddParticipantsFragmentBinding
import org.linphone.ui.call.viewmodel.CurrentCallViewModel
import org.linphone.ui.main.fragment.GenericAddressPickerFragment
import org.linphone.ui.main.viewmodel.AddParticipantsViewModel

@UiThread
class ConferenceAddParticipantsFragment : GenericAddressPickerFragment() {
    companion object {
        private const val TAG = "[Conference Add Participants Fragment]"
    }

    private lateinit var binding: GenericAddParticipantsFragmentBinding

    override lateinit var viewModel: AddParticipantsViewModel

    private lateinit var callViewModel: CurrentCallViewModel

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[AddParticipantsViewModel::class.java]

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        setupRecyclerView(binding.contactsList)

        viewModel.modelsList.observe(
            viewLifecycleOwner
        ) {
            Log.i("$TAG Contacts & suggestions list is ready with [${it.size}] items")
            adapter.submitList(it)

            attachAdapter()

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        viewModel.selectedSipUrisEvent.observe(viewLifecycleOwner) {
            it.consume { list ->
                Log.i("$TAG Trying to add participant(s) [${list.size}] to conference")
                callViewModel.conferenceModel.inviteSipUrisIntoConference(list)
                goBack()
            }
        }
    }
}
