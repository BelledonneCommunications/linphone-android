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
package org.linphone.activities.main.sidemenu.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import java.io.File
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.main.navigateToAccountSettings
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.activities.main.sidemenu.viewmodels.SideMenuViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.SideMenuFragmentBinding
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.ImageUtils
import org.linphone.utils.PermissionHelper

class SideMenuFragment : GenericFragment<SideMenuFragmentBinding>() {
    private lateinit var viewModel: SideMenuViewModel
    private lateinit var sharedViewModel: SharedMainViewModel
    private var temporaryPicturePath: File? = null

    override fun getLayoutId(): Int = R.layout.side_menu_fragment

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(SideMenuViewModel::class.java)
        binding.viewModel = viewModel

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        sharedViewModel.accountRemoved.observe(viewLifecycleOwner, {
            Log.i("[Side Menu] Account removed, update accounts list")
            viewModel.updateAccountsList()
        })

        viewModel.accountsSettingsListener = object : SettingListenerStub() {
            override fun onAccountClicked(identity: String) {
                val args = Bundle()
                args.putString("Identity", identity)
                Log.i("[Side Menu] Navigation to settings for account with identity: $identity")

                sharedViewModel.toggleDrawerEvent.value = Event(true)
                navigateToAccountSettings(identity)
            }
        }

        binding.setSelfPictureClickListener {
            pickFile()
        }

        binding.setAssistantClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            startActivity(Intent(context, AssistantActivity::class.java))
        }

        binding.setSettingsClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            findNavController().navigate(R.id.action_global_settingsFragment)
        }

        binding.setRecordingsClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            findNavController().navigate(R.id.action_global_recordingsFragment)
        }

        binding.setAboutClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            findNavController().navigate(R.id.action_global_aboutFragment)
        }

        binding.setQuitClickListener {
            requireActivity().finishAndRemoveTask()
            coreContext.stop()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                val contactImageFilePath = ImageUtils.getImageFilePathFromPickerIntent(data, temporaryPicturePath)
                if (contactImageFilePath != null) {
                    viewModel.setPictureFromPath(contactImageFilePath)
                }
            }
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
