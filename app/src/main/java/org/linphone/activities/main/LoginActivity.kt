package org.linphone.activities.main

import MultiTapDetector
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.database.DataSetObserver
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.ColorRes
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.awaitFirst
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.RegistrationRequest
import net.openid.appauth.RegistrationResponse
import net.openid.appauth.ResponseTypeValues
import org.linphone.R
import org.linphone.authentication.AuthConfiguration
import org.linphone.authentication.AuthStateManager
import org.linphone.authentication.AuthorizationServiceManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.environment.DimensionsEnvironmentService.Companion.getInstance
import org.linphone.environment.EnvironmentSelectionAdapter
import org.linphone.middleware.FileTree
import org.linphone.services.DiagnosticsService
import org.linphone.services.UserService
import org.linphone.utils.Log
import timber.log.Timber

/**
 * Demonstrates the usage of the AppAuth to authorize a user with an OAuth2 / OpenID Connect
 * provider. Based on the configuration provided in `res/raw/auth_config.json`, the code
 * contained here will:
 *
 * - Retrieve an OpenID Connect discovery document for the provider, or use a local static
 * configuration.
 * - Utilize dynamic client registration, if no static client id is specified.
 * - Initiate the authorization request using the built-in heuristics or a user-selected browser.
 *
 * _NOTE_: From a clean checkout of this project, the authorization service is not configured.
 * Edit `res/raw/auth_config.json` to provide the required configuration properties. See the
 * README.md in the app/ directory for configuration instructions, and the adjacent IDP-specific
 * instructions.
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var mAuthStateManager: AuthStateManager
    private lateinit var mConfiguration: AuthConfiguration

    private val mClientId = AtomicReference<String?>()
    private val mAuthRequest = AtomicReference<AuthorizationRequest?>()
    private val mAuthIntent = AtomicReference<CustomTabsIntent?>()
    private var mAuthIntentLatch = CountDownLatch(1)
    private var mExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var environmentAdapter: EnvironmentSelectionAdapter
    private val mUsePendingIntents = true
    private var isEnvironmentSelected = false
    private val destroy = PublishSubject.create<Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Must be done before the setContentView
        installSplashScreen()

        setContentView(R.layout.login_activity)

        if (!isLoggingInitialised) {
            Timber.plant(Timber.DebugTree(), FileTree(applicationContext))
            Timber.tag("cloud.dimensions.uconnect")
            isLoggingInitialised = true
        }

        mAuthStateManager = AuthStateManager.getInstance(this)
        mConfiguration = AuthConfiguration.getInstance(this)

        findViewById<View>(R.id.retry).setOnClickListener { _: View? ->
            mExecutor.submit(
                Runnable {
                    if (mAuthStateManager.current.isAuthorized) {
                        mAuthStateManager.logout(
                            applicationContext
                        )
                    } else {
                        this.initializeAppAuth()
                    }
                }
            )
        }
        findViewById<View>(R.id.start_auth).setOnClickListener { _: View? -> startAuth() }
        findViewById<View>(R.id.send_logs).setOnClickListener { _: View? -> uploadLogs() }
        MultiTapDetector(findViewById(R.id.login_scrollview)) { nTaps, isComplete ->
            toggleDevMode(nTaps, isComplete)
        }

        val nestedScrollView: NestedScrollView = findViewById(R.id.e911_scrollview)
        val buttonAcceptE911 = findViewById<Button>(R.id.buttonAcceptE911)

        nestedScrollView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (nestedScrollView.isVisible && nestedScrollView.height > 0) {
                    val canScroll = nestedScrollView.canScrollVertically(1) || nestedScrollView.canScrollVertically(
                        -1
                    )
                    buttonAcceptE911.isEnabled = !canScroll

                    // Remove the listener to prevent multiple calls
                    nestedScrollView.viewTreeObserver.removeOnPreDrawListener(this)
                }
                return true
            }
        })

        nestedScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (scrollY > oldScrollY) {
                    // User is scrolling down
                    val view = nestedScrollView.getChildAt(nestedScrollView.childCount - 1)
                    val diff = view.bottom - (nestedScrollView.height + nestedScrollView.scrollY)

                    if (diff == 0) {
                        // User has scrolled to the end
                        buttonAcceptE911.isEnabled = true
                    }
                }
            }
        )

        buttonAcceptE911.setOnClickListener {
            UserService.getInstance(applicationContext).updateE911Accepted(true)

            continueLogin()
        }

        displayLoading("Initializing")

        if (!UserService.getInstance(applicationContext).e911Accepted) {
            displayE911()
        } else {
            continueLogin()
        }
    }

    private fun continueLogin() {
        configureEnvironmentSelector()

        val dimensionsEnvironment = getInstance(applicationContext).getCurrentEnvironment()

        if (dimensionsEnvironment == null) {
            displayAuthOptions()
            return
        }

        // mExecutor.submit(Runnable { this.initializeAppAuth() })
        initializeAppAuth()

        if (!handleAuthIntents()) {
            return
        }

        if (!mConfiguration.isValid) {
            displayError(mConfiguration.configurationError, false)
            return
        }

        if (mConfiguration.hasConfigurationChanged()) {
            // discard any existing authorization state due to the change of configuration
            Log.i("Configuration change detected, discarding old state")
            mAuthStateManager.replace(AuthState(), "onCreate")
            mConfiguration.acceptConfiguration()
        }

        if (intent.getBooleanExtra(EXTRA_FAILED, false)) {
            displayAuthCancelled()
        }

        redirectPermittedUser()
    }

    private fun redirectPermittedUser() {
        if (mAuthStateManager.current.isAuthorized && !mConfiguration.hasConfigurationChanged()) {
            val intent = Intent(this, MainActivity::class.java)

            displayLoading("Fetching user info...")

            CoroutineScope(Dispatchers.IO).launch {
                val user = UserService.getInstance(applicationContext).user.awaitFirst()
                if (!user.hasClientPermission()) {
                    displayErrorLater("You do not have permission to use the client.", true)
                } else {
                    Log.i("User is authenticated, proceeding to main activity")
                    redirectToMain()
                }
            }
        }
    }

    private fun redirectToMain() {
        Log.i("User is authenticated, proceeding to main activity")
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onStart() {
        super.onStart()

        if (mExecutor.isShutdown) {
            mExecutor = Executors.newSingleThreadExecutor()
        }
    }

    override fun onStop() {
        super.onStop()
        mExecutor.shutdownNow()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        displayAuthOptions()

        if (resultCode == RESULT_CANCELED) {
            displayAuthCancelled()
        } else {
            val dimensionsEnvironment = getInstance(applicationContext).getCurrentEnvironment()

            if (dimensionsEnvironment == null) {
                displayAuthOptions()
            }
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtras(data!!.extras!!)
            startActivity(intent)
        }
    }

    private fun handleAuthIntents(): Boolean {
        val asm = AuthStateManager.getInstance(applicationContext)

        if (intent != null &&
            intent.extras != null &&
            intent.extras!!.getString(AuthStateManager.AUTH_KEY) == AuthStateManager.LOGOUT_VALUE
        ) {
            Log.i("LoginActivity.handleAuthIntents::logout::(unset),(unset)")

            asm.updateAfterAuthorization(null, null)
            displayAuthOptions()
        } else {
            val response = AuthorizationResponse.fromIntent(intent)
            val ex = AuthorizationException.fromIntent(intent)

            if (response != null || ex != null) {
                Log.i(
                    String.format(
                        "LoginActivity.handleAuthIntents::(%s),(%s)",
                        response,
                        ex
                    )
                )

                asm.updateAfterAuthorization(response, ex)
            }
        }

        val message = intent.getStringExtra("message")
        if (message != null) {
            displayError(message, true)
            return false
        }

        return true
    }

    @MainThread
    fun startAuth() {
        displayLoading("Making authorization request")

        val dimensionsEnvironment = getInstance(applicationContext).getCurrentEnvironment()

        if (dimensionsEnvironment == null) {
            Log.i("Start auth: no environment")
            displayAuthOptions()
        } else {
            Log.i("Start auth: " + dimensionsEnvironment.name)

            // WrongThread inference is incorrect for lambdas
            // noinspection WrongThread
            mExecutor.submit { this.doAuth() }
        }
    }

    @MainThread
    fun uploadLogs() {
        val dimensionsEnvironment = getInstance(applicationContext).getCurrentEnvironment()
        if (dimensionsEnvironment == null) {
            Snackbar
                .make(
                    findViewById(R.id.login_coordinator),
                    "Please select a region first",
                    Snackbar.LENGTH_SHORT
                ).show()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DiagnosticsService.uploadDiagnostics(applicationContext)
                    Snackbar
                        .make(
                            findViewById(R.id.login_coordinator),
                            "Logs uploaded to server",
                            Snackbar.LENGTH_SHORT
                        ).show()
                } catch (e: Exception) {
                    Log.e("[LoginActivity] Failed to upload logs, $e")
                    Snackbar
                        .make(
                            findViewById(R.id.login_coordinator),
                            "Failed to upload logs! " + e.message,
                            Snackbar.LENGTH_SHORT
                        ).show()
                }
            }
        }
    }

    /**
     * Initializes the authorization service configuration if necessary, either from the local
     * static values or by retrieving an OpenID discovery document.
     */
    // @WorkerThread
    private fun initializeAppAuth() {
        Log.i("LoginActivity.initializeAppAuth")

        recreateAuthorizationService()

        if (mAuthStateManager.current.authorizationServiceConfiguration != null) {
            // configuration is already created, skip to client initialization
            Log.i("auth config already established")
            initializeClient()
            return
        }

        // if we are not using discovery, build the authorization service configuration directly
        // from the static configuration values.
        if (mConfiguration.discoveryUri == null) {
            Log.i("discoveryUri is null - creating auth config from res/raw/auth_config.json")
            val config = AuthorizationServiceConfiguration(
                mConfiguration.authEndpointUri!!,
                mConfiguration.tokenEndpointUri!!,
                mConfiguration.registrationEndpointUri,
                mConfiguration.endSessionEndpoint
            )

            mAuthStateManager.replace(AuthState(config), "initializeAppAuth")

            initializeClient()
            return
        }

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        runOnUiThread { displayLoading("Retrieving discovery document") }
        Log.i("Retrieving OpenID discovery doc from " + mConfiguration.discoveryUri)
        AuthorizationServiceConfiguration.fetchFromUrl(
            mConfiguration.discoveryUri!!,
            {
                    config: AuthorizationServiceConfiguration?,
                    ex: AuthorizationException? ->
                this.handleConfigurationRetrievalResult(
                    config,
                    ex
                )
            },
            mConfiguration.connectionBuilder
        )
    }

    @MainThread
    private fun handleConfigurationRetrievalResult(
        config: AuthorizationServiceConfiguration?,
        ex: AuthorizationException?
    ) {
        Log.i("LoginActivity.handleConfigurationRetrievalResult")
        if (config == null) {
            Log.d("Failed to retrieve discovery document", ex)
            displayError("""Failed to retrieve discovery document: ${ex!!.message} """, true)
            return
        }

        Log.i("Discovery document retrieved")
        mAuthStateManager.replace(AuthState(config), "handleConfigurationRetrievalResult")

        mExecutor.submit { this.initializeClient() }
    }

    /**
     * Initiates a dynamic registration request if a client ID is not provided by the static
     * configuration.
     */
    @WorkerThread
    private fun initializeClient() {
        if (mConfiguration.clientId != "") {
            Log.i("Using static client ID: " + mConfiguration.clientId)
            // use a statically configured client ID
            mClientId.set(mConfiguration.clientId)
            runOnUiThread { this.initializeAuthRequest() }
            return
        }

        val lastResponse = mAuthStateManager.current.lastRegistrationResponse

        if (lastResponse != null) {
            Log.i("Using dynamic client ID: " + lastResponse.clientId)
            // already dynamically registered a client ID
            mClientId.set(lastResponse.clientId)
            runOnUiThread { this.initializeAuthRequest() }
            return
        }

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        runOnUiThread { displayLoading("Dynamically registering client") }
        Log.i("Dynamically registering client")

        val registrationRequest = RegistrationRequest.Builder(
            mAuthStateManager.current.authorizationServiceConfiguration!!,
            listOf(mConfiguration.redirectUri)
        )
            .setTokenEndpointAuthenticationMethod(ClientSecretBasic.NAME)
            .build()

        AuthorizationServiceManager.getInstance(applicationContext).authorizationServiceInstance.performRegistrationRequest(
            registrationRequest
        ) { response: RegistrationResponse?, ex: AuthorizationException? ->
            this.handleRegistrationResponse(
                response,
                ex
            )
        }
    }

    @MainThread
    private fun handleRegistrationResponse(
        response: RegistrationResponse?,
        ex: AuthorizationException?
    ) {
        mAuthStateManager.updateAfterRegistration(response, ex)
        if (response == null) {
            Log.i("Failed to dynamically register client", ex)
            displayErrorLater("Failed to register client: " + ex!!.message, true)
            return
        }

        Log.i("Dynamically registered client: " + response.clientId)
        mClientId.set(response.clientId)
        initializeAuthRequest()
    }

    /**
     * Enumerates the browsers installed on the device and populates a spinner, allowing the
     * demo user to easily test the authorization flow against different browser and custom
     * tab configurations.
     */
    @MainThread
    private fun configureEnvironmentSelector() {
        val environmentService = getInstance(
            applicationContext
        )
        val spinner = findViewById<View>(R.id.environment_selector) as Spinner
        environmentAdapter = EnvironmentSelectionAdapter(this)
        environmentAdapter.registerDataSetObserver(NotifyingDataSetObserver())
        spinner.adapter = environmentAdapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                Log.i("Environment spinner onItemSelected")
                if (!isEnvironmentSelected) {
                    Log.i("Skip setting environment on first load.")
                    isEnvironmentSelected = true
                    return
                }

                val env = environmentAdapter.getItem(position)
                if (env != null && env.id != environmentService.getCurrentEnvironment()?.id) {
                    Log.i("Setting environment " + env.name + " (" + env.identityServerUri + ")")

                    environmentService.setCurrentEnvironment(env)
                    mAuthStateManager.replace(AuthState(), "Environment Selector")

                    initializeAppAuth()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // DimensionsEnvironmentService.Companion.getInstance(getApplicationContext()).setCurrentEnvironment(null);
            }
        }
    }

    /**
     * Performs the authorization request, using the browser selected in the spinner,
     * and a user-provided `login_hint` if available.
     */
    @WorkerThread
    private fun doAuth() {
        try {
            mAuthIntentLatch.await()
        } catch (ex: InterruptedException) {
            Log.w("Interrupted while waiting for auth intent")
        }

        if (mUsePendingIntents) {
            val completionIntent = Intent(this, MainActivity::class.java)
            completionIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )

            val cancelIntent = Intent(this, LoginActivity::class.java)
            cancelIntent.putExtra(EXTRA_FAILED, true)
            cancelIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            var flags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            AuthorizationServiceManager.getInstance(this).authorizationServiceInstance.performAuthorizationRequest(
                mAuthRequest.get()!!,
                PendingIntent.getActivity(this, 0, completionIntent, flags),
                PendingIntent.getActivity(this, 0, cancelIntent, flags),
                mAuthIntent.get()!!
            )
        } else {
            val intent = AuthorizationServiceManager.getInstance(this).authorizationServiceInstance.getAuthorizationRequestIntent(
                mAuthRequest.get()!!,
                mAuthIntent.get()!!
            )
            startActivityForResult(intent, RC_AUTH)
        }
    }

    private fun recreateAuthorizationService() {
        try {
            Log.i("LoginActivity.recreateAuthorizationService")
            mConfiguration.readConfiguration()
        } catch (e: AuthConfiguration.InvalidConfigurationException) {
            displayError("Failed to reload auth configuration.", true)
        }

        if (AuthorizationServiceManager.getInstance(this).authorizationServiceInstance != null) {
            Log.i("Discarding existing AuthService instance")
            AuthorizationServiceManager.getInstance(this).clearAuthorizationServiceInstance()
        }

        mAuthRequest.set(null)
        mAuthIntent.set(null)
    }

    @MainThread
    private fun displayLoading(loadingMessage: String) {
        findViewById<View>(R.id.login_container).visibility = View.VISIBLE
        findViewById<View>(R.id.e911_container).visibility = View.GONE

        findViewById<View>(R.id.loading_container).visibility = View.VISIBLE
        findViewById<View>(R.id.auth_container).visibility = View.GONE
        findViewById<View>(R.id.error_container).visibility = View.GONE
        findViewById<View>(R.id.send_logs_container).visibility = View.VISIBLE

        (findViewById<View>(R.id.loading_description) as TextView).text = loadingMessage
    }

    @MainThread
    private fun displayE911() {
        runOnUiThread() {
            findViewById<View>(R.id.login_container).visibility = View.GONE
            findViewById<View>(R.id.e911_container).visibility = View.VISIBLE

            findViewById<View>(R.id.loading_container).visibility = View.GONE
            findViewById<View>(R.id.auth_container).visibility = View.GONE
            findViewById<View>(R.id.error_container).visibility = View.GONE
            findViewById<View>(R.id.send_logs_container).visibility = View.GONE

            (findViewById<View>(R.id.loading_description) as TextView).text = ""
        }
    }

    @MainThread
    private fun displayError(error: String?, recoverable: Boolean) {
        findViewById<View>(R.id.login_container).visibility = View.VISIBLE
        findViewById<View>(R.id.e911_container).visibility = View.GONE

        findViewById<View>(R.id.error_container).visibility = View.VISIBLE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.auth_container).visibility = View.GONE
        findViewById<View>(R.id.send_logs_container).visibility = View.VISIBLE

        (findViewById<View>(R.id.loading_description) as TextView).text = ""

        (findViewById<View>(R.id.error_description) as TextView).text = error
        findViewById<View>(R.id.retry).visibility = if (recoverable) View.VISIBLE else View.GONE
    }

    // WrongThread inference is incorrect in this case
    @AnyThread
    private fun displayErrorLater(error: String, recoverable: Boolean) {
        runOnUiThread { displayError(error, recoverable) }
    }

    @MainThread
    private fun initializeAuthRequest() {
        createAuthRequest()
        warmUpBrowser()
        displayAuthOptions()
    }

    @MainThread
    private fun displayAuthOptions() {
        findViewById<View>(R.id.auth_container).visibility = View.VISIBLE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.error_container).visibility = View.GONE
        findViewById<View>(R.id.send_logs_container).visibility = View.VISIBLE

        (findViewById<View>(R.id.loading_description) as TextView).text = ""

        val state = mAuthStateManager.current
        val config = state.authorizationServiceConfiguration
    }

    private fun displayAuthCancelled() {
        runOnUiThread {
            Snackbar
                .make(
                    findViewById(R.id.login_coordinator),
                    "Authorization cancelled",
                    Snackbar.LENGTH_SHORT
                ).show()
        }
    }

    private fun warmUpBrowser() {
        mAuthIntentLatch = CountDownLatch(1)
        mExecutor.execute {
            Log.i("Warming up browser instance for auth request")
            val intentBuilder =
                AuthorizationServiceManager.getInstance(this).authorizationServiceInstance.createCustomTabsIntentBuilder(
                    mAuthRequest.get()!!.toUri()
                )
            intentBuilder.setToolbarColor(getColorCompat(R.color.primary_color))
            mAuthIntent.set(intentBuilder.build())
            mAuthIntentLatch.countDown()
        }
    }

    private fun createAuthRequest() {
        Log.i("Creating auth request")

        if (mAuthStateManager.current.authorizationServiceConfiguration == null) {
            return
        }

        val environment = getInstance(applicationContext).getCurrentEnvironment()

        val params = HashMap<String, String>()
        if (environment?.defaultTenantId != null) {
            params["tenant_id"] = environment.defaultTenantId!!
        }

        val authRequestBuilder = AuthorizationRequest.Builder(
            mAuthStateManager.current.authorizationServiceConfiguration!!,
            mClientId.get()!!,
            ResponseTypeValues.CODE,
            mConfiguration.redirectUri
        )
            .setScope(mConfiguration.scope)
            .setAdditionalParameters(params)

        mAuthRequest.set(authRequestBuilder.build())
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Suppress("deprecation")
    private fun getColorCompat(@ColorRes color: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getColor(color)
        } else {
            resources.getColor(color)
        }
    }

    private fun toggleDevMode(nTaps: Int, isComplete: Boolean) {
        if (nTaps == 5) {
            val envSvc = DimensionsEnvironmentService.getInstance(applicationContext)
            envSvc.toggleDevMode()
            environmentAdapter.notifyDataSetChanged()
        }
    }

    companion object {
        private const val EXTRA_FAILED = "failed"
        private const val RC_AUTH = 100
        private var isLoggingInitialised = false
    }

    private inner class NotifyingDataSetObserver : DataSetObserver() {
        override fun onChanged() {
            super.onChanged()

            val environmentService = getInstance(applicationContext)
            val spinner = findViewById<View>(R.id.environment_selector) as Spinner

            val environmentId = environmentService.getCurrentEnvironment()?.id ?: ""

            val index = (spinner.adapter as EnvironmentSelectionAdapter).getItemIndex(environmentId)

            Log.i("Environment: $environmentId ($index)")

            spinner.setSelection(index)
        }
    }
}
