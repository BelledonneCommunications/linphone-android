/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.voip.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.voip.data.CallData
import org.linphone.activities.voip.viewmodels.CallsViewModel
import org.linphone.databinding.VoipCallContextMenuBindingImpl
import org.linphone.databinding.VoipCallsListFragmentBinding
import org.linphone.utils.AppUtils

class CallsListFragment : GenericFragment<VoipCallsListFragmentBinding>() {
    private lateinit var callsViewModel: CallsViewModel

    override fun getLayoutId(): Int = R.layout.voip_calls_list_fragment

    private val callContextMenuClickListener = object : CallData.CallContextMenuClickListener {
        override fun onShowContextMenu(anchor: View, callData: CallData) {
            showCallMenu(anchor, callData)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        callsViewModel = requireActivity().run {
            ViewModelProvider(this).get(CallsViewModel::class.java)
        }
        binding.callsViewModel = callsViewModel

        callsViewModel.noMoreCallEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    requireActivity().finish()
                }
            }
        )

        binding.setCancelClickListener {
            goBack()
        }

        callsViewModel.callsData.observe(
            viewLifecycleOwner,
            {
                for (data in it) {
                    data.contextMenuClickListener = callContextMenuClickListener
                }
            }
        )
    }

    private fun showCallMenu(anchor: View, callData: CallData) {
        val popupView: VoipCallContextMenuBindingImpl = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.voip_call_context_menu, null, false
        )

        val itemSize = AppUtils.getDimension(R.dimen.voip_call_context_menu_item_height).toInt()
        var totalSize = itemSize * 3

        // When using WRAP_CONTENT instead of real size, fails to place the
        // popup window above if not enough space is available below
        val popupWindow = PopupWindow(
            popupView.root,
            AppUtils.getDimension(R.dimen.voip_call_context_menu_width).toInt(),
            totalSize,
            true
        )
        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f

        popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END or Gravity.TOP)
    }
}
