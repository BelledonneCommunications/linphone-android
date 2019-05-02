package org.linphone.utils;

/*
AndroidAudioManager.java
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

import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import java.io.FileInputStream;
import java.io.IOException;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.tools.Log;
import org.linphone.receivers.BluetoothManager;
import org.linphone.settings.LinphonePreferences;

public class AndroidAudioManager {
    private static final int LINPHONE_VOLUME_STREAM = STREAM_VOICE_CALL;

    private Context mContext;
    private AudioManager mAudioManager;
    private Call mRingingCall;
    private MediaPlayer mRingerPlayer;
    private final Vibrator mVibrator;

    private boolean mIsRinging;
    private boolean mAudioFocused;
    private boolean mEchoTesterIsRunning;

    private CoreListenerStub mListener;

    public AndroidAudioManager(Context context) {
        mContext = context;
        mAudioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mEchoTesterIsRunning = false;

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            final Core core,
                            final Call call,
                            final Call.State state,
                            final String message) {
                        if (state == Call.State.IncomingReceived
                                || (state == Call.State.IncomingEarlyMedia
                                        && mContext.getResources()
                                                .getBoolean(
                                                        R.bool.allow_ringing_while_early_media))) {
                            // Brighten screen for at least 10 seconds
                            if (core.getCallsNb() == 1) {
                                requestAudioFocus(STREAM_RING);

                                mRingingCall = call;
                                startRinging(call.getRemoteAddress());
                                // otherwise there is the beep
                            }
                        } else if (call == mRingingCall && mIsRinging) {
                            // previous state was ringing, so stop ringing
                            stopRinging();
                        }

                        if (state == Call.State.Connected) {
                            if (core.getCallsNb() == 1) {
                                // It is for incoming calls, because outgoing calls enter
                                // MODE_IN_COMMUNICATION immediately when they start.
                                // However, incoming call first use the MODE_RINGING to play the
                                // local ring.
                                if (call.getDir() == Call.Dir.Incoming) {
                                    setAudioManagerInCallMode();
                                    // mAudioManager.abandonAudioFocus(null);
                                    requestAudioFocus(STREAM_VOICE_CALL);
                                }
                            }
                        } else if (state == Call.State.End || state == Call.State.Error) {
                            if (core.getCallsNb() == 0) {
                                if (mAudioFocused) {
                                    int res = mAudioManager.abandonAudioFocus(null);
                                    Log.d(
                                            "[Audio Manager] Audio focus released a bit later: "
                                                    + (res
                                                                    == AudioManager
                                                                            .AUDIOFOCUS_REQUEST_GRANTED
                                                            ? "Granted"
                                                            : "Denied"));
                                    mAudioFocused = false;
                                }

                                TelephonyManager tm =
                                        (TelephonyManager)
                                                mContext.getSystemService(
                                                        Context.TELEPHONY_SERVICE);
                                if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                                    Log.d(
                                            "[Audio Manager] ---AndroidAudioManager: back to MODE_NORMAL");
                                    mAudioManager.setMode(AudioManager.MODE_NORMAL);
                                    Log.d(
                                            "[Audio Manager] All call terminated, routing back to earpiece");
                                    routeAudioToEarPiece();
                                }
                            }
                        }
                        if (state == Call.State.OutgoingInit) {
                            // Enter the MODE_IN_COMMUNICATION mode as soon as possible, so that
                            // ringback is heard normally in earpiece or bluetooth receiver.
                            setAudioManagerInCallMode();
                            requestAudioFocus(STREAM_VOICE_CALL);
                            startBluetooth();
                        }

                        if (state == Call.State.StreamsRunning) {
                            startBluetooth();
                            setAudioManagerInCallMode();
                        }
                    }

                    @Override
                    public void onEcCalibrationResult(
                            Core core, EcCalibratorStatus status, int delay_ms) {
                        mAudioManager.setMode(AudioManager.MODE_NORMAL);
                        mAudioManager.abandonAudioFocus(null);
                        Log.i("[Audio Manager] Set audio mode on 'Normal'");
                    }
                };

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
        }
    }

    public void destroy() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }
    }

    /* Audio routing */

    public void setAudioManagerModeNormal() {
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
    }

    public void routeAudioToEarPiece() {
        routeAudioToSpeakerHelper(false);
    }

    public void routeAudioToSpeaker() {
        routeAudioToSpeakerHelper(true);
    }

    public boolean isAudioRoutedToSpeaker() {
        return mAudioManager.isSpeakerphoneOn();
    }

    /* Echo cancellation */

    public void startEcCalibration() {
        Core core = LinphoneManager.getCore();
        if (core == null) {
            return;
        }

        routeAudioToSpeaker();
        setAudioManagerInCallMode();
        Log.i("[Audio Manager] Set audio mode on 'Voice Communication'");
        requestAudioFocus(STREAM_VOICE_CALL);
        int oldVolume = mAudioManager.getStreamVolume(STREAM_VOICE_CALL);
        int maxVolume = mAudioManager.getStreamMaxVolume(STREAM_VOICE_CALL);
        mAudioManager.setStreamVolume(STREAM_VOICE_CALL, maxVolume, 0);
        core.startEchoCancellerCalibration();
        mAudioManager.setStreamVolume(STREAM_VOICE_CALL, oldVolume, 0);
    }

    public void startEchoTester() {
        Core core = LinphoneManager.getCore();
        if (core == null) {
            return;
        }

        routeAudioToSpeaker();
        setAudioManagerInCallMode();
        Log.i("[Audio Manager] Set audio mode on 'Voice Communication'");
        requestAudioFocus(STREAM_VOICE_CALL);
        int maxVolume = mAudioManager.getStreamMaxVolume(STREAM_VOICE_CALL);
        int sampleRate;
        mAudioManager.setStreamVolume(STREAM_VOICE_CALL, maxVolume, 0);
        String sampleRateProperty =
                mAudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        sampleRate = Integer.parseInt(sampleRateProperty);
        core.startEchoTester(sampleRate);
        mEchoTesterIsRunning = true;
    }

    public void stopEchoTester() {
        Core core = LinphoneManager.getCore();
        if (core == null) {
            return;
        }

        mEchoTesterIsRunning = false;
        core.stopEchoTester();
        routeAudioToEarPiece();
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
        Log.i("[Audio Manager] Set audio mode on 'Normal'");
    }

    public boolean getEchoTesterStatus() {
        return mEchoTesterIsRunning;
    }

    public boolean onKeyVolumeAdjust(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            adjustVolume(1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            adjustVolume(-1);
            return true;
        }
        return false;
    }

    private void setAudioManagerInCallMode() {
        if (mAudioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
            Log.w("[Audio Manager] already in MODE_IN_COMMUNICATION, skipping...");
            return;
        }
        Log.d("[Audio Manager] Mode: MODE_IN_COMMUNICATION");

        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    private void startBluetooth() {
        if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            BluetoothManager.getInstance().routeAudioToBluetooth();
        }
    }

    private void requestAudioFocus(int stream) {
        if (!mAudioFocused) {
            int res =
                    mAudioManager.requestAudioFocus(
                            null, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
            Log.d(
                    "[Audio Manager] Audio focus requested: "
                            + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                                    ? "Granted"
                                    : "Denied"));
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) mAudioFocused = true;
        }
    }

    private synchronized void startRinging(Address remoteAddress) {
        if (!LinphonePreferences.instance().isDeviceRingtoneEnabled()) {
            // Enable speaker audio route, linphone library will do the ringing itself automatically
            routeAudioToSpeaker();
            return;
        }

        boolean doNotDisturbPolicyAllowsRinging =
                Compatibility.isDoNotDisturbPolicyAllowingRinging(mContext, remoteAddress);
        if (!doNotDisturbPolicyAllowsRinging) {
            Log.e("[Audio Manager] Do not ring as Android Do Not Disturb Policy forbids it");
            return;
        }

        if (mContext.getResources().getBoolean(R.bool.allow_ringing_while_early_media)) {
            routeAudioToSpeaker(); // Need to be able to ear the ringtone during the early media
        }

        mAudioManager.setMode(MODE_RINGTONE);

        try {
            if ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE
                            || mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL)
                    && mVibrator != null
                    && LinphonePreferences.instance().isIncomingCallVibrationEnabled()) {
                long[] patern = {0, 1000, 1000};
                mVibrator.vibrate(patern, 1);
            }
            if (mRingerPlayer == null) {
                requestAudioFocus(STREAM_RING);
                mRingerPlayer = new MediaPlayer();
                mRingerPlayer.setAudioStreamType(STREAM_RING);

                String ringtone =
                        LinphonePreferences.instance()
                                .getRingtone(Settings.System.DEFAULT_RINGTONE_URI.toString());
                try {
                    if (ringtone.startsWith("content://")) {
                        mRingerPlayer.setDataSource(mContext, Uri.parse(ringtone));
                    } else {
                        FileInputStream fis = new FileInputStream(ringtone);
                        mRingerPlayer.setDataSource(fis.getFD());
                        fis.close();
                    }
                } catch (IOException e) {
                    Log.e(e, "[Audio Manager] Cannot set ringtone");
                }

                mRingerPlayer.prepare();
                mRingerPlayer.setLooping(true);
                mRingerPlayer.start();
            } else {
                Log.w("[Audio Manager] Already ringing");
            }
        } catch (Exception e) {
            Log.e(e, "[Audio Manager] Cannot handle incoming call");
        }
        mIsRinging = true;
    }

    private synchronized void stopRinging() {
        if (mRingerPlayer != null) {
            mRingerPlayer.stop();
            mRingerPlayer.release();
            mRingerPlayer = null;
        }
        if (mVibrator != null) {
            mVibrator.cancel();
        }

        mIsRinging = false;
        if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            if (mContext.getResources().getBoolean(R.bool.isTablet)) {
                Log.d("[Audio Manager] Stopped ringing, routing back to speaker");
                routeAudioToSpeaker();
            } else {
                Log.d("[Audio Manager] Stopped ringing, routing back to earpiece");
                routeAudioToEarPiece();
            }
        }
    }

    private void routeAudioToSpeakerHelper(boolean speakerOn) {
        Log.w(
                "[Audio Manager] Routing audio to "
                        + (speakerOn ? "speaker" : "earpiece")
                        + ", disabling bluetooth audio route");
        BluetoothManager.getInstance().disableBluetoothSCO();

        mAudioManager.setSpeakerphoneOn(speakerOn);
    }

    private void adjustVolume(int i) {
        // starting from ICS, volume must be adjusted by the application, at least for
        // STREAM_VOICE_CALL volume stream
        mAudioManager.adjustStreamVolume(
                LINPHONE_VOLUME_STREAM,
                i < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI);
    }
}
