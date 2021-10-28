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
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.window.layout.FoldingFeature
import com.google.android.material.snackbar.Snackbar
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import kotlin.math.abs
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.SnackBarActivity
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.main.viewmodels.CallOverlayViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.navigateToDialer
import org.linphone.compatibility.Compatibility
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.CorePreferences
import org.linphone.core.tools.Log
import org.linphone.databinding.MainActivityBinding
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.GlideApp

class MainActivity : GenericActivity(), SnackBarActivity, NavController.OnDestinationChangedListener {
    private lateinit var binding: MainActivityBinding
    private lateinit var sharedViewModel: SharedMainViewModel
    private lateinit var callOverlayViewModel: CallOverlayViewModel

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
    private lateinit var statusFragment: FragmentContainerView

    private var overlayX = 0f
    private var overlayY = 0f
    private var initPosX = 0f
    private var initPosY = 0f
    private var overlay: View? = null

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) { }

        override fun onLowMemory() {
            Log.w("[Main Activity] onLowMemory !")
        }

        override fun onTrimMemory(level: Int) {
            Log.w("[Main Activity] onTrimMemory called with level $level !")
            GlideApp.get(this@MainActivity).clearMemory()
        }
    }

    override fun onLayoutChanges(foldingFeature: FoldingFeature?) {
        sharedViewModel.layoutChangedEvent.value = Event(true)
    }

    private var tabsFragmentVisible1 = true
    private var tabsFragmentVisible2 = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this

        sharedViewModel = ViewModelProvider(this)[SharedMainViewModel::class.java]
        binding.viewModel = sharedViewModel

        callOverlayViewModel = ViewModelProvider(this)[CallOverlayViewModel::class.java]
        binding.callOverlayViewModel = callOverlayViewModel

        sharedViewModel.toggleDrawerEvent.observe(
            this,
            {
                it.consume {
                    if (binding.sideMenu.isDrawerOpen(Gravity.LEFT)) {
                        binding.sideMenu.closeDrawer(binding.sideMenuContent, true)
                    } else {
                        binding.sideMenu.openDrawer(binding.sideMenuContent, true)
                    }
                }
            }
        )

        coreContext.callErrorMessageResourceId.observe(
            this,
            {
                it.consume { message ->
                    showSnackBar(message)
                }
            }
        )

        if (coreContext.core.accountList.isEmpty()) {
            if (corePreferences.firstStart) {
                corePreferences.firstStart = false
                startActivity(Intent(this, AssistantActivity::class.java))
            }
        }

        tabsFragment = findViewById(R.id.tabs_fragment)
        statusFragment = findViewById(R.id.status_fragment)

        initOverlay()
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

    override fun showSnackBar(message: String) {
        Snackbar.make(findViewById(R.id.coordinator), message, Snackbar.LENGTH_LONG).show()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        registerComponentCallbacks(componentCallbacks)
        findNavController(R.id.nav_host_fragment).addOnDestinationChangedListener(this)

        binding.rootCoordinatorLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val keyboardVisible = ViewCompat.getRootWindowInsets(binding.rootCoordinatorLayout)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            Log.d("[Tabs Fragment] Keyboard is ${if (keyboardVisible) "visible" else "invisible"}")
            tabsFragmentVisible2 = !portraitOrientation || !keyboardVisible
            updateTabsFragmentVisibility()
        }

        if (intent != null) handleIntentParams(intent)
    }

    override fun onDestroy() {
        findNavController(R.id.nav_host_fragment).removeOnDestinationChangedListener(this)
        unregisterComponentCallbacks(componentCallbacks)
        super.onDestroy()
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        currentFocus?.hideKeyboard()
        if (statusFragment.visibility == View.GONE) {
            statusFragment.visibility = View.VISIBLE
        }

        tabsFragmentVisible1 = when (destination.id) {
            R.id.masterCallLogsFragment, R.id.masterContactsFragment, R.id.dialerFragment, R.id.masterChatRoomsFragment ->
                true
            else -> false
        }
        updateTabsFragmentVisibility()
    }

    private fun updateTabsFragmentVisibility() {
        tabsFragment.visibility = if (tabsFragmentVisible1 && tabsFragmentVisible2) View.VISIBLE else View.GONE
    }

    private fun View.hideKeyboard() {
        val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun handleIntentParams(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SENDTO -> {
                if (intent.type == "text/plain") {
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
            Intent.ACTION_VIEW_LOCUS -> {
                if (corePreferences.disableChat) return
                val locus = Compatibility.extractLocusIdFromIntent(intent)
                if (locus != null) {
                    Log.i("[Main Activity] Found chat room locus intent extra: $locus")
                    handleLocusOrShortcut(locus)
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

                        if (intent.hasExtra("RemoteSipUri") && intent.hasExtra("LocalSipUri")) {
                            val peerAddress = intent.getStringExtra("RemoteSipUri")
                            val localAddress = intent.getStringExtra("LocalSipUri")
                            Log.i("[Main Activity] Found chat room intent extra: local SIP URI=[$localAddress], peer SIP URI=[$peerAddress]")
                            findNavController(R.id.nav_host_fragment).navigate(Uri.parse("linphone-android://chat-room/$localAddress/$peerAddress"))
                        } else {
                            Log.i("[Main Activity] Found chat intent extra, go to chat rooms list")
                            findNavController(R.id.nav_host_fragment).navigate(R.id.action_global_masterChatRoomsFragment)
                        }
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

        when {
            addressToCall.startsWith("tel:") -> {
                Log.i("[Main Activity] Removing tel: prefix")
                addressToCall = addressToCall.substring("tel:".length)
            }
            addressToCall.startsWith("linphone:") -> {
                Log.i("[Main Activity] Removing linphone: prefix")
                addressToCall = addressToCall.substring("linphone:".length)
            }
            addressToCall.startsWith("sip-linphone:") -> {
                Log.i("[Main Activity] Removing linphone: sip-linphone")
                addressToCall = addressToCall.substring("sip-linphone:".length)
            }
        }

        val address = coreContext.core.interpretUrl(addressToCall)
        if (address != null) {
            addressToCall = address.asStringUriOnly()
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

        Log.i("[Main Activity] Found single file to share with type ${intent.type}")

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

        // Check that the current fragment hasn't already handled the event on filesToShare
        // If it has, don't go further.
        // For example this may happen when picking a GIF from the keyboard while inside a chat room
        if (!sharedViewModel.filesToShare.value.isNullOrEmpty()) {
            handleSendChatRoom(intent)
        }
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
                Log.e("[Main Activity] UnsupportedEncodingException: $e")
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
                coreContext.core.defaultAccount?.params?.identityAddress?.asStringUriOnly()
            val deepLink = "linphone-android://chat-room/$localAddress/$peerAddress"
            Log.i("[Main Activity] Starting deep link: $deepLink")
            findNavController(R.id.nav_host_fragment).navigate(Uri.parse(deepLink))
        } else {
            val shortcutId = intent.getStringExtra("android.intent.extra.shortcut.ID") // Intent.EXTRA_SHORTCUT_ID
            if (shortcutId != null) {
                Log.i("[Main Activity] Found shortcut ID: $shortcutId")
                handleLocusOrShortcut(shortcutId)
            } else {
                Log.i("[Main Activity] Going into chat rooms list")
                findNavController(R.id.nav_host_fragment).navigate(R.id.action_global_masterChatRoomsFragment)
            }
        }
    }

    private fun handleLocusOrShortcut(id: String) {
        val split = id.split("~")
        if (split.size == 2) {
            val localAddress = split[0]
            val peerAddress = split[1]
            val deepLink = "linphone-android://chat-room/$localAddress/$peerAddress"
            findNavController(R.id.nav_host_fragment).navigate(Uri.parse(deepLink))
        } else {
            Log.e("[Main Activity] Failed to parse shortcut/locus id: $id")
            findNavController(R.id.nav_host_fragment).navigate(R.id.action_global_masterChatRoomsFragment)
        }
    }

    private fun initOverlay() {
        overlay = binding.root.findViewById(R.id.call_overlay)
        val callOverlay = overlay
        callOverlay ?: return

        callOverlay.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    overlayX = view.x - event.rawX
                    overlayY = view.y - event.rawY
                    initPosX = view.x
                    initPosY = view.y
                }
                MotionEvent.ACTION_MOVE -> {
                    view.animate()
                        .x(event.rawX + overlayX)
                        .y(event.rawY + overlayY)
                        .setDuration(0)
                        .start()
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(initPosX - view.x) < CorePreferences.OVERLAY_CLICK_SENSITIVITY &&
                        abs(initPosY - view.y) < CorePreferences.OVERLAY_CLICK_SENSITIVITY
                    ) {
                        view.performClick()
                    }
                }
                else -> return@setOnTouchListener false
            }
            true
        }

        callOverlay.setOnClickListener {
            coreContext.onCallOverlayClick()
        }
    }
}
