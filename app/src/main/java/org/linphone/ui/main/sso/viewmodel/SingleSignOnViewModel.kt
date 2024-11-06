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
package org.linphone.ui.main.sso.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.TimestampUtils

class SingleSignOnViewModel : GenericViewModel() {
    companion object {
        private const val TAG = "[Single Sign On ViewModel]"

        private const val CLIENT_ID = "linphone"
        private const val REDIRECT_URI = "org.linphone:/openidcallback"
    }

    val singleSignOnProcessCompletedEvent = MutableLiveData<Event<Boolean>>()

    private var singleSignOnUrl = ""

    private var username: String = ""

    val startAuthIntentEvent: MutableLiveData<Event<Intent>> by lazy {
        MutableLiveData<Event<Intent>>()
    }

    val onErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private lateinit var authState: AuthState
    private lateinit var authService: AuthorizationService

    @UiThread
    fun setUp(ssoUrl: String, user: String = "") {
        viewModelScope.launch {
            singleSignOnUrl = ssoUrl
            username = user

            Log.i(
                "$TAG Setting up SSO environment for username [$username] and URL [$singleSignOnUrl], redirect URI is [$REDIRECT_URI]"
            )
            authState = getAuthState()
            updateTokenInfo()
        }
    }

    @UiThread
    fun processAuthIntentResponse(resp: AuthorizationResponse?, ex: AuthorizationException?) {
        if (::authState.isInitialized) {
            Log.i("$TAG Updating AuthState object after authorization response")
            authState.update(resp, ex)
        }

        if (resp != null) {
            Log.i("$TAG Response isn't null, performing request token")
            performRequestToken(resp)
        } else {
            Log.e("$TAG Can't perform request token [$ex]")
            onErrorEvent.postValue(Event(ex?.errorDescription.orEmpty()))
        }
    }

    @UiThread
    private fun singleSignOn() {
        Log.i("$TAG Fetch from issuer [$singleSignOnUrl]")
        AuthorizationServiceConfiguration.fetchFromIssuer(
            Uri.parse(singleSignOnUrl),
            AuthorizationServiceConfiguration.RetrieveConfigurationCallback { serviceConfiguration, ex ->
                if (ex != null) {
                    Log.e(
                        "$TAG Failed to fetch configuration from issuer [$singleSignOnUrl]: ${ex.errorDescription}"
                    )
                    onErrorEvent.postValue(
                        Event("Failed to fetch configuration from issuer $singleSignOnUrl")
                    )
                    return@RetrieveConfigurationCallback
                }

                if (serviceConfiguration == null) {
                    Log.e("$TAG Service configuration is null!")
                    onErrorEvent.postValue(Event("Service configuration is null"))
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

                // Needed for SDK to be able to refresh the token, otherwise it will return
                // an invalid grant error with description "Session not active"
                authRequestBuilder.setScopes("offline_access")

                if (username.isNotEmpty() && corePreferences.useUsernameAsSingleSignOnLoginHint) {
                    Log.i("$TAG Using username [$username] as login hint")
                    authRequestBuilder.setLoginHint(username)
                }

                val authRequest = authRequestBuilder.build()
                authService = AuthorizationService(coreContext.context)
                val authIntent = authService.getAuthorizationRequestIntent(authRequest)
                startAuthIntentEvent.postValue(Event(authIntent))
            }
        )
    }

    @UiThread
    private fun performRefreshToken() {
        if (::authState.isInitialized) {
            if (!::authService.isInitialized) {
                authService = AuthorizationService(coreContext.context)
            }

            val authStateJsonFile = File(corePreferences.ssoCacheFile)
            Log.i("$TAG Starting refresh token request")
            try {
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
                        Log.e(
                            "$TAG Failed to perform token refresh [$ex], destroying auth_state.json file"
                        )
                        onErrorEvent.postValue(Event(ex?.errorDescription.orEmpty()))

                        viewModelScope.launch {
                            FileUtils.deleteFile(authStateJsonFile.absolutePath)
                            Log.w(
                                "$TAG Previous auth_state.json file deleted, starting single sign on process from scratch"
                            )
                            singleSignOn()
                        }
                    }
                }
            } catch (ise: IllegalStateException) {
                Log.e("$TAG Illegal state exception, clearing auth state and trying again: $ise")
                viewModelScope.launch {
                    FileUtils.deleteFile(authStateJsonFile.absolutePath)
                    authState = getAuthState()
                    performRefreshToken()
                }
            } catch (e: Exception) {
                Log.e("$TAG Failed to perform token request: $e")
            }
        }
    }

    @UiThread
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

                    storeTokensInAuthInfo()
                } else {
                    Log.e("$TAG Failed to perform token request [$ex]")
                    onErrorEvent.postValue(Event(ex?.errorDescription.orEmpty()))
                }
            }
        }
    }

    @UiThread
    private suspend fun getAuthState(): AuthState {
        val file = File(corePreferences.ssoCacheFile)
        if (file.exists()) {
            Log.i("$TAG Auth state file found, trying to read it")
            val content = FileUtils.readFile(file)
            if (content.isNotEmpty()) {
                Log.i("$TAG Initializing AuthState from local JSON file")
                Log.d("$TAG Local JSON file contains [$content]")
                try {
                    return AuthState.jsonDeserialize(content)
                } catch (exception: Exception) {
                    Log.e("$TAG Failed to use serialized AuthState [$exception]")
                    onErrorEvent.postValue(Event("Failed to read stored AuthState"))
                }
            }
        } else {
            Log.i("$TAG Auth state file not found yet...")
        }

        return AuthState()
    }

    @UiThread
    private fun storeAuthStateAsJsonFile() {
        Log.i("$TAG Trying to save serialized authState as JSON file")
        val data = authState.jsonSerializeString()
        Log.d("$TAG Date to save is [$data]")
        val file = File(corePreferences.ssoCacheFile)
        viewModelScope.launch {
            if (FileUtils.dumpStringToFile(data, file)) {
                Log.i("$TAG Service configuration saved as JSON as [${file.absolutePath}]")
            } else {
                Log.i(
                    "$TAG Failed to save service configuration as JSON as [${file.absolutePath}]"
                )
            }
        }
    }

    @UiThread
    private fun updateTokenInfo() {
        Log.i("$TAG Updating token info")

        if (::authState.isInitialized) {
            if (authState.isAuthorized) {
                Log.i("$TAG User is already authenticated!")

                val expiration = authState.accessTokenExpirationTime
                if (expiration != null) {
                    if (expiration < System.currentTimeMillis()) {
                        Log.w("$TAG Access token is expired")
                        performRefreshToken()
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
                        storeTokensInAuthInfo()
                    }
                } else {
                    Log.w("$TAG Access token expiration info not available")
                    val file = File(corePreferences.ssoCacheFile)
                    viewModelScope.launch {
                        FileUtils.deleteFile(file.absolutePath)
                        singleSignOn()
                    }
                }
            } else {
                Log.w("$TAG User isn't authenticated yet")
                singleSignOn()
            }
        } else {
            Log.i("$TAG Auth state hasn't been created yet")
            singleSignOn()
        }
    }

    @UiThread
    private fun storeTokensInAuthInfo() {
        coreContext.postOnCoreThread { core ->
            val expire = authState.accessTokenExpirationTime
            if (expire == null) {
                Log.e("$TAG Access token expiration time is null!")
                onErrorEvent.postValue(Event("Invalid access token expiration time"))
            } else {
                val accessToken =
                    Factory.instance().createBearerToken(authState.accessToken, expire / 1000) // Linphone timestamps are in seconds
                val refreshToken =
                    Factory.instance().createBearerToken(authState.refreshToken, expire / 1000) // Linphone timestamps are in seconds

                val authInfo = coreContext.bearerAuthInfoPendingPasswordUpdate
                if (authInfo == null) {
                    Log.e("$TAG No pending auth info in CoreContext!")
                    return@postOnCoreThread
                }
                authInfo.accessToken = accessToken
                authInfo.refreshToken = refreshToken

                try {
                    val data = authState.jsonSerializeString()
                    val json = JSONObject(data)
                    val lastTokenResponse = json.getJSONObject("mLastTokenResponse")
                    val request = lastTokenResponse.getJSONObject("request")
                    val config = request.getJSONObject("configuration")
                    val tokenEndpoint = config.getString("tokenEndpoint")
                    Log.i("$TAG Extracted [$tokenEndpoint] token endpoint URL")
                    authInfo.tokenEndpointUri = tokenEndpoint
                } catch (e: Exception) {
                    Log.e(
                        "$TAG Failed to extract tokenEndpoint from lastTokenResponse in AuthState's JSON: $e"
                    )
                }

                authInfo.clientId = CLIENT_ID
                core.addAuthInfo(authInfo)

                Log.i(
                    "$TAG Auth info for username [$username] filled with access token [${authState.accessToken}], refresh token [${authState.refreshToken}] and expire [$expire], refreshing REGISTERs"
                )
                core.refreshRegisters()
                singleSignOnProcessCompletedEvent.postValue(Event(true))
            }
        }
    }
}
