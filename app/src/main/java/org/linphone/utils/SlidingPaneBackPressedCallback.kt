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
import androidx.annotation.UiThread
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.core.tools.Log

@UiThread
class SlidingPaneBackPressedCallback(private val slidingPaneLayout: SlidingPaneLayout) :
    OnBackPressedCallback(false),
    SlidingPaneLayout.PanelSlideListener {
    companion object {
        private const val TAG = "[Sliding Pane Back Pressed Callback]"
    }

    init {
        slidingPaneLayout.addPanelSlideListener(this)
        val enableCallback = slidingPaneLayout.isSlideable && slidingPaneLayout.isOpen
        Log.d(
            "$TAG Sliding pane layout created, back press callback is ${if (enableCallback) "enabled" else "disabled"}"
        )
        isEnabled = enableCallback
    }

    override fun handleOnBackPressed() {
        Log.i("$TAG handleOnBackPressed: hiding keyboard & closing pane")
        slidingPaneLayout.hideKeyboard()
        if (!slidingPaneLayout.closePane()) {
            Log.w(
                "$TAG handleOnBackPressed: sliding pane is not open, disabling back press callback!"
            )
            isEnabled = false
        }
    }

    override fun onPanelOpened(panel: View) {
        Log.d("$TAG Panel is opened, enabling back press callback")
        isEnabled = true
    }

    override fun onPanelClosed(panel: View) {
        Log.d("$TAG Panel is closed, disabled back press callback")
        isEnabled = false
    }

    override fun onPanelSlide(panel: View, slideOffset: Float) {
        isEnabled = true
    }
}
