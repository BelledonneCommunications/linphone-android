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
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.car.app.connection.CarConnection
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import kotlin.math.max
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.MainActivityBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.main.chat.fragment.ConversationsListFragmentDirections
import org.linphone.utils.PasswordDialogModel
import org.linphone.ui.main.sso.fragment.SingleSignOnFragmentDirections
import org.linphone.ui.main.viewmodel.MainViewModel
import org.linphone.ui.main.viewmodel.SharedMainViewModel
import org.linphone.ui.welcome.WelcomeActivity
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import androidx.core.content.edit

@UiThread
class MainActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Main Activity]"

        private const val DEFAULT_FRAGMENT_KEY = "default_fragment"
        private const val CONTACTS_FRAGMENT_ID = 1
        private const val HISTORY_FRAGMENT_ID = 2
        private const val CHAT_FRAGMENT_ID = 3
        private const val MEETINGS_FRAGMENT_ID = 4

        const val ARGUMENTS_CHAT = "Chat"
        const val ARGUMENTS_CONVERSATION_ID = "ConversationId"
    }

    private lateinit var binding: MainActivityBinding

    private lateinit var viewModel: MainViewModel

    private lateinit var sharedViewModel: SharedMainViewModel

    private var currentlyDisplayedAuthDialog: Dialog? = null

    private var navigatedToDefaultFragment = false

    private val destinationListener = object : NavController.OnDestinationChangedListener {
        override fun onDestinationChanged(
            controller: NavController,
            destination: NavDestination,
            arguments: Bundle?
        ) {
            Log.i("$TAG Latest visited fragment was restored")
            navigatedToDefaultFragment = true
            controller.removeOnDestinationChangedListener(this)
        }
    }

    private val postNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG POST_NOTIFICATIONS permission has been granted")
            viewModel.updateMissingPermissionAlert()
        } else {
            Log.w("$TAG POST_NOTIFICATIONS permission has been denied!")
        }
    }

    private val fullScreenIntentPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG USE_FULL_SCREEN_INTENT permission has been granted")
            viewModel.updateMissingPermissionAlert()
        } else {
            Log.w("$TAG USE_FULL_SCREEN_INTENT permission has been denied!")
        }
    }

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be done before the setContentView
        installSplashScreen()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                true // Force dark mode to always have white icons in status bar
            }
        )

        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this
        setUpToastsArea(binding.toastsArea)

        // Will give the device's status bar background color
        ViewCompat.setOnApplyWindowInsetsListener(binding.notificationsArea) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(0, insets.top, 0, 0)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainNavContainer) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(insets.left, 0, insets.right, max(insets.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerMenuContent) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
            mlp.leftMargin = insets.left
            mlp.topMargin = insets.top
            mlp.rightMargin = insets.right
            mlp.bottomMargin = insets.bottom
            v.layoutParams = mlp
            WindowInsetsCompat.CONSUMED
        }

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

        viewModel.askPostNotificationsPermissionEvent.observe(this) {
            it.consume {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    Log.w("$TAG Asking for POST_NOTIFICATIONS permission")
                    postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Log.i("$TAG Permission request for POST_NOTIFICATIONS will be automatically denied, go to android app settings instead")
                    goToAndroidPermissionSettings()
                }
            }
        }

        viewModel.askFullScreenIntentPermissionEvent.observe(this) {
            it.consume {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.USE_FULL_SCREEN_INTENT)) {
                    Log.w("$TAG Asking for USE_FULL_SCREEN_INTENT permission")
                    fullScreenIntentPermissionLauncher.launch(Manifest.permission.USE_FULL_SCREEN_INTENT)
                } else {
                    Log.i("$TAG Permission request for USE_FULL_SCREEN_INTENT will be automatically denied, go to manage app full screen intent android settings instead")
                    Compatibility.requestFullScreenIntentPermission(this)
                }
            }
        }

        viewModel.showNewAccountToastEvent.observe(this) {
            it.consume {
                val message = getString(R.string.new_account_configured_toast)
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

        viewModel.lastAccountRemovedEvent.observe(this) {
            it.consume {
                startActivity(Intent(this, AssistantActivity::class.java))
            }
        }

        viewModel.clearFilesOrTextPendingSharingEvent.observe(this) {
            it.consume {
                sharedViewModel.filesToShareFromIntent.value = arrayListOf()
                sharedViewModel.textToShareFromIntent.value = ""
            }
        }

        sharedViewModel.filesToShareFromIntent.observe(this) { list ->
            if (list.isNotEmpty()) {
                viewModel.addFilesPendingSharing(list)
            } else {
                viewModel.filesOrTextPendingSharingListCleared()
            }
        }

        sharedViewModel.textToShareFromIntent.observe(this) { text ->
            if (!text.isEmpty()) {
                viewModel.addTextPendingSharing()
            } else {
                viewModel.filesOrTextPendingSharingListCleared()
            }
        }

        // Wait for latest visited fragment to be displayed before hiding the splashscreen
        binding.root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                return if (navigatedToDefaultFragment) {
                    Log.i("$TAG Report UI has been fully drawn (TTFD)")
                    try {
                        reportFullyDrawn()
                    } catch (se: SecurityException) {
                        Log.e("$TAG Security exception when doing reportFullyDrawn(): $se")
                    }
                    binding.root.viewTreeObserver.removeOnPreDrawListener(this)
                    true
                } else {
                    false
                }
            }
        })

        coreContext.bearerAuthenticationRequestedEvent.observe(this) {
            it.consume { pair ->
                val serverUrl = pair.first
                val username = pair.second

                Log.i(
                    "$TAG Navigating to Single Sign On Fragment with server URL [$serverUrl] and username [$username]"
                )
                val action = SingleSignOnFragmentDirections.actionGlobalSingleSignOnFragment(
                    serverUrl,
                    username
                )
                findNavController().navigate(action)
            }
        }

        coreContext.digestAuthenticationRequestedEvent.observe(this) {
            it.consume { identity ->
                try {
                    if (coreContext.digestAuthInfoPendingPasswordUpdate != null) {
                        showAuthenticationRequestedDialog(identity)
                    }
                } catch (e: WindowManager.BadTokenException) {
                    Log.e("$TAG Failed to show authentication dialog: $e")
                }
            }
        }

        coreContext.clearAuthenticationRequestDialogEvent.observe(this) {
            it.consume {
                currentlyDisplayedAuthDialog?.dismiss()
            }
        }

        coreContext.showGreenToastEvent.observe(this) {
            it.consume { pair ->
                val message = getString(pair.first)
                val icon = pair.second
                showGreenToast(message, icon)
            }
        }

        coreContext.showRedToastEvent.observe(this) {
            it.consume { pair ->
                val message = getString(pair.first)
                val icon = pair.second
                showRedToast(message, icon)
            }
        }

        coreContext.showFormattedRedToastEvent.observe(this) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                showRedToast(message, icon)
            }
        }

        coreContext.provisioningAppliedEvent.observe(this) {
            it.consume {
                Log.i("$TAG Remote provisioning was applied, checking if theme has changed")
                checkMainColorTheme()
            }
        }

        coreContext.filesToExportToNativeMediaGalleryEvent.observe(this) {
            it.consume { files ->
                Log.i("$TAG Found [${files.size}] files to export to native media gallery")
                for (file in files) {
                    exportFileToNativeMediaGallery(file)
                }

                coreContext.postOnCoreThread {
                    coreContext.clearFilesToExportToNativeGallery()
                }
            }
        }

        CarConnection(this).type.observe(this) {
            val asString = when (it) {
                CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> "NOT CONNECTED"
                CarConnection.CONNECTION_TYPE_PROJECTION -> "PROJECTION"
                CarConnection.CONNECTION_TYPE_NATIVE -> "NATIVE"
                else -> "UNEXPECTED ($it)"
            }
            Log.i("$TAG Car connection is [$asString]")
            val projection = it == CarConnection.CONNECTION_TYPE_PROJECTION
            coreContext.isConnectedToAndroidAuto = projection
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        goToLatestVisitedFragment()

        // We don't want that intent to be handled upon rotation
        if (savedInstanceState == null && intent != null) {
            Log.d("$TAG savedInstanceState is null but intent isn't, handling it")
            handleIntent(intent)
        }
    }

    override fun onPause() {
        viewModel.enableAccountMonitoring(false)

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
        getPreferences(MODE_PRIVATE).edit {
            putInt(DEFAULT_FRAGMENT_KEY, defaultFragmentId)
        }
        Log.i("$TAG Stored [$defaultFragmentId] as default page")

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        viewModel.enableAccountMonitoring(true)
        viewModel.updateMissingPermissionAlert()
        viewModel.updateAccountsAndNetworkReachability()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("$TAG Handling new intent")
        handleIntent(intent)
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
        return findNavController(R.id.main_nav_container)
    }

    fun loadContacts() {
        coreContext.contactsManager.loadContacts(this)
    }

    private fun goToLatestVisitedFragment() {
        try {
            // Prevent navigating to default fragment upon rotation (we only want to do it on first start)
            if (intent.action == Intent.ACTION_MAIN && intent.type == null && intent.data == null) {
                if (viewModel.mainIntentHandled) {
                    Log.d(
                        "$TAG Main intent without type nor data was already handled, do nothing"
                    )
                    navigatedToDefaultFragment = true
                    return
                } else {
                    viewModel.mainIntentHandled = true
                }
            }

            val defaultFragmentId = getPreferences(MODE_PRIVATE).getInt(
                DEFAULT_FRAGMENT_KEY,
                HISTORY_FRAGMENT_ID
            )
            Log.i(
                "$TAG Trying to navigate to set default destination [$defaultFragmentId]"
            )
            try {
                val navOptionsBuilder = NavOptions.Builder()
                navOptionsBuilder.setPopUpTo(R.id.historyListFragment, true)
                navOptionsBuilder.setLaunchSingleTop(true)
                val navOptions = navOptionsBuilder.build()
                val args = bundleOf()
                when (defaultFragmentId) {
                    CONTACTS_FRAGMENT_ID -> {
                        findNavController().addOnDestinationChangedListener(destinationListener)
                        findNavController().navigate(
                            R.id.contactsListFragment,
                            args,
                            navOptions
                        )
                    }
                    CHAT_FRAGMENT_ID -> {
                        findNavController().addOnDestinationChangedListener(destinationListener)
                        findNavController().navigate(
                            R.id.conversationsListFragment,
                            args,
                            navOptions
                        )
                    }
                    MEETINGS_FRAGMENT_ID -> {
                        findNavController().addOnDestinationChangedListener(destinationListener)
                        findNavController().navigate(
                            R.id.meetingsListFragment,
                            args,
                            navOptions
                        )
                    }
                    else -> {
                        Log.i("$TAG Default fragment is the same as the latest visited one")
                        navigatedToDefaultFragment = true
                    }
                }
            } catch (ise: IllegalStateException) {
                Log.e("$TAG Can't navigate to Conversations fragment: $ise")
            }
        } catch (ise: IllegalStateException) {
            Log.i("$TAG Failed to handle intent: $ise")
        }
    }

    private fun handleIntent(intent: Intent) {
        val extras = intent.extras
        val hasExtra = extras != null && !extras.isEmpty
        Log.i(
            "$TAG Handling intent action [${intent.action}], type [${intent.type}], data [${intent.data}] and has ${if (hasExtra) "extras" else "no extra"}"
        )

        val action = intent.action ?: return
        when (action) {
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
                handleMainIntent(intent)
            }
        }
    }

    private fun handleLocusOrShortcut(id: String) {
        Log.i("$TAG Found locus ID [$id]")
        if (id.isNotEmpty()) {
            Log.i("$TAG Navigating to conversation with ID [$id], computed from shortcut ID")
            sharedViewModel.showConversationEvent.value = Event(id)
        }
    }

    private fun handleMainIntent(intent: Intent) {
        coreContext.postOnCoreThread { core ->
            if (corePreferences.firstLaunch) {
                Log.i("$TAG First time Linphone 6.0 has been started, showing Welcome activity")
                corePreferences.firstLaunch = false
                coreContext.postOnMainThread {
                    try {
                        startActivity(Intent(this, WelcomeActivity::class.java))
                    } catch (ise: IllegalStateException) {
                        Log.e("$TAG Can't start activity: $ise")
                    }
                }
            } else if (core.accountList.isEmpty()) {
                Log.w("$TAG No account found, showing Assistant activity")
                coreContext.postOnMainThread {
                    try {
                        startActivity(Intent(this, AssistantActivity::class.java))
                    } catch (ise: IllegalStateException) {
                        Log.e("$TAG Can't start activity: $ise")
                    }
                }
            } else {
                if (intent.hasExtra(ARGUMENTS_CHAT)) {
                    Log.i("$TAG Intent has [Chat] extra")
                    coreContext.postOnMainThread {
                        try {
                            Log.i("$TAG Trying to go to Conversations fragment")
                            val args = intent.extras
                            val conversationId = args?.getString(ARGUMENTS_CONVERSATION_ID, "")
                            if (conversationId.isNullOrEmpty()) {
                                Log.w("$TAG Found [Chat] extra but no conversation ID!")
                            } else {
                                Log.i("$TAG Found [Chat] extra with conversation ID [$conversationId]")
                                sharedViewModel.showConversationEvent.value = Event(conversationId)
                            }
                            args?.clear()

                            if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                                Log.w(
                                    "$TAG Current destination is already conversations list, skipping navigation"
                                )
                            } else {
                                val navOptionsBuilder = NavOptions.Builder()
                                navOptionsBuilder.setPopUpTo(
                                    findNavController().currentDestination?.id ?: R.id.historyListFragment,
                                    true
                                )
                                navOptionsBuilder.setLaunchSingleTop(true)
                                val navOptions = navOptionsBuilder.build()
                                findNavController().navigate(
                                    R.id.conversationsListFragment,
                                    args,
                                    navOptions
                                )
                            }
                        } catch (ise: IllegalStateException) {
                            Log.e("$TAG Can't navigate to Conversations fragment: $ise")
                        }
                    }
                }
            }
        }
    }

    private fun handleSendIntent(intent: Intent, multiple: Boolean) {
        val parcelablesUri = arrayListOf<Uri>()

        if (intent.type == "text/plain") {
            Log.i("$TAG Intent type is [${intent.type}], expecting text in Intent.EXTRA_TEXT")
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { extraText ->
                Log.i("$TAG Found extra text in intent, long of [${extraText.length}]")
                sharedViewModel.textToShareFromIntent.value = extraText
            }
        }

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

        val list = arrayListOf<String>()
        lifecycleScope.launch {
            val deferred = arrayListOf<Deferred<String?>>()
            for (uri in parcelablesUri) {
                Log.i("$TAG Deferring copy from file [${uri.path}] to local storage")
                deferred.add(async { FileUtils.getFilePath(this@MainActivity, uri, false) })
            }

            if (binding.drawerMenu.isOpen) {
                Log.i("$TAG Drawer menu is opened, closing it")
                closeDrawerMenu()
            }
            if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                if (sharedViewModel.displayedChatRoom != null) {
                    Log.w(
                        "$TAG Closing already opened conversation to prevent attaching file in it directly"
                    )
                    sharedViewModel.hideConversationEvent.value = Event(true)
                } else {
                    Log.i("$TAG No chat room currently displayed, nothing to close")
                }
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
                val conversationId = parseShortcutIfAny(intent)
                if (conversationId != null) {
                    Log.i(
                        "$TAG Navigating from debug to conversation with ID [$conversationId], computed from shortcut ID"
                    )
                    sharedViewModel.showConversationEvent.value = Event(conversationId)
                }

                val action = ConversationsListFragmentDirections.actionGlobalConversationsListFragment()
                val options = NavOptions.Builder()
                options.apply {
                    setPopUpTo(R.id.helpFragment, true)
                    setLaunchSingleTop(true)
                }
                findNavController().navigate(action, options.build())
            } else {
                val conversationId = parseShortcutIfAny(intent)
                if (conversationId != null) {
                    Log.i(
                        "$TAG Navigating to conversation with conversation ID [$conversationId] addresses, computed from shortcut ID"
                    )
                    sharedViewModel.showConversationEvent.value = Event(conversationId)
                }

                if (findNavController().currentDestination?.id == R.id.conversationsListFragment) {
                    Log.w(
                        "$TAG Current destination is already conversations list, skipping navigation"
                    )
                } else {
                    val action = ConversationsListFragmentDirections.actionGlobalConversationsListFragment()
                    findNavController().navigate(action)
                }
            }
        }
    }

    private fun parseShortcutIfAny(intent: Intent): String? {
        val shortcutId = intent.getStringExtra("android.intent.extra.shortcut.ID") // Intent.EXTRA_SHORTCUT_ID
        if (shortcutId != null) {
            Log.i("$TAG Found shortcut ID [$shortcutId]")
            return shortcutId
        } else {
            Log.i("$TAG No shortcut ID was found")
        }
        return null
    }

    private fun handleCallIntent(intent: Intent) {
        val uri = intent.data?.toString()
        if (uri.isNullOrEmpty()) {
            Log.e("$TAG Intent data is null or empty, can't process [${intent.action}] intent")
            return
        }

        Log.i("$TAG Found URI [$uri] as data for intent [${intent.action}]")
        val sipUriToCall = when {
            uri.startsWith("tel:") -> uri.substring("tel:".length)
            uri.startsWith("callto:") -> uri.substring("callto:".length)
            uri.startsWith("sip-linphone:") -> uri.replace("sip-linphone:", "sip:")
            uri.startsWith("linphone-sip:") -> uri.replace("linphone-sip:", "sip:")
            uri.startsWith("sips-linphone:") -> uri.replace("sips-linphone:", "sips:")
            uri.startsWith("linphone-sips:") -> uri.replace("linphone-sips:", "sips:")
            else -> uri.replace("%40", "@") // Unescape @ character if needed
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

    private fun handleConfigIntent(uri: String) {
        Log.i("$TAG Trying to parse config intent [$uri] as remote provisioning URL")
        val url = LinphoneUtils.getRemoteProvisioningUrlFromUri(uri)
        if (url == null) {
            Log.e("$TAG Couldn't parse URI [$uri] into a valid remote provisioning URL, aborting")
            return
        }

        coreContext.postOnCoreThread { core ->
            core.provisioningUri = url
            Log.w("$TAG Remote provisioning URL set to [$url], restarting Core now")
            core.stop()
            Log.i("$TAG Core has been stopped, let's restart it")
            core.start()
            Log.i("$TAG Core has been restarted")
        }
    }

    private fun showAuthenticationRequestedDialog(identity: String) {
        currentlyDisplayedAuthDialog?.dismiss()

        val label = AppUtils.getFormattedString(
            R.string.account_settings_dialog_invalid_password_message,
            identity
        )
        val model = PasswordDialogModel(label)
        val dialog = DialogUtils.getAuthRequestedDialog(this, model)

        model.dismissEvent.observe(this) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(this) {
            it.consume { password ->
                coreContext.postOnCoreThread {
                    coreContext.updateAuthInfo(password)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
        currentlyDisplayedAuthDialog = dialog
    }

    private fun exportFileToNativeMediaGallery(filePath: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                Log.i("$TAG Export file [$filePath] to Android's MediaStore")
                val mediaStorePath = FileUtils.addContentToMediaStore(filePath)
                if (mediaStorePath.isNotEmpty()) {
                    Log.i("$TAG File [$filePath] has been successfully exported to MediaStore")
                } else {
                    Log.e("$TAG Failed to export file [$filePath] to MediaStore!")
                }
            }
        }
    }
}
