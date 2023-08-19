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
package org.linphone.ui.voip

import android.os.Bundle
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication
import org.linphone.R
import org.linphone.databinding.VoipActivityBinding
import org.linphone.ui.voip.fragment.ActiveCallFragmentDirections
import org.linphone.ui.voip.fragment.IncomingCallFragmentDirections
import org.linphone.ui.voip.fragment.OutgoingCallFragmentDirections
import org.linphone.ui.voip.viewmodel.CallsViewModel

@UiThread
class VoipActivity : AppCompatActivity() {
    private lateinit var binding: VoipActivityBinding

    private lateinit var callsViewModel: CallsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)

        val inCallBlackColor = ContextCompat.getColor(
            this,
            R.color.in_call_black
        )
        window.navigationBarColor = inCallBlackColor

        while (!LinphoneApplication.coreContext.isReady()) {
            Thread.sleep(20)
        }

        binding = DataBindingUtil.setContentView(this, R.layout.voip_activity)
        binding.lifecycleOwner = this

        callsViewModel = run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }

        callsViewModel.showIncomingCallEvent.observe(this) {
            it.consume {
                val action = IncomingCallFragmentDirections.actionGlobalIncomingCallFragment()
                findNavController(R.id.voip_nav_container).navigate(action)
            }
        }

        callsViewModel.showOutgoingCallEvent.observe(this) {
            it.consume {
                val action = OutgoingCallFragmentDirections.actionGlobalOutgoingCallFragment()
                findNavController(R.id.voip_nav_container).navigate(action)
            }
        }

        callsViewModel.goToActiveCallEvent.observe(this) {
            it.consume {
                val navController = findNavController(R.id.voip_nav_container)
                val action = if (navController.currentDestination?.id == R.id.outgoingCallFragment) {
                    OutgoingCallFragmentDirections.actionOutgoingCallFragmentToActiveCallFragment()
                } else if (navController.currentDestination?.id == R.id.outgoingCallFragment) {
                    IncomingCallFragmentDirections.actionIncomingCallFragmentToActiveCallFragment()
                } else {
                    ActiveCallFragmentDirections.actionGlobalActiveCallFragment()
                }
                navController.navigate(action)
            }
        }

        callsViewModel.noMoreCallEvent.observe(this) {
            it.consume {
                finish()
            }
        }
    }
}
