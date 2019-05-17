package org.linphone.call.telecom;

/*
LinphoneConnectionService.java
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
import static android.telecom.Connection.STATE_RINGING;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.linphone.LinphoneManager;
import org.linphone.core.tools.Log;

// This class manages Connections, Conferences exclusively for the native mode

@TargetApi(Build.VERSION_CODES.M)
public class LinphoneConnectionService extends ConnectionService {
    // Broadcast (or extras for EXT_TO_CS_CALL_ID) values used to communicate from other components
    // to this ConnectionService implementation
    public static final String EXT_TO_CS_BROADCAST = "EXT_TO_CS_BROADCAST";
    public static final String EXT_TO_CS_ACTION = "EXT_TO_CS_ACTION";
    public static final String EXT_TO_CS_CALL_ID = "EXT_TO_CS_CALL_ID";
    public static final String EXT_TO_CS_HOLD_STATE = "EXT_TO_CS_HOLD_STATE";

    public static final int EXT_TO_CS_END_CALL = 1;
    public static final int EXT_TO_CS_HOLD_CALL = 2;
    public static final int EXT_TO_CS_ESTABLISHED = 3;

    // Broadcast (or extras for CS_TO_EXT_CALL_ID) values used to communicate from this
    // ConnectionService implementation to other components
    public static final String CS_TO_EXT_BROADCAST = "CS_TO_EXT_BROADCAST";
    public static final String CS_TO_EXT_CALL_ID = "CS_TO_EXT_CALL_ID";
    public static final String CS_TO_EXT_ACTION = "CS_TO_EXT_ACTION";

    public static final int CS_TO_EXT_END = 1; // remote hang up
    public static final int CS_TO_EXT_TERMINATE = 2; // local hang up
    public static final int CS_TO_EXT_ANSWER = 3; // answer call
    public static final int CS_TO_EXT_ABORT = 4; // abort call
    public static final int CS_TO_EXT_HOLD = 5; // hold call
    public static final int CS_TO_EXT_UNHOLD = 6; // unhold call

    private final List<LinphoneConnection> mCalls = new ArrayList<>();

    public LinphoneConnectionService() {}

    @Override
    public void onCreate() {
        super.onCreate();
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(mSipEventReceiver, new IntentFilter(EXT_TO_CS_BROADCAST));
    }

    // Receive broadcasts from sendToCS methods in TelecomManagerHelper class
    private BroadcastReceiver mSipEventReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int action = intent.getIntExtra(EXT_TO_CS_ACTION, -1);
                    LinphoneConnection connection = null;
                    String callId = intent.getStringExtra(EXT_TO_CS_CALL_ID);
                    for (LinphoneConnection con : mCalls) {
                        if (callId.equalsIgnoreCase(con.getCallId())) {
                            connection = con;
                            break;
                        }
                    }
                    if (connection == null) {
                        Log.w("[Telecom Manager] Connection not found for call id " + callId);
                        for (LinphoneConnection con : mCalls) {
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
                            connection.setDisconnected(
                                    new DisconnectCause(DisconnectCause.REJECTED));
                            destroyCall(connection);
                            connection.destroy();

                            updateCallCapabilities();
                            updateConferenceable();
                            break;

                        case EXT_TO_CS_HOLD_CALL:
                            boolean holdState = intent.getBooleanExtra(EXT_TO_CS_HOLD_STATE, false);
                            if (holdState) {
                                connection.setOnHold();
                            } else {
                                setAsActive(connection);
                            }
                            break;

                        case EXT_TO_CS_ESTABLISHED:
                            if (connection.getState() != STATE_HOLDING) {
                                setAsActive(connection);
                            }
                            break;
                    }
                }
            };

    final class LinphoneConnection extends Connection {
        private boolean mIsActive = false;
        private String mCallId;

        LinphoneConnection() {
            // FIXME: Don't assume all calls are video capable.
            int capabilities = getConnectionCapabilities();
            capabilities |= CAPABILITY_MUTE;
            capabilities |= CAPABILITY_SUPPORT_HOLD;
            capabilities |= CAPABILITY_HOLD;
            capabilities |= CAPABILITY_CAN_UPGRADE_TO_VIDEO;
            capabilities |= CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL;
            capabilities |= CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL;
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
            destroyCall(this);
            destroy();

            sendLocalBroadcast(CS_TO_EXT_ABORT);
        }

        @Override
        public void onAnswer(int videoState) {
            if (mCalls.size() > 1) {
                holdInActiveCalls(this);
            }
            setAsActive(this);
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
            if (mCalls.size() > 1) {
                performSwitchCall();
            } else {
                setOnHold();
                sendLocalBroadcast(CS_TO_EXT_HOLD);
            }
        }

        @Override
        public void onReject() {
            setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            destroyCall(this);
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
            updateCallCapabilities();
            updateConferenceable();
        }

        public void sendLocalBroadcast(int action) {
            Intent intent = new Intent(CS_TO_EXT_BROADCAST);
            intent.putExtra(CS_TO_EXT_ACTION, action);
            intent.putExtra(CS_TO_EXT_CALL_ID, getCallId());
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }
    }

    // Create LinphoneConnection class on placeCall method from TelecomManager
    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount, ConnectionRequest request) {

        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());

        // Check PhoneAccount activation
        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            LinphoneConnection connection = new LinphoneConnection();
            Bundle extras = request.getExtras();
            String callId = extras.getString(EXT_TO_CS_CALL_ID);
            if (callId == null) {
                callId = LinphoneManager.getCore().getCurrentCall().getCallLog().getCallId();
            }
            connection.setCallId(callId);
            Log.i("[Telecom Manager] Outgoing call id is " + callId);

            Uri providedHandle = request.getAddress();
            connection.setAddress(providedHandle, TelecomManager.PRESENTATION_ALLOWED);

            if (extras != null) {
                // Caller display name not used for outgoing calls,
                // at least on some dialers so keep it just in case...
                String callerName =
                        extras.getString(TelecomHelper.LINPHONE_TELECOM_EXTRA_CONTACT_NAME);
                connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED);
                Log.i("[Telecom Manager] Using display name " + callerName);

                String appName = extras.getString(TelecomHelper.LINPHONE_TELECOM_EXTRA_APP_NAME);
                Icon appIcon = extras.getParcelable(TelecomHelper.LINPHONE_TELECOM_EXTRA_APP_ICON);
                StatusHints statusHints = new StatusHints(appName, appIcon, new Bundle());
                connection.setStatusHints(statusHints);
            }

            connection.setAudioModeIsVoip(true);
            connection.setDialing();
            addCall(connection);

            return connection;
        } else {
            return Connection.createFailedConnection(
                    new DisconnectCause(
                            DisconnectCause.ERROR,
                            "Invalid inputs: " + accountHandle + " " + componentName));
        }
    }

    // Create LinphoneConnection class on addincomingcall method from TelecomManager
    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerAccount, ConnectionRequest request) {
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());

        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            LinphoneConnection connection = new LinphoneConnection();
            Bundle extras = request.getExtras();
            String callId = extras.getString(EXT_TO_CS_CALL_ID);
            connection.setCallId(callId);
            Log.i("[Telecom Manager] Incoming call id is " + callId);

            Uri providedHandle =
                    Uri.parse(extras.getString(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS));
            connection.setAddress(providedHandle, TelecomManager.PRESENTATION_ALLOWED);

            Bundle b = extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
            if (b != null) {
                String callerName = b.getString(TelecomHelper.LINPHONE_TELECOM_EXTRA_CONTACT_NAME);
                connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED);
                Log.i("[Telecom Manager] Using display name " + callerName);

                String appName = b.getString(TelecomHelper.LINPHONE_TELECOM_EXTRA_APP_NAME);
                Icon appIcon = b.getParcelable(TelecomHelper.LINPHONE_TELECOM_EXTRA_APP_ICON);
                StatusHints statusHints = new StatusHints(appName, appIcon, new Bundle());
                connection.setStatusHints(statusHints);
            }

            connection.setAudioModeIsVoip(true);
            connection.setRinging();
            addCall(connection);

            return connection;
        } else {
            return Connection.createFailedConnection(
                    new DisconnectCause(
                            DisconnectCause.ERROR,
                            "Invalid inputs: " + accountHandle + " " + componentName));
        }
    }

    @Override
    public void onCreateOutgoingConnectionFailed(
            PhoneAccountHandle connectionManagerAccount, ConnectionRequest request) {}

    @Override
    public void onConference(Connection a, Connection b) {}

    @Override
    public void onRemoteConferenceAdded(RemoteConference remoteConference) {}

    @Override
    public boolean onUnbind(Intent intent) {
        LocalBroadcastManager.getInstance(getApplicationContext())
                .unregisterReceiver(mSipEventReceiver);
        return super.onUnbind(intent);
    }

    private void addCall(LinphoneConnection connection) {
        mCalls.add(connection);
        updateCallCapabilities();
        updateConferenceable();
    }

    private void destroyCall(LinphoneConnection connection) {
        mCalls.remove(connection);
        updateCallCapabilities();
        updateConferenceable();
    }

    private void updateConferenceable() {
        List<Connection> freeConnections = new ArrayList<>();
        freeConnections.addAll(mCalls);
        for (int i = 0; i < freeConnections.size(); i++) {
            if (freeConnections.get(i).getConference() != null) {
                freeConnections.remove(i);
            }
        }
        for (int i = 0; i < freeConnections.size(); i++) {
            Connection c = freeConnections.remove(i);
            c.setConferenceableConnections(freeConnections);
            freeConnections.add(i, c);
        }
    }

    private void updateCallCapabilities() {
        for (LinphoneConnection connection : mCalls) {
            connection.setConnectionCapabilities(getCallCapabilities(connection, mCalls.size()));
        }
    }

    private int getCallCapabilities(Connection connection, int totalCall) {
        int callCapabilities = 0;
        callCapabilities |= Connection.CAPABILITY_MUTE;
        callCapabilities |= Connection.CAPABILITY_SUPPORT_HOLD;

        // hold capability for only single call
        if (totalCall == 1) {
            if (connection.getState() == Connection.STATE_ACTIVE
                    || connection.getState() == STATE_HOLDING) {
                callCapabilities |= Connection.CAPABILITY_HOLD;
            }
        }

        if (totalCall > 1) {
            callCapabilities |= Connection.CAPABILITY_MERGE_CONFERENCE;
            callCapabilities |= Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE;
            callCapabilities |= Connection.CAPABILITY_SWAP_CONFERENCE;
            callCapabilities |= Connection.CAPABILITY_MANAGE_CONFERENCE;
        }

        return callCapabilities;
    }

    private void holdInActiveCalls(LinphoneConnection activeCall) {
        for (LinphoneConnection con : mCalls) {
            if (!Objects.equals(con, activeCall)) {
                if (con.getConference() == null) {
                    con.setOnHold();
                    con.setLocalActive(false);
                    con.sendLocalBroadcast(CS_TO_EXT_HOLD);
                }
            }
        }
    }

    private void setAsActive(LinphoneConnection connection) {
        for (LinphoneConnection con : mCalls) {
            if (Objects.equals(con, connection) && con.getConference() == null) {
                con.setLocalActive(true);
                con.setActive();
            }
        }
    }

    // Returns 1st inactive call
    private LinphoneConnection getInActive() {
        // Check for calls without conference, in the case we have an active Conference
        for (LinphoneConnection con : mCalls) {
            if ((!con.isLocalActive()) && con.getConference() == null) {
                return con;
            }
        }
        throw new NullPointerException("No inactive call found!");
    }

    private void performSwitchCall() {
        LinphoneConnection futureActive = getInActive();

        // unhold inactive call

        // Send the unhold action to TelecomManagerHelper
        if (futureActive.getConference() == null && futureActive.getState() == STATE_RINGING) {
            futureActive.sendLocalBroadcast(CS_TO_EXT_ANSWER);
        } else if (futureActive.getConference() == null) {
            futureActive.sendLocalBroadcast(CS_TO_EXT_UNHOLD);
        }

        holdInActiveCalls(futureActive);
        setAsActive(futureActive);

        updateCallCapabilities();
        updateConferenceable();
    }
}
