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
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.doOnAttach
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.MainActivityBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.main.chat.fragment.ConversationsListFragmentDirections
import org.linphone.ui.main.fragment.AuthRequestedDialogModel
import org.linphone.ui.main.viewmodel.MainViewModel
import org.linphone.ui.main.viewmodel.SharedMainViewModel
import org.linphone.ui.welcome.WelcomeActivity
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils

@UiThread
class MainActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Main Activity]"

        private const val DEFAULT_FRAGMENT_KEY = "default_fragment"
        private const val CONTACTS_FRAGMENT_ID = 1
        private const val HISTORY_FRAGMENT_ID = 2
        private const val CHAT_FRAGMENT_ID = 3
        private const val MEETINGS_FRAGMENT_ID = 4
    }

    private lateinit var binding: MainActivityBinding

    private lateinit var viewModel: MainViewModel

    private lateinit var sharedViewModel: SharedMainViewModel

    private var currentlyDisplayedAuthDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be done before the setContentView
        installSplashScreen()

        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this

        setUpToastsArea(binding.toastsArea)

        while (!coreContext.isReady()) {
            Thread.sleep(50)
        }

        viewModel = run {
            ViewModelProvider(this)[MainViewModel::class.java]
        }
        binding.viewModel = viewModel

        sharedViewModel = run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        viewModel.changeSystemTopBarColorEvent.observe(this) {
            it.consume { mode ->
                window.statusBarColor = when (mode) {
                    MainViewModel.SINGLE_CALL, MainViewModel.MULTIPLE_CALLS -> {
                        getColor(R.color.success_500)
                    }
                    MainViewModel.NETWORK_NOT_REACHABLE, MainViewModel.NON_DEFAULT_ACCOUNT_NOT_CONNECTED -> {
                        getColor(R.color.danger_500)
                    }
                    MainViewModel.NON_DEFAULT_ACCOUNT_NOTIFICATIONS -> {
                        getColor(R.color.main2_500)
                    }
                    else -> getColor(R.color.main1_500)
                }
            }
        }

        viewModel.goBackToCallEvent.observe(this) {
            it.consume {
                coreContext.showCallActivity()
            }
        }

        viewModel.openDrawerEvent.observe(this) {
            it.consume {
                openDrawerMenu()
            }
        }

        viewModel.defaultAccountRegistrationErrorEvent.observe(this) {
            it.consume { error ->
                val tag = "DEFAULT_ACCOUNT_REGISTRATION_ERROR"
                if (error) {
                    // First remove any already existing connection error toast
                    removePersistentRedToast(tag)

                    val message = getString(R.string.toast_default_account_connection_state_error)
                    showPersistentRedToast(message, R.drawable.warning_circle, tag)
                } else {
                    removePersistentRedToast(tag)
                }
            }
        }

        viewModel.showNewAccountToastEvent.observe(this) {
            it.consume {
                val message = getString(R.string.toast_new_account_configured)
                showGreenToast(message, R.drawable.user_circle)
            }
        }

        viewModel.startLoadingContactsEvent.observe(this) {
            it.consume {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    loadContacts()
                }
            }
        }

        viewModel.authenticationRequestedEvent.observe(this) {
            it.consume { identity ->
                showAuthenticationRequestedDialog(identity)
            }
        }

        binding.root.doOnAttach {
            Log.i("$TAG Report UI has been fully drawn (TTFD)")
            try {
                reportFullyDrawn()
            } catch (se: SecurityException) {
                Log.e("$TAG Security exception when doing reportFullyDrawn(): $se")
            }
        }

        coreContext.greenToastToShowEvent.observe(this) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                showGreenToast(message, icon)
            }
        }

        coreContext.redToastToShowEvent.observe(this) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                showRedToast(message, icon)
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        coreContext.postOnCoreThread { core ->
            if (corePreferences.firstLaunch) {
                Log.i("$TAG First time Linphone 6.0 has been started, showing Welcome activity")
                corePreferences.firstLaunch = false
                coreContext.postOnMainThread {
                    startActivity(Intent(this, WelcomeActivity::class.java))
                }
            } else if (core.accountList.isEmpty()) {
                Log.w("$TAG No account found, showing Assistant activity")
                coreContext.postOnMainThread {
                    startActivity(Intent(this, AssistantActivity::class.java))
                }
            }
        }

        if (intent != null) {
            handleIntent(intent, false)
        } else {
            // This should never happen!
            Log.e("$TAG onPostCreate called without intent !")
        }
    }

    override fun onPause() {
        currentlyDisplayedAuthDialog?.dismiss()
        currentlyDisplayedAuthDialog = null

        val defaultFragmentId = when (sharedViewModel.currentlyDisplayedFragment.value) {
            R.id.contactsListFragment -> {
                CONTACTS_FRAGMENT_ID
            }
            R.id.historyListFragment -> {
                HISTORY_FRAGMENT_ID
            }
            R.id.conversationsListFragment -> {
                CHAT_FRAGMENT_ID
            }
            R.id.meetingsListFragment -> {
                MEETINGS_FRAGMENT_ID
            }
            else -> { // Default
                HISTORY_FRAGMENT_ID
            }
        }
        with(getPreferences(Context.MODE_PRIVATE).edit()) {
            putInt(DEFAULT_FRAGMENT_KEY, defaultFragmentId)
            apply()
        }
        Log.i("$TAG Stored [$defaultFragmentId] as default page")

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        viewModel.checkForNewAccount()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, true)
    }

    @SuppressLint("RtlHardcoded")
    fun toggleDrawerMenu() {
        if (binding.drawerMenu.isDrawerOpen(Gravity.LEFT)) {
            closeDrawerMenu()
        } else {
            openDrawerMenu()
        }
    }

    fun closeDrawerMenu() {
        binding.drawerMenu.closeDrawer(binding.drawerMenuContent, true)
    }

    private fun openDrawerMenu() {
        binding.drawerMenu.openDrawer(binding.drawerMenuContent, true)
    }

    fun findNavController(): NavController {
        return findNavController(R.id.main_nav_host_fragment)
    }

    @MainThread
    private fun handleIntent(intent: Intent, isNewIntent: Boolean) {
        Log.i(
            "$TAG Handling intent action [${intent.action}], type [${intent.type}] and data [${intent.data}]"
        )

        when (intent.action) {
            Intent.ACTION_SEND -> {
                handleSendIntent(intent, false)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleSendIntent(intent, true)
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data?.toString() ?: ""
                if (uri.startsWith("linphone-config:")) {
                    handleConfigIntent(uri)
                } else {
                    handleCallIntent(intent)
                }
            }
            Intent.ACTION_DIAL, Intent.ACTION_CALL -> {
                handleCallIntent(intent)
            }
            Intent.ACTION_VIEW_LOCUS -> {
                val locus = Compatibility.extractLocusIdFromIntent(intent)
                if (locus != null) {
                    Log.i("$TAG Found chat room locus intent extra: $locus")
                    handleLocusOrShortcut(locus)
                }
            }
            else -> {
                handleMainIntent(intent, isNewIntent)
            }
        }
    }

    @MainThread
    private fun handleLocusOrShortcut(id: String) {
        Log.i("$TAG Found locus ID [$id]")
        val pair = LinphoneUtils.getLocalAndPeerSipUrisFromChatRoomId(id)
        if (pair != null) {
            val localSipUri = pair.first
            val remoteSipUri = pair.second
            Log.i(
                "$TAG Navigating to conversation with local [$localSipUri] and peer [$remoteSipUri] addresses, computed from shortcut ID"
            )
            sharedViewModel.showConversationEvent.value = Event(pair)
        }
    }

    @MainThread
    private fun handleMainIntent(intent: Intent, isNewIntent: Boolean) {
        // Prevent navigating to default fragment upon rotation (we only want to do it on first start)
        if (intent.action == Intent.ACTION_MAIN && intent.type == null && intent.data == null && !isNewIntent) {
            if (viewModel.mainIntentHandled) {
                Log.d("$TAG Main intent without type nor data was already handled, do nothing")
                return
            } else {
                viewModel.mainIntentHandled = true
            }
        }

        if (intent.hasExtra("Chat")) {
            Log.i("$TAG Intent has [Chat] extra")
            try {
                Log.i("$TAG Trying to go to Conversations fragment")
                val args = intent.extras
                val localSipUri = args?.getString("LocalSipUri", "")
                val remoteSipUri = args?.getString("RemoteSipUri", "")
                if (remoteSipUri.isNullOrEmpty() || localSipUri.isNullOrEmpty()) {
                    Log.w("$TAG Found [Chat] extra but no local and/or remote SIP URI!")
                } else {
                    Log.i(
                        "$TAG Found [Chat] extra with local [$localSipUri] and peer [$remoteSipUri] addresses"
                    )
                    val pair = Pair(localSipUri, remoteSipUri)
                    sharedViewModel.showConversationEvent.value = Event(pair)
                }
                args?.clear()

                if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                    Log.w(
                        "$TAG Current destination is already conversations list, skipping navigation"
                    )
                } else {
                    val action =
                        ConversationsListFragmentDirections.actionGlobalConversationsListFragment()
                    findNavController().navigate(action)
                }
            } catch (ise: IllegalStateException) {
                Log.e("$TAG Can't navigate to Conversations fragment: $ise")
            }
        } else if (!isNewIntent) {
            try {
                val defaultFragmentId = getPreferences(Context.MODE_PRIVATE).getInt(
                    DEFAULT_FRAGMENT_KEY,
                    CONTACTS_FRAGMENT_ID
                )
                Log.i("$TAG Trying to navigate to set default destination [$defaultFragmentId]")
                val args = intent.extras
                try {
                    val navOptionsBuilder = NavOptions.Builder()
                    navOptionsBuilder.setPopUpTo(R.id.contactsListFragment, true)
                    navOptionsBuilder.setLaunchSingleTop(true)
                    val navOptions = navOptionsBuilder.build()
                    when (defaultFragmentId) {
                        HISTORY_FRAGMENT_ID -> {
                            findNavController().navigate(
                                R.id.historyListFragment,
                                args,
                                navOptions
                            )
                        }
                        CHAT_FRAGMENT_ID -> {
                            findNavController().navigate(
                                R.id.conversationsListFragment,
                                args,
                                navOptions
                            )
                        }
                        MEETINGS_FRAGMENT_ID -> {
                            findNavController().navigate(
                                R.id.meetingsListFragment,
                                args,
                                navOptions
                            )
                        }
                    }
                } catch (ise: IllegalStateException) {
                    Log.e("$TAG Can't navigate to Conversations fragment: $ise")
                }
            } catch (ise: IllegalStateException) {
                Log.i("$TAG Failed to handle intent: $ise")
            }
        }
    }

    @MainThread
    private fun handleSendIntent(intent: Intent, multiple: Boolean) {
        val parcelablesUri = arrayListOf<Uri>()

        if (intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { extraText ->
                sharedViewModel.textToShareFromIntent.value = extraText
            }
        } else {
            if (multiple) {
                val parcelables =
                    intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                for (parcelable in parcelables.orEmpty()) {
                    val uri = parcelable as? Uri
                    if (uri != null) {
                        Log.i("$TAG Found URI [$uri] in parcelable extra list")
                        parcelablesUri.add(uri)
                    }
                }
            } else {
                val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
                if (uri != null) {
                    Log.i("$TAG Found URI [$uri] in parcelable extra")
                    parcelablesUri.add(uri)
                }
            }
        }

        val list = arrayListOf<String>()
        lifecycleScope.launch {
            val deferred = arrayListOf<Deferred<String?>>()
            for (uri in parcelablesUri) {
                deferred.add(async { FileUtils.getFilePath(this@MainActivity, uri, false) })
            }

            if (binding.drawerMenu.isOpen) {
                Log.i("$TAG Drawer menu is opened, closing it")
                closeDrawerMenu()
            }

            val paths = deferred.awaitAll()
            for (path in paths) {
                Log.i("$TAG Found file to share [$path] in intent")
                if (path != null) list.add(path)
            }

            if (list.isNotEmpty()) {
                sharedViewModel.filesToShareFromIntent.value = list
            } else {
                if (sharedViewModel.textToShareFromIntent.value.orEmpty().isNotEmpty()) {
                    Log.i("$TAG Found plain text to share")
                } else {
                    Log.w("$TAG Failed to find at least one file or text to share!")
                }
            }

            if (findNavController().currentDestination?.id == R.id.debugFragment) {
                Log.i(
                    "$TAG App is already started and in debug fragment, navigating to conversations list"
                )
                val pair = parseShortcutIfAny(intent)
                if (pair != null) {
                    Log.i(
                        "$TAG Navigating from debug to conversation with local [${pair.first}] and peer [${pair.second}] addresses, computed from shortcut ID"
                    )
                    sharedViewModel.showConversationEvent.value = Event(pair)
                }
            } else {
                val pair = parseShortcutIfAny(intent)
                if (pair != null) {
                    val localSipUri = pair.first
                    val remoteSipUri = pair.second
                    Log.i(
                        "$TAG Navigating to conversation with local [$localSipUri] and peer [$remoteSipUri] addresses, computed from shortcut ID"
                    )
                    sharedViewModel.showConversationEvent.value = Event(pair)
                }
            }

            if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                Log.w("$TAG Current destination is already conversations list, skipping navigation")
            } else {
                val action =
                    ConversationsListFragmentDirections.actionGlobalConversationsListFragment()
                findNavController().navigate(action)
            }
        }
    }

    @MainThread
    private fun parseShortcutIfAny(intent: Intent): Pair<String, String>? {
        val shortcutId = intent.getStringExtra("android.intent.extra.shortcut.ID") // Intent.EXTRA_SHORTCUT_ID
        if (shortcutId != null) {
            Log.i("$TAG Found shortcut ID [$shortcutId]")
            return LinphoneUtils.getLocalAndPeerSipUrisFromChatRoomId(shortcutId)
        } else {
            Log.i("$TAG No shortcut ID as found")
        }
        return null
    }

    @MainThread
    private fun handleCallIntent(intent: Intent) {
        val uri = intent.data?.toString()
        if (uri.isNullOrEmpty()) {
            Log.e("$TAG Intent data is null or empty, can't process [${intent.action}] intent")
            return
        }

        Log.i("$TAG Found URI [$uri] as data for intent [${intent.action}]")
        val sipUriToCall = if (uri.startsWith("tel:")) {
            uri.substring("tel:".length)
        } else if (uri.startsWith("sip-linphone:")) {
            uri.replace("sip-linphone:", "sip:")
        } else {
            if (uri.startsWith("sips-linphone:")) {
                uri.replace("sips-linphone:", "sips:")
            } else {
                uri
            }.replace("%40", "@") // Unescape @ character if needed
        }

        coreContext.postOnCoreThread {
            val address = coreContext.core.interpretUrl(
                sipUriToCall,
                LinphoneUtils.applyInternationalPrefix()
            )
            Log.i("$TAG Interpreted SIP URI is [${address?.asStringUriOnly()}]")
            if (address != null) {
                coreContext.startAudioCall(address)
            }
        }
    }

    @MainThread
    private fun handleConfigIntent(uri: String) {
        val remoteConfigUri = uri.substring("linphone-config:".length)
        coreContext.postOnCoreThread { core ->
            coreContext.core.provisioningUri = remoteConfigUri
            Log.w("$TAG Remote provisioning URL set to [$remoteConfigUri], restarting Core now")
            coreContext.core.stop()
            coreContext.core.start()
            Log.i("$TAG Core restarted after remote provisioning URL was applied")
        }
    }

    private fun loadContacts() {
        coreContext.contactsManager.loadContacts(this)
    }

    private fun showAuthenticationRequestedDialog(identity: String) {
        currentlyDisplayedAuthDialog?.dismiss()

        val model = AuthRequestedDialogModel(identity)
        val dialog = DialogUtils.getAuthRequestedDialog(this, model)

        model.dismissEvent.observe(this) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(this) {
            it.consume { password ->
                viewModel.updateAuthInfo(password)
                dialog.dismiss()
            }
        }

        dialog.show()
        currentlyDisplayedAuthDialog = dialog
    }
}
