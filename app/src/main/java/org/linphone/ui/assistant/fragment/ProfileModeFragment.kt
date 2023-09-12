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
package org.linphone.ui.assistant.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantLoginFragmentBinding
import org.linphone.databinding.AssistantSecureModeFragmentBinding
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.assistant.model.AcceptConditionsAndPolicyDialogModel
import org.linphone.ui.assistant.viewmodel.AccountLoginViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.PhoneNumberUtils

@UiThread
class ProfileModeFragment : Fragment() {
    companion object {
        private const val TAG = "[Profile Mode Fragment]"
    }

    private lateinit var binding: AssistantSecureModeFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantSecureModeFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setContinueClickListener {
            requireActivity().finish()
        }

        binding.setDefaultModeTooltipClickListener {
            // TODO
        }

        binding.setInteropModeTooltipClickListener {
            // TODO
        }
    }
}
