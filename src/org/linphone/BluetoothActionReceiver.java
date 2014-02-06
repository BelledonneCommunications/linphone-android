package org.linphone;

import org.linphone.mediastream.Log;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BluetoothActionReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)) {
			BluetoothDevice device = (BluetoothDevice) intent.getExtras().get(BluetoothDevice.EXTRA_DEVICE);
			String command = intent.getExtras().getString(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);
			int type = intent.getExtras().getInt(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE);
			
			Object[] temp = (Object[]) intent.getExtras().get(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS);
			String[] args = new String[temp.length];
			String logArgs = "";
			for (int i = 0; i < temp.length; i++) {
				args[i] = (String) temp[i];
				logArgs += args[i] + " ";
			}
			
			Log.d("Bluetooth headset event from " + device.getName() + ", command: " + command + " (type: " + type + ") with args: " + logArgs);
			
			//TODO: parse command/args and do something with it
		}
	}
}
