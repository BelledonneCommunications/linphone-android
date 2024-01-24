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
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.view.View
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.activities.main.sidemenu.viewmodels.SideMenuViewModel
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.databinding.SideMenuFragmentBinding
import org.linphone.utils.*

class SideMenuFragment : GenericFragment<SideMenuFragmentBinding>() {
    private lateinit var viewModel: SideMenuViewModel
    private var temporaryPicturePath: File? = null

    override fun getLayoutId(): Int = R.layout.side_menu_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[SideMenuViewModel::class.java]
        binding.viewModel = viewModel

        sharedViewModel.accountRemoved.observe(
            viewLifecycleOwner
        ) {
            Log.i("[Side Menu] Account removed, update accounts list")
            viewModel.updateAccountsList()
        }

        sharedViewModel.defaultAccountChanged.observe(
            viewLifecycleOwner
        ) {
            Log.i("[Side Menu] Default account changed, update accounts list")
            viewModel.updateAccountsList()
        }

        sharedViewModel.publishPresenceToggled.observe(
            viewLifecycleOwner
        ) {
            viewModel.refreshConsolidatedPresence()
        }

        viewModel.accountsSettingsListener = object : SettingListenerStub() {
            override fun onAccountClicked(identity: String) {
                Log.i("[Side Menu] Navigating to settings for account with identity: $identity")

                sharedViewModel.toggleDrawerEvent.value = Event(true)

                if (corePreferences.askForAccountPasswordToAccessSettings) {
                    showPasswordDialog(goToAccountSettings = true, accountIdentity = identity)
                } else {
                    navigateToAccountSettings(identity)
                }
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

            if (corePreferences.askForAccountPasswordToAccessSettings) {
                showPasswordDialog(goToSettings = true)
            } else {
                navigateToSettings()
            }
        }

        binding.setRecordingsClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            navigateToRecordings()
        }

        binding.setAboutClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            navigateToAbout()
        }

        binding.setConferencesClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            navigateToScheduledConferences()
        }

        binding.setQuitClickListener {
            Log.i("[Side Menu] Quitting app")
            requireActivity().finishAndRemoveTask()

            Log.i("[Side Menu] Stopping Core Context")
            coreContext.notificationsManager.stopForegroundNotification()
            coreContext.stop()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                val contactImageFilePath = FileUtils.getFilePathFromPickerIntent(
                    data,
                    temporaryPicturePath
                )
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
        chooserIntent.putExtra(
            Intent.EXTRA_INITIAL_INTENTS,
            cameraIntents.toArray(arrayOf<Parcelable>())
        )

        startActivityForResult(chooserIntent, 0)
    }

    private fun showPasswordDialog(
        goToSettings: Boolean = false,
        goToAccountSettings: Boolean = false,
        accountIdentity: String = ""
    ) {
        val dialogViewModel = DialogViewModel(
            getString(R.string.settings_password_protection_dialog_title)
        )
        dialogViewModel.showIcon = true
        dialogViewModel.iconResource = R.drawable.security_toggle_icon_green
        dialogViewModel.showPassword = true
        dialogViewModel.passwordTitle = getString(
            R.string.settings_password_protection_dialog_input_hint
        )
        val dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

        dialogViewModel.showCancelButton {
            dialog.dismiss()
        }

        dialogViewModel.showOkButton(
            {
                val defaultAccount = coreContext.core.defaultAccount ?: coreContext.core.accountList.firstOrNull()
                if (defaultAccount == null) {
                    Log.e("[Side Menu] No account found, can't check password input!")
                    (requireActivity() as MainActivity).showSnackBar(R.string.error_unexpected)
                } else {
                    val authInfo = defaultAccount.findAuthInfo()
                    if (authInfo == null) {
                        Log.e(
                            "[Side Menu] No auth info found for account [${defaultAccount.params.identityAddress?.asString()}], can't check password input!"
                        )
                        (requireActivity() as MainActivity).showSnackBar(R.string.error_unexpected)
                    } else {
                        val expectedHash = authInfo.ha1
                        if (expectedHash == null) {
                            Log.e(
                                "[Side Menu] No ha1 found in auth info, can't check password input!"
                            )
                            (requireActivity() as MainActivity).showSnackBar(
                                R.string.error_unexpected
                            )
                        } else {
                            val hashAlgorithm = authInfo.algorithm ?: "MD5"
                            val userId = (authInfo.userid ?: authInfo.username).orEmpty()
                            val realm = authInfo.realm.orEmpty()
                            val password = dialogViewModel.password
                            val computedHash = Factory.instance().computeHa1ForAlgorithm(
                                userId,
                                password,
                                realm,
                                hashAlgorithm
                            )
                            if (computedHash != expectedHash) {
                                Log.e(
                                    "[Side Menu] Computed hash [$computedHash] using userId [$userId], realm [$realm] and algorithm [$hashAlgorithm] doesn't match expected hash!"
                                )
                                (requireActivity() as MainActivity).showSnackBar(
                                    R.string.settings_password_protection_dialog_invalid_input
                                )
                            } else {
                                if (goToSettings) {
                                    navigateToSettings()
                                } else if (goToAccountSettings) {
                                    navigateToAccountSettings(accountIdentity)
                                }
                            }
                        }
                    }
                }

                dialog.dismiss()
            },
            getString(R.string.settings_password_protection_dialog_ok_label)
        )

        dialog.show()
    }
}
