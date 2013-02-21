package org.linphone;

import org.linphone.mediastream.Log;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BluetoothManager extends BroadcastReceiver {
	 public void onReceive(Context context, Intent intent) {
        boolean routeToBT = context.getResources().getBoolean(R.bool.route_audio_to_bluetooth_if_available);
        if (!routeToBT)
        	return;
        
        String action = intent.getAction();
        LinphoneManager lm = LinphoneManager.getInstance();
     
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            Log.e("Bluetooth Received Event" + " ACTION_ACL_DISCONNECTED" );
            
            if (lm != null) {
            	lm.uninitBluetooth();
            	lm.routeAudioToReceiver();
            }
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            Log.e("Bluetooth Received Event" + " ACTION_ACL_CONNECTED" );
            
            if (lm != null) {
            	lm.routeToBluetoothIfAvailable();
            }
        } else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
        	Log.e("Bluetooth state changed!");
            if (lm != null) {
            	lm.startBluetooth();
            }
        }
    }
}
