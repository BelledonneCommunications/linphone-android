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
package org.linphone.ui.main.history.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.StartCallFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericAddressPickerFragment
import org.linphone.ui.main.history.viewmodel.StartCallViewModel
import org.linphone.ui.main.model.GroupSetOrEditSubjectDialogModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.addCharacterAtPosition
import org.linphone.utils.hideKeyboard
import org.linphone.utils.removeCharacterAtPosition
import org.linphone.utils.setKeyboardInsetListener
import org.linphone.utils.showKeyboard
import android.view.KeyEvent

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

    private var lastPressedNumpadView: View? = null

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

    override fun onResume() {
        super.onResume()

        if (corePreferences.automaticallyShowDialpad) {
            showNumpad()
        }
    }

    fun showNumpad() {
        coreContext.postOnMainThread {
            // hide android IME
            binding.searchBar.hideKeyboard()
            binding.searchBar.clearFocus()

            // give hiding a little bit time, before we show our dialpad
            binding.root.postDelayed({
                // show dialpad
                coreContext.postOnCoreThread {
                    viewModel.isNumpadVisible.postValue(true)
                }
            }, 150) // 150ms seems to be a good value
        }
    }

    override fun onPause() {
        super.onPause()

        viewModel.isNumpadVisible.value = false
    }

    fun handleHardwareDialpadKeyEvent(event: KeyEvent): Boolean {
        val digit = mapKeyCodeToDigit(event.keyCode) ?: return false

        // if numpad is hidden, show it
        if (viewModel.isNumpadVisible.value != true) {
            showNumpad()
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // ignore auto-repeat from held down keys
                if (event.repeatCount > 0) return true

                val view = getNumpadButtonForDigit(digit) ?: return false

                // reset old pressed state
                lastPressedNumpadView?.let {
                    it.isPressed = false
                    it.refreshDrawableState()
                }

                // mark new button visually pressed
                view.isPressed = true
                view.refreshDrawableState()

                // simulate click -> play dtmf...
                view.performClick()

                lastPressedNumpadView = view
                return true
            }

            KeyEvent.ACTION_UP -> {
                val view = getNumpadButtonForDigit(digit) ?: lastPressedNumpadView
                view?.let {
                    it.isPressed = false
                    it.refreshDrawableState()
                }
                if (lastPressedNumpadView === view) {
                    lastPressedNumpadView = null
                }
                return true
            }
        }

        return false
    }

    private fun mapKeyCodeToDigit(keyCode: Int): Char? {
        return when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> '0'
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> '1'
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> '2'
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> '3'
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> '4'
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> '5'
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> '6'
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> '7'
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> '8'
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> '9'
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY              -> '*'
            KeyEvent.KEYCODE_BACKSLASH                    -> '#'
            else -> null
        }
    }

    private fun getNumpadButtonForDigit(digit: Char): View? {
        val root = binding.numpadLayout.root
        val id = when (digit) {
            '0' -> R.id.digit_0
            '1' -> R.id.digit_1
            '2' -> R.id.digit_2
            '3' -> R.id.digit_3
            '4' -> R.id.digit_4
            '5' -> R.id.digit_5
            '6' -> R.id.digit_6
            '7' -> R.id.digit_7
            '8' -> R.id.digit_8
            '9' -> R.id.digit_9
            '*' -> R.id.digit_star
            '#' -> R.id.digit_sharp
            else -> null
        }
        return id?.let { root.findViewById<View>(it) }
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

    fun hasSearchText(): Boolean {
        return !binding.searchBar.text?.toString().isNullOrEmpty()
    }

    fun onHardwareCallKeyPressed() {
        // simulate press of green call button
        viewModel.numpadModel.onCallClicked()
    }

    fun onHardwareHangupKeyPressed() {
        // is there an text in sarch field
        val currentText = binding.searchBar.text?.toString().orEmpty()
        if (currentText.isNotEmpty()) {
            // simulate "backspace long press" -> clear complete input
            viewModel.numpadModel.onBackspaceLongClicked()
        }
    }
}
