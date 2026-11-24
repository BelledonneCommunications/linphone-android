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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.integration.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import org.linphone.R
import org.linphone.databinding.IntegrationSettingsFragmentBinding
import org.linphone.ui.main.fragment.GenericMainFragment

/** Settings → Android integration: the page (title bar, back) around [IntegrationPreferencesFragment] */
@UiThread
class IntegrationSettingsFragment : GenericMainFragment() {
    private lateinit var binding: IntegrationSettingsFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = IntegrationSettingsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.setBackClickListener {
            goBack()
        }
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.integration_preferences, IntegrationPreferencesFragment())
                .commit()
        }
    }
}
