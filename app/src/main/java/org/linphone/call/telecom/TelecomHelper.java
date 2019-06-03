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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.tools.Log;
import org.linphone.utils.LinphoneUtils;

@TargetApi(23)
public class TelecomHelper {
    public static final String LINPHONE_TELECOM_EXTRA_CONTACT_NAME = "CONTACT_NAME";
    public static final String LINPHONE_TELECOM_EXTRA_APP_NAME = "APP_NAME";
    public static final String LINPHONE_TELECOM_EXTRA_APP_ICON = "APP_ICON";

    private Context mContext;
    private TelecomManager mTelecomManager;
    private LinphoneTelecomAccount mLinphoneTelecomAccount;
    private PhoneAccountHandle mPhoneAccountHandle;
    private ExternalToLinphoneTelecomBroadcastReceiver mReceiver;
    private CoreListenerStub mListener;

    public TelecomHelper(Context context) {
        mContext = context;

        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        mLinphoneTelecomAccount = new LinphoneTelecomAccount(mContext);
        mPhoneAccountHandle = mLinphoneTelecomAccount.getHandle();

        mReceiver = new ExternalToLinphoneTelecomBroadcastReceiver();

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

        Bundle extras = prepareBundle(call);

        mTelecomManager.addNewIncomingCall(mPhoneAccountHandle, extras);
        if (call.getCore().getCallsNb() == 1) {
            registerCallScreenReceiver();
        }
    }

    @SuppressLint("MissingPermission")
    public void onOutgoingCallStarting(Call call) {
        if (call == null) return;
        Log.i("[Telecom Manager] Outgoing call started");

        Bundle extras = prepareBundle(call);

        // Send the call to LinphoneConnectionService, received by onCreateOutgoingConnection
        mTelecomManager.placeCall(Uri.parse(call.getRemoteAddress().asStringUriOnly()), extras);

        if (call.getCore().getCallsNb() == 1) {
            registerCallScreenReceiver();
        }
    }

    public void onCallTerminated(String callId) {
        Log.i("[Telecom Manager] Call terminated");
        sendToCS(LinphoneConnectionService.EXT_TO_CS_END_CALL, callId);

        Core core = LinphoneManager.getCore();
        if (core != null && core.getCallsNb() == 0) {
            unRegisterCallScreenReceiver();
        }
    }

    private Bundle prepareBundle(Call call) {
        Bundle extras = new Bundle();

        Address address = call.getRemoteAddress();
        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        String contactName =
                contact == null
                        ? LinphoneUtils.getAddressDisplayName(address)
                        : contact.getFullName();

        String bundleKey;
        if (call.getDir() == Call.Dir.Outgoing) {
            bundleKey = TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS;
            extras.putString(
                    TelecomManager.EXTRA_CALL_BACK_NUMBER,
                    call.getCallLog().getFromAddress().asStringUriOnly());
        } else {
            bundleKey = TelecomManager.EXTRA_INCOMING_CALL_EXTRAS;
            extras.putString(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address.asStringUriOnly());
        }

        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, mPhoneAccountHandle);
        extras.putString(
                LinphoneConnectionService.EXT_TO_CS_CALL_ID, call.getCallLog().getCallId());

        Bundle b = new Bundle();
        b.putString(LINPHONE_TELECOM_EXTRA_CONTACT_NAME, contactName);
        b.putString(LINPHONE_TELECOM_EXTRA_APP_NAME, mContext.getString(R.string.app_name));
        b.putParcelable(
                LINPHONE_TELECOM_EXTRA_APP_ICON,
                Icon.createWithResource(mContext, R.drawable.linphone_logo));
        extras.putBundle(bundleKey, b);

        return extras;
    }

    private void registerCallScreenReceiver() {
        LocalBroadcastManager.getInstance(LinphoneService.instance())
                .registerReceiver(
                        mReceiver, new IntentFilter(LinphoneConnectionService.CS_TO_EXT_BROADCAST));
        Log.i("[Telecom Manager] Registered call screen receiver");
    }

    private void unRegisterCallScreenReceiver() {
        LocalBroadcastManager.getInstance(LinphoneService.instance()).unregisterReceiver(mReceiver);
        Log.i("[Telecom Manager] Unregistered call screen receiver");
    }

    private void sendToCS(int action, String callId) {
        Log.i(
                "[Telecom Manager] Sending action "
                        + getActionNameFromValue(action)
                        + " for call id "
                        + callId);
        Intent intent = new Intent(LinphoneConnectionService.EXT_TO_CS_BROADCAST);
        intent.putExtra(LinphoneConnectionService.EXT_TO_CS_ACTION, action);

        intent.putExtra(LinphoneConnectionService.EXT_TO_CS_CALL_ID, callId);
        if (action == LinphoneConnectionService.EXT_TO_CS_HOLD_CALL) {
            intent.putExtra(LinphoneConnectionService.EXT_TO_CS_HOLD_STATE, true);
        }

        LocalBroadcastManager.getInstance(LinphoneService.instance()).sendBroadcast(intent);
    }

    public static String getActionNameFromValue(int action) {
        switch (action) {
            case LinphoneConnectionService.CS_TO_EXT_ANSWER:
                return "CS_TO_EXT_ANSWER";
            case LinphoneConnectionService.CS_TO_EXT_END:
                return "CS_TO_EXT_END";
            case LinphoneConnectionService.CS_TO_EXT_TERMINATE:
                return "CS_TO_EXT_TERMINATE";
            case LinphoneConnectionService.CS_TO_EXT_ABORT:
                return "CS_TO_EXT_ABORT";
            case LinphoneConnectionService.CS_TO_EXT_HOLD:
                return "CS_TO_EXT_HOLD";
            case LinphoneConnectionService.CS_TO_EXT_UNHOLD:
                return "CS_TO_EXT_UNHOLD";
        }
        return "Unknown: " + action;
    }
}
