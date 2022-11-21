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
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.contact.data.ContactEditorData
import org.linphone.activities.main.contact.data.NumberOrAddressEditorData
import org.linphone.activities.main.contact.viewmodels.*
import org.linphone.activities.navigateToContact
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactEditorFragmentBinding
import org.linphone.utils.FileUtils
import org.linphone.utils.PermissionHelper

class ContactEditorFragment : GenericFragment<ContactEditorFragmentBinding>(), SyncAccountPickerFragment.SyncAccountPickedListener {
    private lateinit var data: ContactEditorData
    private var temporaryPicturePath: File? = null

    override fun getLayoutId(): Int = R.layout.contact_editor_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        val contact = sharedViewModel.selectedContact.value
        val contactRefKey = contact?.refKey
        val friend = if (contactRefKey != null) coreContext.core.getFriendByRefKey(contactRefKey) else null
        data = ContactEditorData(friend ?: contact)
        binding.viewModel = data

        useMaterialSharedAxisXForwardAnimation = sharedViewModel.isSlidingPaneSlideable.value == false

        binding.setAvatarClickListener {
            pickFile()
        }

        binding.setSaveChangesClickListener {
            data.syncAccountName = null
            data.syncAccountType = null

            if (data.friend == null && corePreferences.showNewContactAccountDialog) {
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
            list.addAll(data.addresses.value.orEmpty())
            list.add(newSipUri)
            data.addresses.value = list
        }

        if (!PermissionHelper.required(requireContext()).hasWriteContactsPermission()) {
            Log.i("[Contact Editor] Asking for WRITE_CONTACTS permission")
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_CONTACTS), 0)
        }
    }

    override fun onSyncAccountClicked(name: String?, type: String?) {
        Log.i("[Contact Editor] Using account $name / $type")
        data.syncAccountName = name
        data.syncAccountType = type
        saveContact()
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
                Log.i("[Contact Editor] WRITE_CONTACTS permission granted")
            } else {
                Log.w("[Contact Editor] WRITE_CONTACTS permission denied")
                (activity as MainActivity).showSnackBar(R.string.contact_editor_write_permission_denied)
                goBack()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                val contactImageFilePath = FileUtils.getFilePathFromPickerIntent(intent, temporaryPicturePath)
                if (contactImageFilePath != null) {
                    data.setPictureFromPath(contactImageFilePath)
                }
            }
        }
    }

    private fun saveContact() {
        val savedContact = data.save()
        val id = savedContact.refKey
        if (id != null) {
            Log.i("[Contact Editor] Displaying contact $savedContact")
            navigateToContact(id)
        } else {
            goBack()
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
