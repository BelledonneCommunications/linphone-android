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
package org.linphone.activities.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import com.google.android.material.snackbar.Snackbar
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.SnackBarActivity
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.call.CallActivity
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.compatibility.Compatibility
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.tools.Log
import org.linphone.databinding.MainActivityBinding
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils

class MainActivity : GenericActivity(), SnackBarActivity, NavController.OnDestinationChangedListener {
    private lateinit var binding: MainActivityBinding
    private lateinit var sharedViewModel: SharedMainViewModel

    private val listener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Main Activity] Contact(s) updated, update shortcuts")
            if (corePreferences.contactsShortcuts) {
                Compatibility.createShortcutsToContacts(this@MainActivity)
            } else if (corePreferences.chatRoomShortcuts) {
                Compatibility.createShortcutsToChatRooms(this@MainActivity)
            }
        }
    }

    private lateinit var tabsFragment: FragmentContainerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this

        sharedViewModel = ViewModelProvider(this).get(SharedMainViewModel::class.java)
        binding.viewModel = sharedViewModel

        sharedViewModel.toggleDrawerEvent.observe(this, {
            it.consume {
                if (binding.sideMenu.isDrawerOpen(Gravity.LEFT)) {
                    binding.sideMenu.closeDrawer(binding.sideMenuContent, true)
                } else {
                    binding.sideMenu.openDrawer(binding.sideMenuContent, true)
                }
            }
        })

        coreContext.callErrorMessageResourceId.observe(this, {
            it.consume { messageResourceId ->
                showSnackBar(messageResourceId)
            }
        })

        binding.setGoBackToCallClickListener {
            val intent = Intent(this, CallActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        if (coreContext.core.proxyConfigList.isEmpty()) {
            if (corePreferences.firstStart) {
                corePreferences.firstStart = false
                startActivity(Intent(this, AssistantActivity::class.java))
            }
        }

        tabsFragment = findViewById(R.id.tabs_fragment)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent != null) handleIntentParams(intent)
    }

    override fun onResume() {
        super.onResume()
        coreContext.contactsManager.addListener(listener)
    }

    override fun onPause() {
        coreContext.contactsManager.removeListener(listener)
        super.onPause()
    }

    override fun showSnackBar(resourceId: Int) {
        Snackbar.make(findViewById(R.id.coordinator), resourceId, Snackbar.LENGTH_LONG).show()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        findNavController(R.id.nav_host_fragment).addOnDestinationChangedListener(this)

        if (intent != null) handleIntentParams(intent)
    }

    override fun onDestroy() {
        findNavController(R.id.nav_host_fragment).removeOnDestinationChangedListener(this)
        super.onDestroy()
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        currentFocus?.hideKeyboard()

        val motionLayout: MotionLayout = binding.content as MotionLayout
        if (corePreferences.enableAnimations) {
            when (destination.id) {
                R.id.masterCallLogsFragment, R.id.masterContactsFragment, R.id.dialerFragment, R.id.masterChatRoomsFragment ->
                    motionLayout.transitionToState(R.id.visible)
                else -> motionLayout.transitionToState(R.id.gone)
            }
        } else {
            when (destination.id) {
                R.id.masterCallLogsFragment, R.id.masterContactsFragment, R.id.dialerFragment, R.id.masterChatRoomsFragment ->
                    motionLayout.setTransition(R.id.visible, R.id.visible)
                else -> motionLayout.setTransition(R.id.gone, R.id.gone)
            }
        }
    }

    private fun View.hideKeyboard() {
        val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun handleIntentParams(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SENDTO -> {
                if (intent.type?.startsWith("text/") == true) {
                    handleSendText(intent)
                } else {
                    lifecycleScope.launch {
                        handleSendFile(intent)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                lifecycleScope.launch {
                    handleSendMultipleFiles(intent)
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (intent.type == AppUtils.getString(R.string.linphone_address_mime_type)) {
                    if (uri != null) {
                        val contactId = coreContext.contactsManager.getAndroidContactIdFromUri(uri)
                        if (contactId != null) {
                            val deepLink = "linphone-android://contact/view/$contactId"
                            Log.i("[Main Activity] Found contact URI parameter in intent: $uri, starting deep link: $deepLink")
                            findNavController(R.id.nav_host_fragment).navigate(Uri.parse(deepLink))
                        }
                    }
                } else {
                    if (uri != null) {
                        handleTelOrSipUri(uri)
                    }
                }
            }
            Intent.ACTION_DIAL, Intent.ACTION_CALL -> {
                val uri = intent.data
                if (uri != null) {
                    handleTelOrSipUri(uri)
                }
            }
            else -> {
                when {
                    intent.hasExtra("ContactId") -> {
                        val id = intent.getStringExtra("ContactId")
                        val deepLink = "linphone-android://contact/view/$id"
                        Log.i("[Main Activity] Found contact id parameter in intent: $id, starting deep link: $deepLink")
                        findNavController(R.id.nav_host_fragment).navigate(Uri.parse(deepLink))
                    }
                    intent.hasExtra("Chat") -> {
                        if (corePreferences.disableChat) return

                        val deepLink = if (intent.hasExtra("RemoteSipUri") && intent.hasExtra("LocalSipUri")) {
                            val peerAddress = intent.getStringExtra("RemoteSipUri")
                            val localAddress = intent.getStringExtra("LocalSipUri")
                            Log.i("[Main Activity] Found chat room intent extra: local SIP URI=[$localAddress], peer SIP URI=[$peerAddress]")
                            "linphone-android://chat-room/$localAddress/$peerAddress"
                        } else {
                            Log.i("[Main Activity] Found chat intent extra, go to chat rooms list")
                            "linphone-android://chat/"
                        }
                        findNavController(R.id.nav_host_fragment).navigate(Uri.parse(deepLink))
                    }
                    intent.hasExtra("Dialer") -> {
                        Log.i("[Main Activity] Found dialer intent extra, go to dialer")
                        val args = Bundle()
                        args.putBoolean("Transfer", intent.getBooleanExtra("Transfer", false))
                        navigateToDialer(args)
                    }
                }
            }
        }
    }

    private fun handleTelOrSipUri(uri: Uri) {
        Log.i("[Main Activity] Found uri: $uri to call")
        val stringUri = uri.toString()
        var addressToCall: String = stringUri
        try {
            addressToCall = URLDecoder.decode(stringUri, "UTF-8")
        } catch (e: UnsupportedEncodingException) { }

        if (addressToCall.startsWith("tel:")) {
            Log.i("[Main Activity] Removing tel: prefix")
            addressToCall = addressToCall.substring("tel:".length)
        }

        Log.i("[Main Activity] Starting dialer with pre-filled URI $addressToCall")
        val args = Bundle()
        args.putString("URI", addressToCall)
        navigateToDialer(args)
    }

    private fun handleSendText(intent: Intent) {
        if (corePreferences.disableChat) return

        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            sharedViewModel.textToShare.value = it
        }

        handleSendChatRoom(intent)
    }

    private suspend fun handleSendFile(intent: Intent) {
        if (corePreferences.disableChat) return

        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
            val list = arrayListOf<String>()
            coroutineScope {
                val deferred = async {
                    FileUtils.getFilePath(this@MainActivity, it)
                }
                val path = deferred.await()
                if (path != null) {
                    list.add(path)
                    Log.i("[Main Activity] Found single file to share: $path")
                }
            }
            sharedViewModel.filesToShare.value = list
        }

        handleSendChatRoom(intent)
    }

    private suspend fun handleSendMultipleFiles(intent: Intent) {
        if (corePreferences.disableChat) return

        intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.let {
            val list = arrayListOf<String>()
            coroutineScope {
                val deferred = arrayListOf<Deferred<String?>>()
                for (parcelable in it) {
                    val uri = parcelable as Uri
                    deferred.add(async { FileUtils.getFilePath(this@MainActivity, uri) })
                }
                val paths = deferred.awaitAll()
                for (path in paths) {
                    Log.i("[Main Activity] Found file to share: $path")
                    if (path != null) list.add(path)
                }
            }
            sharedViewModel.filesToShare.value = list
        }

        handleSendChatRoom(intent)
    }

    private fun handleSendChatRoom(intent: Intent) {
        if (corePreferences.disableChat) return

        val uri = intent.data
        if (uri != null) {
            Log.i("[Main Activity] Found uri: $uri to send a message to")
            val stringUri = uri.toString()
            var addressToIM: String = stringUri
            try {
                addressToIM = URLDecoder.decode(stringUri, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
            }

            when {
                addressToIM.startsWith("sms:") ->
                    addressToIM = addressToIM.substring("sms:".length)
                addressToIM.startsWith("smsto:") ->
                    addressToIM = addressToIM.substring("smsto:".length)
                addressToIM.startsWith("mms:") ->
                    addressToIM = addressToIM.substring("mms:".length)
                addressToIM.startsWith("mmsto:") ->
                    addressToIM = addressToIM.substring("mmsto:".length)
            }

            val peerAddress = coreContext.core.interpretUrl(addressToIM)?.asStringUriOnly()
            val localAddress =
                coreContext.core.defaultProxyConfig?.contact?.asStringUriOnly()
            val deepLink = "linphone-android://chat-room/$localAddress/$peerAddress"
            Log.i("[Main Activity] Starting deep link: $deepLink")
            findNavController(R.id.nav_host_fragment).navigate(Uri.parse(deepLink))
        } else {
            val deepLink = "linphone-android://chat/"
            Log.i("[Main Activity] Starting deep link: $deepLink")
            findNavController(R.id.nav_host_fragment).navigate(Uri.parse(deepLink))
        }
    }
}
