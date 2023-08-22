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
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.PopupWindow
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
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
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class CallsListFragment : GenericFragment() {
    companion object {
        const val TAG = "[Calls List Fragment]"
    }

    private lateinit var binding: CallsListFragmentBinding

    private val listViewModel: CallsListViewModel by navGraphViewModels(
        R.id.callsListFragment
    )

    private lateinit var adapter: CallsListAdapter

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.startCallFragment) {
            // Holds fragment in place while new contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

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

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        postponeEnterTransition()

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
                        adapter.deleteSelection()
                    }
                )
                modalBottomSheet.show(parentFragmentManager, CallsListMenuDialogFragment.TAG)
            }
        }

        adapter.callLogClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
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
            adapter.submitList(it)
            binding.callsList.scrollToPosition(0)

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
                sharedViewModel.callsListReadyToBeDisplayedEvent.value = Event(true)
            }
        }

        listViewModel.historyDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as MainActivity).showGreenToast(
                    "Historique supprimé",
                    R.drawable.check
                )
            }
        }

        sharedViewModel.forceRefreshCallLogsListEvent.observe(viewLifecycleOwner) {
            it.consume {
                listViewModel.applyFilter()
            }
        }

        sharedViewModel.searchFilter.observe(viewLifecycleOwner) {
            it.consume { filter ->
                listViewModel.applyFilter(filter)
            }
        }

        binding.setMenuClickListener {
            showPopupMenu()
        }

        binding.setStartCallClickListener {
            findNavController().navigate(R.id.action_global_startCallFragment)
        }
    }

    private fun copyNumberOrAddressToClipboard(value: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = "SIP address"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

        (requireActivity() as MainActivity).showGreenToast(
            "Numéro copié dans le presse-papier",
            R.drawable.check
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
