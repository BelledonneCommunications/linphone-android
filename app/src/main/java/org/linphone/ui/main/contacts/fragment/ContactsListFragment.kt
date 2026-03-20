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
package org.linphone.ui.main.contacts.fragment

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.LinphoneApplication.Companion.coreContext
import java.io.File
import com.hansol.siphone.R
import org.linphone.core.FriendList
import org.linphone.core.tools.Log
import com.hansol.siphone.databinding.ContactsListFilterPopupMenuBinding
import com.hansol.siphone.databinding.ContactsListFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.contacts.adapter.ContactsOrgAdapter
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.viewmodel.ContactsListViewModel
import org.linphone.ui.main.fragment.AbstractMainFragment
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class ContactsListFragment : AbstractMainFragment() {
    companion object {
        private const val TAG = "[Contacts List Fragment]"
    }

    private lateinit var binding: ContactsListFragmentBinding

    private lateinit var listViewModel: ContactsListViewModel

    private lateinit var orgAdapter: ContactsOrgAdapter

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (!listViewModel.navigateBack()) {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG READ_CONTACTS permission has been granted, start contacts loader")
            (requireActivity() as MainActivity).loadContacts()
        } else {
            Log.w("$TAG READ_CONTACTS permission has been denied")
        }
    }

    private val swipeToRefreshListener = SwipeRefreshLayout.OnRefreshListener {
        Log.i("$TAG Swipe to refresh triggered, updating CardDAV friend lists")
        listViewModel.refreshCardDavContacts()
    }

    override fun onDefaultAccountChanged() {
        Log.i(
            "$TAG Default account changed, updating avatar in top bar & refreshing contacts list"
        )
        listViewModel.applyCurrentDefaultAccountFilter()
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.newContactFragment
        ) {
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        orgAdapter = ContactsOrgAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[ContactsListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel
        observeToastEvents(listViewModel)

        // Disabled by default, may be enabled in onResume()
        binding.contactsListSwipeRefresh.isEnabled = false
        binding.contactsListSwipeRefresh.setOnRefreshListener(swipeToRefreshListener)

        binding.contactsList.setHasFixedSize(true)
        binding.contactsList.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsList.outlineProvider = outlineProvider
        binding.contactsList.adapter = orgAdapter

        // Category click → navigate deeper
        orgAdapter.categoryClickedEvent.observe(viewLifecycleOwner) {
            it.consume { category ->
                binding.contactsList.scrollToPosition(0)
                listViewModel.navigateIntoCategory(category)
            }
        }

        // Contact click → open contact detail
        orgAdapter.contactClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                sharedViewModel.displayedFriend = model.friend
                sharedViewModel.showContactEvent.value = Event(model.id)
            }
        }

        // Contact long press → bottom sheet menu
        orgAdapter.contactLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = ContactsListMenuDialogFragment(
                    model.isFavourite.value == true,
                    model.isStored,
                    isReadOnly = model.isReadOnly,
                    isNative = model.isNative,
                    { orgAdapter.resetSelection() },
                    { listViewModel.toggleContactFavoriteFlag(model) },
                    {
                        Log.i("$TAG Sharing friend [${model.name.value}]")
                        listViewModel.exportContactAsVCard(model.friend)
                    },
                    { showDeleteConfirmationDialog(model) }
                )
                modalBottomSheet.show(parentFragmentManager, ContactsListMenuDialogFragment.TAG)
                bottomSheetDialog = modalBottomSheet
            }
        }

        listViewModel.isListFiltered.observe(viewLifecycleOwner) { filtered ->
            binding.contactsList.clipToOutline = filtered
        }

        // Observe mixed list (categories + contacts combined)
        listViewModel.mixedList.observe(viewLifecycleOwner) {
            orgAdapter.submitList(it)
            Log.i("$TAG Mixed list updated with [${it.size}] items")
            listViewModel.fetchInProgress.value = false
        }

        // Update breadcrumb when navigation path changes
        listViewModel.navigationPath.observe(viewLifecycleOwner) { path ->
            updateBreadcrumb(path)
            backPressedCallback.isEnabled = path.isNotEmpty()
        }

        listViewModel.vCardTerminatedEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val contactName = pair.first
                val file = pair.second
                Log.i(
                    "$TAG Friend [$contactName] was exported as vCard file [${file.absolutePath}], sharing it"
                )
                shareContact(contactName, file)
            }
        }

        listViewModel.cardDavSynchronizationCompletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG CardDAV synchronization has completed")
                binding.contactsListSwipeRefresh.isRefreshing = false
            }
        }

        binding.setOnNewContactClicked {
            sharedViewModel.showNewContactEvent.value = Event(true)
        }

        binding.setFilterClickListener {
            showFilterPopupMenu(binding.topBar.extraAction)
        }

        sharedViewModel.showContactEvent.observe(viewLifecycleOwner) {
            it.consume { refKey ->
                Log.i("$TAG Displaying contact with ref key [$refKey]")
                val navController = binding.contactsNavContainer.findNavController()
                val action = ContactFragmentDirections.actionGlobalContactFragment(
                    refKey
                )
                navController.navigate(action)
            }
        }

        sharedViewModel.showNewContactEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.contactsListFragment) {
                    Log.i("$TAG Opening contact editor for creating new contact")
                    val action =
                        ContactsListFragmentDirections.actionContactsListFragmentToNewContactFragment()
                    findNavController().navigate(action)
                }
            }
        }

        sharedViewModel.forceRefreshContactsList.observe(viewLifecycleOwner) {
            it.consume {
                listViewModel.filter()
            }
        }

        // Register back press callback
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        // AbstractMainFragment related
        listViewModel.title.value = getString(R.string.bottom_navigation_contacts_label)
        setViewModel(listViewModel)
        initViews(
            binding.slidingPaneLayout,
            binding.topBar,
            binding.bottomNavBar,
            R.id.contactsListFragment
        )

        // Home icon scrolls to top / navigates to root
        binding.breadcrumbHome?.setOnClickListener {
            listViewModel.navigateToPath(emptyList())
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("$TAG READ_CONTACTS permission wasn't granted yet, asking for it now")
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    override fun onPause() {
        super.onPause()

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    override fun onResume() {
        super.onResume()

        coreContext.postOnCoreThread { core ->
            val cardDavFriendList = core.friendsLists.find {
                it.type == FriendList.Type.CardDAV
            }
            val cardDavFriendListFound = cardDavFriendList != null
            if (cardDavFriendListFound) {
                Log.i("$TAG CardDAV friend list [${cardDavFriendList.displayName}] found, enabling swipe to refresh")
            } else {
                Log.i("$TAG No CardDAV friend list was found, disabling swipe to refresh")
            }
            coreContext.postOnMainThread {
                binding.contactsListSwipeRefresh.isEnabled = cardDavFriendListFound
            }
        }
    }

    private fun updateBreadcrumb(path: List<String>) {
        val container = binding.breadcrumbContainer ?: return
        // Remove all views after the home icon (index 0)
        while (container.childCount > 1) {
            container.removeViewAt(1)
        }

        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(R.attr.color_main1_500, typedValue, true)
        val accentColor = typedValue.data
        requireContext().theme.resolveAttribute(R.attr.color_main2_400, typedValue, true)
        val secondaryColor = typedValue.data

        for ((index, segment) in path.withIndex()) {
            // Separator ">"
            val separator = TextView(requireContext())
            separator.text = " › "
            separator.textSize = 14f
            separator.setTextColor(secondaryColor)
            val sepParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            separator.layoutParams = sepParams
            container.addView(separator)

            // Segment text
            val textView = TextView(requireContext())
            textView.text = segment
            textView.textSize = 14f
            val isLast = index == path.size - 1
            if (isLast) {
                textView.setTextColor(accentColor)
                textView.paintFlags = textView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                textView.setTextColor(secondaryColor)
                val clickPath = path.subList(0, index + 1)
                textView.setOnClickListener {
                    listViewModel.navigateToPath(clickPath)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textView.layoutParams = params
            container.addView(textView)
        }
    }

    private fun shareContact(name: String, file: File) {
        val publicUri = FileProvider.getUriForFile(
            requireContext(),
            requireContext().getString(R.string.file_provider),
            file
        )
        Log.i("$TAG Public URI for vCard file is [$publicUri], starting intent chooser")

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, publicUri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            type = ContactsContract.Contacts.CONTENT_VCARD_TYPE
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        try {
            startActivity(shareIntent)
        } catch (anfe: ActivityNotFoundException) {
            Log.e("$TAG Failed to start intent chooser: $anfe")
        }
    }

    private fun showFilterPopupMenu(view: View) {
        val popupView: ContactsListFilterPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.contacts_list_filter_popup_menu,
            null,
            false
        )
        popupView.seeAllSelected = listViewModel.areAllContactsDisplayed.value == true
        popupView.showLinphoneFilter = listViewModel.isDefaultAccountLinphone.value == true

        val popupWindow = PopupWindow(
            popupView.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupView.setNoFilterClickListener {
            if (listViewModel.areAllContactsDisplayed.value != true) {
                listViewModel.changeContactsFilter(
                    onlyLinphoneContacts = false,
                    onlySipContacts = false
                )
            }
            popupWindow.dismiss()
        }

        popupView.setLinphoneOnlyClickListener {
            if (listViewModel.areAllContactsDisplayed.value == true) {
                listViewModel.changeContactsFilter(
                    onlyLinphoneContacts = true,
                    onlySipContacts = false
                )
            }
            popupWindow.dismiss()
        }

        popupView.setSipOnlyClickListener {
            if (listViewModel.areAllContactsDisplayed.value == true) {
                listViewModel.changeContactsFilter(
                    onlyLinphoneContacts = false,
                    onlySipContacts = true
                )
            }
            popupWindow.dismiss()
        }

        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(view, 0, 0, Gravity.BOTTOM)
    }

    private fun showDeleteConfirmationDialog(contactModel: ContactAvatarModel) {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getDeleteContactConfirmationDialog(
            requireActivity(),
            model,
            contactModel.contactName.orEmpty()
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                listViewModel.deleteContact(contactModel)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
