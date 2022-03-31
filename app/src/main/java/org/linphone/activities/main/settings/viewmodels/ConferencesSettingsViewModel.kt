/*
 * Copyright (c) 2010-2022 Belledonne Communications SARL.
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
package org.linphone.activities.main.settings.viewmodels

import androidx.lifecycle.MutableLiveData
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.core.ConferenceLayout

class ConferencesSettingsViewModel : GenericSettingsViewModel() {
    val layoutListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            core.defaultConferenceLayout = ConferenceLayout.fromInt(layoutValues[position])
            layoutIndex.value = position
        }
    }
    val layoutIndex = MutableLiveData<Int>()
    val layoutLabels = MutableLiveData<ArrayList<String>>()
    private val layoutValues = arrayListOf<Int>()

    init {
        initLayoutsList()
    }

    private fun initLayoutsList() {
        val labels = arrayListOf<String>()

        labels.add(prefs.getString(R.string.conference_display_mode_active_speaker))
        layoutValues.add(ConferenceLayout.ActiveSpeaker.toInt())

        labels.add(prefs.getString(R.string.conference_display_mode_mosaic))
        layoutValues.add(ConferenceLayout.Grid.toInt())

        layoutLabels.value = labels
        layoutIndex.value = layoutValues.indexOf(core.defaultConferenceLayout.toInt())
    }
}
