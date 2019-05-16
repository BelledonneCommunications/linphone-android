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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.TelecomManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.linphone.LinphoneManager;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
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
    public static final int EXT_TO_CS_HOLD_CONFERENCE = 4;

    // Broadcast (or extras for CS_TO_EXT_CALL_ID) values used to communicate from this
    // ConnectionService implementation to other components
    public static final String CS_TO_EXT_BROADCAST = "CS_TO_EXT_BROADCAST";
    public static final String CS_TO_EXT_CALL_ID = "CS_TO_EXT_CALL_ID";
    public static final String CS_TO_EXT_ACTION = "CS_TO_EXT_ACTION";
    public static final String CS_TO_EXT_IS_CONFERENCE =
            "CS_TO_EXT_IS_CONFERENCE"; // true if conference

    public static final int CS_TO_EXT_END = 1; // remote hang up
    public static final int CS_TO_EXT_TERMINATE = 2; // local hang up
    public static final int CS_TO_EXT_ANSWER = 3; // answer call
    public static final int CS_TO_EXT_ABORT = 4; // abort call
    public static final int CS_TO_EXT_HOLD = 5; // hold call
    public static final int CS_TO_EXT_UNHOLD = 6; // unhold call
    public static final int CS_TO_EXT_ADD_TO_CONF = 7; // add call to conference
    public static final int CS_TO_EXT_REMOVE_FROM_CONF = 8; // remove call from conference

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
                    if (action != EXT_TO_CS_HOLD_CONFERENCE) {
                        String callId = intent.getStringExtra(EXT_TO_CS_CALL_ID);
                        for (LinphoneConnection con : mCalls) {
                            if (callId.equalsIgnoreCase(con.getCallId())) {
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
                            // Check if the connection is part of a conference and removes from it
                            // if it's the case
                            if (connection.getConference() != null && mCallConference != null) {
                                mCallConference.removeConnection(connection);
                                checkConference();
                            }
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

                        case EXT_TO_CS_HOLD_CONFERENCE:
                            holdState = intent.getBooleanExtra(EXT_TO_CS_HOLD_STATE, false);
                            if (holdState) {
                                getConference().setOnHold();
                                getConference().setLocalActive(false);
                            } else {
                                getConference().setLocalActive(true);
                                getConference().setActive();
                            }
                            break;
                    }
                }
            };

    // Check if conference must be deleted
    private void checkConference() {
        if (mCallConference != null && mCallConference.getConnections().size() < 2) {
            List<Connection> listConnection = mCallConference.getConnections();
            for (Connection con : listConnection) {
                if (con == listConnection.get(0)) {
                    mCallConference.removeConnection(con);
                    LinphoneConnection call = (LinphoneConnection) con;
                    ((LinphoneConnection) con).sendLocalBroadcast(CS_TO_EXT_REMOVE_FROM_CONF);
                    setAsActive(call);

                } else {
                    mCallConference.removeConnection(con);
                    LinphoneConnection call = (LinphoneConnection) con;
                    ((LinphoneConnection) con).sendLocalBroadcast(CS_TO_EXT_REMOVE_FROM_CONF);
                    call.setOnHold();
                    call.sendLocalBroadcast(CS_TO_EXT_HOLD);
                }
            }
            mCallConference.destroy();
            mCallConference = null;

        } else {
            mCallConference.setActive();
            mCallConference.setLocalActive(true);
        }
    }

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
            capabilities |= CAPABILITY_DISCONNECT_FROM_CONFERENCE;
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
            if (c == '1') {
                setDialing();
            }
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

    // Conference part

    private final class LinphoneConference extends Conference {

        public LinphoneConference(LinphoneConnection a, LinphoneConnection b) {
            super(mPhoneAccountHandle);
            updateConnectionCapabilities();
            setActive();

            addConnection(a);
            addConnection(b);
            setLocalActive(true);
            a.sendLocalBroadcast(CS_TO_EXT_ADD_TO_CONF);
            b.sendLocalBroadcast(CS_TO_EXT_ADD_TO_CONF);
        }

        public void setLocalActive(boolean isActive) {
            mIsActive = isActive;
            // Only the conference is considered active, not connections in it
            for (Connection con : this.getConnections()) {
                LinphoneConnection call = (LinphoneConnection) con;
                call.setLocalActive(false);
            }
        }

        public boolean isLocalActive() {
            return mIsActive;
        }

        @Override
        public void onDisconnect() {
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            for (Connection c : getConnections()) {
                LinphoneConnection call = (LinphoneConnection) c;
                call.onDisconnect();
            }
            mCallConference = null;
        }

        @Override
        public void onSeparate(Connection connection) {
            for (Connection c : getConnections()) {
                LinphoneConnection call = (LinphoneConnection) c;

                if (call.equals(connection)) {

                    // create the connection active
                    mCallConference.removeConnection(call);
                    call.sendLocalBroadcast(CS_TO_EXT_REMOVE_FROM_CONF);

                    // Particular case, when Conference will be deleted, the other call must be hold
                    call.setOnHold();
                    call.sendLocalBroadcast(CS_TO_EXT_HOLD);
                }
            }

            // Set conference on Hold if 2 or more people remains
            // destroy conference if not
            checkConference();

            updateConnectionCapabilities();
            updateCallCapabilities();
            updateConferenceable();
        }

        @Override
        public void onHold() {
            // All calls are in conference, no other call to unpause after hold
            if (mCallConference.getConnections().size() == mCalls.size()) {
                mCallConference.setLocalActive(false);
                mCallConference.setOnHold();
                mCallConference.sendLocalBroadcast(CS_TO_EXT_HOLD);
            } else {
                performSwitchCall();
            }
        }

        @Override
        public void onUnhold() {
            sendLocalBroadcast(CS_TO_EXT_UNHOLD);
            setLocalActive(true);
            setActive();
        }

        @Override
        public void onMerge(Connection connection) {}

        @Override
        public void onMerge() {}

        @Override
        public void onSwap() {}

        @Override
        public void onConnectionAdded(Connection connection) {}

        @Override
        public void onCallAudioStateChanged(CallAudioState state) {
            updateConnectionCapabilities();
        }

        // Method to communicate with TelecomManagerHelper about conference
        private void sendLocalBroadcast(int action) {
            Intent intent = new Intent(CS_TO_EXT_BROADCAST);
            intent.putExtra(CS_TO_EXT_ACTION, action);
            intent.putExtra(CS_TO_EXT_IS_CONFERENCE, true);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }

        // utils functions
        protected final void updateConnectionCapabilities() {
            int newCapabilities = buildConnectionCapabilities();
            newCapabilities = applyConferenceTerminationCapabilities(newCapabilities);
            if (getConnectionCapabilities() != newCapabilities) {
                setConnectionCapabilities(newCapabilities);
            }
        }

        /**
         * Builds call capabilities common to all TelephonyConnections. Namely, apply IMS-based
         * capabilities.
         */
        protected int buildConnectionCapabilities() {
            int callCapabilities = 0;
            callCapabilities |= Connection.CAPABILITY_MUTE;
            callCapabilities |= Connection.CAPABILITY_SUPPORT_HOLD;
            if (getState() == Connection.STATE_ACTIVE || getState() == STATE_HOLDING) {
                callCapabilities |= Connection.CAPABILITY_HOLD;
            }

            return callCapabilities;
        }

        private int applyConferenceTerminationCapabilities(int capabilities) {
            int currentCapabilities = capabilities;
            currentCapabilities |= Connection.CAPABILITY_MANAGE_CONFERENCE;
            return currentCapabilities;
        }
    }

    // End conference part

    // Create LinphoneConnection class on placecall method from TelecomManager
    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount, final ConnectionRequest request) {

        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());

        // Check PhoneAccount activation
        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            Uri providedHandle = request.getAddress();
            String callerName = null;
            final LinphoneConnection connection = new LinphoneConnection();

            Address address = LinphoneManager.getCore().getCurrentCall().getRemoteAddress();
            LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
            if (contact != null) {
                callerName = contact.getFullName();
            }
            if (callerName != null) {
                connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED);
            }
            setAddress(connection, providedHandle);
            String callId = LinphoneManager.getCore().getCurrentCall().getCallLog().getCallId();
            connection.setCallId(callId);
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

    // Never occured during experimentation, still here for future work, information
    @Override
    public void onCreateOutgoingConnectionFailed(
            PhoneAccountHandle connectionManagerAccount, final ConnectionRequest request) {

        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());

        Bundle extras = request.getExtras();
        String gatewayPackage = extras.getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE);
        Uri originalHandle = extras.getParcelable(TelecomManager.GATEWAY_ORIGINAL_ADDRESS);
        Log.i(
                "[Telecom Manager] package is "
                        + gatewayPackage
                        + ", original address "
                        + originalHandle);

        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            final LinphoneConnection connection = new LinphoneConnection();
            // Get the stashed intent extra that determines if this is a video sipAudioCall or audio
            // sipAudioCall.
            Uri providedHandle = request.getAddress();
            setAddress(connection, providedHandle);
            connection.setAudioModeIsVoip(true);
            connection.setRinging();
            addCall(connection);
        }
    }

    // Create LinphoneConnection class on addincomingcall method from TelecomManager
    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerAccount, final ConnectionRequest request) {
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());

        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            mPhoneAccountHandle = accountHandle;
            final LinphoneConnection connection = new LinphoneConnection();
            Bundle extras = request.getExtras();
            String callId = extras.getString(EXT_TO_CS_CALL_ID);
            connection.setCallId(callId);

            Uri providedHandle =
                    Uri.parse(extras.getString(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS));

            Bundle b = extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
            if (b != null) {
                String callerName = b.getString(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
                connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED);
            }

            setAddress(connection, providedHandle);
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
    public void onConference(Connection a, Connection b) {
        if (mCallConference == null) {
            mCallConference =
                    new LinphoneConference((LinphoneConnection) a, (LinphoneConnection) b);
            addConference(mCallConference);

        } else {
            LinphoneConnection call = (LinphoneConnection) a;
            mCallConference.addConnection(call);
            mCallConference.updateConnectionCapabilities();

            call.sendLocalBroadcast(CS_TO_EXT_ADD_TO_CONF);
            mCallConference.setActive();
        }
    }

    @Override
    public void onRemoteConferenceAdded(RemoteConference remoteConference) {}

    private PhoneAccountHandle mPhoneAccountHandle = null;
    private final List<LinphoneConnection> mCalls = new ArrayList<>();
    private LinphoneConference mCallConference = null;
    private boolean mIsActive;

    @Override
    public boolean onUnbind(Intent intent) {
        LocalBroadcastManager.getInstance(getApplicationContext())
                .unregisterReceiver(mSipEventReceiver);
        return super.onUnbind(intent);
    }

    public LinphoneConference getConference() {
        return mCallConference;
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
                } else if (((LinphoneConference) con.getConference()).isLocalActive()) {
                    mCallConference = (LinphoneConference) con.getConference();
                    mCallConference.setLocalActive(false);
                    mCallConference.setOnHold();
                    mCallConference.sendLocalBroadcast(CS_TO_EXT_HOLD);
                }
            }
        }
    }

    private void setAsActive(LinphoneConnection connection) {
        for (LinphoneConnection con : mCalls) {
            if (Objects.equals(con, connection) && con.getConference() == null) {
                con.setLocalActive(true);
                con.setActive();
            } else if (Objects.equals(con, connection) && con.getConference() != null) {
                ((LinphoneConference) con.getConference()).setLocalActive(true);
                con.getConference().setActive();
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

        // Check if a conference exists in hold call
        for (LinphoneConnection con : mCalls) {
            if ((con.getConference() != null)
                    && !((LinphoneConference) con.getConference()).isLocalActive()) {
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
        } else {
            mCallConference.sendLocalBroadcast(CS_TO_EXT_UNHOLD);
        }

        holdInActiveCalls(futureActive);
        setAsActive(futureActive);

        updateCallCapabilities();
        updateConferenceable();
    }

    private void setAddress(Connection connection, Uri address) {
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
    }
}
