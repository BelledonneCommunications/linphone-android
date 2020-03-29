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
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.utils.DialogUtils

/**
 * This fragment can be inherited by all fragments that will display a list
 * where items can be selected for removal through the ListTopBarFragment
 */
abstract class MasterFragment : Fragment() {
    protected lateinit var listSelectionViewModel: ListTopBarViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // List selection
        listSelectionViewModel = ViewModelProvider(this).get(ListTopBarViewModel::class.java)

        listSelectionViewModel.isEditionEnabled.observe(viewLifecycleOwner, Observer {
            if (!it) listSelectionViewModel.onUnSelectAll()
        })

        listSelectionViewModel.selectAllEvent.observe(viewLifecycleOwner, Observer {
            it.consume {
                listSelectionViewModel.onSelectAll(getItemCount() - 1)
            }
        })

        listSelectionViewModel.unSelectAllEvent.observe(viewLifecycleOwner, Observer {
            it.consume {
                listSelectionViewModel.onUnSelectAll()
            }
        })

        listSelectionViewModel.deleteSelectionEvent.observe(viewLifecycleOwner, Observer {
            it.consume {
                val viewModel = DialogViewModel(getString(R.string.dialog_default_delete_message))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

                viewModel.showCancelButton {
                    dialog.dismiss()
                    listSelectionViewModel.isEditionEnabled.value = false
                }

                viewModel.showDeleteButton({
                    delete()
                    dialog.dismiss()
                    listSelectionViewModel.isEditionEnabled.value = false
                }, getString(R.string.dialog_delete))

                dialog.show()
            }
        })
    }

    private fun delete() {
        val list = listSelectionViewModel.selectedItems.value ?: arrayListOf()
        deleteItems(list)
    }

    abstract fun getItemCount(): Int

    abstract fun deleteItems(indexesOfItemToDelete: ArrayList<Int>)
}
