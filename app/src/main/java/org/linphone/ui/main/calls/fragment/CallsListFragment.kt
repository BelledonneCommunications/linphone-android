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
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.CallsListFragmentBinding
import org.linphone.databinding.CallsListPopupMenuBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.calls.adapter.CallsListAdapter
import org.linphone.ui.main.calls.model.ConfirmationDialogModel
import org.linphone.ui.main.calls.viewmodel.CallsListViewModel
import org.linphone.ui.main.fragment.AbstractTopBarFragment
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.hideKeyboard
import org.linphone.utils.showKeyboard

@UiThread
class CallsListFragment : AbstractTopBarFragment() {
    companion object {
        private const val TAG = "[Calls List Fragment]"
    }

    private lateinit var binding: CallsListFragmentBinding

    private lateinit var listViewModel: CallsListViewModel

    private lateinit var adapter: CallsListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        listViewModel = requireActivity().run {
            ViewModelProvider(this)[CallsListViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        adapter = CallsListAdapter(viewLifecycleOwner)
        binding.callsList.setHasFixedSize(true)
        binding.callsList.adapter = adapter

        adapter.callLogLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = CallsListMenuDialogFragment(
                    model.friendExists,
                    { // onDismiss
                        adapter.resetSelection()
                    },
                    { // onAddToContact
                        val addressToAdd = model.displayedAddress
                        Log.i(
                            "$TAG Navigating to new contact with pre-filled value [$addressToAdd]"
                        )

                        sharedViewModel.sipAddressToAddToNewContact = addressToAdd
                        sharedViewModel.navigateToContactsEvent.value = Event(true)
                        sharedViewModel.showNewContactEvent.value = Event(true)
                    },
                    { // onGoToContact
                        val friendRefKey = model.friendRefKey
                        if (!friendRefKey.isNullOrEmpty()) {
                            Log.i("$TAG Navigating to contact with ref key [$friendRefKey]")

                            sharedViewModel.navigateToContactsEvent.value = Event(true)
                            sharedViewModel.showContactEvent.value = Event(friendRefKey)
                        } else {
                            Log.w(
                                "$TAG Can't navigate to existing friend, ref key is null or empty"
                            )
                        }
                    },
                    { // onCopyNumberOrAddressToClipboard
                        val addressToCopy = model.displayedAddress
                        Log.i("$TAG Copying number [$addressToCopy] to clipboard")
                        copyNumberOrAddressToClipboard(addressToCopy)
                    },
                    { // onDeleteCallLog
                        Log.i("$TAG Deleting call log with ref key or call ID [${model.id}]")
                        model.delete()
                        listViewModel.applyFilter()
                    }
                )
                modalBottomSheet.show(parentFragmentManager, CallsListMenuDialogFragment.TAG)
            }
        }

        adapter.callLogClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                Log.i("$TAG Show details for call log with ID [${model.id}]")
                sharedViewModel.showCallLogEvent.value = Event(model.id ?: "")
            }
        }

        adapter.callLogCallBackClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                coreContext.postOnCoreThread {
                    Log.i("$TAG Starting call to [${model.address.asStringUriOnly()}]")
                    coreContext.startCall(model.address)
                }
            }
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.callsList.layoutManager = layoutManager

        listViewModel.callLogs.observe(viewLifecycleOwner) {
            val currentCount = adapter.itemCount
            adapter.submitList(it)
            Log.i("$TAG Call logs ready with [${it.size}] items")

            if (currentCount == 0) {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                    sharedViewModel.callsListReadyToBeDisplayedEvent.value = Event(true)
                }
            } else if (currentCount < it.size) {
                binding.callsList.scrollToPosition(0)
            }
        }

        listViewModel.historyDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG All call logs have been deleted")
                (requireActivity() as MainActivity).showGreenToast(
                    getString(R.string.toast_call_history_deleted),
                    R.drawable.check_fat_fill
                )
            }
        }

        sharedViewModel.forceRefreshCallLogsListEvent.observe(viewLifecycleOwner) {
            it.consume {
                listViewModel.applyFilter()
            }
        }

        binding.setMenuClickListener {
            showPopupMenu()
        }

        binding.setStartCallClickListener {
            sharedViewModel.showStartCallEvent.value = Event(true)
        }

        sharedViewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i(
                    "$TAG Default account changed, updating avatar in top bar & re-computing call logs"
                )
                listViewModel.update()
                listViewModel.applyFilter()
            }
        }

        // TopBarFragment related

        setViewModelAndTitle(listViewModel, "Calls")

        listViewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            listViewModel.applyFilter(filter.trim())
        }

        listViewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    binding.topBar.search.showKeyboard(requireActivity().window)
                } else {
                    binding.topBar.search.hideKeyboard()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        Log.i("$TAG Fragment is resumed, resetting missed calls count")
        sharedViewModel.resetMissedCallsCountEvent.value = Event(true)
    }

    private fun copyNumberOrAddressToClipboard(value: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = "SIP address"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

        (requireActivity() as MainActivity).showGreenToast(
            getString(R.string.toast_sip_address_copied_to_clipboard),
            R.drawable.check_fat_fill
        )
    }

    private fun showPopupMenu() {
        val popupView: CallsListPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.calls_list_popup_menu,
            null,
            false
        )
        val popupWindow = PopupWindow(
            popupView.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupView.setDeleteAllHistoryClickListener {
            val model = ConfirmationDialogModel()
            val dialog = DialogUtils.getRemoveAllCallLogsConfirmationDialog(
                requireActivity(),
                model
            )

            model.dismissEvent.observe(viewLifecycleOwner) {
                it.consume {
                    dialog.dismiss()
                }
            }

            model.confirmRemovalEvent.observe(viewLifecycleOwner) {
                it.consume {
                    Log.w("$TAG Removing all call entries from database")
                    listViewModel.removeAllCallLogs()
                    dialog.dismiss()
                }
            }

            dialog.show()
            popupWindow.dismiss()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(binding.menu, 0, 0, Gravity.BOTTOM)
    }
}
