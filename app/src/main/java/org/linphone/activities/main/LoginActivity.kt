package org.linphone.activities.main

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.ColorRes
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
import org.linphone.environment.DimensionsEnvironmentService.Companion.getInstance
import org.linphone.environment.EnvironmentSelectionAdapter
import org.linphone.middleware.FileTree
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
    private var mAuthStateManager: AuthStateManager? = null
    private var mConfiguration: AuthConfiguration? = null

    private val mClientId = AtomicReference<String?>()
    private val mAuthRequest = AtomicReference<AuthorizationRequest?>()
    private val mAuthIntent = AtomicReference<CustomTabsIntent?>()
    private var mAuthIntentLatch = CountDownLatch(1)
    private var mExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val mUsePendingIntents = true

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.i("LoginActivity", "onCreate")

        super.onCreate(savedInstanceState)

        mAuthStateManager = AuthStateManager.getInstance(this)
        mConfiguration = AuthConfiguration.getInstance(this)

        // Must be done before the setContentView
        installSplashScreen()

        if (!isLoggingInitialised) {
            android.util.Log.i("LoginActivity", "Initialise loggers")
            Timber.plant(Timber.DebugTree(), FileTree(applicationContext))
            Timber.tag("cloud.dimensions.uconnect")
            isLoggingInitialised = true
        }

        setContentView(R.layout.login_activity)

        findViewById<View>(R.id.retry).setOnClickListener { _: View? ->
            mExecutor.submit(
                Runnable { this.initializeAppAuth() }
            )
        }
        findViewById<View>(R.id.start_auth).setOnClickListener { _: View? -> startAuth() }

        handleAuthIntents()

        configureEnvironmentSelector()

        val dimensionsEnvironment = getInstance(applicationContext).getCurrentEnvironment()

        if (dimensionsEnvironment == null) {
            displayAuthOptions()
            return
        } else if (mAuthStateManager!!.getCurrent().isAuthorized && !mConfiguration!!.hasConfigurationChanged()) {
            Log.i("User is already authenticated, proceeding to token activity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        if (!mConfiguration!!.isValid) {
            displayError(mConfiguration!!.configurationError, false)
            return
        }

        if (mConfiguration!!.hasConfigurationChanged()) {
            // discard any existing authorization state due to the change of configuration
            Log.i("Configuration change detected, discarding old state")
            mAuthStateManager!!.replace(AuthState(), "onCreate")
            mConfiguration!!.acceptConfiguration()
        }

        if (intent.getBooleanExtra(EXTRA_FAILED, false)) {
            displayAuthCancelled()
        }

        displayLoading("Initializing")

        mExecutor.submit(Runnable { this.initializeAppAuth() })
    }

    override fun onStart() {
        super.onStart()

        if (mExecutor.isShutdown) {
            mExecutor = Executors.newSingleThreadExecutor()
        }

        // handleAuthIntents()
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

    private fun handleAuthIntents() {
        val asm = AuthStateManager.getInstance(applicationContext)

        if (intent != null &&
            intent.extras != null &&
            intent.extras!!.getString(AuthStateManager.AUTH_KEY) == AuthStateManager.LOGOUT_VALUE
        ) {
            Log.i("LoginActivity.handleAuthIntents::logout::(unset),(unset)")

            asm.updateAfterAuthorization(null, null)
            displayAuthOptions()

//            val isAuthorised = mAuthStateManager!!.getCurrent().isAuthorized
//            if (isAuthorised) return
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

    /**
     * Initializes the authorization service configuration if necessary, either from the local
     * static values or by retrieving an OpenID discovery document.
     */
    @WorkerThread
    private fun initializeAppAuth() {
        Log.i("LoginActivity.initializeAppAuth")

        recreateAuthorizationService()

        if (mAuthStateManager!!.current.authorizationServiceConfiguration != null) {
            // configuration is already created, skip to client initialization
            Log.i("auth config already established")
            initializeClient()
            // return;
        }

        // if we are not using discovery, build the authorization service configuration directly
        // from the static configuration values.
        if (mConfiguration!!.discoveryUri == null) {
            Log.i("Creating auth config from res/raw/auth_config.json")
            val config = AuthorizationServiceConfiguration(
                mConfiguration!!.authEndpointUri!!,
                mConfiguration!!.tokenEndpointUri!!,
                mConfiguration!!.registrationEndpointUri,
                mConfiguration!!.endSessionEndpoint
            )

            mAuthStateManager!!.replace(AuthState(config), "initializeAppAuth")

            initializeClient()
            return
        }

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        runOnUiThread { displayLoading("Retrieving discovery document") }
        Log.i("Retrieving OpenID discovery doc from " + mConfiguration!!.discoveryUri)
        AuthorizationServiceConfiguration.fetchFromUrl(
            mConfiguration!!.discoveryUri!!,
            {
                    config: AuthorizationServiceConfiguration?,
                    ex: AuthorizationException? ->
                this.handleConfigurationRetrievalResult(
                    config,
                    ex
                )
            },
            mConfiguration!!.connectionBuilder
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
        mAuthStateManager!!.replace(AuthState(config), "handleConfigurationRetrievalResult")

        mExecutor.submit { this.initializeClient() }
    }

    /**
     * Initiates a dynamic registration request if a client ID is not provided by the static
     * configuration.
     */
    @WorkerThread
    private fun initializeClient() {
        if (mConfiguration!!.clientId != null) {
            Log.i("Using static client ID: " + mConfiguration!!.clientId)
            // use a statically configured client ID
            mClientId.set(mConfiguration!!.clientId)
            runOnUiThread { this.initializeAuthRequest() }
            return
        }

        val lastResponse = mAuthStateManager!!.current.lastRegistrationResponse

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
            mAuthStateManager!!.current.authorizationServiceConfiguration!!,
            listOf(mConfiguration!!.redirectUri)
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
        mAuthStateManager!!.updateAfterRegistration(response, ex)
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
        val spinner = findViewById<View>(R.id.environment_selector) as Spinner
        val adapter = EnvironmentSelectionAdapter(this)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val env = adapter.getItem(position)
                if (env != null) {
                    Log.i(
                        TAG,
                        "Setting environment " + env.name + " (" + env.identityServerUri + ")"
                    )

                    val environmentService = getInstance(
                        applicationContext
                    )
                    environmentService.setCurrentEnvironment(env)
                    mAuthStateManager!!.replace(AuthState(), "Environment Selector")

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
            Log.w(TAG, "Interrupted while waiting for auth intent")
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
            mConfiguration!!.readConfiguration()
        } catch (e: AuthConfiguration.InvalidConfigurationException) {
            displayError("Failed to reload auth configuration.", true)
        }

        if (AuthorizationServiceManager.getInstance(this).authorizationServiceInstance != null) {
            Log.i(TAG, "Discarding existing AuthService instance")
            AuthorizationServiceManager.getInstance(this).clearAuthorizationServiceInstance()
        }

        mAuthRequest.set(null)
        mAuthIntent.set(null)
    }

    @MainThread
    private fun displayLoading(loadingMessage: String) {
        findViewById<View>(R.id.loading_container).visibility = View.VISIBLE
        findViewById<View>(R.id.auth_container).visibility = View.GONE
        findViewById<View>(R.id.error_container).visibility = View.GONE

        (findViewById<View>(R.id.loading_description) as TextView).text = loadingMessage
    }

    @MainThread
    private fun displayError(error: String?, recoverable: Boolean) {
        findViewById<View>(R.id.error_container).visibility = View.VISIBLE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.auth_container).visibility = View.GONE

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
        createAuthRequest(null)
        warmUpBrowser()
        displayAuthOptions()
    }

    @MainThread
    private fun displayAuthOptions() {
        findViewById<View>(R.id.auth_container).visibility = View.VISIBLE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.error_container).visibility = View.GONE

        val state = mAuthStateManager!!.current
        val config = state.authorizationServiceConfiguration
    }

    private fun displayAuthCancelled() {
        Snackbar.make(
            findViewById(R.id.coordinator),
            "Authorization canceled",
            Snackbar.LENGTH_SHORT
        )
            .show()
    }

    private fun warmUpBrowser() {
        mAuthIntentLatch = CountDownLatch(1)
        mExecutor.execute {
            Log.i(TAG, "Warming up browser instance for auth request")
            val intentBuilder =
                AuthorizationServiceManager.getInstance(this).authorizationServiceInstance.createCustomTabsIntentBuilder(
                    mAuthRequest.get()!!.toUri()
                )
            intentBuilder.setToolbarColor(getColorCompat(R.color.primary_color))
            mAuthIntent.set(intentBuilder.build())
            mAuthIntentLatch.countDown()
        }
    }

    private fun createAuthRequest(loginHint: String?) {
        Log.i(TAG, "Creating auth request for login hint: $loginHint")
        val authRequestBuilder = AuthorizationRequest.Builder(
            mAuthStateManager!!.current.authorizationServiceConfiguration!!,
            mClientId.get()!!,
            ResponseTypeValues.CODE,
            mConfiguration!!.redirectUri
        )
            .setScope(mConfiguration!!.scope)

        if (!TextUtils.isEmpty(loginHint)) {
            authRequestBuilder.setLoginHint(loginHint)
        }

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

    /**
     * Responds to changes in the login hint. After a "debounce" delay, warms up the browser
     * for a request with the new login hint; this avoids constantly re-initializing the
     * browser while the user is typing.
     */
    private inner class LoginHintChangeHandler : TextWatcher {
        private val mHandler = Handler(Looper.getMainLooper())
        private var mTask: RecreateAuthRequestTask

        init {
            mTask = RecreateAuthRequestTask()
        }

        override fun beforeTextChanged(cs: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(cs: CharSequence, start: Int, before: Int, count: Int) {
            mTask.cancel()
            mTask = RecreateAuthRequestTask()
            mHandler.postDelayed(mTask, Companion.DEBOUNCE_DELAY_MS.toLong())
        }

        override fun afterTextChanged(ed: Editable) {}
    }

    private inner class RecreateAuthRequestTask : Runnable {
        private val mCanceled = AtomicBoolean()

        override fun run() {
            if (mCanceled.get()) {
                return
            }

            createAuthRequest(null)
            warmUpBrowser()
        }

        fun cancel() {
            mCanceled.set(true)
        }
    }

    companion object {
        private const val TAG = "LoginActivity"
        private const val EXTRA_FAILED = "failed"
        private const val RC_AUTH = 100
        private const val DEBOUNCE_DELAY_MS = 500

        private var isLoggingInitialised = false
    }
}
