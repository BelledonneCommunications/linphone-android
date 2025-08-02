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
package com.naminfo.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.navigation.navGraphViewModels
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.databinding.AccountProfileSecureModeFragmentBinding
import com.naminfo.ui.main.fragment.GenericMainFragment
import com.naminfo.ui.main.settings.viewmodel.AccountProfileViewModel

@UiThread
class AccountProfileModeFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Account Profile Mode Fragment]"
    }

    private lateinit var binding: AccountProfileSecureModeFragmentBinding

    private val viewModel: AccountProfileViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AccountProfileSecureModeFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            Log.i("$TAG Leaving without saving changes...")
            goBack()
        }
    }
}
