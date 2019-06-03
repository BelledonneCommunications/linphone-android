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

import static android.telecom.Connection.CAPABILITY_CAN_PAUSE_VIDEO;
import static android.telecom.Connection.CAPABILITY_CAN_UPGRADE_TO_VIDEO;
import static android.telecom.Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL;
import static android.telecom.Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL;
import static android.telecom.Connection.STATE_RINGING;

import android.annotation.TargetApi;
import android.content.ComponentName;
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
import android.telecom.VideoProfile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.linphone.LinphoneManager;
import org.linphone.core.Call;
import org.linphone.core.Core;
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
    private LinphoneToExternalTelecomBroadcastReceiver mBroadcastReceiver;

    public LinphoneConnectionService() {}

    @Override
    public void onCreate() {
        super.onCreate();

        mBroadcastReceiver = new LinphoneToExternalTelecomBroadcastReceiver(this);

        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(mBroadcastReceiver, new IntentFilter(EXT_TO_CS_BROADCAST));
    }

    public List<LinphoneConnection> getCalls() {
        return mCalls;
    }

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount, ConnectionRequest request) {
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());

        // Check PhoneAccount activation
        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            Log.i("[Telecom Manager] Creating outgoing connection");
            LinphoneConnection connection = new LinphoneConnection(this);
            Bundle extras = request.getExtras();
            String callId = extras.getString(EXT_TO_CS_CALL_ID);
            if (callId == null) {
                callId = LinphoneManager.getCore().getCurrentCall().getCallLog().getCallId();
            }
            connection.setCallId(callId);
            Log.i("[Telecom Manager] Call id is " + callId);

            Uri providedHandle = request.getAddress();
            connection.setAddress(providedHandle, TelecomManager.PRESENTATION_ALLOWED);
            Log.i("[Telecom Manager] Address is " + providedHandle.toString());

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

            mCalls.add(connection);
            connection.setConnectionCapabilities(getCallCapabilities(connection, mCalls.size()));

            return connection;
        } else {
            Log.e("[Telecom Manager] Error: " + accountHandle + " " + componentName);
            return Connection.createFailedConnection(
                    new DisconnectCause(
                            DisconnectCause.ERROR,
                            "Invalid inputs: " + accountHandle + " " + componentName));
        }
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerAccount, ConnectionRequest request) {
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(getApplicationContext(), this.getClass());

        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            Log.i("[Telecom Manager] Creating incoming connection");
            LinphoneConnection connection = new LinphoneConnection(this);
            Bundle extras = request.getExtras();
            String callId = extras.getString(EXT_TO_CS_CALL_ID);
            if (callId == null) {
                callId = LinphoneManager.getCore().getCurrentCall().getCallLog().getCallId();
            }
            connection.setCallId(callId);
            Log.i("[Telecom Manager] Call id is " + callId);

            Uri providedHandle =
                    Uri.parse(extras.getString(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS));
            connection.setAddress(providedHandle, TelecomManager.PRESENTATION_ALLOWED);
            Log.i("[Telecom Manager] Address is " + providedHandle.toString());

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

            mCalls.add(connection);
            connection.setConnectionCapabilities(getCallCapabilities(connection, mCalls.size()));

            return connection;
        } else {
            Log.e("[Telecom Manager] Error: " + accountHandle + " " + componentName);
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
                .unregisterReceiver(mBroadcastReceiver);
        return super.onUnbind(intent);
    }

    public void destroyCall(LinphoneConnection connection) {
        mCalls.remove(connection);
    }

    public void updateCallCapabilities() {
        for (LinphoneConnection connection : mCalls) {
            connection.setConnectionCapabilities(getCallCapabilities(connection, mCalls.size()));
        }
    }

    public void holdInActiveCalls(LinphoneConnection activeCall) {
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

    public void setAsActive(LinphoneConnection connection) {
        for (LinphoneConnection con : mCalls) {
            if (Objects.equals(con, connection) && con.getConference() == null) {
                con.setLocalActive(true);
                con.setActive();
            }
        }
    }

    public void performSwitchCall() {
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
    }

    private int getCallCapabilities(Connection connection, int totalCall) {
        int callCapabilities = connection.getConnectionCapabilities();

        Core core = LinphoneManager.getCore();
        if (core.videoEnabled()) {
            callCapabilities |= CAPABILITY_CAN_UPGRADE_TO_VIDEO;
            callCapabilities |= CAPABILITY_CAN_PAUSE_VIDEO;
        }

        Call call = null;
        Call[] calls = core.getCalls();
        LinphoneConnection linphoneConnection = (LinphoneConnection) connection;

        if (totalCall == 1 && calls.length > 0) {
            call = calls[0];
        } else if (totalCall <= calls.length) {
            for (Call tmpCall : calls) {
                if (tmpCall.getCallLog().getCallId().equals(linphoneConnection.getCallId())) {
                    call = tmpCall;
                    break;
                }
            }
        }

        if (call != null) {
            if (call.getCurrentParams().videoEnabled()) {
                if (connection.getVideoProvider() == null) {
                    Log.i("[Telecom Manager] Video is enabled, adding provider");
                    connection.setVideoProvider(new LinphoneVideoProvider());
                    connection.setVideoState(VideoProfile.STATE_BIDIRECTIONAL);
                } else {
                    Log.i("[Telecom Manager] Video is enabled, provider already set");
                }

                // TODO: the following isn't always true
                callCapabilities |= CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL;
                callCapabilities |= CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL;
            } else {
                // TODO: remove video capabilities
            }
        } else {
            Log.w(
                    "[Telecom Manager] Call with id "
                            + linphoneConnection.getCallId()
                            + " not found !");
        }

        // TODO: Conference capabilities

        return callCapabilities;
    }

    // Returns 1st inactive call
    private LinphoneConnection getInActive() {
        // Check for calls without conference, in the case we have an active Conference
        for (LinphoneConnection connection : mCalls) {
            if ((!connection.isLocalActive()) && connection.getConference() == null) {
                return connection;
            }
        }
        throw new NullPointerException("No inactive call found!");
    }
}
