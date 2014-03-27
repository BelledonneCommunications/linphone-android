package org.linphone.compatibility;

import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.mediastream.Log;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;

import com.google.android.gcm.GCMRegistrar;

/*
ApiEightPlus.java
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
/**
 * @author Sylvain Berfini
 */
@TargetApi(8)
public class ApiEightPlus {

	public static void initPushNotificationService(Context context) {
		try {
			// Starting the push notification service
			GCMRegistrar.checkDevice(context);
			GCMRegistrar.checkManifest(context);
			final String regId = GCMRegistrar.getRegistrationId(context);
			String newPushSenderID = context.getString(R.string.push_sender_id);
			String currentPushSenderID = LinphonePreferences.instance().getPushNotificationRegistrationID();
			if (regId.equals("") || currentPushSenderID == null || !currentPushSenderID.equals(newPushSenderID)) {
				GCMRegistrar.register(context, newPushSenderID);

				Log.d("Push Notification : storing current sender id = " + newPushSenderID);
				LinphonePreferences.instance().setPushNotificationRegistrationID(newPushSenderID);
			} else {
				Log.d("Push Notification : already registered with id = " + regId);
				LinphonePreferences.instance().setPushNotificationRegistrationID(regId);
			}
		} catch (java.lang.UnsupportedOperationException e) {
			Log.i("Push Notification not activated");
		}
	}

	@SuppressWarnings("deprecation")
	public static String getAudioManagerEventForBluetoothConnectionStateChangedEvent() {
		return AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED;
	}
}
