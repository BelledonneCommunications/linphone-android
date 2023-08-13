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
package org.linphone.ui.main.calls.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import org.linphone.core.tools.Log
import org.linphone.databinding.CallFragmentBinding
import org.linphone.ui.main.calls.viewmodel.CallLogViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.Event

class CallFragment : GenericFragment() {
    private lateinit var binding: CallFragmentBinding

    private lateinit var viewModel: CallLogViewModel

    private val args: CallFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack() {
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[CallLogViewModel::class.java]
        binding.viewModel = viewModel

        val callId = args.callId
        Log.i("[Call Fragment] Looking up for call log with call id [$callId]")
        viewModel.findCallLogByCallId(callId)

        binding.setBackClickListener {
            goBack()
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        viewModel.callLogFoundEvent.observe(viewLifecycleOwner) {
            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
            sharedViewModel.openSlidingPaneEvent.value = Event(true)
        }
    }
}
