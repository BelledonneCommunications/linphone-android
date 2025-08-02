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
package com.naminfo.ui.main.fragment

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.core.tools.Log
import com.naminfo.ui.main.viewmodel.DefaultAccountChangedViewModel

@UiThread
abstract class SlidingPaneChildFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Sliding Pane Child Fragment]"
    }

    private lateinit var defaultAccountChangedViewModel: DefaultAccountChangedViewModel

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            Log.d("$TAG ${getFragmentRealClassName()} handleOnBackPressed")
            try {
                if (!goBack()) {
                    Log.d(
                        "$TAG ${getFragmentRealClassName()}'s goBack() method returned false, disabling back pressed callback and trying again"
                    )
                    isEnabled = false
                    try {
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    } catch (ise: IllegalStateException) {
                        Log.w(
                            "$TAG ${getFragmentRealClassName()} Can't go back: $ise"
                        )
                    }
                }
            } catch (ise: IllegalStateException) {
                Log.e(
                    "$TAG ${getFragmentRealClassName()} Can't go back: $ise"
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )

        defaultAccountChangedViewModel = ViewModelProvider(this)[DefaultAccountChangedViewModel::class.java]
        defaultAccountChangedViewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Default account changed, leaving fragment")
                goBack()
            }
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            val enabled = backPressedCallBackEnabled(slideable)
            onBackPressedCallback.isEnabled = enabled
            Log.d(
                "$TAG ${getFragmentRealClassName()} Our own back press callback is ${if (enabled) "enabled" else "disabled"}"
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        onBackPressedCallback.remove()
    }

    override fun goBack(): Boolean {
        if (!findNavController().popBackStack()) {
            Log.d("$TAG ${getFragmentRealClassName()} Couldn't pop back stack")
            if (!findNavController().navigateUp()) {
                Log.d("$TAG ${getFragmentRealClassName()} Couldn't navigate up")
                onBackPressedCallback.isEnabled = false
                return super.goBack()
            }
            return false
        }
        return false
    }

    private fun backPressedCallBackEnabled(slideable: Boolean): Boolean {
        // This allow to navigate a SlidingPane child nav graph.
        // This only concerns fragments for which the nav graph is inside a SlidingPane layout.
        // In our case it's all graphs except the main one.
        Log.d(
            "$TAG ${getFragmentRealClassName()} Sliding pane is ${if (slideable) "slideable" else "flat"}"
        )
        return slideable
    }
}
