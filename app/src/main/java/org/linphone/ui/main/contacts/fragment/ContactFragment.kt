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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import java.io.File
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.contacts.viewmodel.ContactViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

@UiThread
class ContactFragment : GenericFragment() {
    companion object {
        const val TAG = "[Contact Fragment]"
    }

    private lateinit var binding: ContactFragmentBinding

    private lateinit var viewModel: ContactViewModel

    private val args: ContactFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack() {
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ContactViewModel::class.java]
        binding.viewModel = viewModel

        val refKey = args.contactRefKey
        Log.i("[Contact Fragment] Looking up for contact with ref key [$refKey]")
        viewModel.findContactByRefKey(refKey)

        binding.setBackClickListener {
            goBack()
        }

        binding.setShareClickListener {
            viewModel.exportContactAsVCard()
        }

        binding.setDeleteClickListener {
            viewModel.deleteContact()
            goBack()
            // TODO: show toast ?
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        viewModel.contactFoundEvent.observe(viewLifecycleOwner) {
            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
            sharedViewModel.openSlidingPaneEvent.value = Event(true)
        }

        viewModel.showLongPressMenuForNumberOrAddressEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = ContactNumberOrAddressMenuDialogFragment(model.isSip, {
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
            }
        }

        viewModel.showNumberOrAddressPickerDialogEvent.observe(viewLifecycleOwner) {
            it.consume {
                val model = NumberOrAddressPickerDialogModel(
                    viewModel.sipAddressesAndPhoneNumbers.value.orEmpty()
                )
                val dialog = DialogUtils.getNumberOrAddressPickerDialog(requireActivity(), model)

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
                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(Uri.parse(uri), ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    putExtra("finishActivityOnSaveCompleted", true)
                }
                startActivity(editIntent)
            }
        }

        viewModel.openLinphoneContactEditor.observe(viewLifecycleOwner) {
            it.consume { refKey ->
                val action = ContactFragmentDirections.actionContactFragmentToEditContactFragment(
                    refKey
                )
                findNavController().navigate(action)
            }
        }

        viewModel.vCardTerminatedEvent.observe(viewLifecycleOwner) {
            it.consume { file ->
                Log.i("$TAG Friend was exported as vCard file [${file.absolutePath}]")
                shareContact(file)
            }
        }
    }

    private fun copyNumberOrAddressToClipboard(value: String, isSip: Boolean) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = if (isSip) "SIP address" else "Phone number"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

        (requireActivity() as MainActivity).showGreenToast(
            "Numéro copié dans le presse-papier",
            R.drawable.check
        )
    }

    private fun shareContact(file: File) {
        val publicUri = FileProvider.getUriForFile(
            requireContext(),
            requireContext().getString(R.string.file_provider),
            file
        )
        Log.i("$TAG Public URI for vCard file is [$publicUri], starting intent chooser")

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, publicUri)
            putExtra(Intent.EXTRA_SUBJECT, viewModel.contact.value?.friend?.name)
            type = ContactsContract.Contacts.CONTENT_VCARD_TYPE
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun inviteContactBySms(number: String) {
        Log.i("$TAG Sending SMS to [$number]")
        val smsIntent: Intent = Intent().apply {
            action = Intent.ACTION_SENDTO
            data = Uri.parse("smsto:$number")
            putExtra("address", number)
            putExtra("sms_body", "Coucou <3") // TODO FIXME
        }
        startActivity(smsIntent)
    }
}