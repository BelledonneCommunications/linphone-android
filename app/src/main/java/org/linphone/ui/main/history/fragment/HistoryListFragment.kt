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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.getListOfSipAddressesAndPhoneNumbers
import org.linphone.core.tools.Log
import org.linphone.databinding.HistoryListFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.fragment.AbstractMainFragment
import org.linphone.ui.main.history.adapter.HistoryListAdapter
import org.linphone.ui.main.history.model.CallLogModel
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.ui.main.history.viewmodel.HistoryListViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.RecyclerViewHeaderDecoration

@UiThread
class HistoryListFragment : AbstractMainFragment() {
    companion object {
        private const val TAG = "[History List Fragment]"
    }

    private lateinit var binding: HistoryListFragmentBinding

    private lateinit var listViewModel: HistoryListViewModel

    private lateinit var adapter: HistoryListAdapter

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private val numberOrAddressClickListener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(model: ContactNumberOrAddressModel) {
            coreContext.postOnCoreThread {
                val address = model.address
                if (address != null) {
                    Log.i("$TAG Starting call to [${address.asStringUriOnly()}]")
                    coreContext.startAudioCall(address)
                }
            }
        }

        override fun onLongPress(model: ContactNumberOrAddressModel) { }
    }

    override fun onDefaultAccountChanged() {
        Log.i(
            "$TAG Default account changed, updating avatar in top bar & re-computing call logs"
        )
        listViewModel.filter()
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.startCallFragment ||
            findNavController().currentDestination?.id == R.id.meetingWaitingRoomFragment
        ) {
            // Holds fragment in place while new fragment slides over it
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
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[HistoryListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel
        observeToastEvents(listViewModel)

        binding.historyList.setHasFixedSize(true)
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.outlineProvider = outlineProvider
        binding.historyList.clipToOutline = true

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.historyList.addItemDecoration(headerItemDecoration)

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
                        showDeleteConfirmationDialog(model)
                    }
                )
                modalBottomSheet.show(parentFragmentManager, HistoryMenuDialogFragment.TAG)
                bottomSheetDialog = modalBottomSheet
            }
        }

        adapter.callLogClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val uri = model.id
                Log.i("$TAG Show details for call log with ID [$uri]")
                if (!uri.isNullOrEmpty()) {
                    val navController = binding.historyNavContainer.findNavController()
                    val action =
                        HistoryFragmentDirections.actionGlobalHistoryFragment(uri)
                    navController.navigate(action)
                }
            }
        }

        adapter.callLogCallBackClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                coreContext.postOnCoreThread { core ->
                    val conferenceInfo = core.findConferenceInformationFromUri(model.address)
                    if (conferenceInfo != null) {
                        Log.i(
                            "$TAG Going to waiting room for conference [${conferenceInfo.subject}]"
                        )
                        sharedViewModel.goToMeetingWaitingRoomEvent.postValue(
                            Event(model.address.asStringUriOnly())
                        )
                    } else {
                        Log.i("$TAG Starting call to [${model.address.asStringUriOnly()}]")
                        coreContext.startAudioCall(model.address)
                    }
                }
            }
        }

        adapter.callFriendClickedEvent.observe(viewLifecycleOwner) {
            it.consume { friend ->
                coreContext.postOnCoreThread {
                    val singleAvailableAddress = LinphoneUtils.getSingleAvailableAddressForFriend(friend)
                    if (singleAvailableAddress != null) {
                        Log.i(
                            "$TAG Only 1 SIP address or phone number found for contact [${friend.name}], using it"
                        )
                        coreContext.startAudioCall(singleAvailableAddress)
                    } else {
                        val list = friend.getListOfSipAddressesAndPhoneNumbers(numberOrAddressClickListener)
                        Log.i(
                            "$TAG [${list.size}] numbers or addresses found for contact [${friend.name}], showing selection dialog"
                        )
                        coreContext.postOnMainThread {
                            showNumbersOrAddressesDialog(list)
                        }
                    }
                }
            }
        }

        adapter.callAddressClickedEvent.observe(viewLifecycleOwner) {
            it.consume { address ->
                Log.i("$TAG Starting call to [${address.asStringUriOnly()}]")
                coreContext.startAudioCall(address)
            }
        }

        listViewModel.callLogs.observe(viewLifecycleOwner) {
            adapter.submitList(it)

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.historyList.adapter != adapter) {
                binding.historyList.adapter = adapter
            }

            Log.i("$TAG Call logs ready with [${it.size}] items")
            listViewModel.fetchInProgress.value = false
        }

        listViewModel.historyInsertedEvent.observe(viewLifecycleOwner) {
            it.consume {
                // Scroll to top to display latest call log
                binding.historyList.scrollToPosition(0)
            }
        }

        listViewModel.historyDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG All call logs have been deleted")
                (requireActivity() as GenericActivity).showGreenToast(
                    getString(R.string.call_history_deleted_toast),
                    R.drawable.check
                )
            }
        }

        sharedViewModel.forceRefreshCallLogsListEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Re-compute call log history")
                listViewModel.filter()
            }
        }

        sharedViewModel.goToMeetingWaitingRoomEvent.observe(viewLifecycleOwner) {
            it.consume { uri ->
                if (findNavController().currentDestination?.id == R.id.historyListFragment) {
                    Log.i("$TAG Navigating to meeting waiting room fragment with URI [$uri]")
                    val action =
                        HistoryListFragmentDirections.actionHistoryListFragmentToMeetingWaitingRoomFragment(
                            uri
                        )
                    findNavController().navigate(action)
                }
            }
        }

        binding.setDeleteAllClickListener {
            showDeleteAllConfirmationDialog()
        }

        binding.setStartCallClickListener {
            if (findNavController().currentDestination?.id == R.id.historyListFragment) {
                Log.i("$TAG Navigating to start call fragment")
                val action =
                    HistoryListFragmentDirections.actionHistoryListFragmentToStartCallFragment()
                findNavController().navigate(action)
            }
        }

        // AbstractMainFragment related

        listViewModel.title.value = getString(R.string.bottom_navigation_calls_label)
        setViewModel(listViewModel)
        initViews(
            binding.slidingPaneLayout,
            binding.topBar,
            binding.bottomNavBar,
            R.id.historyListFragment
        )
    }

    override fun onPause() {
        super.onPause()

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    override fun onResume() {
        super.onResume()

        Log.i("$TAG Fragment is resumed, resetting missed calls count")
        sharedViewModel.resetMissedCallsCountEvent.value = Event(true)
        sharedViewModel.refreshDrawerMenuAccountsListEvent.value = Event(false)

        if (shouldRefreshDataInOnResume()) {
            Log.i("$TAG Keep app alive setting is enabled, refreshing view just in case")
            listViewModel.filter()
        }
    }

    private fun copyNumberOrAddressToClipboard(value: String) {
        if (AppUtils.copyToClipboard(requireContext(), "SIP address", value)) {
            (requireActivity() as GenericActivity).showGreenToast(
                getString(R.string.sip_address_copied_to_clipboard_toast),
                R.drawable.check
            )
        }
    }

    private fun showDeleteAllConfirmationDialog() {
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

    private fun showDeleteConfirmationDialog(callLogModel: CallLogModel) {
        val dialogModel = ConfirmationDialogModel()
        val dialog = DialogUtils.getRemoveCallLogConfirmationDialog(
            requireActivity(),
            dialogModel
        )

        dialogModel.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        dialogModel.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Deleting call log with ref key or call ID [${callLogModel.id}]")
                callLogModel.delete()
                listViewModel.filter()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showNumbersOrAddressesDialog(list: List<ContactNumberOrAddressModel>) {
        val numberOrAddressModel = NumberOrAddressPickerDialogModel(list)
        val dialog =
            DialogUtils.getNumberOrAddressPickerDialog(
                requireActivity(),
                numberOrAddressModel
            )

        numberOrAddressModel.dismissEvent.observe(viewLifecycleOwner) { event ->
            event.consume {
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
