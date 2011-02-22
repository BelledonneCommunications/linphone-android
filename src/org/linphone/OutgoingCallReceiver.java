/*
OutgoingCallReceiver.java
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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Intercept outgoing calls dialed through Android dialer.
 * Redirect the calls through Linphone according to user preferences.
 * 
 */
public class OutgoingCallReceiver extends BroadcastReceiver {
	public static final String TAG = ";0000000";
	public static final String key_off="off";
	public static final String key_on_demand="ask_for_outcall_interception";
	public static final String key_always="alway_intercept_out_call";


	@Override
	public void onReceive(Context context, Intent intent) {
		String to = intent.getStringExtra("android.intent.extra.PHONE_NUMBER");

		//do not catch ussd codes
		if (to==null || to.contains("#"))
			return;

		if (!to.contains(TAG)) {
			if (LinphoneService.isReady() && LinphoneManager.getLc().getDefaultProxyConfig()==null) {
				//just return
				return;
			}
			setResult(Activity.RESULT_OK,null, null);
			Intent lIntent = new Intent();
			// 1 check config 
			if (PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.pref_handle_outcall_key),key_on_demand).equals(key_always)) {
				//start linphone directly
				lIntent.setClass(context, LinphoneActivity.class);
			} else {
				//start activity chooser
				lIntent.setAction(Intent.ACTION_CALL);
			}
				
			lIntent.setData(Uri.parse("tel:"+to+TAG));
			lIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(lIntent);

		} else {
			setResult(Activity.RESULT_OK,to.replace(TAG, ""),null);
		}
	}

}
