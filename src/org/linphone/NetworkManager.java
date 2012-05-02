/*
ContactPickerActivity.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
package org.linphone;

import org.linphone.core.Log;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;



/**
 * 
 * Intercept network state changes and update linphone core through LinphoneManager.
 *
 */
public class NetworkManager extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		
		NetworkInfo lNetworkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
		Log.i("Network info [",lNetworkInfo,"]");
		Boolean lNoConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY,false);

		
		if (!LinphoneService.isReady()) {
			Log.i("Network broadcast received while Linphone service not ready");
			return;
		}
		
		
		if (lNoConnectivity | ((lNetworkInfo.getState() == NetworkInfo.State.DISCONNECTED) /*&& !lIsFailOver*/)) {
			LinphoneManager.getLc().setNetworkReachable(false);
		} else if (lNetworkInfo.getState() == NetworkInfo.State.CONNECTED){
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
			boolean wifiOnly = pref.getBoolean(context.getString(R.string.pref_wifi_only_key), false);
			if (lNetworkInfo.getTypeName().equals("WIFI") || (lNetworkInfo.getTypeName().equals("mobile") && !wifiOnly)) {
				LinphoneManager.getLc().setNetworkReachable(true);
			} else {
				LinphoneManager.getLc().setNetworkReachable(false);
			}
		} else {
			 // Other unhandled events
		}

		NetworkInfo eventInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		LinphoneManager.getInstance().connectivityChanged(eventInfo, cm);
	}

}
