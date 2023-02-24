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
package org.linphone.activities.assistant.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AccountCreator
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log

abstract class AbstractPushTokenViewModel(val accountCreator: AccountCreator) : ViewModel() {
    private var waitingForPushToken = false
    private var waitForPushJob: Job? = null

    private val coreListener = object : CoreListenerStub() {
        override fun onPushNotificationReceived(core: Core, payload: String?) {
            Log.i("[Assistant] Push received: [$payload]")

            val data = payload.orEmpty()
            if (data.isNotEmpty()) {
                try {
                    // This is because JSONObject.toString() done by the SDK will result in payload looking like {"custom-payload":"{\"token\":\"value\"}"}
                    val cleanPayload = data.replace("\\\"", "\"").replace("\"{", "{").replace(
                        "}\"",
                        "}"
                    )
                    Log.i("[Assistant] Cleaned payload is: [$cleanPayload]")
                    val json = JSONObject(cleanPayload)
                    val customPayload = json.getJSONObject("custom-payload")
                    if (customPayload.has("token")) {
                        waitForPushJob?.cancel()
                        waitingForPushToken = false

                        val token = customPayload.getString("token")
                        if (token.isNotEmpty()) {
                            Log.i("[Assistant] Extracted token [$token] from push payload")
                            accountCreator.token = token
                            onFlexiApiTokenReceived()
                        } else {
                            Log.e("[Assistant] Push payload JSON object has an empty 'token'!")
                            onFlexiApiTokenRequestError()
                        }
                    } else {
                        Log.e("[Assistant] Push payload JSON object has no 'token' key!")
                        onFlexiApiTokenRequestError()
                    }
                } catch (e: JSONException) {
                    Log.e("[Assistant] Exception trying to parse push payload as JSON: [$e]")
                    onFlexiApiTokenRequestError()
                }
            } else {
                Log.e("[Assistant] Push payload is null or empty, can't extract auth token!")
                onFlexiApiTokenRequestError()
            }
        }
    }

    init {
        coreContext.core.addListener(coreListener)
    }

    override fun onCleared() {
        coreContext.core.removeListener(coreListener)
        waitForPushJob?.cancel()
    }

    abstract fun onFlexiApiTokenReceived()
    abstract fun onFlexiApiTokenRequestError()

    protected fun requestFlexiApiToken() {
        if (!coreContext.core.isPushNotificationAvailable) {
            Log.e(
                "[Assistant] Core says push notification aren't available, can't request a token from FlexiAPI"
            )
            onFlexiApiTokenRequestError()
            return
        }

        val pushConfig = coreContext.core.pushNotificationConfig
        if (pushConfig != null) {
            Log.i(
                "[Assistant] Found push notification info: provider [${pushConfig.provider}], param [${pushConfig.param}] and prid [${pushConfig.prid}]"
            )
            accountCreator.pnProvider = pushConfig.provider
            accountCreator.pnParam = pushConfig.param
            accountCreator.pnPrid = pushConfig.prid

            // Request an auth token, will be sent by push
            val result = accountCreator.requestAuthToken()
            if (result == AccountCreator.Status.RequestOk) {
                val waitFor = 5000
                waitingForPushToken = true
                waitForPushJob?.cancel()

                Log.i("[Assistant] Waiting push with auth token for $waitFor ms")
                waitForPushJob = viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        delay(waitFor.toLong())
                    }
                    withContext(Dispatchers.Main) {
                        if (waitingForPushToken) {
                            waitingForPushToken = false
                            Log.e("[Assistant] Auth token wasn't received by push in $waitFor ms")
                            onFlexiApiTokenRequestError()
                        }
                    }
                }
            } else {
                Log.e("[Assistant] Failed to require a push with an auth token: [$result]")
                onFlexiApiTokenRequestError()
            }
        } else {
            Log.e("[Assistant] No push configuration object in Core, shouldn't happen!")
            onFlexiApiTokenRequestError()
        }
    }
}
