/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
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
package org.linphone.telecom

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contacts.getListOfSipAddresses
import org.linphone.core.GlobalState
import org.linphone.core.tools.Log

@RequiresApi(Build.VERSION_CODES.Q)
class TelecomRedirectionService : CallRedirectionService() {
    companion object {
        private const val TAG = "[Telecom Redirection Service]"
    }

    override fun onCreate() {
        Log.i("$TAG onCreate")
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("$TAG onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.i("$TAG onDestroy")
        super.onDestroy()
    }

    override fun onPlaceCall(
        handle: Uri,
        initialPhoneAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean
    ) {
        while (!coreContext.isReady()) {
            Thread.sleep(50)
        }
        while (coreContext.core.globalState != GlobalState.On) {
            Thread.sleep(50)
        }
        Log.i("$TAG onPlaceCall called with URI [$handle]")

        if (!corePreferences.useCallRedirectionService) {
            Log.i("$TAG Use of call redirection service is OFF, placing call unmodified")
            placeCallUnmodified()
            return
        }

        if (handle.scheme != "tel") {
            Log.i("$TAG URI isn't using tel: scheme, placing call unmodified")
            placeCallUnmodified()
            return
        }

        val number = handle.schemeSpecificPart
        Log.i("$TAG Extracted number [$number] from tel: URI")

        val phoneAccount = getPhoneAccount(coreContext.context)
        val linphoneAccountHandle = phoneAccount.accountHandle
        Log.i("$TAG Found account handle [$linphoneAccountHandle] from phone account [$phoneAccount]")
        if (linphoneAccountHandle != null) {
            val sipUri = getSipAddressAssociatedToPhoneNumber(number)
            if (sipUri != null) {
                Log.i("$TAG Redirecting call originally going to [$handle] to [$sipUri]")
                redirectCall(sipUri.toUri(), linphoneAccountHandle, false)
            } else {
                Log.w("$TAG Placing call to [$handle] unmodified")
                placeCallUnmodified()
            }
        } else {
            Log.w("$TAG No account handle found, placing call unmodified!")
            placeCallUnmodified()
        }
    }

    @WorkerThread
    private fun getPhoneAccount(context: Context): PhoneAccount {
        val accountHandle = PhoneAccountHandle(
            ComponentName(context, TelecomConnectionService::class.java),
            context.packageName
        )

        val identity = coreContext.core.defaultAccount?.params?.identityAddress?.asStringUriOnly()
            ?: coreContext.core.createPrimaryContactParsed()?.asStringUriOnly()
            ?: "sip:linphone.android@sip.linphone.org"

        val address = identity.toUri()
        val account = PhoneAccount.builder(accountHandle, context.getString(R.string.app_name))
            .setAddress(address)
            .setIcon(Icon.createWithResource(context, R.drawable.linphone_notification))
            .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
            .setHighlightColor(context.getColor(R.color.main1_500))
            .setShortDescription(context.getString(R.string.app_name))
            .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_SIP))
            .build()
        
        val telecomManager = context.getSystemService(TELECOM_SERVICE) as android.telecom.TelecomManager
        try {
            telecomManager.registerPhoneAccount(account)
            Log.i("$TAG Phone account created: $account")
        } catch (uoe: UnsupportedOperationException) {
            Log.e("$TAG Unsupported Operation Exception: $uoe")
        } catch (e: Exception) {
            Log.e("$TAG Exception: $e")
        }
        return account
    }

    @WorkerThread
    private fun getSipAddressAssociatedToPhoneNumber(number: String): String? {
        val friend = coreContext.core.findFriendByPhoneNumber(number)
        if (friend != null) {
            Log.i("$TAG Found friend [${friend.name}] for phone number [$number]")
            return friend.getListOfSipAddresses().firstOrNull()?.asStringUriOnly()
        } else {
            Log.i("$TAG No friend matching phone number [$number]")
        }
        return null
    }
}
