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
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import java.io.File
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.contact.viewmodels.*
import org.linphone.activities.main.navigateToContact
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.contact.NativeContact
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactEditorFragmentBinding
import org.linphone.utils.FileUtils
import org.linphone.utils.PermissionHelper

class ContactEditorFragment : Fragment() {
    private lateinit var binding: ContactEditorFragmentBinding
    private lateinit var viewModel: ContactEditorViewModel
    private lateinit var sharedViewModel: SharedMainViewModel
    private var temporaryPicturePath: File? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ContactEditorFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        viewModel = ViewModelProvider(
            this,
            ContactEditorViewModelFactory(sharedViewModel.selectedContact.value)
        )[ContactEditorViewModel::class.java]
        binding.viewModel = viewModel

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setAvatarClickListener {
            pickFile()
        }

        binding.setSaveChangesClickListener {
            val savedContact = viewModel.save()
            if (savedContact is NativeContact) {
                savedContact.syncValuesFromAndroidContact(requireContext())
                Log.i("[Contact Editor] Displaying contact $savedContact")
                navigateToContact(savedContact)
            } else {
                findNavController().popBackStack()
            }
        }

        val sipUri = arguments?.getString("SipUri")
        if (sipUri != null) {
            Log.i("[Contact Editor] Found SIP URI in arguments: $sipUri")
            val newSipUri = NumberOrAddressEditorViewModel("", true)
            newSipUri.newValue.value = sipUri

            val list = arrayListOf<NumberOrAddressEditorViewModel>()
            list.addAll(viewModel.addresses.value.orEmpty())
            list.add(newSipUri)
            viewModel.addresses.value = list
        }

        if (!PermissionHelper.required(requireContext()).hasWriteContactsPermission()) {
            Log.i("[Contact Editor] Asking for WRITE_CONTACTS permission")
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_CONTACTS), 0)
        }
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
                var fileToUploadPath: String? = null

                val temporaryFileUploadPath = temporaryPicturePath
                if (temporaryFileUploadPath != null) {
                    if (data != null) {
                        val dataUri = data.data
                        if (dataUri != null) {
                            fileToUploadPath = dataUri.toString()
                            Log.i("[Chat Room] Using data URI $fileToUploadPath")
                        } else if (temporaryFileUploadPath.exists()) {
                            fileToUploadPath = temporaryFileUploadPath.absolutePath
                            Log.i("[Chat Room] Data URI is null, using $fileToUploadPath")
                        }
                    } else if (temporaryFileUploadPath.exists()) {
                        fileToUploadPath = temporaryFileUploadPath.absolutePath
                        Log.i("[Chat Room] Data is null, using $fileToUploadPath")
                    }
                }

                if (fileToUploadPath != null) {
                    if (fileToUploadPath.startsWith("content://") ||
                        fileToUploadPath.startsWith("file://")
                    ) {
                        val uriToParse = Uri.parse(fileToUploadPath)
                        fileToUploadPath = FileUtils.getFilePath(requireContext(), uriToParse)
                        Log.i("[Chat] Path was using a content or file scheme, real path is: $fileToUploadPath")
                        if (fileToUploadPath == null) {
                            Log.e("[Chat] Failed to get access to file $uriToParse")
                        }
                    }
                }

                if (fileToUploadPath != null) {
                    viewModel.setPictureFromPath(fileToUploadPath)
                }
            }
        }
    }

    private fun pickFile() {
        val cameraIntents = ArrayList<Intent>()

        // Handles image & video picking
        val galleryIntent = Intent(Intent.ACTION_PICK)
        galleryIntent.type = "image/*"

        if (PermissionHelper.get().hasCameraPermission()) {
            // Allows to capture directly from the camera
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val tempFileName = System.currentTimeMillis().toString() + ".jpeg"
            temporaryPicturePath = FileUtils.getFileStoragePath(tempFileName)
            val uri = Uri.fromFile(temporaryPicturePath)
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            cameraIntents.add(captureIntent)
        }

        val chooserIntent =
            Intent.createChooser(galleryIntent, getString(R.string.chat_message_pick_file_dialog))
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(arrayOf<Parcelable>()))

        startActivityForResult(chooserIntent, 0)
    }
}
