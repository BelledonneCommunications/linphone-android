/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
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
package org.linphone.call;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.widget.Toast;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallParams;
import org.linphone.core.Core;
import org.linphone.core.MediaEncryption;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.dialer.views.AddressType;
import org.linphone.mediastream.Version;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.FileUtils;
import org.linphone.utils.LinphoneUtils;

/** Handle call updating, reinvites. */
public class CallManager {
    private Context mContext;
    private CallActivityInterface mCallInterface;
    private BandwidthManager mBandwidthManager;

    public CallManager(Context context) {
        mContext = context;
        mBandwidthManager = new BandwidthManager();
    }

    public void destroy() {
        mBandwidthManager.destroy();
    }

    public void terminateCurrentCallOrConferenceOrAll() {
        Core core = LinphoneManager.getCore();
        Call call = core.getCurrentCall();
        if (call != null) {
            call.terminate();
        } else if (core.isInConference()) {
            core.terminateConference();
        } else {
            core.terminateAllCalls();
        }
    }

    public void addVideo() {
        Call call = LinphoneManager.getCore().getCurrentCall();
        if (call.getState() == Call.State.End || call.getState() == Call.State.Released) return;
        if (!call.getCurrentParams().videoEnabled()) {
            enableCamera(call, true);
            reinviteWithVideo();
        }
    }

    public void removeVideo() {
        Core core = LinphoneManager.getCore();
        Call call = core.getCurrentCall();
        CallParams params = core.createCallParams(call);
        params.enableVideo(false);
        call.update(params);
    }

    public void switchCamera() {
        Core core = LinphoneManager.getCore();
        if (core == null) return;

        String currentDevice = core.getVideoDevice();
        Log.i("[Call Manager] Current camera device is " + currentDevice);

        String[] devices = core.getVideoDevicesList();
        for (String d : devices) {
            if (!d.equals(currentDevice) && !d.equals("StaticImage: Static picture")) {
                Log.i("[Call Manager] New camera device will be " + d);
                core.setVideoDevice(d);
                break;
            }
        }

        Call call = core.getCurrentCall();
        if (call == null) {
            Log.i("[Call Manager] Switching camera while not in call");
            return;
        }
        call.update(null);
    }

    public boolean acceptCall(Call call) {
        if (call == null) return false;

        Core core = LinphoneManager.getCore();
        CallParams params = core.createCallParams(call);

        boolean isLowBandwidthConnection =
                !LinphoneUtils.isHighBandwidthConnection(
                        LinphoneContext.instance().getApplicationContext());

        if (params != null) {
            params.enableLowBandwidth(isLowBandwidthConnection);
            params.setRecordFile(
                    FileUtils.getCallRecordingFilename(mContext, call.getRemoteAddress()));
        } else {
            Log.e("[Call Manager] Could not create call params for call");
            return false;
        }

        call.acceptWithParams(params);
        return true;
    }

    public void acceptCallUpdate(boolean accept) {
        Core core = LinphoneManager.getCore();
        Call call = core.getCurrentCall();
        if (call == null) {
            return;
        }

        CallParams params = core.createCallParams(call);
        if (accept) {
            params.enableVideo(true);
            core.enableVideoCapture(true);
            core.enableVideoDisplay(true);
        }

        call.acceptUpdate(params);
    }

    public void inviteAddress(Address address, boolean forceZRTP) {
        boolean isLowBandwidthConnection =
                !LinphoneUtils.isHighBandwidthConnection(
                        LinphoneContext.instance().getApplicationContext());

        inviteAddress(address, false, isLowBandwidthConnection, forceZRTP);
    }

    public void inviteAddress(Address address, boolean videoEnabled, boolean lowBandwidth) {
        inviteAddress(address, videoEnabled, lowBandwidth, false);
    }

    public void newOutgoingCall(AddressType address) {
        String to = address.getText().toString();
        newOutgoingCall(to, address.getDisplayedName());
    }

    public void newOutgoingCall(String to, String displayName) {
        if (to == null) return;

        // If to is only a username, try to find the contact to get an alias if existing
        if (!to.startsWith("sip:") || !to.contains("@")) {
            LinphoneContact contact = ContactsManager.getInstance().findContactFromPhoneNumber(to);
            if (contact != null) {
                String alias = contact.getContactFromPresenceModelForUriOrTel(to);
                if (alias != null) {
                    to = alias;
                }
            }
        }

        LinphonePreferences preferences = LinphonePreferences.instance();
        Core core = LinphoneManager.getCore();
        Address address;
        address = core.interpretUrl(to); // InterpretUrl does normalizePhoneNumber
        if (address == null) {
            Log.e("[Call Manager] Couldn't convert to String to Address : " + to);
            return;
        }

        ProxyConfig lpc = core.getDefaultProxyConfig();
        if (mContext.getResources().getBoolean(R.bool.forbid_self_call)
                && lpc != null
                && address.weakEqual(lpc.getIdentityAddress())) {
            return;
        }
        address.setDisplayName(displayName);

        boolean isLowBandwidthConnection =
                !LinphoneUtils.isHighBandwidthConnection(
                        LinphoneContext.instance().getApplicationContext());

        if (core.isNetworkReachable()) {
            if (Version.isVideoCapable()) {
                boolean prefVideoEnable = preferences.isVideoEnabled();
                boolean prefInitiateWithVideo = preferences.shouldInitiateVideoCall();
                inviteAddress(
                        address,
                        prefVideoEnable && prefInitiateWithVideo,
                        isLowBandwidthConnection);
            } else {
                inviteAddress(address, false, isLowBandwidthConnection);
            }
        } else {
            Toast.makeText(
                            mContext,
                            mContext.getString(R.string.error_network_unreachable),
                            Toast.LENGTH_LONG)
                    .show();
            Log.e(
                    "[Call Manager] Error: "
                            + mContext.getString(R.string.error_network_unreachable));
        }
    }

    public void playDtmf(ContentResolver r, char dtmf) {
        try {
            if (Settings.System.getInt(r, Settings.System.DTMF_TONE_WHEN_DIALING) == 0) {
                // audible touch disabled: don't play on speaker, only send in outgoing stream
                return;
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e("[Call Manager] playDtmf exception: " + e);
        }

        LinphoneManager.getCore().playDtmf(dtmf, -1);
    }

    public boolean shouldShowAcceptCallUpdateDialog(Call call) {
        if (call == null) return true;

        boolean remoteVideo = call.getRemoteParams().videoEnabled();
        boolean localVideo = call.getCurrentParams().videoEnabled();
        boolean autoAcceptCameraPolicy =
                LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
        return remoteVideo
                && !localVideo
                && !autoAcceptCameraPolicy
                && !call.getCore().isInConference();
    }

    public void setCallInterface(CallActivityInterface callInterface) {
        mCallInterface = callInterface;
    }

    public void resetCallControlsHidingTimer() {
        if (mCallInterface != null) {
            mCallInterface.resetCallControlsHidingTimer();
        }
    }

    public void refreshInCallActions() {
        if (mCallInterface != null) {
            mCallInterface.refreshInCallActions();
        }
    }

    public void removeCallFromConference(Call call) {
        if (call == null || call.getConference() == null) {
            return;
        }
        call.getConference().removeParticipant(call.getRemoteAddress());

        if (call.getCore().getConferenceSize() <= 1) {
            call.getCore().leaveConference();
        }
    }

    public void pauseConference() {
        Core core = LinphoneManager.getCore();
        if (core == null) return;
        if (core.isInConference()) {
            Log.i("[Call Manager] Pausing conference");
            core.leaveConference();
        } else {
            Log.w("[Call Manager] Core isn't in a conference, can't pause it");
        }
    }

    public void resumeConference() {
        Core core = LinphoneManager.getCore();
        if (core == null) return;
        if (!core.isInConference()) {
            Log.i("[Call Manager] Resuming conference");
            core.enterConference();
        } else {
            Log.w("[Call Manager] Core is already in a conference, can't resume it");
        }
    }

    private void inviteAddress(
            Address address, boolean videoEnabled, boolean lowBandwidth, boolean forceZRTP) {
        Core core = LinphoneManager.getCore();

        CallParams params = core.createCallParams(null);
        mBandwidthManager.updateWithProfileSettings(params);

        if (videoEnabled && params.videoEnabled()) {
            params.enableVideo(true);
        } else {
            params.enableVideo(false);
        }

        if (lowBandwidth) {
            params.enableLowBandwidth(true);
            Log.d("[Call Manager] Low bandwidth enabled in call params");
        }

        if (forceZRTP) {
            params.setMediaEncryption(MediaEncryption.ZRTP);
        }

        String recordFile =
                FileUtils.getCallRecordingFilename(
                        LinphoneContext.instance().getApplicationContext(), address);
        params.setRecordFile(recordFile);

        core.inviteAddressWithParams(address, params);
    }

    private boolean reinviteWithVideo() {
        Core core = LinphoneManager.getCore();
        Call call = core.getCurrentCall();
        if (call == null) {
            Log.e("[Call Manager] Trying to add video while not in call");
            return false;
        }
        if (call.getRemoteParams().lowBandwidthEnabled()) {
            Log.e("[Call Manager] Remote has low bandwidth, won't be able to do video");
            return false;
        }

        CallParams params = core.createCallParams(call);
        if (params.videoEnabled()) return false;

        // Check if video possible regarding bandwidth limitations
        mBandwidthManager.updateWithProfileSettings(params);

        // Abort if not enough bandwidth...
        if (!params.videoEnabled()) {
            return false;
        }

        // Not yet in video call: try to re-invite with video
        call.update(params);
        return true;
    }

    private void enableCamera(Call call, boolean enable) {
        if (call != null) {
            call.enableCamera(enable);
            if (mContext.getResources().getBoolean(R.bool.enable_call_notification))
                LinphoneContext.instance()
                        .getNotificationManager()
                        .displayCallNotification(LinphoneManager.getCore().getCurrentCall());
        }
    }
}
