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
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.databinding.MainActivityBinding
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.main.viewmodel.DrawerMenuViewModel

class MainActivity : AppCompatActivity() {
    companion object {
        private const val CONTACTS_PERMISSION_REQUEST = 0
        private const val CAMERA_PERMISSION_REQUEST = 1
        private const val RECORD_AUDIO_PERMISSION_REQUEST = 2
    }

    private lateinit var binding: MainActivityBinding
    private lateinit var drawerMenuViewModel: DrawerMenuViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(
            this,
            R.color.primary_color
        )

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

        drawerMenuViewModel = run {
            ViewModelProvider(this)[DrawerMenuViewModel::class.java]
        }
        binding.drawerMenuViewModel = drawerMenuViewModel

        drawerMenuViewModel.startAssistantEvent.observe(this) {
            it.consume {
                startActivity(Intent(baseContext, AssistantActivity::class.java))
                binding.sideMenu.close()
            }
        }

        drawerMenuViewModel.closeDrawerEvent.observe(this) {
            it.consume {
                binding.sideMenu.close()
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

    fun toggleDrawerMenu() {
        if (binding.sideMenu.isDrawerOpen(Gravity.LEFT)) {
            binding.sideMenu.closeDrawer(binding.sideMenuContent, true)
        } else {
            binding.sideMenu.openDrawer(binding.sideMenuContent, true)
        }
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
}
