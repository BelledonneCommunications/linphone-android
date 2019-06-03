package org.linphone.call.telecom;

/*
LinphoneConnection.java
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

import static org.linphone.call.telecom.LinphoneConnectionService.CS_TO_EXT_ABORT;
import static org.linphone.call.telecom.LinphoneConnectionService.CS_TO_EXT_ACTION;
import static org.linphone.call.telecom.LinphoneConnectionService.CS_TO_EXT_ANSWER;
import static org.linphone.call.telecom.LinphoneConnectionService.CS_TO_EXT_BROADCAST;
import static org.linphone.call.telecom.LinphoneConnectionService.CS_TO_EXT_CALL_ID;
import static org.linphone.call.telecom.LinphoneConnectionService.CS_TO_EXT_END;
import static org.linphone.call.telecom.LinphoneConnectionService.CS_TO_EXT_HOLD;
import static org.linphone.call.telecom.LinphoneConnectionService.CS_TO_EXT_TERMINATE;
import static org.linphone.call.telecom.LinphoneConnectionService.CS_TO_EXT_UNHOLD;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@TargetApi(Build.VERSION_CODES.M)
public class LinphoneConnection extends Connection {
    private LinphoneConnectionService mConnectionService;
    private boolean mIsActive = false;
    private String mCallId;

    LinphoneConnection(LinphoneConnectionService cs) {
        mConnectionService = cs;

        int capabilities = getConnectionCapabilities();
        capabilities |= CAPABILITY_MUTE;
        capabilities |= CAPABILITY_SUPPORT_HOLD;
        capabilities |= CAPABILITY_HOLD;
        setConnectionCapabilities(capabilities);
    }

    public String getCallId() {
        return mCallId;
    }

    public void setCallId(String mCallId) {
        this.mCallId = mCallId;
    }

    public void setLocalActive(boolean isActive) {
        mIsActive = isActive;
    }

    public boolean isLocalActive() {
        return mIsActive;
    }

    @Override
    public void onAbort() {
        mConnectionService.destroyCall(this);
        destroy();

        sendLocalBroadcast(CS_TO_EXT_ABORT);
    }

    @Override
    public void onAnswer(int videoState) {
        if (mConnectionService.getCalls().size() > 1) {
            mConnectionService.holdInActiveCalls(this);
        }
        mConnectionService.setAsActive(this);
        sendLocalBroadcast(CS_TO_EXT_ANSWER);
    }

    @Override
    public void onPlayDtmfTone(char c) {
        // TODO: play/send DTMF
    }

    @Override
    public void onStopDtmfTone() {}

    @Override
    public void onDisconnect() {
        sendLocalBroadcast(CS_TO_EXT_TERMINATE);
    }

    @Override
    public void onHold() {
        if (mConnectionService.getCalls().size() > 1) {
            mConnectionService.performSwitchCall();
        } else {
            setOnHold();
            sendLocalBroadcast(CS_TO_EXT_HOLD);
        }
    }

    @Override
    public void onReject() {
        setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        mConnectionService.destroyCall(this);
        destroy();
        sendLocalBroadcast(CS_TO_EXT_END);
        super.onReject();
    }

    @Override
    public void onUnhold() {
        setActive();
        sendLocalBroadcast(CS_TO_EXT_UNHOLD);
    }

    @Override
    public void onStateChanged(int state) {
        mConnectionService.updateCallCapabilities();
    }

    public void sendLocalBroadcast(int action) {
        Intent intent = new Intent(CS_TO_EXT_BROADCAST);
        intent.putExtra(CS_TO_EXT_ACTION, action);
        intent.putExtra(CS_TO_EXT_CALL_ID, getCallId());
        LocalBroadcastManager.getInstance(mConnectionService.getApplicationContext())
                .sendBroadcast(intent);
    }
}
