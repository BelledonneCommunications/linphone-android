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

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

class TelecomConnectionService : ConnectionService() {
    companion object {
        private const val TAG = "[Telecom Connection Service]"
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection {
        val uri = request.address
        Log.i("$TAG Received request with URI [$uri]")

        if (coreContext.core.callsNb == 0) {
            val address = coreContext.core.interpretUrl(uri.toString(), true)
            if (address != null) {
                Log.i("$TAG Starting call to [${address.asStringUriOnly()}]")
                coreContext.startCall(address = address, skipNetworkReachabilityTest = true)
            } else {
                Log.e("$TAG Failed to parse [$uri] as a SIP address!")
            }
        } else {
            Log.w("$TAG At least another call exists, do nothing")
        }

        return Connection.createCanceledConnection()
    }
}
