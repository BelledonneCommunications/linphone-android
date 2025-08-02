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
package com.naminfo.ui.main.history.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.LinphoneApplication.Companion.corePreferences
import com.naminfo.R
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import com.naminfo.databinding.StartCallFragmentBinding
import com.naminfo.ui.GenericActivity
import com.naminfo.ui.main.fragment.GenericAddressPickerFragment
import com.naminfo.ui.main.history.viewmodel.StartCallViewModel
import com.naminfo.ui.main.model.GroupSetOrEditSubjectDialogModel
import com.naminfo.utils.DialogUtils
import com.naminfo.utils.Event
import com.naminfo.utils.addCharacterAtPosition
import com.naminfo.utils.hideKeyboard
import com.naminfo.utils.removeCharacterAtPosition
import com.naminfo.utils.setKeyboardInsetListener
import com.naminfo.utils.showKeyboard

@UiThread
class StartCallFragment : GenericAddressPickerFragment() {
    companion object {
        private const val TAG = "[Start Call Fragment]"
    }

    private lateinit var binding: StartCallFragmentBinding

    override lateinit var viewModel: StartCallViewModel

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN) {
                viewModel.isNumpadVisible.value = false
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) { }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val actionsBottomSheetBehavior = BottomSheetBehavior.from(binding.numpadLayout.root)
            if (actionsBottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
                actionsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                return
            }

            Log.i("$TAG Back gesture/click detected, no bottom sheet is expanded, going back")
            isEnabled = false
            try {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            } catch (ise: IllegalStateException) {
                Log.w("$TAG Can't go back: $ise")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = StartCallFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[StartCallViewModel::class.java]

        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel.title.value = getString(R.string.history_call_start_title)
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            // If back button from UI was clicked, go back even if numpad is opened
            backPressedCallback.isEnabled = false
            goBack()
        }

        binding.setHideNumpadClickListener {
            viewModel.hideNumpad()
        }

        binding.setAskForGroupCallSubjectClickListener {
            viewModel.hideNumpad()
            showGroupCallSubjectDialog()
        }

        setupRecyclerView(binding.contactsAndSuggestionsList)

        viewModel.modelsList.observe(
            viewLifecycleOwner
        ) {
            Log.i("$TAG Contacts & suggestions list is ready with [${it.size}] items")
            adapter.submitList(it)

            attachAdapter()

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        viewModel.leaveFragmentEvent.observe(viewLifecycleOwner) {
            it.consume {
                // Post on main thread to allow for main activity to be resumed
                coreContext.postOnMainThread {
                    Log.i("$TAG Going back")
                    goBack()
                }
            }
        }

        viewModel.removedCharacterAtCurrentPositionEvent.observe(viewLifecycleOwner) {
            it.consume {
                binding.searchBar.removeCharacterAtPosition()
            }
        }

        viewModel.clearSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume {
                binding.searchBar.setText("")
            }
        }

        viewModel.appendDigitToSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { digit ->
                binding.searchBar.addCharacterAtPosition(digit)
            }
        }

        viewModel.requestKeyboardVisibilityChangedEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    binding.searchBar.showKeyboard()
                } else {
                    binding.searchBar.requestFocus()
                    binding.searchBar.hideKeyboard()
                }
            }
        }

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.numpadLayout.root)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)

        viewModel.isNumpadVisible.observe(viewLifecycleOwner) { visible ->
            if (visible) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        viewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.updateGroupCallButtonVisibility()
            }
        }

        binding.root.setKeyboardInsetListener { keyboardVisible ->
            if (keyboardVisible) {
                viewModel.isNumpadVisible.value = false
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )
    }

    @WorkerThread
    override fun onSingleAddressSelected(address: Address, friend: Friend) {
        coreContext.startAudioCall(address)
        viewModel.leaveFragmentEvent.postValue(Event(true))
    }

    override fun onResume() {
        super.onResume()

        coreContext.postOnCoreThread {
            if (corePreferences.automaticallyShowDialpad) {
                viewModel.isNumpadVisible.postValue(true)
            }
        }
    }

    override fun onPause() {
        super.onPause()

        viewModel.isNumpadVisible.value = false
    }

    private fun showGroupCallSubjectDialog() {
        val model = GroupSetOrEditSubjectDialogModel("", isGroupConversation = false)

        val dialog = DialogUtils.getSetOrEditGroupSubjectDialog(
            requireContext(),
            viewLifecycleOwner,
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Set group call subject cancelled")
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume { newSubject ->
                if (newSubject.isNotEmpty()) {
                    Log.i(
                        "$TAG Group call subject has been set to [$newSubject]"
                    )
                    viewModel.subject.value = newSubject
                    viewModel.createGroupCall()

                    dialog.currentFocus?.hideKeyboard()
                    dialog.dismiss()
                } else {
                    val message = getString(R.string.conversation_invalid_empty_subject_toast)
                    val icon = R.drawable.warning_circle
                    (requireActivity() as GenericActivity).showRedToast(message, icon)
                }
            }
        }

        Log.i("$TAG Showing dialog to set group call subject")
        dialog.show()
    }
}
