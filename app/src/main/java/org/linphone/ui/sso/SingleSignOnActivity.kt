/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
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
package org.linphone.ui.sso

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.SingleSignOnActivityBinding
import org.linphone.ui.GenericActivity
import kotlin.math.max

class SingleSignOnActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Single Sign On Activity]"

        private const val ACTIVITY_RESULT_ID = 666

        const val INTENT_EXTRA_USERNAME = "EXTRA_USERNAME"
        const val INTENT_EXTRA_SERVER_URL = "EXTRA_SERVER_URL"
    }

    private lateinit var binding: SingleSignOnActivityBinding

    private lateinit var viewModel: SingleSignOnViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.single_sign_on_activity)
        binding.lifecycleOwner = this
        setUpToastsArea(binding.toastsArea)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                insets.left,
                insets.top,
                insets.right,
                max(insets.bottom, keyboard.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }

        viewModel = ViewModelProvider(this)[SingleSignOnViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents()

        binding.setBackClickListener {
            finish()
        }

        viewModel.singleSignOnProcessCompletedEvent.observe(this) {
            it.consume {
                Log.i("$TAG Process complete, going back")
                finish()
            }
        }

        viewModel.startAuthIntentEvent.observe(this) {
            it.consume { intent ->
                Log.i("$TAG Starting auth intent activity")
                try {
                    startActivityForResult(intent, ACTIVITY_RESULT_ID)
                } catch (exception: ActivityNotFoundException) {
                    Log.e("$TAG No activity found to handle intent: $exception")
                }
            }
        }

        val username = intent.getStringExtra(INTENT_EXTRA_USERNAME).orEmpty()
        val serverUrl = intent.getStringExtra(INTENT_EXTRA_SERVER_URL).orEmpty()
        Log.i("$TAG Found server URL [$serverUrl] and username [$username] in args")
        viewModel.setUp(serverUrl, username)
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

    private fun observeToastEvents() {
        viewModel.showRedToastEvent.observe(this) {
            it.consume { pair ->
                val message = getString(pair.first)
                val icon = pair.second
                showRedToast(message, icon)
            }
        }

        viewModel.showFormattedRedToastEvent.observe(this) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                showRedToast(message, icon)
            }
        }

        viewModel.showGreenToastEvent.observe(this) {
            it.consume { pair ->
                val message = getString(pair.first)
                val icon = pair.second
                showGreenToast(message, icon)
            }
        }

        viewModel.showFormattedGreenToastEvent.observe(this) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                showGreenToast(message, icon)
            }
        }
    }
}
