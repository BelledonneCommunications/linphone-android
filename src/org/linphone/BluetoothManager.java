package org.linphone;

import org.linphone.mediastream.Log;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BluetoothManager extends BroadcastReceiver {
	 @SuppressWarnings("deprecation")
	public void onReceive(Context context, Intent intent) {
        boolean routeToBT = context.getResources().getBoolean(R.bool.route_audio_to_bluetooth_if_available);
        if (!routeToBT)
        	return;
        
        String action = intent.getAction();
        LinphoneManager lm = LinphoneManager.getInstance();
        
        String actionScoConnected = AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED;
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            Log.e("Bluetooth Received Event" + " ACTION_ACL_DISCONNECTED" );
            
            if (lm != null) {
            	lm.isBluetoothScoConnected = false;
            	lm.scoDisconnected();
            	lm.routeAudioToReceiver();
            }
        } 
        else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            Log.e("Bluetooth Received Event" + " ACTION_ACL_CONNECTED" );
            
            if (lm != null) {
        		lm.isBluetoothScoConnected = true;
            	lm.scoConnected();
            }
        } 
        else if (actionScoConnected.equals(action)) {
        	int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, 0);
    		Log.e("Bluetooth sco state changed : " + state);
        	if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
        		if (lm != null) {
            		lm.isBluetoothScoConnected = true;
                	lm.scoConnected();
                }
        	} else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
        		if (lm != null) {
        			lm.isBluetoothScoConnected = false;
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
        		lm.isBluetoothScoConnected = true;
            	lm.startBluetooth();
            }
        } 
    }
}
