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

import android.app.Dialog
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.doOnAttach
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.window.layout.FoldingFeature
import coil.imageLoader
import com.google.android.material.snackbar.Snackbar
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import kotlin.math.abs
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationService.TokenResponseCallback
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientAuthentication.UnsupportedAuthenticationMethod
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.SnackBarActivity
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.main.viewmodels.CallOverlayViewModel
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.navigateToChatRoom
import org.linphone.activities.navigateToChatRooms
import org.linphone.activities.navigateToContact
import org.linphone.activities.navigateToContacts
import org.linphone.activities.navigateToDialer
import org.linphone.authentication.AuthStateManager
import org.linphone.compatibility.Compatibility
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.AVPFMode
import org.linphone.core.AuthInfo
import org.linphone.core.AuthMethod
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.CorePreferences
import org.linphone.core.Factory
import org.linphone.core.TransportType
import org.linphone.databinding.MainActivityBinding
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.middleware.FileTree
import org.linphone.models.UserDevice
import org.linphone.services.APIClientService
import org.linphone.services.BrandingService
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.Log
import org.linphone.utils.PermissionHelper
import org.linphone.utils.ShortcutsHelper
import org.linphone.utils.hideKeyboard
import org.linphone.utils.setKeyboardInsetListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class MainActivity : GenericActivity(), SnackBarActivity, NavController.OnDestinationChangedListener {
    private lateinit var binding: MainActivityBinding
    private lateinit var sharedViewModel: SharedMainViewModel
    private lateinit var callOverlayViewModel: CallOverlayViewModel

    private val listener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Main Activity] Contact(s) updated, update shortcuts")
            if (corePreferences.contactsShortcuts) {
                ShortcutsHelper.createShortcutsToContacts(this@MainActivity)
            } else if (corePreferences.chatRoomShortcuts) {
                ShortcutsHelper.createShortcutsToChatRooms(this@MainActivity)
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
            applicationContext.imageLoader.memoryCache?.clear()
        }
    }

    override fun onLayoutChanges(foldingFeature: FoldingFeature?) {
        sharedViewModel.layoutChangedEvent.value = Event(true)
    }

    private var shouldTabsBeVisibleDependingOnDestination = true
    private var shouldTabsBeVisibleDueToOrientationAndKeyboard = true

    private val authenticationRequestedEvent: MutableLiveData<Event<AuthInfo>> by lazy {
        MutableLiveData<Event<AuthInfo>>()
    }
    private var authenticationRequiredDialog: Dialog? = null

    private val coreListener: CoreListenerStub = object : CoreListenerStub() {
        override fun onAuthenticationRequested(core: Core, authInfo: AuthInfo, method: AuthMethod) {
            if (authInfo.username == null || authInfo.domain == null || authInfo.realm == null) {
                return
            }

            Log.w(
                "[Main Activity] Authentication requested for account [${authInfo.username}@${authInfo.domain}] with realm [${authInfo.realm}] using method [$method]"
            )
            authenticationRequestedEvent.value = Event(authInfo)
        }
    }

    private val keyboardVisibilityListeners = arrayListOf<AppUtils.KeyboardVisibilityListener>()

    private lateinit var apiClientService: APIClientService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.plant(Timber.DebugTree(), FileTree(applicationContext))
        Timber.tag("cloud.dimensions.uconnect")
        Timber.d("OnCreate")
        Log.d("OnCreate")

        // Must be done before the setContentView
        installSplashScreen()

        val dimensionsEnvironment = DimensionsEnvironmentService.getInstance(applicationContext).getCurrentEnvironment()
        val asm = AuthStateManager.getInstance(applicationContext)

        apiClientService = APIClientService()
        apiClientService.getUCGatewayService(
            this.applicationContext,
            dimensionsEnvironment!!.gatewayApiUri
        ).doGetUserDevices(
            userID = asm.fetchUserId()
        )
            .enqueue(object : Callback<List<UserDevice>> {
                override fun onFailure(call: Call<List<UserDevice>>, t: Throwable) {
                }

                override fun onResponse(
                    call: Call<List<UserDevice>>,
                    response: Response<List<UserDevice>>
                ) {
                    val userDevices = response.body()
                    if (!userDevices.isNullOrEmpty()) {
                        registerSipEndpoints(userDevices)
                    }
                }
            })

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this

        sharedViewModel = ViewModelProvider(this)[SharedMainViewModel::class.java]
        binding.viewModel = sharedViewModel

        callOverlayViewModel = ViewModelProvider(this)[CallOverlayViewModel::class.java]
        binding.callOverlayViewModel = callOverlayViewModel

        sharedViewModel.toggleDrawerEvent.observe(
            this
        ) {
            it.consume {
                if (binding.sideMenu.isDrawerOpen(Gravity.LEFT)) {
                    binding.sideMenu.closeDrawer(binding.sideMenuContent, true)
                } else {
                    binding.sideMenu.openDrawer(binding.sideMenuContent, true)
                }
            }
        }

        coreContext.callErrorMessageResourceId.observe(
            this
        ) {
            it.consume { message ->
                showSnackBar(message)
            }
        }

        authenticationRequestedEvent.observe(
            this
        ) {
            it.consume { authInfo ->
                showAuthenticationRequestedDialog(authInfo)
            }
        }

        if (coreContext.core.accountList.isEmpty()) {
            if (corePreferences.firstStart) {
                startActivity(Intent(this, AssistantActivity::class.java))
            }
        }

        tabsFragment = findViewById(R.id.tabs_fragment)
        statusFragment = findViewById(R.id.status_fragment)

        binding.root.doOnAttach {
            Log.i("[Main Activity] Report UI has been fully drawn (TTFD)")
            try {
                reportFullyDrawn()
            } catch (se: SecurityException) {
                Log.e("[Main Activity] Security exception when doing reportFullyDrawn(): $se")
            }
        }

        BrandingService.getInstance(applicationContext)
            .brand
            .subscribe { brand ->
                Log.i("Got brand: " + brand.value?.brandName)

                // DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setThemeOverlay(Color.CYAN))
                // val primary = resources.getColor(R.color.primary_color)
                // window.navigationBarColor = Color.CYAN
            }
    }

    private fun registerSipEndpoints(userDeviceList: List<UserDevice>) {
        setAudioPayloadTypes()
        setVideoPayloadTypes()

        coreContext.core.clearAllAuthInfo()
        coreContext.core.clearProxyConfig()
        coreContext.core.clearAccounts()

        userDeviceList.forEach {
            RegisterSipEndpoint(it)
        }

        if (coreContext.core.accountList.any()) {
            coreContext.core.defaultAccount = coreContext.core.accountList.first()
        }
    }

    private fun RegisterSipEndpoint(userDevice: UserDevice) {
        if (userDevice.hasCredentials()) {
            val accountParams = coreContext.core.createAccountParams()
            val identityUri = "${userDevice.sipUsername} <sip:${userDevice.sipUsername}@${userDevice.sipRealm}>"

            accountParams.identityAddress = coreContext.core.interpretUrl(identityUri, false)

            var enableOutboundProxy = false
            if (userDevice.sipOutboundProxy.isNotBlank()) {
                enableOutboundProxy = true
            }

            var sipTransport = userDevice.sipTransport
            if (sipTransport.isBlank()) {
                sipTransport = "tls"
            }

            var serverUri = "<sip:${userDevice.sipOutboundProxy};transport=${stringToTransportType(
                sipTransport
            )}>"
            accountParams.serverAddress = coreContext.core.interpretUrl(serverUri, false)

            accountParams.isOutboundProxyEnabled = enableOutboundProxy
            accountParams.isRegisterEnabled = true
            accountParams.avpfMode = AVPFMode.Disabled // This is always disabled in PlumMobile
            accountParams.expires = userDevice.sipRegisterTimeout
            accountParams.pushNotificationAllowed = false
            accountParams.remotePushNotificationAllowed = false

            val auth = Factory.instance().createAuthInfo(
                userDevice.sipUsername,
                userDevice.sipUsername,
                userDevice.sipUserPassword,
                "",
                "",
                userDevice.sipRealm
            )
            coreContext.core.addAuthInfo(auth)

            if (accountParams.natPolicy == null) {
                accountParams.natPolicy = coreContext.core.createNatPolicy()
            }

            accountParams.natPolicy!!.isIceEnabled = false
            accountParams.natPolicy!!.isStunEnabled = false
            accountParams.natPolicy!!.stunServerUsername = null
            accountParams.natPolicy!!.isTurnEnabled = false
            accountParams.natPolicy!!.isUdpTurnTransportEnabled = false
            accountParams.natPolicy!!.isTcpTurnTransportEnabled = false
            accountParams.natPolicy!!.isTlsTurnTransportEnabled = false

            // TODO createPushToken service
            // val pushToken = pushTokenService.GetVoipToken()

            val account = coreContext.core.createAccount(accountParams)
            coreContext.core.addAccount(account)
        }
    }

    private fun stringToTransportType(sipTransport: String): TransportType {
        if (sipTransport.lowercase() == "udp") return TransportType.Udp
        if (sipTransport.lowercase() == "tcp") return TransportType.Tcp

        return TransportType.Tls
    }

    private fun setVideoPayloadTypes() {
//        for (payloadType in coreContext.core.videoPayloadTypes) {
//        }
    }

    private fun setAudioPayloadTypes() {
        for (payloadType in coreContext.core.audioPayloadTypes) {
            if (payloadType.mimeType.equals("PCMA", true) ||
                payloadType.mimeType.equals("PCMU", true)
            ) {
                payloadType.enable(true)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent != null) {
            Log.d("[Main Activity] Found new intent")
            handleIntentParams(intent)
        }
    }

    override fun onStart() {
        super.onStart()

        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        val asm = AuthStateManager.getInstance(applicationContext)

        Log.i("isAuthorised: " + asm.current.isAuthorized)

        if (asm.current.isAuthorized) {
            // displayAuthorized();
            return
        }

        if (response != null || ex != null) {
            asm.updateAfterAuthorization(response, ex)
        }

        if (response?.authorizationCode != null) {
            // authorization code exchange is required
            asm.updateAfterAuthorization(response, ex)
            exchangeAuthorizationCode(response)
        } else if (ex != null) {
            // displayNotAuthorized("Authorization flow failed: " + ex.message)
        } else {
            // displayNotAuthorized("No authorization state retained - reauthorization required")
        }
    }

    override fun onResume() {
        super.onResume()
        coreContext.contactsManager.addListener(listener)
        coreContext.core.addListener(coreListener)
    }

    override fun onPause() {
        coreContext.core.removeListener(coreListener)
        coreContext.contactsManager.removeListener(listener)
        super.onPause()
    }

    override fun showSnackBar(@StringRes resourceId: Int) {
        Snackbar.make(findViewById(R.id.coordinator), resourceId, Snackbar.LENGTH_LONG).show()
    }

    override fun showSnackBar(@StringRes resourceId: Int, action: Int, listener: () -> Unit) {
        Snackbar
            .make(findViewById(R.id.coordinator), resourceId, Snackbar.LENGTH_LONG)
            .setAction(action) {
                Log.i("[Snack Bar] Action listener triggered")
                listener()
            }
            .show()
    }

    override fun showSnackBar(message: String) {
        Snackbar.make(findViewById(R.id.coordinator), message, Snackbar.LENGTH_LONG).show()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        registerComponentCallbacks(componentCallbacks)
        findNavController(R.id.nav_host_fragment).addOnDestinationChangedListener(this)

        binding.rootCoordinatorLayout.setKeyboardInsetListener { keyboardVisible ->
            val portraitOrientation = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            Log.i(
                "[Main Activity] Keyboard is ${if (keyboardVisible) "visible" else "invisible"}, orientation is ${if (portraitOrientation) "portrait" else "landscape"}"
            )
            shouldTabsBeVisibleDueToOrientationAndKeyboard = !portraitOrientation || !keyboardVisible
            updateTabsFragmentVisibility()

            for (listener in keyboardVisibilityListeners) {
                listener.onKeyboardVisibilityChanged(keyboardVisible)
            }
        }

        initOverlay()

        if (intent != null) {
            Log.d("[Main Activity] Found post create intent")
            handleIntentParams(intent)
        }
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
        hideKeyboard()
        if (statusFragment.visibility == View.GONE) {
            statusFragment.visibility = View.VISIBLE
        }

        shouldTabsBeVisibleDependingOnDestination = when (destination.id) {
            R.id.masterCallLogsFragment, R.id.masterContactsFragment, R.id.dialerFragment, R.id.masterChatRoomsFragment ->
                true
            else -> false
        }
        updateTabsFragmentVisibility()
    }

    fun addKeyboardVisibilityListener(listener: AppUtils.KeyboardVisibilityListener) {
        keyboardVisibilityListeners.add(listener)
    }

    fun removeKeyboardVisibilityListener(listener: AppUtils.KeyboardVisibilityListener) {
        keyboardVisibilityListeners.remove(listener)
    }

    fun hideKeyboard() {
        currentFocus?.hideKeyboard()
    }

    fun showKeyboard() {
        // Requires a text field to have the focus
        if (currentFocus != null) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(currentFocus, 0)
        } else {
            Log.w("[Main Activity] Can't show the keyboard, no focused view")
        }
    }

    fun hideStatusFragment(hide: Boolean) {
        statusFragment.visibility = if (hide) View.GONE else View.VISIBLE
    }

    private fun updateTabsFragmentVisibility() {
        tabsFragment.visibility = if (shouldTabsBeVisibleDependingOnDestination && shouldTabsBeVisibleDueToOrientationAndKeyboard) View.VISIBLE else View.GONE
    }

    private fun handleIntentParams(intent: Intent) {
        Log.i(
            "[Main Activity] Handling intent with action [${intent.action}], type [${intent.type}] and data [${intent.data}]"
        )

        when (intent.action) {
            Intent.ACTION_MAIN -> handleMainIntent(intent)
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
                if (uri != null) {
                    if (
                        intent.type == AppUtils.getString(R.string.linphone_address_mime_type) &&
                        PermissionHelper.get().hasReadContactsPermission()
                    ) {
                        val contactId =
                            coreContext.contactsManager.getAndroidContactIdFromUri(uri)
                        if (contactId != null) {
                            Log.i("[Main Activity] Found contact URI parameter in intent: $uri")
                            navigateToContact(contactId)
                        }
                    } else {
                        val stringUri = uri.toString()
                        if (stringUri.startsWith("linphone-config:")) {
                            val remoteConfigUri = stringUri.substring("linphone-config:".length)
                            if (corePreferences.autoRemoteProvisioningOnConfigUriHandler) {
                                Log.w(
                                    "[Main Activity] Remote provisioning URL set to [$remoteConfigUri], restarting Core now"
                                )
                                applyRemoteProvisioning(remoteConfigUri)
                            } else {
                                Log.i(
                                    "[Main Activity] Remote provisioning URL found [$remoteConfigUri], asking for user validation"
                                )
                                showAcceptRemoteConfigurationDialog(remoteConfigUri)
                            }
                        } else {
                            handleTelOrSipUri(uri)
                        }
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
            else -> handleMainIntent(intent)
        }

        // Prevent this intent to be processed again
        intent.action = null
        intent.data = null
        val extras = intent.extras
        if (extras != null) {
            for (key in extras.keySet()) {
                intent.removeExtra(key)
            }
        }
    }

    private fun handleMainIntent(intent: Intent) {
        when {
            intent.hasExtra("ContactId") -> {
                val id = intent.getStringExtra("ContactId")
                Log.i("[Main Activity] Found contact ID in extras: $id")
                navigateToContact(id)
            }
            intent.hasExtra("Chat") -> {
                if (corePreferences.disableChat) return

                if (intent.hasExtra("RemoteSipUri") && intent.hasExtra("LocalSipUri")) {
                    val peerAddress = intent.getStringExtra("RemoteSipUri")
                    val localAddress = intent.getStringExtra("LocalSipUri")
                    Log.i(
                        "[Main Activity] Found chat room intent extra: local SIP URI=[$localAddress], peer SIP URI=[$peerAddress]"
                    )
                    navigateToChatRoom(localAddress, peerAddress)
                } else {
                    Log.i("[Main Activity] Found chat intent extra, go to chat rooms list")
                    navigateToChatRooms()
                }
            }
            intent.hasExtra("Dialer") -> {
                Log.i("[Main Activity] Found dialer intent extra, go to dialer")
                val isTransfer = intent.getBooleanExtra("Transfer", false)
                sharedViewModel.pendingCallTransfer = isTransfer
                navigateToDialer()
            }
            intent.hasExtra("Contacts") -> {
                Log.i("[Main Activity] Found contacts intent extra, go to contacts list")
                val isTransfer = intent.getBooleanExtra("Transfer", false)
                sharedViewModel.pendingCallTransfer = isTransfer
                navigateToContacts()
            }
            else -> {
                val core = coreContext.core
                val call = core.currentCall ?: core.calls.firstOrNull()
                if (call != null) {
                    Log.i(
                        "[Main Activity] Launcher clicked while there is at least one active call, go to CallActivity"
                    )
                    val callIntent = Intent(
                        this,
                        org.linphone.activities.voip.CallActivity::class.java
                    )
                    callIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                    startActivity(callIntent)
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

        addressToCall = addressToCall.replace("%40", "@")

        val address = coreContext.core.interpretUrl(
            addressToCall,
            LinphoneUtils.applyInternationalPrefix()
        )
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

            val localAddress =
                coreContext.core.defaultAccount?.params?.identityAddress?.asStringUriOnly()
            val peerAddress = coreContext.core.interpretUrl(
                addressToIM,
                LinphoneUtils.applyInternationalPrefix()
            )?.asStringUriOnly()
            Log.i(
                "[Main Activity] Navigating to chat room with local [$localAddress] and peer [$peerAddress] addresses"
            )
            navigateToChatRoom(localAddress, peerAddress)
        } else {
            val shortcutId = intent.getStringExtra("android.intent.extra.shortcut.ID") // Intent.EXTRA_SHORTCUT_ID
            if (shortcutId != null) {
                Log.i("[Main Activity] Found shortcut ID: $shortcutId")
                handleLocusOrShortcut(shortcutId)
            } else {
                Log.i("[Main Activity] Going into chat rooms list")
                navigateToChatRooms()
            }
        }
    }

    private fun handleLocusOrShortcut(id: String) {
        val split = id.split("~")
        if (split.size == 2) {
            val localAddress = split[0]
            val peerAddress = split[1]
            Log.i(
                "[Main Activity] Navigating to chat room with local [$localAddress] and peer [$peerAddress] addresses, computed from shortcut/locus id"
            )
            navigateToChatRoom(localAddress, peerAddress)
        } else {
            Log.e(
                "[Main Activity] Failed to parse shortcut/locus id: $id, going to chat rooms list"
            )
            navigateToChatRooms()
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

    private fun applyRemoteProvisioning(remoteConfigUri: String) {
        coreContext.core.provisioningUri = remoteConfigUri
        coreContext.core.stop()
        coreContext.core.start()
    }

    private fun showAcceptRemoteConfigurationDialog(remoteConfigUri: String) {
        val dialogViewModel = DialogViewModel(
            remoteConfigUri,
            getString(R.string.dialog_apply_remote_provisioning_title)
        )
        val dialog = DialogUtils.getDialog(this, dialogViewModel)

        dialogViewModel.showCancelButton {
            Log.i("[Main Activity] User cancelled remote provisioning config")
            dialog.dismiss()
        }

        val okLabel = getString(
            R.string.dialog_apply_remote_provisioning_button
        )
        dialogViewModel.showOkButton(
            {
                Log.w(
                    "[Main Activity] Remote provisioning URL set to [$remoteConfigUri], restarting Core now"
                )
                applyRemoteProvisioning(remoteConfigUri)
                dialog.dismiss()
            },
            okLabel
        )

        dialog.show()
    }

    private fun showAuthenticationRequestedDialog(
        authInfo: AuthInfo
    ) {
        authenticationRequiredDialog?.dismiss()

        val accountFound = coreContext.core.accountList.find {
            it.params.identityAddress?.username == authInfo.username && it.params.identityAddress?.domain == authInfo.domain
        }
        if (accountFound == null) {
            Log.w("[Main Activity] Failed to find account matching auth info, aborting auth dialog")
            return
        }

        val identity = "${authInfo.username}@${authInfo.domain}"
        Log.i("[Main Activity] Showing authentication required dialog for account [$identity]")

        val dialogViewModel = DialogViewModel(
            getString(R.string.dialog_authentication_required_message, identity),
            getString(R.string.dialog_authentication_required_title)
        )
        dialogViewModel.showPassword = true
        dialogViewModel.passwordTitle = getString(
            R.string.settings_password_protection_dialog_input_hint
        )
        val dialog = DialogUtils.getDialog(this, dialogViewModel)

        dialogViewModel.showCancelButton {
            dialog.dismiss()
            authenticationRequiredDialog = null
        }

        dialogViewModel.showOkButton(
            {
                Log.i(
                    "[Main Activity] Updating password for account [$identity] using auth info [$authInfo]"
                )
                val newPassword = dialogViewModel.password
                authInfo.password = newPassword
                coreContext.core.addAuthInfo(authInfo)

                coreContext.core.refreshRegisters()

                dialog.dismiss()
                authenticationRequiredDialog = null
            },
            getString(R.string.dialog_authentication_required_change_password_label)
        )

        dialog.show()
        authenticationRequiredDialog = dialog
    }

    @MainThread
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        // displayLoading("Exchanging authorization code")
        performTokenRequest(
            authorizationResponse.createTokenExchangeRequest(),
            this::handleCodeExchangeResponse
        )
    }

    @MainThread
    private fun performTokenRequest(
        request: TokenRequest,
        callback: TokenResponseCallback
    ) {
        val asm = AuthStateManager.getInstance(applicationContext)
        val clientAuthentication: ClientAuthentication

        try {
            clientAuthentication = asm.getCurrent().getClientAuthentication()
        } catch (ex: UnsupportedAuthenticationMethod) {
            Log.i("Token request cannot be made - endpoint could not be constructed (%s)", ex)
            // TODO displayNotAuthorized("Client authentication method is unsupported")
            return
        }

        val authService = AuthorizationService(applicationContext)
        authService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    @WorkerThread
    private fun handleCodeExchangeResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        val asm = AuthStateManager.getInstance(applicationContext)
        asm.updateAfterTokenResponse(tokenResponse, authException)
        if (!asm.getCurrent().isAuthorized) {
            val message = (
                "Authorization Code exchange failed" +
                    (if ((authException != null)) authException.error else "")
                )

            Log.i(message)
            // WrongThread inference is incorrect for lambdas
            // TODO: runOnUiThread { displayNotAuthorized(message) }
        } else {
            // TODO: runOnUiThread(this::displayAuthorized)
        }
    }
}
