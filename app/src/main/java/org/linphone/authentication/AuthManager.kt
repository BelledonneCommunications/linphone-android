package org.linphone.authentication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import com.auth0.android.jwt.JWT
import java.security.MessageDigest
import java.security.SecureRandom
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.browser.BrowserAllowList
import net.openid.appauth.browser.VersionedBrowserMatcher
import org.json.JSONException
import org.linphone.activities.GenericActivity
import org.linphone.utils.Constants

class AuthManager : GenericActivity() {

    private var authState: AuthState = AuthState()
    private var jwt: JWT? = null
    private lateinit var authorizationService: AuthorizationService
    lateinit var authServiceConfig: AuthorizationServiceConfiguration

    fun restoreState() {
        val jsonString: String? = getApplication()
            .getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(Constants.AUTH_STATE, null)

        if (jsonString != null && !TextUtils.isEmpty(jsonString)) {
            try {
                authState = AuthState.jsonDeserialize(jsonString)

                if (!TextUtils.isEmpty(authState.idToken)) {
                    jwt = JWT(authState.idToken!!)
                }
            } catch (jsonException: JSONException) {
            }
        }
    }

    fun persistState() {
        getApplication().getSharedPreferences(
            Constants.SHARED_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(Constants.AUTH_STATE, authState.jsonSerializeString())
            .commit()
    }

    private fun initAuthServiceConfig() {
        authServiceConfig = AuthorizationServiceConfiguration(
            Uri.parse(Constants.URL_AUTHORIZATION),
            Uri.parse(Constants.URL_TOKEN_EXCHANGE),
            null,
            Uri.parse(Constants.URL_LOGOUT)
        )
    }

    private fun initAuthService() {
        val appAuthConfiguration = AppAuthConfiguration.Builder()
            .setBrowserMatcher(
                BrowserAllowList(
                    VersionedBrowserMatcher.CHROME_CUSTOM_TAB,
                    VersionedBrowserMatcher.SAMSUNG_CUSTOM_TAB
                )
            ).build()

        authorizationService = AuthorizationService(
            getApplication(),
            appAuthConfiguration
        )
    }

    fun attemptAuthorization() {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)

        val encoding = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        val codeVerifier = Base64.encodeToString(bytes, encoding)

        val digest = MessageDigest.getInstance(Constants.MESSAGE_DIGEST_ALGORITHM)
        val hash = digest.digest(codeVerifier.toByteArray())
        val codeChallenge = Base64.encodeToString(hash, encoding)

        val builder = AuthorizationRequest.Builder(
            authServiceConfig,
            Constants.CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(Constants.URL_AUTH_REDIRECT)
        )
            .setCodeVerifier(
                codeVerifier,
                codeChallenge,
                Constants.CODE_VERIFIER_CHALLENGE_METHOD
            )

        builder.setScopes(
            Constants.SCOPE_PROFILE,
            Constants.SCOPE_EMAIL,
            Constants.SCOPE_OPENID,
            Constants.SCOPE_DRIVE
        )

        val request = builder.build()

        val authIntent = authorizationService.getAuthorizationRequestIntent(request)
        launchForResult(authIntent)
    }

    private fun launchForResult(intent: Intent) {
        val launcher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            run {
                if (result.resultCode == Activity.RESULT_OK) {
                    // handleAuthorizationResponse(result.data!!)
                }
            }
        }

        launcher.launch((intent))
    }
}
