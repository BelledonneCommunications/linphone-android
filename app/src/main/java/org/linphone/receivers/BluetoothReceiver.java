package org.linphone.receivers;

/*
BluetoothReceiver.java
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

import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import org.linphone.LinphoneManager;
import org.linphone.core.tools.Log;

public class BluetoothReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
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
        } else {
            Log.w("[Bluetooth] Bluetooth unknown action: " + action);
        }
    }
}
