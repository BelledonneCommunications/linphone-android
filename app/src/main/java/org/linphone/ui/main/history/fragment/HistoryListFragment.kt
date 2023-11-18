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
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryListFragmentBinding
import org.linphone.databinding.HistoryListPopupMenuBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.AbstractTopBarFragment
import org.linphone.ui.main.history.adapter.HistoryListAdapter
import org.linphone.ui.main.history.model.ConfirmationDialogModel
import org.linphone.ui.main.history.viewmodel.HistoryListViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class HistoryListFragment : AbstractTopBarFragment() {
    companion object {
        private const val TAG = "[Calls List Fragment]"
    }

    private lateinit var binding: HistoryListFragmentBinding

    private lateinit var listViewModel: HistoryListViewModel

    private lateinit var adapter: HistoryListAdapter

    override fun onDefaultAccountChanged() {
        Log.i(
            "$TAG Default account changed, updating avatar in top bar & re-computing call logs"
        )
        listViewModel.update()
        listViewModel.applyFilter()
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.startCallFragment) {
            // Holds fragment in place while new contact fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = HistoryListAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HistoryListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[HistoryListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        binding.historyList.setHasFixedSize(true)
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())

        adapter.callLogLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = HistoryMenuDialogFragment(
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
                        val addressToCopy = model.sipUri
                        Log.i("$TAG Copying number [$addressToCopy] to clipboard")
                        copyNumberOrAddressToClipboard(addressToCopy)
                    },
                    { // onDeleteCallLog
                        Log.i("$TAG Deleting call log with ref key or call ID [${model.id}]")
                        model.delete()
                        listViewModel.applyFilter()
                    }
                )
                modalBottomSheet.show(parentFragmentManager, HistoryMenuDialogFragment.TAG)
            }
        }

        adapter.callLogClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val uri = model.id
                Log.i("$TAG Show details for call log with ID [$uri]")
                if (!uri.isNullOrEmpty()) {
                    val navController = binding.historyNavContainer.findNavController()
                    val action =
                        HistoryContactFragmentDirections.actionGlobalHistoryContactFragment(uri)
                    navController.navigate(action)
                }
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

        listViewModel.callLogs.observe(viewLifecycleOwner) {
            val currentCount = adapter.itemCount
            adapter.submitList(it)
            Log.i("$TAG Call logs ready with [${it.size}] items")

            if (binding.historyList.adapter != adapter) {
                binding.historyList.adapter = adapter
            }

            if (currentCount < it.size) {
                binding.historyList.scrollToPosition(0)
            }

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
                sharedViewModel.isFirstFragmentReady = true
            }
        }

        listViewModel.historyDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG All call logs have been deleted")
                (requireActivity() as MainActivity).showGreenToast(
                    getString(R.string.toast_call_history_deleted),
                    R.drawable.check
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
            if (findNavController().currentDestination?.id == R.id.historyListFragment) {
                Log.i("$TAG Navigating to start call fragment")
                val action =
                    HistoryListFragmentDirections.actionHistoryListFragmentToStartCallFragment()
                findNavController().navigate(action)
            }
        }

        // TopBarFragment related

        setViewModelAndTitle(
            binding.topBar.search,
            listViewModel,
            getString(R.string.bottom_navigation_calls_label)
        )

        initBottomNavBar(binding.bottomNavBar.root)

        initSlidingPane(binding.slidingPaneLayout)

        initNavigation(R.id.historyListFragment)
    }

    override fun onResume() {
        super.onResume()

        Log.i("$TAG Fragment is resumed, resetting missed calls count")
        sharedViewModel.resetMissedCallsCountEvent.value = Event(true)

        // Scroll to top to display latest call logs
        binding.historyList.scrollToPosition(0)
    }

    private fun copyNumberOrAddressToClipboard(value: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = "SIP address"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

        (requireActivity() as MainActivity).showGreenToast(
            getString(R.string.toast_sip_address_copied_to_clipboard),
            R.drawable.check
        )
    }

    private fun showPopupMenu() {
        val popupView: HistoryListPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.history_list_popup_menu,
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
            showDeleteConfirmationDialog()
            popupWindow.dismiss()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(binding.menu, 0, 0, Gravity.BOTTOM)
    }

    private fun showDeleteConfirmationDialog() {
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

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG Removing all call entries from database")
                listViewModel.removeAllCallLogs()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
