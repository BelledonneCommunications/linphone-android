/*
LinphoneLauncherActivity.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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

import static android.content.Intent.ACTION_MAIN;

import org.linphone.core.Log;
import org.linphone.mediastream.Version;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.google.android.gcm.GCMRegistrar;

/**
 * 
 * Launch Linphone main activity when Service is ready.
 * 
 * @author Guillaume Beraudo
 *
 */
public class LinphoneLauncherActivity extends Activity {

	private Handler mHandler;
	private ServiceWaitThread mThread;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Hack to avoid to draw twice LinphoneActivity on tablets
        if (Version.isXLargeScreen(this)) {
        	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
        	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
		setContentView(R.layout.launcher);
        
		mHandler = new Handler();

		// Starting the push notification service
		if (getResources().getBoolean(R.bool.enable_push_id)) {
			GCMRegistrar.checkDevice(this);
			GCMRegistrar.checkManifest(this);
			final String regId = GCMRegistrar.getRegistrationId(this);
			if (regId.equals("")) {
				GCMRegistrar.register(this, getString(R.string.push_sender_id));
			} else {
				Log.e("Push Notification : already registered with id = " + regId);
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString(this.getString(R.string.push_reg_id_key), regId);
				editor.commit();
			}
		}
		
		if (LinphoneService.isReady()) {
			onServiceReady();
		} else {
			// start linphone as background  
			startService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
			mThread = new ServiceWaitThread();
			mThread.start();
		}
	}

	protected void onServiceReady() {
		LinphoneService.instance().setActivityToLaunchOnIncomingReceived(LinphoneActivity.class);
		
		startActivity(new Intent()
		.setClass(this, LinphoneActivity.class)
		.setData(getIntent().getData()));

		finish();
	}


	private class ServiceWaitThread extends Thread {
		public void run() {
			while (!LinphoneService.isReady()) {
				try {
					sleep(30);
				} catch (InterruptedException e) {
					throw new RuntimeException("waiting thread sleep() has been interrupted");
				}
			}

			mHandler.post(new Runnable() {
				@Override
				public void run() {
					onServiceReady();
				}
			});
			mThread = null;
		}
	}
}


