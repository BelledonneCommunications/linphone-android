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

import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.tools.Log;
import org.linphone.receivers.BluetoothReceiver;
import org.linphone.receivers.HeadsetReceiver;
import org.linphone.settings.LinphonePreferences;

public class AndroidAudioManager {
    private Context mContext;
    private AudioManager mAudioManager;
    private Call mRingingCall;
    private MediaPlayer mRingerPlayer;
    private final Vibrator mVibrator;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothHeadset mBluetoothHeadset;
    private BluetoothReceiver mBluetoothReceiver;
    private HeadsetReceiver mHeadsetReceiver;
    private boolean mHeadsetReceiverRegistered;

    private boolean mIsRinging;
    private boolean mAudioFocused;
    private boolean mEchoTesterIsRunning;
    private boolean mIsBluetoothHeadsetConnected;
    private boolean mIsBluetoothHeadsetScoConnected;

    private CoreListenerStub mListener;

    public AndroidAudioManager(Context context) {
        mContext = context;
        mAudioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mEchoTesterIsRunning = false;
        mHeadsetReceiverRegistered = false;

        startBluetooth();

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
                                if (!mIsBluetoothHeadsetConnected) {
                                    if (mContext.getResources().getBoolean(R.bool.isTablet)) {
                                        routeAudioToSpeaker();
                                    } else {
                                        // Only force earpiece audio route for incoming audio calls,
                                        // outgoing calls may have manually enabled speaker
                                        if (call.getDir() == Call.Dir.Incoming) {
                                            routeAudioToEarPiece();
                                        }
                                    }
                                }
                                // Only register this one when a call is active

                                enableHeadsetReceiver();
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

                                // Only register this one when a call is active
                                if (mHeadsetReceiver != null && mHeadsetReceiverRegistered) {
                                    Log.i("[Audio Manager] Unregistering headset receiver");
                                    mContext.unregisterReceiver(mHeadsetReceiver);
                                    mHeadsetReceiverRegistered = false;
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
                            if (mIsBluetoothHeadsetConnected) {
                                routeAudioToBluetooth();
                            }
                        }

                        if (state == Call.State.StreamsRunning) {
                            setAudioManagerInCallMode();
                            if (mIsBluetoothHeadsetConnected) {
                                routeAudioToBluetooth();
                            }
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
        if (mBluetoothAdapter != null && mBluetoothHeadset != null) {
            Log.i("[Audio Manager] [Bluetooth] Closing HEADSET profile proxy");
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
        }

        Log.i("[Audio Manager] [Bluetooth] Unegistering bluetooth receiver");
        if (mBluetoothReceiver != null) {
            mContext.unregisterReceiver(mBluetoothReceiver);
        }

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
        return mAudioManager.isSpeakerphoneOn() && !isUsingBluetoothAudioRoute();
    }

    public boolean isAudioRoutedToEarpiece() {
        return !mAudioManager.isSpeakerphoneOn() && !isUsingBluetoothAudioRoute();
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

        routeAudioToSpeaker();
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
    }

    private void routeAudioToSpeakerHelper(boolean speakerOn) {
        Log.w("[Audio Manager] Routing audio to " + (speakerOn ? "speaker" : "earpiece"));
        if (mIsBluetoothHeadsetScoConnected) {
            Log.w("[Audio Manager] [Bluetooth] Disabling bluetooth audio route");
            changeBluetoothSco(false);
        }

        mAudioManager.setSpeakerphoneOn(speakerOn);
    }

    private void adjustVolume(int i) {
        if (mAudioManager.isVolumeFixed()) {
            Log.e("[Audio Manager] Can't adjust volume, device has it fixed...");
            // Keep going just in case...
        }

        int stream = STREAM_VOICE_CALL;
        if (mIsBluetoothHeadsetScoConnected) {
            Log.i(
                    "[Audio Manager] Bluetooth is connected, try to change the volume on STREAM_BLUETOOTH_SCO");
            stream = 6; // STREAM_BLUETOOTH_SCO, it's hidden...
        }

        // starting from ICS, volume must be adjusted by the application,
        // at least for STREAM_VOICE_CALL volume stream
        mAudioManager.adjustStreamVolume(
                stream,
                i < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI);
    }

    // Bluetooth

    public synchronized void bluetoothHeadetConnectionChanged(boolean connected) {
        mIsBluetoothHeadsetConnected = connected;
        mAudioManager.setBluetoothScoOn(connected);
        if (LinphoneContext.isReady()) LinphoneManager.getCallManager().refreshInCallActions();
    }

    public synchronized void bluetoothHeadetAudioConnectionChanged(boolean connected) {
        mIsBluetoothHeadsetScoConnected = connected;
        mAudioManager.setBluetoothScoOn(connected);
    }

    public synchronized boolean isBluetoothHeadsetConnected() {
        return mIsBluetoothHeadsetConnected;
    }

    public synchronized void bluetoothHeadetScoConnectionChanged(boolean connected) {
        mIsBluetoothHeadsetScoConnected = connected;
        if (LinphoneContext.isReady()) LinphoneManager.getCallManager().refreshInCallActions();
    }

    public synchronized boolean isUsingBluetoothAudioRoute() {
        return mIsBluetoothHeadsetScoConnected;
    }

    public synchronized void routeAudioToBluetooth() {
        if (!isBluetoothHeadsetConnected()) {
            Log.w("[Audio Manager] [Bluetooth] No headset connected");
            return;
        }
        if (mAudioManager.getMode() != AudioManager.MODE_IN_COMMUNICATION) {
            Log.w(
                    "[Audio Manager] [Bluetooth] Changing audio mode to MODE_IN_COMMUNICATION and requesting STREAM_VOICE_CALL focus");
            mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            requestAudioFocus(STREAM_VOICE_CALL);
        }
        changeBluetoothSco(true);
    }

    private synchronized void changeBluetoothSco(final boolean enable) {
        // IT WILL TAKE A CERTAIN NUMBER OF CALLS TO EITHER START/STOP BLUETOOTH SCO FOR IT TO WORK
        if (enable && mIsBluetoothHeadsetScoConnected) {
            Log.i("[Audio Manager] [Bluetooth] SCO already enabled, skipping");
            return;
        } else if (!enable && !mIsBluetoothHeadsetScoConnected) {
            Log.i("[Audio Manager] [Bluetooth] SCO already disabled, skipping");
            return;
        }

        new Thread() {
            @Override
            public void run() {
                Log.i("[Audio Manager] [Bluetooth] SCO start/stop thread started");
                boolean resultAcknowledged;
                int retries = 0;

                do {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Log.e(e);
                    }

                    synchronized (AndroidAudioManager.this) {
                        if (enable) {
                            Log.i(
                                    "[Audio Manager] [Bluetooth] Starting SCO: try number "
                                            + retries);
                            mAudioManager.startBluetoothSco();
                        } else {
                            Log.i(
                                    "[Audio Manager] [Bluetooth] Stopping SCO: try number "
                                            + retries);
                            mAudioManager.stopBluetoothSco();
                        }
                        resultAcknowledged = isUsingBluetoothAudioRoute() == enable;
                        retries++;
                    }
                } while (!resultAcknowledged && retries < 10);
            }
        }.start();
    }

    public void bluetoothAdapterStateChanged() {
        if (mBluetoothAdapter.isEnabled()) {
            Log.i("[Audio Manager] [Bluetooth] Adapter enabled");
            mIsBluetoothHeadsetConnected = false;
            mIsBluetoothHeadsetScoConnected = false;

            BluetoothProfile.ServiceListener bluetoothServiceListener =
                    new BluetoothProfile.ServiceListener() {
                        public void onServiceConnected(int profile, BluetoothProfile proxy) {
                            if (profile == BluetoothProfile.HEADSET) {
                                Log.i("[Audio Manager] [Bluetooth] HEADSET profile connected");
                                mBluetoothHeadset = (BluetoothHeadset) proxy;

                                List<BluetoothDevice> devices =
                                        mBluetoothHeadset.getConnectedDevices();
                                if (devices.size() > 0) {
                                    Log.i(
                                            "[Audio Manager] [Bluetooth] A device is already connected");
                                    bluetoothHeadetConnectionChanged(true);
                                }

                                Log.i("[Audio Manager] [Bluetooth] Registering bluetooth receiver");

                                IntentFilter filter = new IntentFilter();
                                filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
                                filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
                                filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
                                filter.addAction(
                                        BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT);

                                Intent sticky =
                                        mContext.registerReceiver(mBluetoothReceiver, filter);
                                Log.i("[Audio Manager] [Bluetooth] Bluetooth receiver registered");
                                int state =
                                        sticky.getIntExtra(
                                                AudioManager.EXTRA_SCO_AUDIO_STATE,
                                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                                    Log.i(
                                            "[Audio Manager] [Bluetooth] Bluetooth headset SCO connected");
                                    bluetoothHeadetScoConnectionChanged(true);
                                } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                                    Log.i(
                                            "[Audio Manager] [Bluetooth] Bluetooth headset SCO disconnected");
                                    bluetoothHeadetScoConnectionChanged(false);
                                } else if (state == AudioManager.SCO_AUDIO_STATE_CONNECTING) {
                                    Log.i(
                                            "[Audio Manager] [Bluetooth] Bluetooth headset SCO connecting");
                                } else if (state == AudioManager.SCO_AUDIO_STATE_ERROR) {
                                    Log.i(
                                            "[Audio Manager] [Bluetooth] Bluetooth headset SCO connection error");
                                } else {
                                    Log.w(
                                            "[Audio Manager] [Bluetooth] Bluetooth headset unknown SCO state changed: "
                                                    + state);
                                }
                            }
                        }

                        public void onServiceDisconnected(int profile) {
                            if (profile == BluetoothProfile.HEADSET) {
                                Log.i("[Audio Manager] [Bluetooth] HEADSET profile disconnected");
                                mBluetoothHeadset = null;
                                mIsBluetoothHeadsetConnected = false;
                                mIsBluetoothHeadsetScoConnected = false;
                            }
                        }
                    };

            mBluetoothAdapter.getProfileProxy(
                    mContext, bluetoothServiceListener, BluetoothProfile.HEADSET);
        } else {
            Log.w("[Audio Manager] [Bluetooth] Adapter disabled");
        }
    }

    private void startBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            Log.i("[Audio Manager] [Bluetooth] Adapter found");
            if (mAudioManager.isBluetoothScoAvailableOffCall()) {
                Log.i("[Audio Manager] [Bluetooth] SCO available off call, continue");
            } else {
                Log.w("[Audio Manager] [Bluetooth] SCO not available off call !");
            }

            mBluetoothReceiver = new BluetoothReceiver();
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            mContext.registerReceiver(mBluetoothReceiver, filter);

            bluetoothAdapterStateChanged();
        }
    }

    // HEADSET

    private void enableHeadsetReceiver() {
        mHeadsetReceiver = new HeadsetReceiver();

        Log.i("[Audio Manager] Registering headset receiver");
        mContext.registerReceiver(
                mHeadsetReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        mContext.registerReceiver(
                mHeadsetReceiver, new IntentFilter(AudioManager.ACTION_HEADSET_PLUG));
        mHeadsetReceiverRegistered = true;
    }
}
