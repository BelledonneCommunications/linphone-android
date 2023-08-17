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
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.contacts.viewmodel.ContactViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

class ContactFragment : GenericFragment() {
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
                val modalBottomSheet = ContactNumberOrAddressMenuDialogFragment({
                    // onDismiss
                    model.selected.value = false
                }, {
                    // onCopyNumberOrAddressToClipboard
                    copyNumberOrAddressToClipboard(model.displayedValue, model.isSip)
                })

                modalBottomSheet.show(
                    parentFragmentManager,
                    ContactNumberOrAddressMenuDialogFragment.TAG
                )
            }
        }

        viewModel.showNumberOrAddressPickerDialogEvent.observe(viewLifecycleOwner) {
            it.consume {
                val model = NumberOrAddressPickerDialogModel(viewModel)
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
}
