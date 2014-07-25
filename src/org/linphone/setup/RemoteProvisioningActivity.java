package org.linphone.setup;
/*
RemoteProvisioningActivity.java
Copyright (C) 2014  Belledonne Communications, Grenoble, France

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

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneLauncherActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneSimpleListener.LinphoneOnRemoteProvisioningListener;
import org.linphone.R;
import org.linphone.core.LinphoneCore.RemoteProvisioningState;
import org.linphone.mediastream.Log;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * @author Sylvain Berfini
 */
public class RemoteProvisioningActivity extends Activity implements LinphoneOnRemoteProvisioningListener {
	private static RemoteProvisioningActivity instance = null;
	
	private Handler mHandler = new Handler();
	private String configUriParam = null;
	private ProgressBar spinner;
	
	public static RemoteProvisioningActivity getInstance() {
		return instance;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.remote_provisioning);
		spinner = (ProgressBar) findViewById(R.id.spinner);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		instance = this;
		LinphonePreferences.instance().setContext(this);
		
		checkIntentForConfigUri(getIntent());
	}
	
	@Override
	protected void onPause() {
		instance = null;
		super.onPause();
	}

	@Override
	public void onConfiguringStatus(final RemoteProvisioningState state) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (spinner != null) spinner.setVisibility(View.GONE);
				if (state == RemoteProvisioningState.ConfiguringSuccessful) {
					goToLinphoneActivity();
				} else if (state == RemoteProvisioningState.ConfiguringFailed) {
					Toast.makeText(RemoteProvisioningActivity.this, R.string.remote_provisioning_failure, Toast.LENGTH_LONG).show();
				}
			}
		});
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		checkIntentForConfigUri(intent);
	}
	
	private void checkIntentForConfigUri(final Intent intent) {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				Uri openUri = intent.getData();
				if (openUri != null) {
					// We expect something like linphone-config://http://linphone.org/config.xml
					configUriParam = openUri.getEncodedSchemeSpecificPart().substring(2); // Removes the linphone-config://
					Log.d("Using config uri: " + configUriParam);
				}
				
				if (configUriParam == null) {
					if (!LinphonePreferences.instance().isFirstRemoteProvisioning()) {
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								goToLinphoneActivity();
							}
						});
					} else if (!getResources().getBoolean(R.bool.forbid_app_usage_until_remote_provisioning_completed)) {
						// Show this view for a few seconds then go to the dialer
						mHandler.postDelayed(new Runnable() {
							@Override
							public void run() {
								goToLinphoneActivity();
							}
						}, 1500);
					} // else we do nothing if there is no config uri parameter and if user not allowed to leave this screen
				} else {
					if (getResources().getBoolean(R.bool.display_confirmation_popup_after_first_configuration) 
							&& !LinphonePreferences.instance().isFirstRemoteProvisioning()) {
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								displayDialogConfirmation();
							}
						});
					} else {
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								setRemoteProvisioningAddressAndRestart(configUriParam);
							}
						});
					}
				}
			}
		}).start();
	}
	
	private void displayDialogConfirmation() {
		new AlertDialog.Builder(RemoteProvisioningActivity.this)
        .setTitle(getString(R.string.remote_provisioning_again_title))
        .setMessage(getString(R.string.remote_provisioning_again_message))
        .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) { 
            	setRemoteProvisioningAddressAndRestart(configUriParam);
            }
         })
        .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) { 
            	goToLinphoneActivity();
            }
         })
         .show();
	}
	
	private void setRemoteProvisioningAddressAndRestart(String configUri) {
		if (spinner != null) spinner.setVisibility(View.VISIBLE);
		
		LinphonePreferences.instance().setContext(this); // Needed, else the next call will crash
		LinphonePreferences.instance().setRemoteProvisioningUrl(configUri);
		LinphonePreferences.instance().firstRemoteProvisioningSuccessful();
		
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				LinphoneManager.destroy();
				stopService(new Intent(ACTION_MAIN).setClass(RemoteProvisioningActivity.this, LinphoneService.class));
				Intent intent = new Intent();
				intent.setClass(RemoteProvisioningActivity.this, LinphoneLauncherActivity.class);
				startActivity(intent);
			}
		}, 1000);
	}
	
	private void goToLinphoneActivity() {
		if (LinphoneService.isReady()) {
			LinphoneService.instance().setActivityToLaunchOnIncomingReceived(LinphoneActivity.class);
			//finish(); // To prevent the user to come back to this page using back button
			startActivity(new Intent().setClass(this, LinphoneActivity.class));
		} else {
			finish();
		}
	}
}
