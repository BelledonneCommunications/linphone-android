/*
iLinphoneActivity.java
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


import static android.content.Intent.ACTION_MAIN;
import static android.media.AudioManager.MODE_NORMAL;
import static android.media.AudioManager.ROUTE_ALL;
import static android.media.AudioManager.ROUTE_SPEAKER;

import java.util.List;

import org.linphone.LinphoneManager.EcCalibrationListener;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.Version;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.RegistrationState;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
	
public class LinphoneActivity extends TabActivity  {
	public static final String DIALER_TAB = "dialer";
    public static final String PREF_FIRST_LAUNCH = "pref_first_launch";
    static final int VIDEO_VIEW_ACTIVITY = 100;
    static final int FIRST_LOGIN_ACTIVITY = 101;
    static final int INCALL_ACTIVITY = 102; 
    private static final String PREF_CHECK_CONFIG = "pref_check_config";

	private static LinphoneActivity instance;
	private AudioManager mAudioManager;

	
	
	private FrameLayout mMainFrame;
	private SensorManager mSensorManager;
	private static SensorEventListener mSensorEventListener;
	private static String TAG = LinphoneManager.TAG;
	
	private static final String SCREEN_IS_HIDDEN ="screen_is_hidden";
	
	
	// Customization
	private static boolean useFirstLoginActivity;
	private static boolean useMenuSettings;
	private static boolean useMenuAbout;
	private boolean checkAccount;

	
	
	
	static final LinphoneActivity instance() {
		if (instance != null) return instance;

		throw new RuntimeException("LinphoneActivity not instantiated yet");
	}

	protected void onSaveInstanceState (Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(SCREEN_IS_HIDDEN, mMainFrame.getVisibility() == View.INVISIBLE);
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		instance = this;
		setContentView(R.layout.main);

		useFirstLoginActivity = getResources().getBoolean(R.bool.useFirstLoginActivity);
		useMenuSettings = getResources().getBoolean(R.bool.useMenuSettings);
		useMenuAbout = getResources().getBoolean(R.bool.useMenuAbout);
		checkAccount = !useFirstLoginActivity;

		// start linphone as background       
		startService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));

		
		mMainFrame = (FrameLayout) findViewById(R.id.main_frame);
		mAudioManager = ((AudioManager)getSystemService(Context.AUDIO_SERVICE));
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

		if (!useFirstLoginActivity || pref.getBoolean(getString(R.string.first_launch_suceeded_once_key), false)) {
			fillTabHost();
		} else {
			startActivityForResult(new Intent().setClass(this, FirstLoginActivity.class), FIRST_LOGIN_ACTIVITY);
		}

		if (checkAccount && !useFirstLoginActivity) {
			if (pref.getBoolean(PREF_FIRST_LAUNCH, true)) {
				onFirstLaunch();
			} else if (!pref.getBoolean(PREF_CHECK_CONFIG, false)
					&& !checkDefined(pref, R.string.pref_username_key, R.string.pref_passwd_key, R.string.pref_domain_key)) {
				onBadSettings(pref);
			} else {
				checkAccount = false;
			}
		}
		
	    if (savedInstanceState !=null && savedInstanceState.getBoolean(SCREEN_IS_HIDDEN,false)) {
	    	hideScreen(true);
	    }
	}
	
	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		if (requestCode == FIRST_LOGIN_ACTIVITY) {
			if (resultCode == RESULT_OK) {
				Toast.makeText(this, getString(R.string.ec_calibration_launch_message), Toast.LENGTH_LONG).show();

				try {
					LinphoneManager.getInstance().startEcCalibration(new EcCalibrationListener() {
						public void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs) {
							PreferenceManager.getDefaultSharedPreferences(LinphoneActivity.this)
							.edit().putBoolean(
									getString(R.string.pref_echo_canceller_calibration_key),
									status == EcCalibratorStatus.Done).commit();
						}
					});
				} catch (LinphoneCoreException e) {
					Log.e(TAG, "Unable to calibrate EC", e);
				}

				fillTabHost();
			} else {
				finish();
				stopService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
			}
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}


	private void fillTabHost() {
		TabHost lTabHost = getTabHost();  // The activity TabHost  
	    TabHost.TabSpec spec;  // Reusable TabSpec for each tab
	    Drawable tabDrawable; // Drawable for a tab
	    Intent tabIntent; // Intent for the a table
	    CharSequence indicator;

	    //Call History
	    tabIntent =  new Intent().setClass(this, HistoryActivity.class);
	    tabDrawable = getResources().getDrawable(R.drawable.history_orange);
	    indicator = getString(R.string.tab_history);
	    spec = lTabHost.newTabSpec("history")
	    		.setIndicator(indicator, tabDrawable)
	    		.setContent(tabIntent);
	    lTabHost.addTab(spec);
	    
	    // Dialer
	    tabIntent = new Intent().setClass(this, DialerActivity.class).setData(getIntent().getData());
	    tabDrawable = getResources().getDrawable(R.drawable.dialer_orange);
	    indicator = getString(R.string.tab_dialer);
	    tabDrawable = getResources().getDrawable(R.drawable.dialer_orange);
	    spec = lTabHost.newTabSpec(DIALER_TAB)
	    		.setIndicator(indicator, tabDrawable)
	    		.setContent(tabIntent);
	    lTabHost.addTab(spec);
	    

	    // Contact picker
	    tabIntent = new Intent().setClass(this, Version.sdkAboveOrEqual(5) ?
	    		ContactPickerActivityNew.class : ContactPickerActivityOld.class);
	    indicator = getString(R.string.tab_contact);
	    tabDrawable = getResources().getDrawable(R.drawable.contact_orange);
	    spec = lTabHost.newTabSpec("contact")
	    	.setIndicator(indicator, tabDrawable)
	    	.setContent(tabIntent);
	    lTabHost.addTab(spec);


	    lTabHost.setCurrentTabByTag("dialer");
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (intent.getData() == null) {
			Log.e(TAG, "LinphoneActivity received an intent without data, discarding");
			return;
		}
		

		if (DialerActivity.instance() != null) {
			DialerActivity.instance().newOutgoingCall(intent);
		} else {
			Toast.makeText(this, getString(R.string.dialer_null_on_new_intent), Toast.LENGTH_LONG).show();
		}
	}
	@Override
	protected void onPause() {
		super.onPause();
		if  (isFinishing())  {
			//restore audio settings   
			if (Version.sdkStrictlyBelow(4) /*<donut*/) {
				mAudioManager.setMode(MODE_NORMAL); 
				mAudioManager.setRouting(MODE_NORMAL, ROUTE_SPEAKER, ROUTE_ALL);
			} else {
				mAudioManager.setSpeakerphoneOn(false); 
			}
			stopProxymitySensor();//just in case
			instance = null;
		}
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the currently selected menu XML resource.
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.linphone_activity_menu, menu);
		
		menu.findItem(R.id.menu_settings).setVisible(useMenuSettings);
		menu.findItem(R.id.menu_about).setVisible(useMenuAbout);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			startprefActivity();
			return true;
		case R.id.menu_exit:
			finish();
			stopService(new Intent(ACTION_MAIN)
				.setClass(this, LinphoneService.class));
			break;
		case R.id.menu_about:
			startActivity(new Intent(ACTION_MAIN)
				.setClass(this, AboutActivity.class));
		default:
			Log.e(TAG, "Unknown menu item ["+item+"]");
			break;
		}

		return false;
	}

	void startprefActivity() {
		Intent intent = new Intent(ACTION_MAIN);
		intent.setClass(this, LinphonePreferencesActivity.class);
		startActivity(intent);
	}



	void hideScreen(boolean isHidden) {
		WindowManager.LayoutParams lAttrs =getWindow().getAttributes(); 
		if (isHidden) {
			lAttrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN; 
			mMainFrame.setVisibility(View.INVISIBLE);
		} else  {
			lAttrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN); 
			mMainFrame.setVisibility(View.VISIBLE);
		}
		getWindow().setAttributes(lAttrs);
	}

	synchronized void startProxymitySensor() {
		if (mSensorEventListener != null) {
			Log.i(TAG, "proximity sensor already active");
			return;
		}
		List<Sensor> lSensorList = mSensorManager.getSensorList(Sensor.TYPE_PROXIMITY);
		mSensorEventListener = new SensorEventListener() {
			public void onSensorChanged(SensorEvent event) {
				if (event.timestamp == 0) return; //just ignoring for nexus 1
				Log.d(TAG, "Proximity sensor report ["+event.values[0]+"] , for max range ["+event.sensor.getMaximumRange()+"]");
				
				if (event.values[0] != event.sensor.getMaximumRange() ) {
					instance().hideScreen(true);
				} else  {
					instance().hideScreen(false);
				}
			}

			public void onAccuracyChanged(Sensor sensor, int accuracy) {}	
		};
		if (lSensorList.size() >0) {
			mSensorManager.registerListener(mSensorEventListener,lSensorList.get(0),SensorManager.SENSOR_DELAY_UI);
			Log.i(TAG, "Proximity sensor detected, registering");
		}		
	}

	protected synchronized void stopProxymitySensor() {
		if (mSensorManager!=null) {
			mSensorManager.unregisterListener(mSensorEventListener);
			mSensorEventListener=null; 
		}
		hideScreen(false);
	}


	void showPreferenceErrorDialog(String message) {
		if (!useMenuSettings) {
			Toast.makeText(this, message, Toast.LENGTH_LONG);
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this)
			.setMessage(String.format(getString(R.string.config_error), message))
			.setCancelable(false)
			.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					Intent intent = new Intent(ACTION_MAIN);
					intent.setClass(getApplicationContext(), LinphonePreferencesActivity.class);
					startActivity(intent);
				}
			})
			.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});

			builder.create().show();
		}
	}

	public void onRegistrationStateChanged(RegistrationState state,
			String message) {
		
		if (FirstLoginActivity.instance != null) {
			FirstLoginActivity.instance.onRegistrationStateChanged(state);
		}
	}
	
	
	
	/***** Check Account *******/
	private boolean checkDefined(SharedPreferences pref, int ... keys) {
		for (int key : keys) {
			String conf = pref.getString(getString(key), null);
			if (conf == null || "".equals(conf))
				return false;
		}
		return true;
	}

	private void onFirstLaunch() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		TextView lDialogTextView = new TextView(this);
		lDialogTextView.setAutoLinkMask(0x0f/*all*/);
		lDialogTextView.setPadding(10, 10, 10, 10);

		lDialogTextView.setText(Html.fromHtml(getString(R.string.first_launch_message)));

		builder.setCustomTitle(lDialogTextView)
		.setCancelable(false)
		.setPositiveButton(getString(R.string.cont), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				LinphoneActivity.instance().startprefActivity();
				checkAccount = false;
			}
		});

		builder.create().show();
	}

	private void onBadSettings(final SharedPreferences pref) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		TextView lDialogTextView = new TextView(this);
		lDialogTextView.setAutoLinkMask(0x0f/*all*/);
		lDialogTextView.setPadding(10, 10, 10, 10);

		lDialogTextView.setText(Html.fromHtml(getString(R.string.initial_config_error)));

		builder.setCustomTitle(lDialogTextView)
		.setCancelable(false)
		.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				LinphoneActivity.instance().startprefActivity();
				checkAccount = false;
			}
		}).setNeutralButton(getString(R.string.no), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
				checkAccount = false;
			}
		}).setNegativeButton(getString(R.string.never_remind), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				pref.edit().putBoolean(PREF_CHECK_CONFIG, true).commit();
				dialog.cancel();
				checkAccount = false;
			}
		});

		builder.create().show();
	}

	public static void setAddressAndGoToDialer(String number, String name) {
		DialerActivity.instance().setContactAddress(number, name);
		instance.getTabHost().setCurrentTabByTag(DIALER_TAB);
	}
}

