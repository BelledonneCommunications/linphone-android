package org.linphone.call.telecom;

/*
ExternalToLinphoneTelecomBroadcastReceiver.java
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;

public class ExternalToLinphoneTelecomBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int action = intent.getIntExtra(LinphoneConnectionService.CS_TO_EXT_ACTION, -1);
        String callId = intent.getStringExtra(LinphoneConnectionService.CS_TO_EXT_CALL_ID);

        Call call = findCallFromId(callId);
        Log.i(
                "[Telecom Broadcast Receiver] Received action "
                        + TelecomHelper.getActionNameFromValue(action)
                        + " for call id "
                        + callId
                        + (call == null ? " (not found)" : " (found)"));
        if (call == null) {
            LinphoneService.instance().getTelecomHelper().onCallTerminated(callId);
        }

        switch (action) {
            case LinphoneConnectionService.CS_TO_EXT_ANSWER:
                LinphoneManager.getCallManager().acceptCall(call);
                break;
            case LinphoneConnectionService.CS_TO_EXT_END:
                call.terminate();
                break;
            case LinphoneConnectionService.CS_TO_EXT_TERMINATE:
                call.terminate();
                break;
            case LinphoneConnectionService.CS_TO_EXT_ABORT:
                call.terminate();
                break;
            case LinphoneConnectionService.CS_TO_EXT_HOLD:
                call.pause();
                break;
            case LinphoneConnectionService.CS_TO_EXT_UNHOLD:
                call.resume();
                break;
        }
    }

    private Call findCallFromId(String callId) {
        Core core = LinphoneManager.getCore();
        if (callId == null) return core.getCurrentCall();

        Call[] calls = core.getCalls();
        for (Call call : calls) {
            CallLog log = call.getCallLog();
            if (log != null && callId.equals(log.getCallId())) {
                return call;
            }
        }
        return null;
    }
}
