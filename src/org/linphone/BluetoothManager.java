package org.linphone;
/*
BluetoothManager.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
import org.linphone.mediastream.Log;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;

/**
 * @author Sylvain Berfini
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BluetoothManager extends BroadcastReceiver {
	 @SuppressWarnings("deprecation")
	public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        LinphoneManager lm = LinphoneManager.getInstance();
        
        String actionScoConnected = AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED;
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            Log.e("Bluetooth Received Event" + " ACTION_ACL_DISCONNECTED" );
            
            if (lm != null) {
            	lm.scoDisconnected();
            	lm.routeAudioToReceiver();
            }
        } 
        else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            Log.e("Bluetooth Received Event" + " ACTION_ACL_CONNECTED" );
            
            if (lm != null) {
            	lm.scoConnected();
            }
        } 
        else if (actionScoConnected.equals(action)) {
        	int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, 0);
    		Log.e("Bluetooth sco state changed : " + state);
        	if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
        		if (lm != null) {
                	lm.scoConnected();
                }
        	} else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
        		if (lm != null) {
                	lm.scoDisconnected();
                	lm.routeAudioToReceiver();
                }
        	}
        }
        //Using real value instead of constant because not available before sdk 11 
        else if ("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED".equals(action)) { //BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED
        	int currentConnState = intent.getIntExtra("android.bluetooth.adapter.extra.CONNECTION_STATE", //BluetoothAdapter.EXTRA_CONNECTION_STATE
        			0); //BluetoothAdapter.STATE_DISCONNECTED
        	Log.e("Bluetooth state changed: " + currentConnState);
            if (lm != null && currentConnState == 2) { //BluetoothAdapter.STATE_CONNECTED
            	lm.startBluetooth();
            }
        } 
    }
}
