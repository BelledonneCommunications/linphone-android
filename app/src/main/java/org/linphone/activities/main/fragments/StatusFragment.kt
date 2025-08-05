/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.fragments

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RelativeLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.viewmodels.StatusViewModel
import org.linphone.authentication.DimensionsAccountsManager
import org.linphone.databinding.StatusFragmentBinding
import org.linphone.utils.Event
import org.linphone.utils.Log

class StatusFragment : GenericFragment<StatusFragmentBinding>() {
    private lateinit var viewModel: StatusViewModel

    override fun getLayoutId(): Int = R.layout.status_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        useMaterialSharedAxisXForwardAnimation = false

        viewModel = ViewModelProvider(this)[StatusViewModel::class.java]
        binding.viewModel = viewModel

        sharedViewModel.accountRemoved.observe(
            viewLifecycleOwner
        ) {
            Log.i("[Status Fragment] An account was removed, update default account state")
            val defaultAccount = coreContext.core.defaultAccount
            if (defaultAccount != null) {
                viewModel.updateDefaultAccountRegistrationStatus(defaultAccount.state)
            }
        }

        val statusContainer = view.findViewById<RelativeLayout>(R.id.status_container)
        ViewCompat.setOnApplyWindowInsetsListener(statusContainer) { v, windowInsets ->
            val insets = windowInsets.getInsets((WindowInsetsCompat.Type.statusBars()))

            val contentDpHeight = 50f
            val contentPixelHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                contentDpHeight,
                resources.displayMetrics
            )

            statusContainer.layoutParams.height = (insets.top + contentPixelHeight).toInt()

            WindowInsetsCompat.CONSUMED
        }

        binding.setMenuClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
        }

        binding.setPresenceClickListener {
            sharedViewModel.togglePresenceDrawerEvent.value = Event(true)
        }

        binding.setRefreshClickListener {
            viewModel.refreshRegister()
        }

        binding.setVoicemailClickListener {
            viewModel.dialVoicemail()
        }

        val devices = DimensionsAccountsManager.getInstance(requireContext()).devicesSubject
            .subscribe(
                { devices ->
                    if (devices.userDevices.isNullOrEmpty()) {
                        viewModel.showDeviceMissing()
                    }
                },
                { error -> Log.e(error) }
            )
    }
}
