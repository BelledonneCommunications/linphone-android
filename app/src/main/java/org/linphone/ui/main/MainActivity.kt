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
package org.linphone.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import androidx.annotation.DrawableRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.databinding.AccountPopupMenuBinding
import org.linphone.databinding.MainActivityBinding
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.main.settings.fragment.AccountProfileFragmentDirections
import org.linphone.ui.main.viewmodel.DrawerMenuViewModel
import org.linphone.utils.slideInToastFromTopForDuration

@UiThread
class MainActivity : AppCompatActivity() {
    companion object {
        private const val CONTACTS_PERMISSION_REQUEST = 0
        private const val CAMERA_PERMISSION_REQUEST = 1
        private const val RECORD_AUDIO_PERMISSION_REQUEST = 2
        private const val POST_NOTIFICATIONS_PERMISSION_REQUEST = 3
        private const val MANAGE_OWN_CALLS_PERMISSION_REQUEST = 4
    }

    private lateinit var binding: MainActivityBinding
    private lateinit var drawerMenuViewModel: DrawerMenuViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)

        while (!coreContext.isReady()) {
            Thread.sleep(20)
        }

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        }
        checkSelfPermission(Manifest.permission.CAMERA)
        checkSelfPermission(Manifest.permission.RECORD_AUDIO)

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this

        binding.setSettingsClickedListener {
            val navController = findNavController(R.id.main_nav_host_fragment)
            navController.navigate(R.id.action_global_settingsFragment)
            binding.drawerMenu.close()
        }

        binding.setRecordingsClickListener {
            val navController = findNavController(R.id.main_nav_host_fragment)
            navController.navigate(R.id.action_global_recordingsFragment)
            binding.drawerMenu.close()
        }

        binding.setHelpClickedListener {
            val navController = findNavController(R.id.main_nav_host_fragment)
            navController.navigate(R.id.action_global_helpFragment)
            binding.drawerMenu.close()
        }

        drawerMenuViewModel = run {
            ViewModelProvider(this)[DrawerMenuViewModel::class.java]
        }
        binding.drawerMenuViewModel = drawerMenuViewModel

        drawerMenuViewModel.startAssistantEvent.observe(this) {
            it.consume {
                startActivity(Intent(baseContext, AssistantActivity::class.java))
                binding.drawerMenu.close()
            }
        }

        drawerMenuViewModel.closeDrawerEvent.observe(this) {
            it.consume {
                binding.drawerMenu.close()
            }
        }

        drawerMenuViewModel.showAccountPopupMenuEvent.observe(this) {
            it.consume { pair ->
                showAccountPopupMenu(pair.first, pair.second)
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_CONTACTS),
                CONTACTS_PERMISSION_REQUEST
            )
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_REQUEST
            )
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                POST_NOTIFICATIONS_PERMISSION_REQUEST
            )
        }
        if (checkSelfPermission(Manifest.permission.MANAGE_OWN_CALLS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                MANAGE_OWN_CALLS_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CONTACTS_PERMISSION_REQUEST && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("RtlHardcoded")
    fun toggleDrawerMenu() {
        if (binding.drawerMenu.isDrawerOpen(Gravity.LEFT)) {
            binding.drawerMenu.closeDrawer(binding.drawerMenuContent, true)
        } else {
            binding.drawerMenu.openDrawer(binding.drawerMenuContent, true)
        }
    }

    fun showGreenToast(message: String, @DrawableRes icon: Int) {
        binding.greenToast.message = message
        binding.greenToast.icon = icon

        val target = binding.greenToast.root
        target.slideInToastFromTopForDuration(binding.root as ViewGroup, lifecycleScope)
    }

    private fun loadContacts() {
        coreContext.contactsManager.loadContacts(this)

        /* TODO: Uncomment later, only fixes a small UI display issue for contacts with emoji in the name
        val emojiCompat = coreContext.emojiCompat
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Wait for emoji compat library to have been loaded
                Log.i("[Main Activity] Waiting for emoji compat library to have been loaded")
                while (emojiCompat.loadState == EmojiCompat.LOAD_STATE_DEFAULT || emojiCompat.loadState == EmojiCompat.LOAD_STATE_LOADING) {
                    delay(100)
                }

                Log.i(
                    "[Main Activity] Emoji compat library loading status is ${emojiCompat.loadState}, re-loading contacts"
                )
                coreContext.postOnMainThread {
                    // Contacts loading must be started from UI thread
                    coreContext.contactsManager.loadContacts(this@MainActivity)
                }
            }
        }*/
    }

    private fun showAccountPopupMenu(view: View, account: Account) {
        val popupView: AccountPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(baseContext),
            R.layout.account_popup_menu,
            null,
            false
        )

        val popupWindow = PopupWindow(
            popupView.root,
            WRAP_CONTENT,
            WRAP_CONTENT,
            true
        )

        popupView.setManageProfileClickListener {
            val navController = findNavController(R.id.main_nav_host_fragment)
            val identity = account.params.identityAddress?.asStringUriOnly().orEmpty()
            val action = AccountProfileFragmentDirections.actionGlobalAccountProfileFragment(
                identity
            )
            navController.navigate(action)
            popupWindow.dismiss()
            binding.drawerMenu.close()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(view, 0, 0, Gravity.BOTTOM)
    }
}
