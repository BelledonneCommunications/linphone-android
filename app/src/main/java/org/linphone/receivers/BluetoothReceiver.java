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
package org.linphone.receivers;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import org.linphone.LinphoneManager;
import org.linphone.core.tools.Log;

public class BluetoothReceiver extends BroadcastReceiver {
    public BluetoothReceiver() {
        super();
        Log.i("[Bluetooth] Bluetooth receiver created");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i("[Bluetooth] Bluetooth broadcast received");

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    Log.w("[Bluetooth] Adapter has been turned off");
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Log.w("[Bluetooth] Adapter is being turned off");
                    break;
                case BluetoothAdapter.STATE_ON:
                    Log.i("[Bluetooth] Adapter has been turned on");
                    LinphoneManager.getAudioManager().bluetoothAdapterStateChanged();
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    Log.i("[Bluetooth] Adapter is being turned on");
                    break;
                case BluetoothAdapter.ERROR:
                    Log.e("[Bluetooth] Adapter is in error state !");
                    break;
                default:
                    Log.w("[Bluetooth] Unknown adapter state: ", state);
                    break;
            }
        } else if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
            int state =
                    intent.getIntExtra(
                            BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
            if (state == BluetoothHeadset.STATE_CONNECTED) {
                Log.i("[Bluetooth] Bluetooth headset connected");
                LinphoneManager.getAudioManager().bluetoothHeadetConnectionChanged(true);
            } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                Log.i("[Bluetooth] Bluetooth headset disconnected");
                LinphoneManager.getAudioManager().bluetoothHeadetConnectionChanged(false);
            } else {
                Log.w("[Bluetooth] Bluetooth headset unknown state changed: " + state);
            }
        } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
            int state =
                    intent.getIntExtra(
                            BluetoothHeadset.EXTRA_STATE,
                            BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
            if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                Log.i("[Bluetooth] Bluetooth headset audio connected");
                LinphoneManager.getAudioManager().bluetoothHeadetAudioConnectionChanged(true);
            } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                Log.i("[Bluetooth] Bluetooth headset audio disconnected");
                LinphoneManager.getAudioManager().bluetoothHeadetAudioConnectionChanged(false);
            } else if (state == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
                Log.i("[Bluetooth] Bluetooth headset audio connecting");
            } else {
                Log.w("[Bluetooth] Bluetooth headset unknown audio state changed: " + state);
            }
        } else if (action.equals(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)) {
            int state =
                    intent.getIntExtra(
                            AudioManager.EXTRA_SCO_AUDIO_STATE,
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                Log.i("[Bluetooth] Bluetooth headset SCO connected");
                LinphoneManager.getAudioManager().bluetoothHeadetScoConnectionChanged(true);
            } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                Log.i("[Bluetooth] Bluetooth headset SCO disconnected");
                LinphoneManager.getAudioManager().bluetoothHeadetScoConnectionChanged(false);
            } else if (state == AudioManager.SCO_AUDIO_STATE_CONNECTING) {
                Log.i("[Bluetooth] Bluetooth headset SCO connecting");
            } else if (state == AudioManager.SCO_AUDIO_STATE_ERROR) {
                Log.i("[Bluetooth] Bluetooth headset SCO connection error");
            } else {
                Log.w("[Bluetooth] Bluetooth headset unknown SCO state changed: " + state);
            }
        } else if (action.equals(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)) {
            String command =
                    intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);
            int type =
                    intent.getIntExtra(
                            BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, -1);

            String commandType;
            switch (type) {
                case BluetoothHeadset.AT_CMD_TYPE_ACTION:
                    commandType = "AT Action";
                    break;
                case BluetoothHeadset.AT_CMD_TYPE_READ:
                    commandType = "AT Read";
                    break;
                case BluetoothHeadset.AT_CMD_TYPE_TEST:
                    commandType = "AT Test";
                    break;
                case BluetoothHeadset.AT_CMD_TYPE_SET:
                    commandType = "AT Set";
                    break;
                case BluetoothHeadset.AT_CMD_TYPE_BASIC:
                    commandType = "AT Basic";
                    break;
                default:
                    commandType = "AT Unknown";
                    break;
            }
            Log.i("[Bluetooth] Vendor action " + commandType + " : " + command);
        } else {
            Log.w("[Bluetooth] Bluetooth unknown action: " + action);
        }
    }
}
