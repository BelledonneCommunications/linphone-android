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
package org.linphone.ui.assistant

import android.content.Intent
import android.os.Bundle
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantSingleSignOnActivityBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.assistant.viewmodel.SingleSignOnViewModel

@UiThread
class SingleSignOnActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Single Sign On Activity]"

        private const val ACTIVITY_RESULT_ID = 666
    }

    private lateinit var binding: AssistantSingleSignOnActivityBinding

    private lateinit var viewModel: SingleSignOnViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.assistant_single_sign_on_activity)
        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this)[SingleSignOnViewModel::class.java]
        binding.viewModel = viewModel

        setUpToastsArea(binding.toastsArea)

        if (intent != null) {
            Log.i(
                "$TAG Handling intent action [${intent.action}], type [${intent.type}] and data [${intent.data}]"
            )
            val uri = intent.data?.toString() ?: ""
            if (uri.startsWith("linphone-sso:")) {
                val ssoUrl = uri.replace("linphone-sso:", "https:")
                Log.i("$TAG Setting SSO URL [$ssoUrl]")
                viewModel.singleSignOnUrl.value = ssoUrl
            }
        }

        viewModel.singleSignOnUrl.observe(this) { url ->
            Log.i("$TAG SSO URL found [$url], setting it up")
            viewModel.setUp()
        }

        viewModel.singleSignOnProcessCompletedEvent.observe(this) {
            it.consume {
                Log.i("$TAG Process complete, leaving assistant")
                finish()
            }
        }

        viewModel.startAuthIntentEvent.observe(this) {
            it.consume { intent ->
                Log.i("$TAG Starting auth intent activity")
                startActivityForResult(intent, ACTIVITY_RESULT_ID)
            }
        }

        viewModel.onErrorEvent.observe(this) {
            it.consume { errorMessage ->
                showRedToast(
                    errorMessage,
                    R.drawable.warning_circle
                )
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
