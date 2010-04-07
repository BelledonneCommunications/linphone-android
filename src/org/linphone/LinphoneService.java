/*
LinphoneService.java
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
import org.linphone.core.LinphoneCore.GeneralState;


import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class LinphoneService extends Service implements LinphoneCoreListener {
	static final public String TAG="Linphone";
	/** Called when the activity is first created. */
	private static String LINPHONE_FACTORY_RC = "/data/data/org.linphone/files/linphonerc";
	private static String LINPHONE_RC = "/data/data/org.linphone/files/.linphonerc";
	private static String RING_SND = "/data/data/org.linphone/files/oldphone_mono.wav"; 
	private static String RINGBACK_SND = "/data/data/org.linphone/files/ringback.wav";

	private static LinphoneService theLinphone;
	private LinphoneCore mLinphoneCore;
	private SharedPreferences mPref;
	Timer mTimer = new Timer("Linphone scheduler");

	private Handler mHandler =  new Handler() ;
	static boolean isready() {
		return (theLinphone!=null);
	}
	static LinphoneService instance()  {
		if (theLinphone == null) {
			throw new RuntimeException("LinphoneActivity not instanciated yet");
		} else {
			return theLinphone;
		}
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		theLinphone = this;

		mPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		try {
			copyAssetsFromPackage();

			mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(	this
					, new File(LINPHONE_RC) 
			, new File(LINPHONE_FACTORY_RC)
			, null);

			mLinphoneCore.setSoftPlayLevel(3);  

			try {
				initFromConf();
			} catch (LinphoneException e) {
				Log.w(TAG, "no config ready yet"); 
			}
			TimerTask lTask = new TimerTask() {
				@Override
				public void run() {
					mLinphoneCore.iterate();
				}

			};

			mTimer.scheduleAtFixedRate(lTask, 0, 100); 
		}
		catch (Exception e) {
			Log.e(TAG,"Cannot start linphone",e);
		}

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
	public void displayStatus(final LinphoneCore lc, final String message) {
		Log.i(TAG, message); 
		if (DialerActivity.getDialer()!=null)  {
			mHandler.post(new Runnable() {
				public void run() {
					DialerActivity.getDialer().displayStatus(lc,message);					
				}
				
			});
			
		}
	}
	public void displayWarning(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub

	}
	public void generalState(final LinphoneCore lc, final LinphoneCore.GeneralState state) {
		Log.i(TAG, "new state ["+state+"]");
		if (state == GeneralState.GSTATE_POWER_OFF) {
			//just exist
			//System.exit(0); 
		}
		if (DialerActivity.getDialer()!=null) {
			mHandler.post(new Runnable() {
				public void run() {
					DialerActivity.getDialer().generalState(lc,state);
				}
			});
		}
	}
	public void inviteReceived(LinphoneCore lc, String from) {
		// TODO Auto-generated method stub

	}
	public void show(LinphoneCore lc) {
		// TODO Auto-generated method stub

	}


	
	public void initFromConf() throws LinphoneConfigException, LinphoneException {
		//traces
		boolean lIsDebug = mPref.getBoolean(getString(R.string.pref_debug_key), false);
		LinphoneCoreFactory.instance().setDebugMode(lIsDebug);
		
		//1 read proxy config from preferences
		String lUserName = mPref.getString(getString(R.string.pref_username_key), null);
		if (lUserName == null || lUserName.length()==0) {
			throw new LinphoneConfigException(getString(R.string.wrong_username));
		}

		String lPasswd = mPref.getString(getString(R.string.pref_passwd_key), null);
		if (lPasswd == null || lPasswd.length()==0) {
			throw new LinphoneConfigException(getString(R.string.wrong_passwd));
		}

		String lDomain = mPref.getString(getString(R.string.pref_domain_key), null);
		if (lDomain == null || lDomain.length()==0) {
			throw new LinphoneConfigException(getString(R.string.wrong_domain));
		}


		//auth
		mLinphoneCore.clearAuthInfos();
		LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(lUserName, lPasswd);
		mLinphoneCore.addAuthInfo(lAuthInfo);


		//proxy
		String lProxy = mPref.getString(getString(R.string.pref_proxy_key),null);
		if (lProxy == null || lProxy.length() == 0) {
			lProxy = "sip:"+lDomain;
		}
		//get Default proxy if any
		LinphoneProxyConfig lDefaultProxyConfig = mLinphoneCore.getDefaultProxyConfig();
		String lIdentity = "sip:"+lUserName+"@"+lDomain;
		try {
			if (lDefaultProxyConfig == null) {
				lDefaultProxyConfig = mLinphoneCore.createProxyConfig(lIdentity, lProxy, null,true);
				mLinphoneCore.addProxyConfig(lDefaultProxyConfig);
				mLinphoneCore.setDefaultProxyConfig(lDefaultProxyConfig);

			} else {
				lDefaultProxyConfig.edit();
				lDefaultProxyConfig.setIdentity(lIdentity);
				lDefaultProxyConfig.setProxy(lProxy);
				lDefaultProxyConfig.enableRegister(true);
				lDefaultProxyConfig.done();
			}
			lDefaultProxyConfig = mLinphoneCore.getDefaultProxyConfig();

			if (lDefaultProxyConfig !=null) {
				//prefix      
				String lPrefix = mPref.getString(getString(R.string.pref_prefix_key), null);
				if (lPrefix != null  && lPrefix.length()>0) {
					lDefaultProxyConfig.setDialPrefix(lPrefix);
				}
				//escape +
				lDefaultProxyConfig.setDialEscapePlus(true);
			}
			//init network state
			ConnectivityManager lConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			mLinphoneCore.setNetworkStateReachable(lConnectivityManager.getActiveNetworkInfo().getState() ==NetworkInfo.State.CONNECTED); 
			
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(getString(R.string.wrong_settings),e);
		}
	}
	


	protected LinphoneCore getLinphoneCore() {
		return mLinphoneCore;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mTimer.cancel();
		mLinphoneCore.destroy();
		theLinphone=null;
	}

}