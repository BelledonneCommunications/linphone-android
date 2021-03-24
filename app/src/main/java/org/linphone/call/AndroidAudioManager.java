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

import static android.media.AudioManager.STREAM_VOICE_CALL;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AudioDevice;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.tools.Log;

public class AndroidAudioManager {
    private Context mContext;
    private AudioManager mAudioManager;
    private boolean mEchoTesterIsRunning = false;
    private boolean mPreviousStateIsConnected = false;

    private CoreListenerStub mListener;

    public AndroidAudioManager(Context context) {
        mContext = context;
        mAudioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        mPreviousStateIsConnected = false;

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            final Core core,
                            final Call call,
                            final Call.State state,
                            final String message) {
                        if (state == Call.State.Connected) {
                            if (core.getCallsNb() == 1) {
                                if (!isBluetoothHeadsetConnected()) {
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
                            }
                        } else if (state == Call.State.StreamsRunning
                                && mPreviousStateIsConnected) {
                            if (isBluetoothHeadsetConnected()) {
                                routeAudioToBluetooth();
                            }
                        }
                        mPreviousStateIsConnected = state == Call.State.Connected;
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

    public void routeAudioToEarPiece() {
        routeAudioToSpeakerHelper(false);
    }

    public void routeAudioToSpeaker() {
        routeAudioToSpeakerHelper(true);
    }

    public boolean isAudioRoutedToSpeaker() {
        return isUsingSpeakerAudioRoute() && !isUsingBluetoothAudioRoute();
    }

    public boolean isAudioRoutedToEarpiece() {
        return !isUsingSpeakerAudioRoute() && !isUsingBluetoothAudioRoute();
    }

    /* Echo cancellation */

    public void startEchoTester() {
        Core core = LinphoneManager.getCore();
        if (core == null) {
            return;
        }
        int sampleRate;
        String sampleRateProperty =
                mAudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        sampleRate = Integer.parseInt(sampleRateProperty);
        mEchoTesterIsRunning = true;
        core.startEchoTester(sampleRate);
    }

    public void stopEchoTester() {
        Core core = LinphoneManager.getCore();
        if (core == null) {
            return;
        }

        core.stopEchoTester();
        mEchoTesterIsRunning = false;
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

    public synchronized boolean isUsingSpeakerAudioRoute() {
        if (LinphoneManager.getCore().getCallsNb() == 0) return false;
        Call currentCall = LinphoneManager.getCore().getCurrentCall();
        if (currentCall == null) currentCall = LinphoneManager.getCore().getCalls()[0];
        if (currentCall == null) return false;
        AudioDevice audioDevice = currentCall.getOutputAudioDevice();
        if (audioDevice == null) return false;
        Log.i(
                "[Audio Manager] Currently used audio device: ",
                audioDevice.getDeviceName(),
                "/",
                audioDevice.getType().name());
        return audioDevice.getType() == AudioDevice.Type.Speaker;
    }

    private void routeAudioToSpeakerHelper(boolean speakerOn) {
        Log.w("[Audio Manager] Routing audio to " + (speakerOn ? "speaker" : "earpiece"));

        if (LinphoneManager.getCore().getCallsNb() == 0) return;
        Call currentCall = LinphoneManager.getCore().getCurrentCall();
        if (currentCall == null) currentCall = LinphoneManager.getCore().getCalls()[0];
        if (currentCall == null) return;

        for (AudioDevice audioDevice : LinphoneManager.getCore().getAudioDevices()) {
            if (speakerOn && audioDevice.getType() == AudioDevice.Type.Speaker) {
                currentCall.setOutputAudioDevice(audioDevice);
                return;
            } else if (!speakerOn && audioDevice.getType() == AudioDevice.Type.Earpiece) {
                currentCall.setOutputAudioDevice(audioDevice);
                return;
            }
        }
    }

    private void adjustVolume(int i) {
        if (mAudioManager.isVolumeFixed()) {
            Log.e("[Audio Manager] Can't adjust volume, device has it fixed...");
            // Keep going just in case...
        }

        int stream = STREAM_VOICE_CALL;
        if (isUsingBluetoothAudioRoute()) {
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

    public synchronized boolean isUsingBluetoothAudioRoute() {
        if (LinphoneManager.getCore().getCallsNb() == 0) return false;
        Call currentCall = LinphoneManager.getCore().getCurrentCall();
        if (currentCall == null) currentCall = LinphoneManager.getCore().getCalls()[0];
        if (currentCall == null) return false;
        AudioDevice audioDevice = currentCall.getOutputAudioDevice();
        if (audioDevice == null) return false;
        Log.i(
                "[Audio Manager] Currently used audio device: ",
                audioDevice.getDeviceName(),
                "/",
                audioDevice.getType().name());
        return audioDevice.getType() == AudioDevice.Type.Bluetooth;
    }

    public synchronized boolean isBluetoothHeadsetConnected() {
        for (AudioDevice audioDevice : LinphoneManager.getCore().getAudioDevices()) {
            if (audioDevice.getType() == AudioDevice.Type.Bluetooth
                    && audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                Log.i(
                        "[Audio Manager] Found bluetooth device: ",
                        audioDevice.getDeviceName(),
                        "/",
                        audioDevice.getType().name());
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isWiredHeadsetAvailable() {
        for (AudioDevice audioDevice : LinphoneManager.getCore().getExtendedAudioDevices()) {
            if (audioDevice.getType() == AudioDevice.Type.Headphones
                    || audioDevice.getType() == AudioDevice.Type.Headset) {
                Log.i(
                        "[Audio Manager] Found headset/headphone device: ",
                        audioDevice.getDeviceName(),
                        "/",
                        audioDevice.getType().name());
                return true;
            }
        }
        return false;
    }

    public synchronized void routeAudioToBluetooth() {
        if (LinphoneManager.getCore().getCallsNb() == 0) return;
        Call currentCall = LinphoneManager.getCore().getCurrentCall();
        if (currentCall == null) currentCall = LinphoneManager.getCore().getCalls()[0];
        if (currentCall == null) return;

        for (AudioDevice audioDevice : LinphoneManager.getCore().getAudioDevices()) {
            if (audioDevice.getType() == AudioDevice.Type.Bluetooth
                    && audioDevice.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                Log.i(
                        "[Audio Manager] Found bluetooth audio device",
                        audioDevice.getDeviceName(),
                        "/",
                        audioDevice.getType().name(),
                        ", routing audio to it");
                currentCall.setOutputAudioDevice(audioDevice);
                return;
            }
        }
        Log.w(
                "[Audio Manager] Didn't find any bluetooth audio device, keeping default audio route");
    }
}
