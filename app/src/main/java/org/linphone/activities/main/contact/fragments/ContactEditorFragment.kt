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

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.view.View
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import java.io.File
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.contact.data.NumberOrAddressEditorData
import org.linphone.activities.main.contact.viewmodels.*
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.navigateToContact
import org.linphone.contact.NativeContact
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactEditorFragmentBinding
import org.linphone.utils.FileUtils
import org.linphone.utils.ImageUtils
import org.linphone.utils.PermissionHelper

class ContactEditorFragment : GenericFragment<ContactEditorFragmentBinding>(), SyncAccountPickerFragment.SyncAccountPickedListener {
    private lateinit var viewModel: ContactEditorViewModel
    private lateinit var sharedViewModel: SharedMainViewModel
    private var temporaryPicturePath: File? = null

    override fun getLayoutId(): Int = R.layout.contact_editor_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        viewModel = ViewModelProvider(
            this,
            ContactEditorViewModelFactory(sharedViewModel.selectedContact.value)
        )[ContactEditorViewModel::class.java]
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        binding.setAvatarClickListener {
            pickFile()
        }

        binding.setSaveChangesClickListener {
            viewModel.syncAccountName = null
            viewModel.syncAccountType = null

            if (viewModel.c == null && corePreferences.showNewContactAccountDialog) {
                Log.i("[Contact Editor] New contact, ask user where to store it")
                SyncAccountPickerFragment(this).show(childFragmentManager, "SyncAccountPicker")
            } else {
                saveContact()
            }
        }

        val sipUri = arguments?.getString("SipUri")
        if (sipUri != null) {
            Log.i("[Contact Editor] Found SIP URI in arguments: $sipUri")
            val newSipUri = NumberOrAddressEditorData("", true)
            newSipUri.newValue.value = sipUri

            val list = arrayListOf<NumberOrAddressEditorData>()
            list.addAll(viewModel.addresses.value.orEmpty())
            list.add(newSipUri)
            viewModel.addresses.value = list
        }

        if (!PermissionHelper.required(requireContext()).hasWriteContactsPermission()) {
            Log.i("[Contact Editor] Asking for WRITE_CONTACTS permission")
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_CONTACTS), 0)
        }
    }

    override fun onSyncAccountClicked(name: String?, type: String?) {
        Log.i("[Contact Editor] Using account $name / $type")
        viewModel.syncAccountName = name
        viewModel.syncAccountType = type
        saveContact()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Log.i("[Contact Editor] WRITE_CONTACTS permission granted")
            } else {
                Log.w("[Contact Editor] WRITE_CONTACTS permission denied")
                (requireActivity() as MainActivity).showSnackBar(R.string.contact_editor_write_permission_denied)
                findNavController().popBackStack()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                val contactImageFilePath = ImageUtils.getFilePathFromPickerIntent(data, temporaryPicturePath)
                if (contactImageFilePath != null) {
                    viewModel.setPictureFromPath(contactImageFilePath)
                }
            }
        }
    }

    private fun saveContact() {
        val savedContact = viewModel.save()
        if (savedContact is NativeContact) {
            savedContact.syncValuesFromAndroidContact(requireContext())
            Log.i("[Contact Editor] Displaying contact $savedContact")
            navigateToContact(savedContact)
        } else {
            findNavController().popBackStack()
        }
    }

    private fun pickFile() {
        val cameraIntents = ArrayList<Intent>()

        // Handles image picking
        val galleryIntent = Intent(Intent.ACTION_PICK)
        galleryIntent.type = "image/*"

        if (PermissionHelper.get().hasCameraPermission()) {
            // Allows to capture directly from the camera
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val tempFileName = System.currentTimeMillis().toString() + ".jpeg"
            val file = FileUtils.getFileStoragePath(tempFileName)
            temporaryPicturePath = file
            val publicUri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getString(R.string.file_provider),
                file
            )
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, publicUri)
            captureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            cameraIntents.add(captureIntent)
        }

        val chooserIntent =
            Intent.createChooser(galleryIntent, getString(R.string.chat_message_pick_file_dialog))
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(arrayOf<Parcelable>()))

        startActivityForResult(chooserIntent, 0)
    }
}
