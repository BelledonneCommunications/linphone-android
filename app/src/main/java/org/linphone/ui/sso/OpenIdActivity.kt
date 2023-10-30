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
package org.linphone.ui.sso

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthState.AuthStateAction
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceConfiguration.RetrieveConfigurationCallback
import net.openid.appauth.ResponseTypeValues
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.SingleSignOnOpenIdActivityBinding
import org.linphone.utils.FileUtils
import org.linphone.utils.TimestampUtils

@UiThread
class OpenIdActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "[Open ID Activity]"

        private const val WELL_KNOWN = "https://auth.linphone.org:8443/realms/sip.linphone.org/.well-known/openid-configuration"
        private const val CLIENT_ID = "account"
        private const val SCOPE = "openid email profile"
        private const val REDIRECT_URI = "org.linphone.sso:/openidcallback"
        private const val ACTIVITY_RESULT_ID = 666
    }

    private lateinit var binding: SingleSignOnOpenIdActivityBinding

    private lateinit var authState: AuthState
    private lateinit var authService: AuthorizationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.single_sign_on_open_id_activity)
        binding.lifecycleOwner = this

        lifecycleScope.launch {
            authState = getAuthState()
            updateTokenInfo()
        }

        binding.setSingleSignOnClickListener {
            lifecycleScope.launch {
                singleSignOn()
            }
        }

        binding.setRefreshTokenClickListener {
            lifecycleScope.launch {
                performRefreshToken()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACTIVITY_RESULT_ID && data != null) {
            val resp = AuthorizationResponse.fromIntent(data)
            val ex = AuthorizationException.fromIntent(data)

            if (::authState.isInitialized) {
                Log.i("$TAG Updating AuthState object after authorization response")
                authState.update(resp, ex)
            }

            if (resp != null) {
                Log.i("$TAG Response isn't null, performing request token")
                performRequestToken(resp)
            } else {
                Log.e("$TAG Can't perform request token [$ex]")
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun singleSignOn() {
        Log.i("$TAG Fetch from issuer")
        AuthorizationServiceConfiguration.fetchFromUrl(
            Uri.parse(WELL_KNOWN),
            RetrieveConfigurationCallback { serviceConfiguration, ex ->
                if (ex != null) {
                    Log.e("$TAG Failed to fetch configuration")
                    return@RetrieveConfigurationCallback
                }
                if (serviceConfiguration == null) {
                    Log.e("$TAG Service configuration is null!")
                    return@RetrieveConfigurationCallback
                }

                if (!::authState.isInitialized) {
                    Log.i("$TAG Initializing AuthState object")
                    authState = AuthState(serviceConfiguration)
                    storeAuthStateAsJsonFile()
                }

                val authRequestBuilder = AuthorizationRequest.Builder(
                    serviceConfiguration, // the authorization service configuration
                    CLIENT_ID, // the client ID, typically pre-registered and static
                    ResponseTypeValues.CODE, // the response_type value: we want a code
                    Uri.parse(REDIRECT_URI) // the redirect URI to which the auth response is sent
                )

                val authRequest = authRequestBuilder
                    .setScope(SCOPE)
                    .build()
                authService = AuthorizationService(this)
                val authIntent = authService.getAuthorizationRequestIntent(authRequest)
                startActivityForResult(authIntent, ACTIVITY_RESULT_ID)
            }
        )
    }

    private fun performRequestToken(response: AuthorizationResponse) {
        if (::authService.isInitialized) {
            Log.i("$TAG Starting perform token request")
            authService.performTokenRequest(
                response.createTokenExchangeRequest()
            ) { resp, ex ->
                if (resp != null) {
                    Log.i("$TAG Token exchange succeeded!")

                    if (::authState.isInitialized) {
                        Log.i("$TAG Updating AuthState object after token response")
                        authState.update(resp, ex)
                        storeAuthStateAsJsonFile()
                    }

                    useToken()
                } else {
                    Log.e("$TAG Failed to perform token request [$ex]")
                }
            }
        }
    }

    private fun performRefreshToken() {
        if (::authState.isInitialized) {
            if (!::authService.isInitialized) {
                authService = AuthorizationService(this)
            }

            Log.i("$TAG Starting refresh token request")
            authService.performTokenRequest(
                authState.createTokenRefreshRequest()
            ) { resp, ex ->
                if (resp != null) {
                    Log.i("$TAG Token refresh succeeded!")

                    if (::authState.isInitialized) {
                        Log.i("$TAG Updating AuthState object after refresh token response")
                        authState.update(resp, ex)
                        storeAuthStateAsJsonFile()
                    }
                    updateTokenInfo()
                } else {
                    Log.e("$TAG Failed to perform token refresh [$ex]")
                }
            }
        }
    }

    private fun useToken() {
        if (::authState.isInitialized && ::authService.isInitialized) {
            if (authState.needsTokenRefresh && authState.refreshToken.isNullOrEmpty()) {
                Log.e("$TAG Attempted to take an unauthorized action without a refresh token!")
                return
            }

            Log.i("$TAG Performing action with fresh token")
            authState.performActionWithFreshTokens(
                authService,
                AuthStateAction { accessToken, idToken, ex ->
                    if (ex != null) {
                        Log.e("$TAG Failed to use token [$ex]")
                        return@AuthStateAction
                    }

                    Log.i("$$TAG Access & id tokens are now available")
                    Log.d("$TAG Access token [$accessToken], id token [$idToken]")

                    storeAuthStateAsJsonFile()
                }
            )
        }
    }

    private suspend fun getAuthState(): AuthState {
        val file = File(applicationContext.filesDir.absolutePath, "auth_state.json")
        if (file.exists()) {
            val content = FileUtils.readFile(file)
            if (content.isNotEmpty()) {
                Log.i("$TAG Initializing AuthState from local JSON file")
                Log.d("$TAG Local JSON file contains [$content]")
                try {
                    return AuthState.jsonDeserialize(content)
                } catch (exception: Exception) {
                    Log.e("$TAG Failed to use serialized AuthState [$exception]")
                }
            }
        }

        return AuthState()
    }

    private fun storeAuthStateAsJsonFile() {
        Log.i("$TAG Trying to save serialized authState as JSON file")
        val data = authState.jsonSerializeString()
        Log.d("$TAG Date to save is [$data]")
        val file = File(applicationContext.filesDir.absolutePath, "auth_state.json")
        lifecycleScope.launch {
            if (FileUtils.dumpStringToFile(data, file)) {
                Log.i("$TAG Service configuration saved as JSON as [${file.absolutePath}]")
            } else {
                Log.i(
                    "$TAG Failed to save service configuration as JSON as [${file.absolutePath}]"
                )
            }
        }
    }

    private fun updateTokenInfo() {
        if (::authState.isInitialized) {
            if (authState.isAuthorized) {
                Log.i("$TAG User is already authenticated!")
                binding.sso.visibility = View.GONE
                binding.tokenRefresh.visibility = View.GONE
                binding.tokenExpires.visibility = View.VISIBLE

                val expiration = authState.accessTokenExpirationTime
                if (expiration != null) {
                    if (expiration < System.currentTimeMillis()) {
                        Log.w("$TAG Access token is expired")
                        binding.tokenExpires.text = "Token expired!"
                        binding.tokenRefresh.visibility = View.VISIBLE
                    } else {
                        val date = if (TimestampUtils.isToday(expiration, timestampInSecs = false)) {
                            "today"
                        } else {
                            TimestampUtils.toString(
                                expiration,
                                onlyDate = true,
                                timestampInSecs = false
                            )
                        }
                        val time = TimestampUtils.toString(expiration, timestampInSecs = false)
                        Log.i("$TAG Access token expires [$date] [$time]")
                        binding.tokenExpires.text = "Token expires $date at $time"
                    }
                } else {
                    Log.w("$TAG Access token expiration info not available")
                    binding.tokenExpires.text = "Can't access token expiration!"
                }
            } else {
                Log.w("$TAG User isn't authenticated yet!")
                binding.sso.visibility = View.VISIBLE
                binding.tokenRefresh.visibility = View.GONE
                binding.tokenExpires.visibility = View.GONE
            }
        }
    }
}
