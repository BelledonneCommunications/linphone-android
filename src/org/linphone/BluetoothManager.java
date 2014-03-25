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
import java.util.List;

import org.linphone.LinphoneSimpleListener.LinphoneOnAudioChangedListener.AudioState;
import org.linphone.compatibility.Compatibility;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;

/**
 * @author Sylvain Berfini
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BluetoothManager extends BroadcastReceiver {
	private static BluetoothManager instance;

	private Context mContext;
	private AudioManager mAudioManager;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothHeadset mBluetoothHeadset;
	private BluetoothDevice mBluetoothDevice;
	private BluetoothProfile.ServiceListener mProfileListener;
	private BroadcastReceiver bluetoothActionReceiver = new BluetoothActionReceiver();
	private boolean isBluetoothConnected;
	private boolean isUsingBluetoothAudioRoute;
	
	public static BluetoothManager getInstance() {
		if (instance == null) {
			instance = new BluetoothManager();
		}
		return instance;
	}
	
	/**
	 * Do not call !
	 */
	public BluetoothManager() {
		isBluetoothConnected = false;
		isUsingBluetoothAudioRoute = false;
		try { 
			mContext = LinphoneManager.getInstance().getContext();
			mAudioManager = ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE));
			instance = this;
		} catch (Exception e) {}
	}
	
	public void startBluetooth() {
		if (isBluetoothConnected) {
			Log.e("Bluetooth already started");
			return;
		}
		
		IntentFilter filter = new IntentFilter();
		filter.addAction("android.bluetooth.device.action.ACL_CONNECTED");
		filter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
		filter.addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED");
		mContext.registerReceiver(this,  filter);
		Log.d("Bluetooth receiver started");
		
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if (mBluetoothAdapter.isEnabled()) {
			if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
				if (mProfileListener != null) {
					Log.w("Bluetooth headset profile was already opened, let's close it");
					mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
				}
				
				mProfileListener = new BluetoothProfile.ServiceListener() {
					public void onServiceConnected(int profile, BluetoothProfile proxy) {
					    if (profile == BluetoothProfile.HEADSET) {
					        Log.d("Bluetooth headset connected");
							mContext.registerReceiver(bluetoothActionReceiver, new IntentFilter(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT));
					        mBluetoothHeadset = (BluetoothHeadset) proxy;
					        isBluetoothConnected = true;
					    }
					}
					public void onServiceDisconnected(int profile) {
					    if (profile == BluetoothProfile.HEADSET) {
					    	mContext.unregisterReceiver(bluetoothActionReceiver);
					        mBluetoothHeadset = null;
					        isBluetoothConnected = false;
					        Log.d("Bluetooth headset disconnected");
					        LinphoneManager.getInstance().routeAudioToSpeaker();
					    }
					}
				};
				boolean success = mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.HEADSET);
				if (!success) {
					Log.e("Bluetooth getProfileProxy failed !");
				}
			}
		} else {
			Log.w("Bluetooth interface disabled on device");
		}
	}
	
	public boolean routeAudioToBluetooth() {
		return routeAudioToBluetooth(false);
	}
	
	private boolean routeAudioToBluetooth(boolean isRetry) {
		if (mBluetoothAdapter == null) {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		}
		
		if (mBluetoothAdapter.isEnabled() && mAudioManager.isBluetoothScoAvailableOffCall()) {
			isUsingBluetoothAudioRoute = isBluetoothHeadsetAvailable();
				
			if (isUsingBluetoothAudioRoute) {
				if (mAudioManager != null) {
					mAudioManager.setBluetoothScoOn(true);	
					mAudioManager.startBluetoothSco();
					LinphoneManager.getInstance().audioStateChanged(AudioState.BLUETOOTH);
				}
			} else {
				LinphoneManager.getInstance().audioStateChanged(AudioState.SPEAKER);
			}
			
			isUsingBluetoothAudioRoute = mBluetoothHeadset.isAudioConnected(mBluetoothDevice);
			if (!isUsingBluetoothAudioRoute && !isRetry) {
				Log.w("Routing audio to bluetooth headset failed, retry....");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {}
				return routeAudioToBluetooth(true);
			} else if (isRetry) {
				if (isUsingBluetoothAudioRoute) {
					Log.d("Retry worked, audio is routed to bluetooth headset");
				} else {
					Log.e("Retry not worked, audio isn't routed to bluetooth headset...");
					disableBluetoothSCO();
				}
			} else {
				Log.d("Routing audio to bluetooth headset worked at first try");
			}
			
			return isUsingBluetoothAudioRoute;
		}
		
		return false;
	}
	
	public boolean isUsingBluetoothAudioRoute() {
		return mBluetoothHeadset.isAudioConnected(mBluetoothDevice);
	}
	
	public boolean isBluetoothHeadsetAvailable() {
		if (mBluetoothAdapter == null) {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		}
		
		if (mBluetoothAdapter.isEnabled() && mAudioManager.isBluetoothScoAvailableOffCall()) {
			if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
				boolean isHeadsetConnected = false;
				if (mBluetoothHeadset != null) {
					List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();                        
					for (final BluetoothDevice dev : devices) {    
						if (mBluetoothHeadset.getConnectionState(dev) == BluetoothHeadset.STATE_CONNECTED) {
							mBluetoothDevice = dev;
							isHeadsetConnected = true;
							break;
						}
					}
					Log.d(isHeadsetConnected ? "Headset found, bluetooth audio route available" : "No headset found, bluetooth audio route unavailable");
				}
				return isHeadsetConnected;
			}
		}
		
		return false;
	}
	
	public void disableBluetoothSCO() {
		isUsingBluetoothAudioRoute = false;
		if (mAudioManager != null) {
			mAudioManager.stopBluetoothSco();
			mAudioManager.setBluetoothScoOn(false);
			Log.w("Bluetooth sco disconnected!");
		}
	}
	
	public void stopBluetooth() {
		Log.w("Stopping bluetooth...");
		isBluetoothConnected = false;
		isUsingBluetoothAudioRoute = false;
		
		if (mAudioManager != null) {
			mAudioManager.stopBluetoothSco();
			mAudioManager.setBluetoothScoOn(false);
		}
		
		if (mProfileListener != null && mBluetoothHeadset != null) {
			mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
			mContext.unregisterReceiver(bluetoothActionReceiver);
			mProfileListener = null;
			mBluetoothHeadset = null;
		}
		mBluetoothDevice = null;
		
		Log.w("Bluetooth stopped!");
		
		if (LinphoneManager.getLc().getCallsNb() > 0) {
			Log.w("Bluetooth disabled, Going back to incall mode");
			Compatibility.setAudioManagerInCallMode(mAudioManager);
		}
		
		try {
			mContext.unregisterReceiver(this);
			Log.d("Bluetooth receiver stopped");
		} catch (Exception e) {}
		
		if (LinphoneManager.isInstanciated()) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
		}
	}
	
	public void destroy() {
		try {
			stopBluetooth();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("deprecation")
	public void onReceive(Context context, Intent intent) {
        if (!LinphoneManager.isInstanciated())
        	return;

        String action = intent.getAction();
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            Log.d("Bluetooth Received Event" + " ACTION_ACL_DISCONNECTED");

            stopBluetooth();
        } 
        else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            Log.d("Bluetooth Received Event" + " ACTION_ACL_CONNECTED");
            startBluetooth();
        } 
        else if (AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED.equals(action)) {
        	int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, 0);
    		Log.d("Bluetooth sco state changed : " + state);
        	if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
        		startBluetooth();
        	} else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
        		stopBluetooth();
        	}
        }
        //Using real value instead of constant because not available before sdk 11 
        else if ("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED".equals(action)) { //BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED
        	int currentConnState = intent.getIntExtra("android.bluetooth.adapter.extra.CONNECTION_STATE", //BluetoothAdapter.EXTRA_CONNECTION_STATE
        			0); //BluetoothAdapter.STATE_DISCONNECTED
        	Log.d("Bluetooth state changed: " + currentConnState);
            if (currentConnState == 2) { //BluetoothAdapter.STATE_CONNECTED
            	startBluetooth();
            }
        } 
    }
}
