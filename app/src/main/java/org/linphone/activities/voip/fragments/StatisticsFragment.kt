/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.voip.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.voip.viewmodels.StatisticsListViewModel
import org.linphone.databinding.VoipCallStatsFragmentBinding

class StatisticsFragment : GenericFragment<VoipCallStatsFragmentBinding>() {
    private lateinit var statsViewModel: StatisticsListViewModel

    override fun getLayoutId(): Int = R.layout.voip_call_stats_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        statsViewModel = ViewModelProvider(this).get(StatisticsListViewModel::class.java)
        binding.statsViewModel = statsViewModel

        binding.setCancelClickListener {
            goBack()
        }
    }
}
