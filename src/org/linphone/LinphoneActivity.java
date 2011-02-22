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
import static android.media.AudioManager.*;
import java.util.List;

import org.linphone.core.Version;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TabHost;

public class LinphoneActivity extends TabActivity  {
	public static final String DIALER_TAB = "dialer";
	private AudioManager mAudioManager;
	private static LinphoneActivity instance;
	
	private FrameLayout mMainFrame;
	private SensorManager mSensorManager;
	private static SensorEventListener mSensorEventListener;
	
	private static final String SCREEN_IS_HIDDEN ="screen_is_hidden";
	
	protected static LinphoneActivity instance() {
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
		

		// start linphone as background       
		startService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));

		
		mMainFrame = (FrameLayout) findViewById(R.id.main_frame);
		mAudioManager = ((AudioManager)getSystemService(Context.AUDIO_SERVICE));
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		
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
	    tabIntent = new Intent().setClass(this, ContactPickerActivity.class);
	    indicator = getString(R.string.tab_contact);
	    tabDrawable = getResources().getDrawable(R.drawable.contact_orange);
	    spec = lTabHost.newTabSpec("contact")
	    	.setIndicator(indicator, tabDrawable)
	    	.setContent(tabIntent);
	    lTabHost.addTab(spec);


	    
	    lTabHost.setCurrentTabByTag("dialer");

	    
	    if (savedInstanceState !=null && savedInstanceState.getBoolean(SCREEN_IS_HIDDEN,false)) {
	    	hideScreen(true);
	    }
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
	    if (intent.getData() != null) {
	    	DialerActivity.instance().newOutgoingCall(intent);
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
			Log.e(LinphoneManager.TAG, "Unknown menu item ["+item+"]");
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
			Log.i(LinphoneManager.TAG, "proximity sensor already active");
			return;
		}
		List<Sensor> lSensorList = mSensorManager.getSensorList(Sensor.TYPE_PROXIMITY);
		mSensorEventListener = new SensorEventListener() {
			public void onSensorChanged(SensorEvent event) {
				if (event.timestamp == 0) return; //just ignoring for nexus 1
				Log.d(LinphoneManager.TAG, "Proximity sensor report ["+event.values[0]+"] , for max range ["+event.sensor.getMaximumRange()+"]");
				
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
			Log.i(LinphoneManager.TAG, "Proximity sensor detected, registering");
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

