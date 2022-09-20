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
package org.linphone.activities.main.contact.fragments

import android.app.Dialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.MaterialSharedAxis
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.SnackBarActivity
import org.linphone.activities.clearDisplayedContact
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.contact.adapters.ContactsListAdapter
import org.linphone.activities.main.contact.viewmodels.ContactsListViewModel
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.navigateToContact
import org.linphone.activities.navigateToContactEditor
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactMasterFragmentBinding
import org.linphone.utils.*

class MasterContactsFragment : MasterFragment<ContactMasterFragmentBinding, ContactsListAdapter>() {
    override val dialogConfirmationMessageBeforeRemoval = R.plurals.contact_delete_dialog
    private lateinit var listViewModel: ContactsListViewModel

    private var sipUriToAdd: String? = null
    private var editOnClick: Boolean = false
    private var contactIdToDisplay: String? = null

    override fun getLayoutId(): Int = R.layout.contact_master_fragment

    override fun onDestroyView() {
        binding.contactsList.adapter = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        listViewModel = ViewModelProvider(this)[ContactsListViewModel::class.java]
        binding.viewModel = listViewModel

        /* Shared view model & sliding pane related */

        setUpSlidingPane(binding.slidingPane)

        useMaterialSharedAxisXForwardAnimation = false
        sharedViewModel.updateContactsAnimationsBasedOnDestination.observe(
            viewLifecycleOwner
        ) {
            it.consume { id ->
                val forward = when (id) {
                    R.id.dialerFragment, R.id.masterChatRoomsFragment -> false
                    else -> true
                }
                if (corePreferences.enableAnimations) {
                    val portraitOrientation =
                        resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
                    val axis =
                        if (portraitOrientation) MaterialSharedAxis.X else MaterialSharedAxis.Y
                    enterTransition = MaterialSharedAxis(axis, forward)
                    reenterTransition = MaterialSharedAxis(axis, forward)
                    returnTransition = MaterialSharedAxis(axis, !forward)
                    exitTransition = MaterialSharedAxis(axis, !forward)
                }
            }
        }

        sharedViewModel.contactFragmentOpenedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                binding.slidingPane.openPane()
            }
        }

        sharedViewModel.layoutChangedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                sharedViewModel.isSlidingPaneSlideable.value = binding.slidingPane.isSlideable
                if (binding.slidingPane.isSlideable) {
                    val navHostFragment =
                        childFragmentManager.findFragmentById(R.id.contacts_nav_container) as NavHostFragment
                    if (navHostFragment.navController.currentDestination?.id == R.id.emptyContactFragment) {
                        Log.i("[Contacts] Foldable device has been folded, closing side pane with empty fragment")
                        binding.slidingPane.closePane()
                    }
                }
            }
        }

        /* End of shared view model & sliding pane related */

        _adapter = ContactsListAdapter(listSelectionViewModel, viewLifecycleOwner)
        binding.contactsList.setHasFixedSize(true)
        binding.contactsList.adapter = adapter

        binding.setEditClickListener {
            if (PermissionHelper.get().hasWriteContactsPermission()) {
                listSelectionViewModel.isEditionEnabled.value = true
            } else {
                Log.i("[Contacts] Asking for WRITE_CONTACTS permission")
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_CONTACTS), 1)
            }
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.contactsList.layoutManager = layoutManager

        // Swipe action
        val swipeConfiguration = RecyclerViewSwipeConfiguration()
        val white = ContextCompat.getColor(requireContext(), R.color.white_color)

        swipeConfiguration.rightToLeftAction = RecyclerViewSwipeConfiguration.Action(
            requireContext().getString(R.string.dialog_delete),
            white,
            ContextCompat.getColor(requireContext(), R.color.red_color)
        )
        val swipeListener = object : RecyclerViewSwipeListener {
            override fun onLeftToRightSwipe(viewHolder: RecyclerView.ViewHolder) {}

            override fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder) {
                val viewModel = DialogViewModel(getString(R.string.contact_delete_one_dialog))
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

                val index = viewHolder.bindingAdapterPosition
                if (index < 0 || index >= adapter.currentList.size) {
                    Log.e("[Contacts] Index is out of bound, can't delete contact")
                } else {
                    val contactViewModel = adapter.currentList[index]
                    if (contactViewModel.isNativeContact.value == false) {
                        adapter.notifyItemChanged(index)
                        val activity = requireActivity() as MainActivity
                        activity.showSnackBar(R.string.contact_cant_be_deleted)
                        return
                    }

                    viewModel.showCancelButton {
                        adapter.notifyItemChanged(index)
                        dialog.dismiss()
                    }

                    viewModel.showDeleteButton(
                        {
                            val deletedContact =
                                adapter.currentList[index].contact.value
                            if (deletedContact != null) {
                                listViewModel.deleteContact(deletedContact)
                                if (!binding.slidingPane.isSlideable &&
                                    deletedContact == sharedViewModel.selectedContact.value
                                ) {
                                    Log.i("[Contacts] Currently displayed contact has been deleted, removing detail fragment")
                                    clearDisplayedContact()
                                }
                            }
                            dialog.dismiss()
                        },
                        getString(R.string.dialog_delete)
                    )
                }

                dialog.show()
            }
        }
        RecyclerViewSwipeUtils(ItemTouchHelper.LEFT, swipeConfiguration, swipeListener)
            .attachToRecyclerView(binding.contactsList)

        // Divider between items
        binding.contactsList.addItemDecoration(AppUtils.getDividerDecoration(requireContext(), layoutManager))

        // Displays the first letter header
        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.contactsList.addItemDecoration(headerItemDecoration)

        adapter.selectedContactEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { contact ->
                Log.d("[Contacts] Selected item in list changed: $contact")
                sharedViewModel.selectedContact.value = contact
                (requireActivity() as MainActivity).hideKeyboard()

                if (editOnClick) {
                    navigateToContactEditor(sipUriToAdd, binding.slidingPane)
                    editOnClick = false
                    sipUriToAdd = null
                } else {
                    navigateToContact()
                }
            }
        }

        coreContext.contactsManager.fetchInProgress.observe(
            viewLifecycleOwner
        ) {
            listViewModel.fetchInProgress.value = it
        }

        listViewModel.contactsList.observe(
            viewLifecycleOwner
        ) {
            val id = contactIdToDisplay
            if (id != null) {
                val contact = coreContext.contactsManager.findContactById(id)
                if (contact != null) {
                    contactIdToDisplay = null
                    Log.i("[Contacts] Found matching contact $contact after callback")
                    adapter.selectedContactEvent.value = Event(contact)
                }
            }
            adapter.submitList(it)
        }

        listViewModel.moreResultsAvailableEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                (requireActivity() as SnackBarActivity).showSnackBar(R.string.contacts_ldap_query_more_results_available)
            }
        }

        binding.setAllContactsToggleClickListener {
            listViewModel.sipContactsSelected.value = false
        }
        binding.setSipContactsToggleClickListener {
            listViewModel.sipContactsSelected.value = true
        }

        listViewModel.sipContactsSelected.observe(
            viewLifecycleOwner
        ) {
            listViewModel.updateContactsList(true)
        }

        listViewModel.filter.observe(
            viewLifecycleOwner
        ) {
            listViewModel.updateContactsList(false)
        }

        binding.setNewContactClickListener {
            // Remove any previously selected contact
            sharedViewModel.selectedContact.value = null
            editOnClick = false
            navigateToContactEditor(sipUriToAdd, binding.slidingPane)
            sipUriToAdd = null
        }

        val id = arguments?.getString("id")
        val sipUri = arguments?.getString("sipUri")
        val addressString = arguments?.getString("address")
        arguments?.clear()

        if (id != null) {
            Log.i("[Contacts] Found contact id parameter in arguments: $id")
            val contact = coreContext.contactsManager.findContactById(id)
            if (contact != null) {
                Log.i("[Contacts] Found matching contact $contact")
                adapter.selectedContactEvent.value = Event(contact)
            } else {
                Log.w("[Contacts] Matching contact not found yet, waiting for contacts updated callback")
                contactIdToDisplay = id
            }
        } else if (sipUri != null) {
            Log.i("[Contacts] Found sipUri parameter in arguments: $sipUri")
            sipUriToAdd = sipUri
            (activity as MainActivity).showSnackBar(R.string.contact_choose_existing_or_new_to_add_number)
            editOnClick = true
        } else if (addressString != null) {
            val address = Factory.instance().createAddress(addressString)
            if (address != null) {
                Log.i("[Contacts] Found friend native pointer parameter in arguments: ${address.asStringUriOnly()}")
                val contact = coreContext.contactsManager.findContactByAddress(address)
                if (contact != null) {
                    Log.i("[Contacts] Found matching contact $contact")
                    adapter.selectedContactEvent.value = Event(contact)
                }
            }
        }

        if (corePreferences.enableNativeAddressBookIntegration) {
            if (!PermissionHelper.get().hasReadContactsPermission()) {
                Log.i("[Contacts] Asking for READ_CONTACTS permission")
                requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 0)
            }
        }
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<Friend>()
        var closeSlidingPane = false
        for (index in indexesOfItemToDelete) {
            val contact = adapter.currentList[index].contact.value
            if (contact != null) {
                list.add(contact)
            }

            if (contact == sharedViewModel.selectedContact.value) {
                closeSlidingPane = true
            }
        }
        listViewModel.deleteContacts(list)

        if (!binding.slidingPane.isSlideable && closeSlidingPane) {
            Log.i("[Contacts] Currently displayed contact has been deleted, removing detail fragment")
            clearDisplayedContact()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Log.i("[Contacts] READ_CONTACTS permission granted")
                coreContext.fetchContacts()
            } else {
                Log.w("[Contacts] READ_CONTACTS permission denied")
            }
        } else if (requestCode == 1) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Log.i("[Contacts] WRITE_CONTACTS permission granted")
                listSelectionViewModel.isEditionEnabled.value = true
            } else {
                Log.w("[Contacts] WRITE_CONTACTS permission denied")
            }
        }
    }
}
