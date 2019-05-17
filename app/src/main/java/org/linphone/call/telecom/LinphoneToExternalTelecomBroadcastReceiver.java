package org.linphone.call.telecom;

/*
LinphoneToExternalTelecomBroadcastReceiver.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import static android.telecom.Connection.STATE_HOLDING;
import static org.linphone.call.telecom.LinphoneConnectionService.EXT_TO_CS_ACTION;
import static org.linphone.call.telecom.LinphoneConnectionService.EXT_TO_CS_CALL_ID;
import static org.linphone.call.telecom.LinphoneConnectionService.EXT_TO_CS_END_CALL;
import static org.linphone.call.telecom.LinphoneConnectionService.EXT_TO_CS_ESTABLISHED;
import static org.linphone.call.telecom.LinphoneConnectionService.EXT_TO_CS_HOLD_CALL;
import static org.linphone.call.telecom.LinphoneConnectionService.EXT_TO_CS_HOLD_STATE;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.DisconnectCause;
import org.linphone.core.tools.Log;

@TargetApi(23)
public class LinphoneToExternalTelecomBroadcastReceiver extends BroadcastReceiver {
    LinphoneConnectionService mService;

    public LinphoneToExternalTelecomBroadcastReceiver(LinphoneConnectionService connectionService) {
        mService = connectionService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        int action = intent.getIntExtra(EXT_TO_CS_ACTION, -1);
        LinphoneConnection connection = null;
        String callId = intent.getStringExtra(EXT_TO_CS_CALL_ID);
        for (LinphoneConnection con : mService.getCalls()) {
            if (callId.equalsIgnoreCase(con.getCallId())) {
                connection = con;
                break;
            }
        }
        if (connection == null) {
            Log.w("[Telecom Manager] Connection not found for call id " + callId);
            for (LinphoneConnection con : mService.getCalls()) {
                if (con.getCallId() == null && action == EXT_TO_CS_END_CALL) {
                    connection = con;
                    break;
                }
            }
            if (connection == null) {
                return;
            }
        }

        switch (action) {
            case EXT_TO_CS_END_CALL:
                connection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                mService.destroyCall(connection);
                connection.destroy();

                mService.updateCallCapabilities();
                mService.updateConferenceable();
                break;

            case EXT_TO_CS_HOLD_CALL:
                boolean holdState = intent.getBooleanExtra(EXT_TO_CS_HOLD_STATE, false);
                if (holdState) {
                    connection.setOnHold();
                } else {
                    mService.setAsActive(connection);
                }
                break;

            case EXT_TO_CS_ESTABLISHED:
                if (connection.getState() != STATE_HOLDING) {
                    mService.setAsActive(connection);
                }
                break;
        }
    }
}
