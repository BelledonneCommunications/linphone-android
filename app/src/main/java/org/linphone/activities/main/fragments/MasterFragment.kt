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
package org.linphone.activities.main.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnPreDraw
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.R
import org.linphone.activities.main.adapters.SelectionListAdapter
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.hideKeyboard

/**
 * This fragment can be inherited by all fragments that will display a list
 * where items can be selected for removal through the ListTopBarFragment
 */
abstract class MasterFragment<T : ViewDataBinding, U : SelectionListAdapter<*, *>> : SecureFragment<T>() {
    protected var _adapter: U? = null
    protected val adapter: U
        get() {
            if (_adapter == null) {
                Log.e("[Master Fragment] Attempting to get a null adapter!")
            }
            return _adapter!!
        }

    protected lateinit var listSelectionViewModel: ListTopBarViewModel
    protected open val dialogConfirmationMessageBeforeRemoval: Int = R.plurals.dialog_default_delete

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // List selection
        listSelectionViewModel = ViewModelProvider(this)[ListTopBarViewModel::class.java]

        listSelectionViewModel.isEditionEnabled.observe(
            viewLifecycleOwner
        ) {
            if (!it) listSelectionViewModel.onUnSelectAll()
        }

        listSelectionViewModel.selectAllEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                listSelectionViewModel.onSelectAll(getItemCount() - 1)
            }
        }

        listSelectionViewModel.unSelectAllEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                listSelectionViewModel.onUnSelectAll()
            }
        }

        listSelectionViewModel.deleteSelectionEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val confirmationDialog = AppUtils.getStringWithPlural(
                    dialogConfirmationMessageBeforeRemoval,
                    listSelectionViewModel.selectedItems.value.orEmpty().size
                )
                val viewModel = DialogViewModel(confirmationDialog)
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

                viewModel.showCancelButton {
                    dialog.dismiss()
                    listSelectionViewModel.isEditionEnabled.value = false
                }

                viewModel.showDeleteButton(
                    {
                        delete()
                        dialog.dismiss()
                        listSelectionViewModel.isEditionEnabled.value = false
                    },
                    getString(R.string.dialog_delete)
                )

                dialog.show()
            }
        }
    }

    fun setUpSlidingPane(slidingPane: SlidingPaneLayout) {
        binding.root.doOnPreDraw {
            sharedViewModel.isSlidingPaneSlideable.value = slidingPane.isSlideable

            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                SlidingPaneBackPressedCallback(slidingPane)
            )
        }

        slidingPane.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED
    }

    private fun delete() {
        val list = listSelectionViewModel.selectedItems.value ?: arrayListOf()
        deleteItems(list)
    }

    private fun getItemCount(): Int {
        return adapter.itemCount
    }

    abstract fun deleteItems(indexesOfItemToDelete: ArrayList<Int>)

    class SlidingPaneBackPressedCallback(private val slidingPaneLayout: SlidingPaneLayout) :
        OnBackPressedCallback
        (
            slidingPaneLayout.isSlideable && slidingPaneLayout.isOpen
        ),
        SlidingPaneLayout.PanelSlideListener {

        init {
            Log.d("[Master Fragment] SlidingPane isSlideable = ${slidingPaneLayout.isSlideable}, isOpen = ${slidingPaneLayout.isOpen}")
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
}
