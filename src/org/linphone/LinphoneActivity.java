/*
LinphoneActivity.java
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

import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;

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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TabHost.TabSpec;
	
public class LinphoneActivity extends TabActivity implements
		SensorEventListener
		, ContactPicked
		, LinphoneOnCallStateChangedListener
		{
	public static final String DIALER_TAB = "dialer";
    public static final String PREF_FIRST_LAUNCH = "pref_first_launch";
    private static final int video_activity = 100;
    static final int FIRST_LOGIN_ACTIVITY = 101;
    static final int INCOMING_CALL_ACTIVITY = 103;
	private static final int incall_activity = 104;
	private static final int conferenceDetailsActivity = 105;
    private static final String PREF_CHECK_CONFIG = "pref_check_config";

	private static LinphoneActivity instance;

	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private int previousRotation = -1;

	private Handler mHandler = new Handler();

	// Customization
	private static boolean useFirstLoginActivity;
	private static boolean useMenuSettings;
	private static boolean useMenuAbout;
	private boolean checkAccount;
	
	static final boolean isInstanciated() {
		return instance != null;
	}
	
	public static final LinphoneActivity instance() {
		if (instance != null) return instance;

		throw new RuntimeException("LinphoneActivity not instantiated yet");
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!LinphoneManager.isInstanciated()) {
			Log.e("No service running: avoid crash by finishing ", this.getClass().getName());
			// super.onCreate called earlier
			finish();
			return;
		}
		instance = this;
		setContentView(R.layout.main);

		LinphonePreferenceManager.getInstance(this);
		useFirstLoginActivity = getResources().getBoolean(R.bool.useFirstLoginActivity);
		useMenuSettings = getResources().getBoolean(R.bool.useMenuSettings);
		useMenuAbout = getResources().getBoolean(R.bool.useMenuAbout);
		checkAccount = !useFirstLoginActivity;

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
					&& !checkDefined(pref, R.string.pref_username_key, R.string.pref_domain_key)) {
				onBadSettings(pref);
			} else {
				checkAccount = false;
			}
		}

		LinphoneManager.addListener(this);
	}
	
	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case FIRST_LOGIN_ACTIVITY:
			if (resultCode == RESULT_OK) {
//				Toast.makeText(this, getString(R.string.ec_calibration_launch_message), Toast.LENGTH_LONG).show();
//				try {
//					LinphoneManager.getInstance().startEcCalibration(new EcCalibrationListener() {
//						public void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs) {
//							PreferenceManager.getDefaultSharedPreferences(LinphoneActivity.this)
//							.edit().putBoolean(
//									getString(R.string.pref_echo_canceller_calibration_key),
//									status == EcCalibratorStatus.Done).commit();
//						}
//					});
//				} catch (LinphoneCoreException e) {
//					Log.e(e, "Unable to calibrate EC");
//				}
				fillTabHost();
			} else {
				finish();
				stopService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
			}
			break;
		case incall_activity:
			break;
		default:
			break;
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}


	private synchronized void fillTabHost() {
		if (((TabWidget) findViewById(android.R.id.tabs)).getChildCount() != 0) return;

	    startActivityInTab("history",
	    		new Intent().setClass(this, HistoryActivity.class),
	    		R.string.tab_history, R.drawable.history_orange);

	    
	    startActivityInTab(DIALER_TAB,
	    		new Intent().setClass(this, DialerActivity.class).setData(getIntent().getData()),
	    		R.string.tab_dialer, R.drawable.dialer_orange);
	    

	    startActivityInTab("contact",
	    		new Intent().setClass(this, Version.sdkAboveOrEqual(5) ?
	    		ContactPickerActivityNew.class : ContactPickerActivityOld.class),
	    		R.string.tab_contact, R.drawable.contact_orange);


	    selectDialerTab();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (intent.getData() == null) {
			Log.i("LinphoneActivity received an intent without data, recreating GUI if needed");
			if (!LinphoneManager.getLc().isIncall()) return;
			if(LinphoneManager.getLc().isInComingInvitePending()) {
				selectDialerTab();
			} else {
				startIncallActivity();
			}
			return;
		}


		if (DialerActivity.instance() != null) {
			DialerActivity.instance().newOutgoingCall(intent);
		} else {
			Toast.makeText(this, getString(R.string.dialer_null_on_new_intent), Toast.LENGTH_LONG).show();
		}
	}
	
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		
	}
	public void onSensorChanged(SensorEvent event) {
		if (event==null || event.sensor == mAccelerometer) {
			int rot;
			
			rot = AndroidVideoWindowImpl.rotationToAngle(getWindowManager().getDefaultDisplay().getOrientation());

			if (rot != previousRotation) {
				Log.d("New device rotation: ", rot);
				// Returning rotation FROM ITS NATURAL ORIENTATION
				LinphoneCore lc=LinphoneManager.getLcIfManagerNotDestroyedOrNull();
				if (lc!=null) lc.setDeviceRotation(rot);
				//else ignore, we are probably exiting.
				previousRotation = rot;
			}
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if  (isFinishing())  {
			//restore audio settings
			LinphoneManager.removeListener(this);
			LinphoneManager.stopProximitySensorForActivity(this);
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
	public boolean onPrepareOptionsMenu(Menu menu) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null)
			return true;
		// hide settings menu when in call
		// otherwise, exiting the 'setting' menu will cause exosip deletion/recreation...
		menu.findItem(R.id.menu_settings).setVisible(!lc.isIncall());
		
		return super.onPrepareOptionsMenu(menu);
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
			Log.e("Unknown menu item [",item,"]");
			break;
		}

		return false;
	}

	void startprefActivity() {
		Intent intent = new Intent(ACTION_MAIN);
		intent.setClass(this, LinphonePreferencesActivity.class);
		startActivity(intent);
	}


	public synchronized void stopOrientationSensor() {
		if (mSensorManager!=null)
			mSensorManager.unregisterListener(this, mAccelerometer);
	}


	
	public synchronized void startOrientationSensor() {
		if (mSensorManager!=null) {
			mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
		
		/* init LC orientation value on orientation sensor startup */
		previousRotation = -1;
		onSensorChanged(null);
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
					startprefActivity();
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
				LinphoneManager.getInstance().initializePayloads();
				startprefActivity();
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
				startprefActivity();
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

	public void setAddressAndGoToDialer(String number, String name, Uri photo) {
		DialerActivity.instance().setContactAddress(number, name, photo);
		selectDialerTab();
	}

	private void selectDialerTab() {
		getTabHost().setCurrentTabByTag(DIALER_TAB);
	}

	
	private void startActivityInTab(String tag, Intent intent, int indicatorId, int drawableId) {
	    Drawable tabDrawable = getResources().getDrawable(drawableId);
	    TabSpec spec = getTabHost().newTabSpec(tag)
	    		.setIndicator(getString(indicatorId), tabDrawable)
	    		.setContent(intent);
	    getTabHost().addTab(spec);
	}


	// Do not call if video activity already launched as it would cause a pause() of the launched one
	// and a race condition with capture surfaceview leading to a crash
	public void startVideoActivity(final LinphoneCall call, int delay) {
		if (VideoCallActivity.launched || call == null) {
			return;
		}

		mHandler.postDelayed(new Runnable() {
			public void run() {
				if (VideoCallActivity.launched) return;
				LinphoneManager.getInstance().enableCamera(call, true);
				startActivityForResult(new Intent().setClass(
						LinphoneActivity.this,
						VideoCallActivity.class),
						video_activity);
				// Avoid two consecutive runs to enter the previous block
				VideoCallActivity.launched = true;
				}
		}, delay);
		LinphoneManager.getInstance().routeAudioToSpeaker();
	}

	public synchronized void startIncallActivity() {
		if (IncallActivity.active) {
			return;
		}
		startActivityForResult(new Intent().setClass(
				LinphoneActivity.this,
				IncallActivity.class),
				incall_activity);
		IncallActivity.active = true;
	}

	public void startIncomingCallActivity(LinphoneCall pendingCall) {
		Intent intent = new Intent()
			.setClass(this, IncomingCallActivity.class)
			.putExtra("stringUri", pendingCall.getRemoteAddress().asStringUriOnly());
		startActivityForResult(intent, INCOMING_CALL_ACTIVITY);
	}

	
	@Override
	public void onCallStateChanged(LinphoneCall call, State state,
			String message) {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc==null) {
			/* we are certainly exiting, ignore then.*/
			return;
		}

		if (state==State.IncomingReceived) {
			startIncomingCallActivity(call);
		}
		if (state == State.OutgoingInit) {
			startIncallActivity();
		}
		if (state==State.OutgoingInit || state==State.IncomingReceived) {
			startOrientationSensor();
		} else if (state==State.Error || state==State.CallEnd){
			stopOrientationSensor();
			finishActivity(INCOMING_CALL_ACTIVITY);
		}
		if (state==State.Connected) {
			if (call.getDirection() == CallDirection.Incoming) {
				startIncallActivity();
			}
		}
		if (state == LinphoneCall.State.StreamsRunning && Version.isVideoCapable() && !call.isInConference()) {
			boolean videoEnabled = call.getCurrentParamsCopy().getVideoEnabled();
			if (videoEnabled) {
				startVideoActivity(call, 1000);
			} else {
				finishActivity(video_activity);
			}
		}

		if (state == LinphoneCall.State.CallUpdatedByRemote && Version.isVideoCapable()) {
			if (VideoCallActivity.launched && !call.getCurrentParamsCopy().getVideoEnabled()) {
				finishActivity(video_activity);
			}
		}

		if (state==State.Error){
			showToast(R.string.call_error, message);
		}
		if (state==State.Error || state==State.CallEnd) {
			if (lc.getCallsNb() == 0){
				exitIncallActivity();
			}
			if (ConferenceDetailsActivity.active && lc.getConferenceSize() == 0) {
				finishActivity(conferenceDetailsActivity);
			}
		}
	}
	
	private void showToast(int id, String txt) {
		final String msg = String.format(getString(id), txt);
		mHandler.post(new Runnable() {
			public void run() {
				Toast.makeText(LinphoneActivity.this, msg, Toast.LENGTH_LONG).show();
			}
		});
	}



	private void exitIncallActivity() {
		finishActivity(incall_activity);
		setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
	}


	@Override
	protected void onResume() {
		super.onResume();
		LinphoneCall pendingCall = LinphoneManager.getInstance().getPendingIncomingCall();
		if (pendingCall != null) {
			startIncomingCallActivity(pendingCall);
		} 
	}

	@Override
	public void goToDialer() {
		selectDialerTab();
	}

	public void startConferenceDetailsActivity() {
		startActivityForResult(new Intent().setClass(this, ConferenceDetailsActivity.class), conferenceDetailsActivity);
	}
}

interface ContactPicked {
	void setAddressAndGoToDialer(String number, String name, Uri photo);
	void goToDialer();
}
