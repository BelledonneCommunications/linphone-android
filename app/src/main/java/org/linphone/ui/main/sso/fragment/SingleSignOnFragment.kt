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
package org.linphone.ui.main.sso.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.SingleSignOnFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.ui.main.sso.viewmodel.SingleSignOnViewModel

class SingleSignOnFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Single Sign On Fragment]"

        private const val ACTIVITY_RESULT_ID = 666
    }

    private lateinit var binding: SingleSignOnFragmentBinding

    private lateinit var viewModel: SingleSignOnViewModel

    private val args: SingleSignOnFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SingleSignOnFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[SingleSignOnViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        viewModel.singleSignOnProcessCompletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Process complete, going back")
                goBack()
            }
        }

        viewModel.startAuthIntentEvent.observe(viewLifecycleOwner) {
            it.consume { intent ->
                Log.i("$TAG Starting auth intent activity")
                try {
                    startActivityForResult(intent, ACTIVITY_RESULT_ID)
                } catch (exception: ActivityNotFoundException) {
                    Log.e("$TAG No activity found to handle intent: $exception")
                }
            }
        }

        viewModel.onErrorEvent.observe(viewLifecycleOwner) {
            it.consume { errorMessage ->
                (requireActivity() as GenericActivity).showRedToast(
                    errorMessage,
                    R.drawable.warning_circle
                )
            }
        }

        val serverUrl = args.serverUrl
        val username = args.username
        Log.i("$TAG Found server URL [$serverUrl] and username [$username] in args")
        viewModel.setUp(serverUrl, username.orEmpty())
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
