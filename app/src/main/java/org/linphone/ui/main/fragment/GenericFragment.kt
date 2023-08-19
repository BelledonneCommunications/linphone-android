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
package org.linphone.ui.main.fragment

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.ui.main.viewmodel.SharedMainViewModel

@UiThread
abstract class GenericFragment : Fragment() {
    protected lateinit var sharedViewModel: SharedMainViewModel

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            try {
                val navController = findNavController()
                Log.d("[Generic Fragment] ${getFragmentRealClassName()} handleOnBackPressed")
                if (!navController.popBackStack()) {
                    Log.d("[Generic Fragment] ${getFragmentRealClassName()} couldn't pop")
                    if (!navController.navigateUp()) {
                        Log.d(
                            "[Generic Fragment] ${getFragmentRealClassName()} couldn't navigate up"
                        )
                        // Disable this callback & start a new back press event
                        isEnabled = false
                        goBack()
                    }
                }
            } catch (ise: IllegalStateException) {
                Log.e(
                    "[Generic Fragment] ${getFragmentRealClassName()}.handleOnBackPressed() Can't go back: $ise"
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) {
            Log.d(
                "[Generic Fragment] ${getFragmentRealClassName()} shared main VM sliding pane has changed"
            )
            onBackPressedCallback.isEnabled = backPressedCallBackEnabled()
        }

        setupBackPressCallback()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        onBackPressedCallback.remove()
    }

    private fun getFragmentRealClassName(): String {
        return this.javaClass.name
    }

    protected open fun goBack() {
        try {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        } catch (ise: IllegalStateException) {
            Log.w("[Generic Fragment] ${getFragmentRealClassName()}.goBack() can't go back: $ise")
            onBackPressedCallback.handleOnBackPressed()
        }
    }

    private fun setupBackPressCallback() {
        Log.d("[Generic Fragment] ${getFragmentRealClassName()} setupBackPressCallback")

        val backButton = view?.findViewById<ImageView>(R.id.back)
        if (backButton != null && backButton.visibility == View.VISIBLE) {
            Log.d("[Generic Fragment] ${getFragmentRealClassName()} found back button")
            // If popping navigation back stack entry would bring us to an "empty" fragment
            // then don't do it if sliding pane layout isn't "flat"
            onBackPressedCallback.isEnabled = backPressedCallBackEnabled()
            backButton.setOnClickListener { goBack() }
        } else {
            onBackPressedCallback.isEnabled = false
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )
    }

    private fun backPressedCallBackEnabled(): Boolean {
        // This allow to navigate a SlidingPane child nav graph.
        // This only concerns fragments for which the nav graph is inside a SlidingPane layout.
        // In our case it's all graphs except the main one.
        if (findNavController().graph.id == R.id.main_nav_graph) return false

        val isSlidingPaneFlat = sharedViewModel.isSlidingPaneSlideable.value == false
        Log.d(
            "[Generic Fragment] ${getFragmentRealClassName()} isSlidingPaneFlat ? $isSlidingPaneFlat"
        )
        val isPreviousFragmentEmpty = findNavController().previousBackStackEntry?.destination?.id == R.id.emptyFragment
        Log.d(
            "[Generic Fragment] ${getFragmentRealClassName()} isPreviousFragmentEmpty ? $isPreviousFragmentEmpty"
        )
        val popBackStack = isSlidingPaneFlat || !isPreviousFragmentEmpty
        Log.d("[Generic Fragment] ${getFragmentRealClassName()} popBackStack ? $popBackStack")
        return popBackStack
    }
}
