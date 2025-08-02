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
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import org.linphone.core.tools.Log
import com.naminfo.databinding.SettingsContactsLdapBinding
import com.naminfo.ui.main.fragment.GenericMainFragment
import com.naminfo.ui.main.settings.viewmodel.LdapViewModel

@UiThread
class LdapServerConfigurationFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[LDAP Server Configuration Fragment]"
    }

    private lateinit var binding: SettingsContactsLdapBinding

    private lateinit var viewModel: LdapViewModel

    private val args: LdapServerConfigurationFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsContactsLdapBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[LdapViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val ldapServerUrl = args.serverUrl
        if (ldapServerUrl != null) {
            Log.i("$TAG Found server URL in arguments, loading values")
            viewModel.loadLdap(ldapServerUrl)
        } else {
            Log.i("$TAG No server URL found in arguments, starting from scratch")
        }

        binding.setBackClickListener {
            goBack()
        }

        viewModel.ldapServerOperationSuccessfulEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG LDAP server operation was successful, going back")
                goBack()
            }
        }
    }
}
