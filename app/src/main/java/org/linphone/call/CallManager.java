package org.linphone.call;

/*
CallManager.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

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

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.widget.Toast;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
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
import org.linphone.mediastream.Version;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.FileUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.views.AddressType;

/** Handle call updating, reinvites. */
public class CallManager {
    private Context mContext;
    private boolean mHandsetON = false;
    private CallActivity.CallActivityInterface mCallInterface;
    private BandwidthManager mBandwidthManager;

    public CallManager(Context context) {
        mContext = context;
        mBandwidthManager = new BandwidthManager();
    }

    public void destroy() {
        mBandwidthManager.destroy();
    }

    public void inviteAddress(Address lAddress, boolean forceZRTP) {
        boolean isLowBandwidthConnection =
                !LinphoneUtils.isHighBandwidthConnection(
                        LinphoneService.instance().getApplicationContext());

        inviteAddress(lAddress, false, isLowBandwidthConnection, forceZRTP);
    }

    private void inviteAddress(
            Address lAddress, boolean videoEnabled, boolean lowBandwidth, boolean forceZRTP) {
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
                FileUtils.getCallRecordingFilename(LinphoneService.instance(), lAddress);
        params.setRecordFile(recordFile);

        core.inviteAddressWithParams(lAddress, params);
    }

    public void inviteAddress(Address lAddress, boolean videoEnabled, boolean lowBandwidth) {
        inviteAddress(lAddress, videoEnabled, lowBandwidth, false);
    }

    /**
     * Add video to a currently running voice only call. No re-invite is sent if the current call is
     * already video or if the bandwidth settings are too low.
     *
     * @return if updateCall called
     */
    public boolean reinviteWithVideo() {
        Core core = LinphoneManager.getCore();
        Call call = core.getCurrentCall();
        if (call == null) {
            Log.e("[Call Manager] Trying to reinviteWithVideo while not in call: doing nothing");
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

    /**
     * Change the preferred video size used by linphone core. (impact landscape/portrait buffer).
     * Update current call, without reinvite. The camera will be restarted when mediastreamer chain
     * is recreated and setParameters is called.
     */
    public void updateCall() {
        Core core = LinphoneManager.getCore();
        Call call = core.getCurrentCall();
        if (call == null) {
            Log.e("[Call Manager] Trying to updateCall while not in call: doing nothing");
            return;
        }
        call.update(null);
    }

    public void newOutgoingCall(AddressType address) {
        String to = address.getText().toString();
        newOutgoingCall(to, address.getDisplayedName());
    }

    public void newOutgoingCall(String to, String displayName) {
        //		if (mCore.inCall()) {
        //			listenerDispatcher.tryingNewOutgoingCallButAlreadyInCall();
        //			return;
        //		}
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
                        LinphoneService.instance().getApplicationContext());

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

    private void enableCamera(Call call, boolean enable) {
        if (call != null) {
            call.enableCamera(enable);
            if (mContext.getResources().getBoolean(R.bool.enable_call_notification))
                LinphoneService.instance()
                        .getNotificationManager()
                        .displayCallNotification(LinphoneManager.getCore().getCurrentCall());
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

    private void terminateCall() {
        Core core = LinphoneManager.getCore();
        if (core.inCall()) {
            core.getCurrentCall().terminate();
        }
    }

    /** @return false if already in video call. */
    public boolean addVideo() {
        Call call = LinphoneManager.getCore().getCurrentCall();
        enableCamera(call, true);
        return reinviteWithVideo();
    }

    public boolean acceptCall(Call call) {
        if (call == null) return false;

        Core core = LinphoneManager.getCore();
        CallParams params = core.createCallParams(call);

        boolean isLowBandwidthConnection =
                !LinphoneUtils.isHighBandwidthConnection(
                        LinphoneService.instance().getApplicationContext());

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

    public void setCallInterface(CallActivity.CallActivityInterface callInterface) {
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

    public void setHandsetMode(Boolean on) {
        if (mHandsetON == on) return;
        Core core = LinphoneManager.getCore();

        if (core.isIncomingInvitePending() && on) {
            mHandsetON = true;
            acceptCall(core.getCurrentCall());
        } else if (on && mCallInterface != null) {
            mHandsetON = true;
            mCallInterface.setSpeakerEnabled(true);
            mCallInterface.refreshInCallActions();
        } else if (!on) {
            mHandsetON = false;
            terminateCall();
        }
    }
}
