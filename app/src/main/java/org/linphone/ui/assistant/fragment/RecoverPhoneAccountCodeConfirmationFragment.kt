/*
 * Copyright (c) 2010-2025 Belledonne Communications SARL.
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

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantRecoverPhoneAccountConfirmSmsCodeFragmentBinding
import org.linphone.ui.GenericFragment
import org.linphone.ui.assistant.viewmodel.RecoverPhoneAccountViewModel

@UiThread
class RecoverPhoneAccountCodeConfirmationFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Recover Phone Account Code Confirmation Fragment]"
    }

    private lateinit var binding: AssistantRecoverPhoneAccountConfirmSmsCodeFragmentBinding

    private val viewModel: RecoverPhoneAccountViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantRecoverPhoneAccountConfirmSmsCodeFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        viewModel.accountCreatedEvent.observe(viewLifecycleOwner) {
            it.consume { identity ->
                Log.i("$TAG Account [$identity] has been created, leaving assistant")
                requireActivity().finish()
            }
        }

        // This won't work starting Android 10 as clipboard access is denied unless app has focus,
        // which won't be the case when the SMS arrives unless it is added into clipboard from a notification
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener {
            val data = clipboard.primaryClip
            if (data != null && data.itemCount > 0) {
                val clip = data.getItemAt(0).text.toString()
                if (clip.length == 4) {
                    Log.i(
                        "$TAG Found 4 digits [$clip] as primary clip in clipboard, using it and clear it"
                    )
                    viewModel.smsCodeFirstDigit.value = clip[0].toString()
                    viewModel.smsCodeSecondDigit.value = clip[1].toString()
                    viewModel.smsCodeThirdDigit.value = clip[2].toString()
                    viewModel.smsCodeLastDigit.value = clip[3].toString()
                    clipboard.clearPrimaryClip()
                }
            }
        }
    }

    private fun goBack() {
        findNavController().popBackStack()
    }
}
