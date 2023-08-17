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
package org.linphone.ui.main.calls.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.CallFragmentBinding
import org.linphone.databinding.CallPopupMenuBinding
import org.linphone.ui.main.calls.viewmodel.CallLogViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.Event
import org.linphone.utils.slideInToastFromTopForDuration

class CallFragment : GenericFragment() {
    private lateinit var binding: CallFragmentBinding

    private lateinit var viewModel: CallLogViewModel

    private val args: CallFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack() {
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[CallLogViewModel::class.java]
        binding.viewModel = viewModel

        val callId = args.callId
        Log.i("[Call Fragment] Looking up for call log with call id [$callId]")
        viewModel.findCallLogByCallId(callId)

        binding.setBackClickListener {
            goBack()
        }

        binding.setMenuClickListener {
            showPopupMenu()
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        viewModel.callLogFoundEvent.observe(viewLifecycleOwner) {
            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
            sharedViewModel.openSlidingPaneEvent.value = Event(true)
        }
    }

    private fun copyNumberOrAddressToClipboard(value: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = "SIP address"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

        binding.greenToast.message = "Numéro copié dans le presse-papier"
        binding.greenToast.icon = R.drawable.check

        val target = binding.greenToast.root
        target.slideInToastFromTopForDuration(binding.root as ViewGroup, lifecycleScope)
    }

    private fun showPopupMenu() {
        val popupView: CallPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.call_popup_menu,
            null,
            false
        )

        popupView.setDeleteAllHistoryClickListener {
            viewModel.deleteHistory()
        }

        popupView.setCopyNumberClickListener {
            copyNumberOrAddressToClipboard(viewModel.callLogModel.value?.displayedAddress.orEmpty())
        }

        val popupWindow = PopupWindow(
            popupView.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(binding.menu, 0, 0, Gravity.BOTTOM)
    }
}
