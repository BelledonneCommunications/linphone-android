/*
Linphone.java
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

import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneProxyConfig;


import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.Toast;

public class Linphone extends TabActivity implements LinphoneCoreListener {
	static final public String TAG="Linphone";
	/** Called when the activity is first created. */
	private static String LINPHONE_FACTORY_RC = "/data/data/org.linphone/files/linphonerc";
	private static String LINPHONE_RC = "/data/data/org.linphone/files/.linphonerc";
	private static String RING_SND = "/data/data/org.linphone/files/oldphone_mono.wav"; 
	private static String RINGBACK_SND = "/data/data/org.linphone/files/ringback.wav";

	private static Linphone theLinphone;
	private LinphoneCore mLinphoneCore;
	private SharedPreferences mPref;
	Timer mTimer = new Timer("Linphone scheduler");
	public static String DIALER_TAB = "dialer";

	private Handler mIteratehandler;
	
	static Linphone getLinphone()  {
		if (theLinphone == null) {
			throw new RuntimeException("LinphoneActivity not instanciated yet");
		} else {
			return theLinphone;
		}
	}
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		theLinphone = this;
		mPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		try {
			copyAssetsFromPackage();

			mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(	this
			, new File(LINPHONE_RC) 
			, new File(LINPHONE_FACTORY_RC)
			, null);

			initFromConf();
			mIteratehandler = new Handler() {
			      public void handleMessage(Message msg) {  
			          //iterate is called inside an Android handler to allow UI interaction within LinphoneCoreListener
			    	  mLinphoneCore.iterate();
			      }
			  }; 

			TimerTask lTask = new TimerTask() {
				@Override
				public void run() {
					mIteratehandler.sendEmptyMessage(0);
				}

			};
			
			mTimer.scheduleAtFixedRate(lTask, 0, 100); 


		    TabHost lTabHost = getTabHost();  // The activity TabHost
		    TabHost.TabSpec spec;  // Reusable TabSpec for each tab
		   

		    // Create an Intent to launch an Activity for the tab (to be reused)
		    Intent lDialerIntent = new Intent().setClass(this, DialerActivity.class);

		    // Initialize a TabSpec for each tab and add it to the TabHost
		    spec = lTabHost.newTabSpec("dialer").setIndicator(getString(R.string.tab_dialer),
		                      getResources().getDrawable(android.R.drawable.ic_menu_call))
		                  .setContent(lDialerIntent);
		    lTabHost.addTab(spec);
		    
		 
		    
		    // Do the same for the other tabs
		    Intent lContactItent =  new Intent().setClass(this, ContactPickerActivity.class);
		    
		    spec = lTabHost.newTabSpec("contact").setIndicator(getString(R.string.tab_contact),
		                      null)
		                  .setContent(lContactItent);
		    lTabHost.addTab(spec);

		    lTabHost.setCurrentTabByTag("dialer");
		    



		} catch (Exception e) {
			Log.e(TAG,"Cannot start linphone",e);
		}

	}


	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		//finish();
	}


	private void copyAssetsFromPackage() throws IOException {
		copyIfNotExist(R.raw.oldphone_mono,RING_SND);
		copyIfNotExist(R.raw.ringback,RINGBACK_SND);
		copyFromPackage(R.raw.linphonerc, new File(LINPHONE_FACTORY_RC).getName());
	}
	private  void copyIfNotExist(int ressourceId,String target) throws IOException {
		File lFileToCopy = new File(target);
		if (!lFileToCopy.exists()) {		
		   copyFromPackage(ressourceId,lFileToCopy.getName());
		}

	}
	private void copyFromPackage(int ressourceId,String target) throws IOException{
		FileOutputStream lOutputStream = openFileOutput (target, 0); 
		InputStream lInputStream = getResources().openRawResource(ressourceId);
		int readByte;
		byte[] buff = new byte[8048];
		while (( readByte = lInputStream.read(buff))!=-1) {
			lOutputStream.write(buff,0, readByte);
		}
		lOutputStream.flush();
		lOutputStream.close();
		lInputStream.close();
		
	}
	public void authInfoRequested(LinphoneCore lc, String realm, String username) {

	}
	public void byeReceived(LinphoneCore lc, String from) {
		// TODO Auto-generated method stub

	}
	public void displayMessage(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub

	}
	public void displayStatus(LinphoneCore lc, String message) {
		Log.i(TAG, message); 
		if (DialerActivity.getDialer()!=null) DialerActivity.getDialer().displayStatus(lc,message);
	}
	public void displayWarning(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub

	}
	public void generalState(LinphoneCore lc, LinphoneCore.GeneralState state) {
		Log.i(TAG, "new state ["+state+"]");
		if (DialerActivity.getDialer()!=null) DialerActivity.getDialer().generalState(lc,state);
	}
	public void inviteReceived(LinphoneCore lc, String from) {
		// TODO Auto-generated method stub

	}
	public void show(LinphoneCore lc) {
		// TODO Auto-generated method stub

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
		default:
			Log.e(TAG, "Unknown menu item ["+item+"]");
			break;
		}

		return false;
	}

	
	public void initFromConf() throws LinphoneCoreException {
		//1 read proxy config from preferences
		String lUserName = mPref.getString(getString(R.string.pref_username_key), null);
		if (lUserName == null) {
			handleBadConfig(getString(R.string.wrong_username));
			return;
		}

		String lPasswd = mPref.getString(getString(R.string.pref_passwd_key), null);
		if (lPasswd == null) {
			handleBadConfig(getString(R.string.wrong_passwd));
			return;

		}
		
		String lDomain = mPref.getString(getString(R.string.pref_domain_key), null);
		if (lDomain == null) {
			handleBadConfig(getString(R.string.wrong_domain));
			return; 
		}


		//auth
		mLinphoneCore.clearAuthInfos();
		LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(lUserName, lPasswd);
		mLinphoneCore.addAuthInfo(lAuthInfo);

		
		//proxy
		String lProxy = mPref.getString(getString(R.string.pref_proxy_key), "sip:"+lDomain);
		
		//get Default proxy if any
		LinphoneProxyConfig lDefaultProxyConfig = mLinphoneCore.getDefaultProxyConfig();
		String lIdentity = "sip:"+lUserName+"@"+lDomain;
		if (lDefaultProxyConfig == null) {
			lDefaultProxyConfig = mLinphoneCore.createProxyConfig(lIdentity, lProxy, null,true);
			mLinphoneCore.addtProxyConfig(lDefaultProxyConfig);
			mLinphoneCore.setDefaultProxyConfig(lDefaultProxyConfig);

		} else {
			lDefaultProxyConfig.edit();
			lDefaultProxyConfig.setIdentity(lIdentity);
			lDefaultProxyConfig.setProxy(lProxy);
			lDefaultProxyConfig.enableRegister(true);
			lDefaultProxyConfig.done();
		}
		lDefaultProxyConfig = mLinphoneCore.getDefaultProxyConfig();
		
		//prefix
		String lPrefix = mPref.getString(getString(R.string.pref_prefix_key), null);
		if (lPrefix != null && lDefaultProxyConfig !=null) {
			lDefaultProxyConfig.setDialPrefix(lPrefix);
		}
		
	}
	
	private void handleBadConfig(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(String.format(getString(R.string.config_error),message))
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
	private void startprefActivity() {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setClass(Linphone.this, LinphonePreferencesActivity.class);
		startActivity(intent);
	}
	protected LinphoneCore getLinphoneCore() {
		return mLinphoneCore;
	}

}