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
package org.linphone.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log

abstract class GenericFragment<T : ViewDataBinding> : Fragment() {
    companion object {
        val emptyFragmentsIds = arrayListOf(
            R.id.emptyChatFragment,
            R.id.emptyContactFragment,
            R.id.emptySettingsFragment,
            R.id.emptyCallHistoryFragment
        )
    }

    private var _binding: T? = null
    protected val binding get() = _binding!!

    protected var useMaterialSharedAxisXForwardAnimation = true

    protected lateinit var sharedViewModel: SharedMainViewModel

    protected fun isSharedViewModelInitialized(): Boolean {
        return ::sharedViewModel.isInitialized
    }

    protected fun isBindingAvailable(): Boolean {
        return _binding != null
    }

    private fun getFragmentRealClassName(): String {
        return this.javaClass.name
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            try {
                val navController = findNavController()
                Log.d("[Generic Fragment] ${getFragmentRealClassName()} handleOnBackPressed")
                if (!navController.popBackStack()) {
                    Log.d("[Generic Fragment] ${getFragmentRealClassName()} couldn't pop")
                    if (!navController.navigateUp()) {
                        Log.d("[Generic Fragment] ${getFragmentRealClassName()} couldn't navigate up")
                        // Disable this callback & start a new back press event
                        isEnabled = false
                        goBack()
                    }
                }
            } catch (ise: IllegalStateException) {
                Log.e("[Generic Fragment] ${getFragmentRealClassName()} Can't go back: $ise")
            }
        }
    }

    abstract fun getLayoutId(): Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) {
            Log.d("[Generic Fragment] ${getFragmentRealClassName()} shared main VM sliding pane has changed")
            onBackPressedCallback.isEnabled = backPressedCallBackEnabled()
        }

        _binding = DataBindingUtil.inflate(inflater, getLayoutId(), container, false)
        return _binding!!.root
    }

    override fun onStart() {
        super.onStart()

        if (useMaterialSharedAxisXForwardAnimation && corePreferences.enableAnimations) {
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
            reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
            returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
            exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)

            postponeEnterTransition()
            binding.root.doOnPreDraw { startPostponedEnterTransition() }
        }

        setupBackPressCallback()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        onBackPressedCallback.remove()
        _binding = null
    }

    protected fun goBack() {
        try {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        } catch (ise: IllegalStateException) {
            Log.e("[Generic Fragment] ${getFragmentRealClassName()} can't go back: $ise")
        }
    }

    private fun setupBackPressCallback() {
        Log.d("[Generic Fragment] ${getFragmentRealClassName()} setupBackPressCallback")

        val backButton = binding.root.findViewById<ImageView>(R.id.back)
        if (backButton != null) {
            Log.d("[Generic Fragment] ${getFragmentRealClassName()} found back button")
            // If popping navigation back stack entry would bring us to an "empty" fragment
            // then don't do it if sliding pane layout isn't "flat"
            onBackPressedCallback.isEnabled = backPressedCallBackEnabled()
            backButton.setOnClickListener { goBack() }
        } else {
            onBackPressedCallback.isEnabled = false
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun backPressedCallBackEnabled(): Boolean {
        // This allow to navigate a SlidingPane child nav graph.
        // This only concerns fragments for which the nav graph is inside a SlidingPane layout.
        // In our case it's all graphs except the main one.
        if (findNavController().graph.id == R.id.main_nav_graph_xml) return false

        val isSlidingPaneFlat = sharedViewModel.isSlidingPaneSlideable.value == false
        Log.d("[Generic Fragment] ${getFragmentRealClassName()} isSlidingPaneFlat ? $isSlidingPaneFlat")
        val isPreviousFragmentEmpty = findNavController().previousBackStackEntry?.destination?.id in emptyFragmentsIds
        Log.d("[Generic Fragment] ${getFragmentRealClassName()} isPreviousFragmentEmpty ? $isPreviousFragmentEmpty")
        val popBackStack = isSlidingPaneFlat || !isPreviousFragmentEmpty
        Log.d("[Generic Fragment] ${getFragmentRealClassName()} popBackStack ? $popBackStack")
        return popBackStack
    }
}
