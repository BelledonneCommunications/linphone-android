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

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.contacts.model.ContactTrustDialogModel
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.contacts.viewmodel.ContactViewModel
import org.linphone.ui.main.fragment.SlidingPaneChildFragment
import org.linphone.utils.AppUtils
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import androidx.core.net.toUri

@UiThread
class ContactFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[Contact Fragment]"
    }

    private lateinit var binding: ContactFragmentBinding

    private lateinit var viewModel: ContactViewModel

    private val args: ContactFragmentArgs by navArgs()

    private var numberOrAddressPickerDialog: Dialog? = null

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)

        if (findNavController().currentDestination?.id == R.id.contactFragment) {
            // If not done this fragment won't be paused, which will cause us issues
            val action = ContactFragmentDirections.actionContactFragmentToEmptyFragment()
            findNavController().navigate(action)
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ContactViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val refKey = args.contactRefKey
        Log.i("$TAG Looking up for contact with ref key [$refKey]")
        viewModel.findContact(sharedViewModel.displayedFriend, refKey)

        binding.setBackClickListener {
            goBack()
        }

        binding.setShareClickListener {
            Log.i("$TAG Sharing friend, exporting it as vCard file first")
            viewModel.exportContactAsVCard()
        }

        binding.setDeleteClickListener {
            showDeleteConfirmationDialog()
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        viewModel.contactFoundEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Contact has been found, start postponed enter transition")
                startPostponedEnterTransition()
                sharedViewModel.openSlidingPaneEvent.value = Event(true)
            }
        }

        viewModel.showLongPressMenuForNumberOrAddressEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = ContactNumberOrAddressMenuDialogFragment(model.isSip, model.hasPresence, {
                    // onDismiss
                    model.selected.value = false
                }, {
                    // onCopyNumberOrAddressToClipboard
                    copyNumberOrAddressToClipboard(model.displayedValue, model.isSip)
                }, {
                    // onInviteNumberOrAddress
                    inviteContactBySms(model.displayedValue)
                })

                modalBottomSheet.show(
                    parentFragmentManager,
                    ContactNumberOrAddressMenuDialogFragment.TAG
                )
                bottomSheetDialog = modalBottomSheet
            }
        }

        viewModel.showNumberOrAddressPickerDialogEvent.observe(viewLifecycleOwner) {
            it.consume {
                val model = NumberOrAddressPickerDialogModel(
                    viewModel.sipAddressesAndPhoneNumbers.value.orEmpty()
                )
                val dialog = DialogUtils.getNumberOrAddressPickerDialog(requireActivity(), model)
                numberOrAddressPickerDialog = dialog

                model.dismissEvent.observe(viewLifecycleOwner) { event ->
                    event.consume {
                        dialog.dismiss()
                    }
                }

                dialog.show()
            }
        }

        viewModel.openNativeContactEditor.observe(viewLifecycleOwner) {
            it.consume { uri ->
                try {
                    val editIntent = Intent(Intent.ACTION_EDIT).apply {
                        setDataAndType(uri.toUri(), ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                        putExtra("finishActivityOnSaveCompleted", true)
                    }
                    startActivity(editIntent)
                } catch (anfe: ActivityNotFoundException) {
                    Log.e("$TAG Failed to open native contact editor with URI [$uri]: $anfe")
                }
            }
        }

        viewModel.openLinphoneContactEditor.observe(viewLifecycleOwner) {
            it.consume { refKey ->
                if (findNavController().currentDestination?.id == R.id.contactFragment) {
                    val action =
                        ContactFragmentDirections.actionContactFragmentToEditContactFragment(
                            refKey
                        )
                    findNavController().navigate(action)
                }
            }
        }

        viewModel.goToConversationEvent.observe(viewLifecycleOwner) {
            it.consume { conversationId ->
                Log.i("$TAG Going to conversation [$conversationId]")
                sharedViewModel.showConversationEvent.value = Event(conversationId)
                sharedViewModel.navigateToConversationsEvent.value = Event(true)
            }
        }

        viewModel.vCardTerminatedEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val contactName = pair.first
                val file = pair.second
                Log.i(
                    "$TAG Friend [$contactName] was exported as vCard file [${file.absolutePath}], sharing it"
                )
                shareContact(contactName, file)
            }
        }

        viewModel.displayTrustProcessDialogEvent.observe(viewLifecycleOwner) {
            it.consume {
                showTrustProcessDialog()
            }
        }

        viewModel.startCallToDeviceToIncreaseTrustEvent.observe(viewLifecycleOwner) {
            it.consume { triple ->
                callDirectlyOrShowConfirmTrustCallDialog(triple.first, triple.second, triple.third)
            }
        }

        viewModel.contactRemovedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w(
                    "$TAG Contact [${viewModel.contact.value?.name?.value}] has been deleted"
                )

                val message = getString(R.string.contact_deleted_toast)
                val icon = R.drawable.check
                (requireActivity() as GenericActivity).showGreenToast(message, icon)

                goBack()
            }
        }
    }

    override fun onPause() {
        super.onPause()

        numberOrAddressPickerDialog?.dismiss()
        numberOrAddressPickerDialog = null

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
    }

    private fun copyNumberOrAddressToClipboard(value: String, isSip: Boolean) {
        val label = if (isSip) "SIP address" else "Phone number"
        if (AppUtils.copyToClipboard(requireContext(), label, value)) {
            val message = if (isSip) {
                getString(R.string.sip_address_copied_to_clipboard_toast)
            } else {
                getString(R.string.contact_details_phone_number_copied_to_clipboard_toast)
            }
            (requireActivity() as GenericActivity).showGreenToast(message, R.drawable.check)
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

    private fun inviteContactBySms(number: String) {
        Log.i("$TAG Sending SMS to [$number]")

        val smsBody = getString(
            R.string.contact_sms_invite_content,
            getString(R.string.website_download_url)
        )
        val smsIntent: Intent = Intent().apply {
            action = Intent.ACTION_SENDTO
            data = "smsto:$number".toUri()
            putExtra("address", number)
            putExtra("sms_body", smsBody)
        }
        try {
            startActivity(smsIntent)
        } catch (anfe: ActivityNotFoundException) {
            Log.e("$TAG Failed to start SMS intent: $anfe")
        }
    }

    private fun showTrustProcessDialog() {
        val initials = viewModel.contact.value?.initials?.value.orEmpty()
        val picture = viewModel.contact.value?.picturePath?.value.orEmpty()
        val model = ContactTrustDialogModel(initials, picture)
        val dialog = DialogUtils.getContactTrustProcessExplanationDialog(requireActivity(), model)
        dialog.show()
    }

    private fun callDirectlyOrShowConfirmTrustCallDialog(contactName: String, deviceName: String, deviceSipUri: String) {
        coreContext.postOnCoreThread {
            if (corePreferences.showDialogWhenCallingDeviceUuidDirectly) {
                coreContext.postOnMainThread {
                    showConfirmTrustCallDialog(contactName, deviceName, deviceSipUri)
                }
            } else {
                val address = Factory.instance().createAddress(deviceSipUri)
                if (address != null) {
                    coreContext.startCall(address, forceZRTP = true)
                }
            }
        }
    }

    private fun showConfirmTrustCallDialog(contactName: String, deviceName: String, deviceSipUri: String) {
        val label = AppUtils.getFormattedString(
            R.string.contact_dialog_increase_trust_level_message,
            contactName,
            deviceName
        )
        val model = ConfirmationDialogModel(label)
        val dialog = DialogUtils.getContactTrustCallConfirmationDialog(requireActivity(), model)

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                coreContext.postOnCoreThread {
                    if (model.doNotShowAnymore.value == true) {
                        corePreferences.showDialogWhenCallingDeviceUuidDirectly = false
                    }

                    val address = Factory.instance().createAddress(deviceSipUri)
                    if (address != null) {
                        coreContext.startCall(address, forceZRTP = true)
                    }
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmationDialog() {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getDeleteContactConfirmationDialog(
            requireActivity(),
            model,
            viewModel.contact.value?.name?.value ?: ""
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w(
                    "$TAG Deleting contact [${viewModel.contact.value?.name?.value}]"
                )
                viewModel.deleteContact()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
