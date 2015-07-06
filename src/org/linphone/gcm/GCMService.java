package org.linphone.gcm;
/*
GCMService.java
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

import static android.content.Intent.ACTION_MAIN;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.UIThreadDispatcher;
import org.linphone.mediastream.Log;

import android.content.Context;
import android.content.Intent;

import com.google.android.gcm.GCMBaseIntentService;

/**
 * @author Sylvain Berfini
 */
// Warning ! Do not rename the service !
public class GCMService extends GCMBaseIntentService {

	public GCMService() {
		
	}
	
	@Override
	protected void onError(Context context, String errorId) {
		Log.e("Error while registering push notification : " + errorId);
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d("Push notification received");
		if (!LinphoneService.isReady()) {
			startService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
		} else if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() == 0) {
			UIThreadDispatcher.dispatch(new Runnable(){
				@Override
				public void run() {
					if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() == 0){
						LinphoneManager.getLc().setNetworkReachable(false);
						LinphoneManager.getLc().setNetworkReachable(true);
					}
				}
			});

		}
	}

	@Override
	protected void onRegistered(Context context, String regId) {
		Log.d("Registered push notification : " + regId);
		LinphonePreferences.instance().setPushNotificationRegistrationID(regId);
	}

	@Override
	protected void onUnregistered(Context context, String regId) {
		Log.w("Unregistered push notification : " + regId);
		LinphonePreferences.instance().setPushNotificationRegistrationID(null);
	}
	
	protected String[] getSenderIds(Context context) {
	    return new String[] { context.getString(R.string.push_sender_id) };
	}
}
