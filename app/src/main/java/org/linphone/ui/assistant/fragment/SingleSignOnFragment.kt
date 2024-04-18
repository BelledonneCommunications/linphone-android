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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantSingleSignOnFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.assistant.viewmodel.SingleSignOnViewModel

@UiThread
class SingleSignOnFragment : Fragment() {
    companion object {
        private const val TAG = "[Single Sign On Fragment]"

        private const val ACTIVITY_RESULT_ID = 666
    }

    private lateinit var binding: AssistantSingleSignOnFragmentBinding

    private lateinit var viewModel: SingleSignOnViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantSingleSignOnFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = requireActivity().run {
            ViewModelProvider(this)[SingleSignOnViewModel::class.java]
        }
        binding.viewModel = viewModel

        viewModel.singleSignOnUrl.observe(viewLifecycleOwner) { url ->
            Log.i("$TAG SSO URL found [$url], setting it up")
            viewModel.setUp()
        }

        viewModel.singleSignOnProcessCompletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Process complete, leaving assistant")
                requireActivity().finish()
            }
        }

        viewModel.startAuthIntentEvent.observe(viewLifecycleOwner) {
            it.consume { intent ->
                Log.i("$TAG Starting auth intent activity")
                startActivityForResult(intent, ACTIVITY_RESULT_ID)
            }
        }

        viewModel.onErrorEvent.observe(viewLifecycleOwner) {
            it.consume { errorMessage ->
                (requireActivity() as GenericActivity).showRedToast(
                    errorMessage,
                    R.drawable.warning_circle
                )
                try {
                    findNavController().popBackStack()
                } catch (ise: IllegalStateException) {
                    // Excepted in SingleSignOnActivity as no NavController is set
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACTIVITY_RESULT_ID && data != null) {
            val resp = AuthorizationResponse.fromIntent(data)
            val ex = AuthorizationException.fromIntent(data)
            viewModel.processAuthIntentResponse(resp, ex)
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
