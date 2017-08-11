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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.linphone;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import org.linphone.assistant.RemoteProvisioningActivity;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.tutorials.TutorialLauncherActivity;

import static android.content.Intent.ACTION_MAIN;

/**
 *
 * Launch Linphone main activity when Service is ready.
 *
 * @author Guillaume Beraudo
 *
 */
public class LinphoneLauncherActivity extends Activity {

	private Handler mHandler;
	private ServiceWaitThread mServiceThread;
	private String addressToCall;
	private Uri uriToResolve;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Hack to avoid to draw twice LinphoneActivity on tablets
		if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
		setContentView(R.layout.launch_screen);

		mHandler = new Handler();

		Intent intent = getIntent();
		if (intent != null) {
			String action = intent.getAction();
			if (Intent.ACTION_CALL.equals(action)) {
				if (intent.getData() != null) {
					addressToCall = intent.getData().toString();
					addressToCall = addressToCall.replace("%40", "@");
					addressToCall = addressToCall.replace("%3A", ":");
					if (addressToCall.startsWith("sip:")) {
						addressToCall = addressToCall.substring("sip:".length());
					}
				}
			} else if (Intent.ACTION_VIEW.equals(action)) {
				if (LinphoneService.isReady()) {
					addressToCall = ContactsManager.getInstance().getAddressOrNumberForAndroidContact(getContentResolver(), intent.getData());
				} else {
					uriToResolve = intent.getData();
				}
			}
		}

		if (LinphoneService.isReady()) {
			onServiceReady();
		} else {
			// start linphone as background
			startService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
			mServiceThread = new ServiceWaitThread();
			mServiceThread.start();
		}
	}

	protected void onServiceReady() {
		final Class<? extends Activity> classToStart;
		if (getResources().getBoolean(R.bool.show_tutorials_instead_of_app)) {
			classToStart = TutorialLauncherActivity.class;
		} else if (getResources().getBoolean(R.bool.display_sms_remote_provisioning_activity) && LinphonePreferences.instance().isFirstRemoteProvisioning()) {
			classToStart = RemoteProvisioningActivity.class;
		} else {
			classToStart = LinphoneActivity.class;
		}

		// We need LinphoneService to start bluetoothManager
		if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			BluetoothManager.getInstance().initBluetooth();
		}

		//TODO :
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				Intent newIntent = new Intent(LinphoneLauncherActivity.this, classToStart);
				Intent intent = getIntent();
                String msgShared = null;
				Uri imageUri = null;
				if (intent != null) {
					String action = intent.getAction();
					String type = intent.getType();
					newIntent.setData(intent.getData());
					if (Intent.ACTION_SEND.equals(action) && type != null) {
						if ("text/plain".equals(type) && intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
							Log.e(" ====>>> type = "+type+" share msg");
                            msgShared = intent.getStringExtra(Intent.EXTRA_TEXT);
							newIntent.putExtra("msgShared", msgShared);
						}else if ( type.contains("image") ){
							msgShared = intent.getStringExtra(Intent.EXTRA_STREAM);
							imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
							Log.e(" ====>>> type = "+type+" share images msgShared = "+msgShared +" VS toPath() = "+imageUri.getPath());
							newIntent.putExtra("fileShared", imageUri.getPath());
						}else{
							Log.e(" ====>>> type = "+type+" share something else");
						}
					}
				}
				if (uriToResolve != null) {
					addressToCall = ContactsManager.getInstance().getAddressOrNumberForAndroidContact(getContentResolver(), uriToResolve);
					Log.i("Intent has uri to resolve : " + uriToResolve.toString());
					uriToResolve = null;
				}
				if (addressToCall != null) {
					newIntent.putExtra("SipUriOrNumber", addressToCall);
					Log.i("Intent has address to call : " + addressToCall);
					addressToCall = null;
				}
				startActivity(newIntent);
                if (classToStart == LinphoneActivity.class && LinphoneActivity.isInstanciated() && (msgShared != null || imageUri != null)) {

					if(msgShared != null) {
						LinphoneActivity.instance().displayChat(null, msgShared, null);
					}
					if(imageUri != null) {
						LinphoneActivity.instance().displayChat(null, null, imageUri.toString());
					}
                }
				finish();
			}
		}, 1000);
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
			mServiceThread = null;
		}
	}

}


