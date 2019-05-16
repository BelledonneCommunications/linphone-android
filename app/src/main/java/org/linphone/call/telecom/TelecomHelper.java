package org.linphone.call.telecom;

/*
TelecomHelper.java
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

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.tools.Log;
import org.linphone.utils.LinphoneUtils;

@TargetApi(23)
public class TelecomHelper {
    private Context mContext;
    private TelecomManager mTelecomManager;
    private LinphoneTelecomAccount mLinphoneTelecomAccount;
    private PhoneAccountHandle mPhoneAccountHandle;
    private TelecomBroadcastReceiver mReceiver;
    private CoreListenerStub mListener;

    public TelecomHelper(Context context) {
        mContext = context;

        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        mLinphoneTelecomAccount = new LinphoneTelecomAccount(mContext);
        mPhoneAccountHandle = mLinphoneTelecomAccount.getHandle();

        mReceiver = new TelecomBroadcastReceiver();

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        switch (state) {
                            case End:
                            case Released:
                                onCallTerminated(call.getCallLog().getCallId());
                                break;
                            case Connected:
                                if (call.getDir() == Call.Dir.Outgoing) {
                                    sendToCS(
                                            LinphoneConnectionService.EXT_TO_CS_ESTABLISHED,
                                            call.getCallLog().getCallId());
                                }
                        }
                    }
                };
        Core core = LinphoneManager.getCore();
        core.addListener(mListener);

        Log.i("[Telecom Manager] Created");
    }

    public void destroy() {
        Core core = LinphoneManager.getCore();
        core.removeListener(mListener);
        // mLinphoneTelecomAccount.unregister();
        Log.i("[Telecom Manager] Destroyed");
    }

    public void refreshLinphoneTelecomAccount() {
        Log.i("[Telecom Manager] Refreshing account");
        mLinphoneTelecomAccount.refreshPhoneAccount();
    }

    public boolean isAccountEnabled() {
        return mLinphoneTelecomAccount.getPhoneAccount().isEnabled();
    }

    public void disable() {
        mLinphoneTelecomAccount.unregister();
    }

    public void onIncomingCallReceived(Call call) {
        if (call == null) return;
        Log.i("[Telecom Manager] Incoming call received");

        Address address = call.getRemoteAddress();
        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        String contactName =
                contact == null
                        ? LinphoneUtils.getAddressDisplayName(address)
                        : contact.getFullName();

        Bundle extras = new Bundle();
        final Bundle b = new Bundle();

        extras.putString(
                LinphoneConnectionService.EXT_TO_CS_CALL_ID, call.getCallLog().getCallId());
        extras.putString(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address.asStringUriOnly());

        b.putString(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, contactName);
        extras.putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, b);

        mTelecomManager.addNewIncomingCall(mPhoneAccountHandle, extras);
        if (call.getCore().getCallsNb() == 1) {
            registerCallScreenReceiver();
        }
    }

    public void onOutgoingCallStarting(Call call) {
        if (call == null) return;
        Log.i("[Telecom Manager] Outgoing call started");

        Bundle extras = new Bundle();
        // FIXME: use correct video state
        extras.putInt(
                TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_AUDIO_ONLY);
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, mPhoneAccountHandle);
        extras.putString(
                TelecomManager.EXTRA_CALL_BACK_NUMBER,
                call.getCallLog().getFromAddress().asStringUriOnly());

        Address address = call.getRemoteAddress();

        // Send the call to LinphoneConnectionService, received by onCreateOutgoingConnection
        // FIXME: check CALL_PHONE permission
        mTelecomManager.placeCall(Uri.parse(address.asStringUriOnly()), extras);

        if (call.getCore().getCallsNb() == 1) {
            registerCallScreenReceiver();
        }
    }

    public void goBackToCallScreen() {
        // FIXME: check CALL_PHONE permission
        mTelecomManager.showInCallScreen(false);
    }

    private void onCallTerminated(String callId) {
        Log.i("[Telecom Manager] Call terminated");
        sendToCS(LinphoneConnectionService.EXT_TO_CS_END_CALL, callId);

        Core core = LinphoneManager.getCore();
        if (core != null && core.getCallsNb() == 0) {
            unRegisterCallScreenReceiver();
        }
    }

    private void registerCallScreenReceiver() {
        LocalBroadcastManager.getInstance(LinphoneService.instance())
                .registerReceiver(
                        mReceiver, new IntentFilter(LinphoneConnectionService.CS_TO_EXT_BROADCAST));
        Log.i("[Telecom Manager] Registered");
    }

    private void unRegisterCallScreenReceiver() {
        LocalBroadcastManager.getInstance(LinphoneService.instance()).unregisterReceiver(mReceiver);
        Log.i("[Telecom Manager] Unregistered");
    }

    private void sendToCS(int action, String callId) {
        Log.i("[Telecom Manager] Sending action " + action + " for call id " + callId);
        Intent intent = new Intent(LinphoneConnectionService.EXT_TO_CS_BROADCAST);
        intent.putExtra(LinphoneConnectionService.EXT_TO_CS_ACTION, action);

        intent.putExtra(LinphoneConnectionService.EXT_TO_CS_CALL_ID, callId);
        if (action == LinphoneConnectionService.EXT_TO_CS_HOLD_CALL) {
            intent.putExtra(LinphoneConnectionService.EXT_TO_CS_HOLD_STATE, true);
        }

        LocalBroadcastManager.getInstance(LinphoneService.instance()).sendBroadcast(intent);
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

    private class TelecomBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int action = intent.getIntExtra(LinphoneConnectionService.CS_TO_EXT_ACTION, -1);
            String callId = intent.getStringExtra(LinphoneConnectionService.CS_TO_EXT_CALL_ID);

            Call call = findCallFromId(callId);
            Log.i(
                    "[Telecom Manager] Received action "
                            + action
                            + " for call id "
                            + callId
                            + (call == null ? " (not found)" : " (found)"));
            if (call == null) {
                onCallTerminated(callId);
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
    }
}
