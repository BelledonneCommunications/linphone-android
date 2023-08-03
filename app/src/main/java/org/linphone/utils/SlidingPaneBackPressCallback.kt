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
package org.linphone.utils

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.core.tools.Log

class SlidingPaneBackPressedCallback(private val slidingPaneLayout: SlidingPaneLayout) :
    OnBackPressedCallback(
        slidingPaneLayout.isSlideable && slidingPaneLayout.isOpen
    ),
    SlidingPaneLayout.PanelSlideListener {

    init {
        Log.d(
            "[Master Fragment] SlidingPane isSlideable = ${slidingPaneLayout.isSlideable}, isOpen = ${slidingPaneLayout.isOpen}"
        )
        slidingPaneLayout.addPanelSlideListener(this)
    }

    override fun handleOnBackPressed() {
        Log.d("[Master Fragment] handleOnBackPressed, closing sliding pane")
        slidingPaneLayout.hideKeyboard()
        slidingPaneLayout.closePane()
    }

    override fun onPanelOpened(panel: View) {
        Log.d("[Master Fragment] onPanelOpened")
        isEnabled = true
    }

    override fun onPanelClosed(panel: View) {
        Log.d("[Master Fragment] onPanelClosed")
        isEnabled = false
    }

    override fun onPanelSlide(panel: View, slideOffset: Float) { }
}
