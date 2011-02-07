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

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore.FirewallPolicy;
import org.linphone.core.LinphoneCore.GlobalState;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
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
	
	NotificationManager mNotificationManager;
	Notification mNotification;
	PendingIntent mNofificationContentIntent;
	final static int NOTIFICATION_ID=1;
	final String NOTIFICATION_TITLE = "Linphone";
	
	final int IC_LEVEL_OFFLINE=3;
	final int IC_LEVEL_ORANGE=0;
	final int IC_LEVEL_GREEN=1;
	final int IC_LEVEL_RED=2;

	MediaPlayer mRingerPlayer;
	LinphoneCall.State mCurrentCallState;
	Vibrator mVibrator;
	private AudioManager mAudioManager;
	private  BroadcastReceiver mKeepAliveMgrReceiver = new KeepAliveManager();
	private  BroadcastReceiver mOutgoingCallReceiver = new OutgoingCallReceiver();
	
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
		
		// Dump some debugging information to the logs
		Hacks.dumpDeviceInformation();

		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotification = new Notification(R.drawable.status_level
														, ""
														, System.currentTimeMillis());
		mNotification.iconLevel=IC_LEVEL_ORANGE;
		mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
		Intent notificationIntent = new Intent(this, LinphoneActivity.class);
		mNofificationContentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		mNotification.setLatestEventInfo(this, NOTIFICATION_TITLE,"", mNofificationContentIntent);
		mNotificationManager.notify(NOTIFICATION_ID, mNotification);		
		mPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		mAudioManager = ((AudioManager)getSystemService(Context.AUDIO_SERVICE));
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		try {
			copyAssetsFromPackage();

			mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(	this
					, LINPHONE_RC 
			, LINPHONE_FACTORY_RC
			, null);

			mLinphoneCore.setPlaybackGain(3);   
			mLinphoneCore.setRing(null);

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
	        IntentFilter lFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
	        lFilter.addAction(Intent.ACTION_SCREEN_OFF);
			registerReceiver(mKeepAliveMgrReceiver, lFilter);
			if (!mPref.getString(getString(R.string.pref_handle_outcall_key), OutgoingCallReceiver.key_on_demand).equalsIgnoreCase(OutgoingCallReceiver.key_off)){
				lFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
				lFilter.setPriority(0);
				lFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
				registerReceiver(mOutgoingCallReceiver,lFilter);
			}
			
		
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
					if (DialerActivity.getDialer()!=null)
						DialerActivity.getDialer().displayStatus(lc,message);					
				}
				
			});
			
		}
	}
	public void displayWarning(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub

	}
	public void globalState(final LinphoneCore lc, final LinphoneCore.GlobalState state, final String message) {
		Log.i(TAG, "new state ["+state+"]");
		if (state == GlobalState.GlobalOn) {
			mNotification.iconLevel=IC_LEVEL_OFFLINE;
			mNotification.when=System.currentTimeMillis();
			mNotification.setLatestEventInfo(this
					, NOTIFICATION_TITLE
					,getString(R.string.notification_started)
					, mNofificationContentIntent);
			mNotificationManager.notify(NOTIFICATION_ID, mNotification);
			if (DialerActivity.getDialer()!=null) {
				mHandler.post(new Runnable() {
					public void run() {
						DialerActivity.getDialer().globalState(lc,state,message);
					}
				});
			} 
			
		}
	}
	public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig cfg,final LinphoneCore.RegistrationState state,final String smessage) {
	Log.i(TAG, "new state ["+state+"]");
		if (state == LinphoneCore.RegistrationState.RegistrationOk && lc.getDefaultProxyConfig().isRegistered()) {
			mNotification.iconLevel=IC_LEVEL_ORANGE;
			mNotification.when=System.currentTimeMillis();
			mNotification.setLatestEventInfo(this
					, NOTIFICATION_TITLE
					,String.format(getString(R.string.notification_registered),lc.getDefaultProxyConfig().getIdentity())
					, mNofificationContentIntent);
			mNotificationManager.notify(NOTIFICATION_ID, mNotification);
		}
		if (state == LinphoneCore.RegistrationState.RegistrationFailed ) {
			mNotification.iconLevel=IC_LEVEL_OFFLINE;
			mNotification.when=System.currentTimeMillis();
			mNotification.setLatestEventInfo(this
					, NOTIFICATION_TITLE
					,String.format(getString(R.string.notification_register_failure),lc.getDefaultProxyConfig().getIdentity())
					, mNofificationContentIntent);
			mNotificationManager.notify(NOTIFICATION_ID, mNotification);
		}
		mHandler.post(new Runnable() {
			public void run() {
				if (DialerActivity.getDialer()!=null) DialerActivity.getDialer().registrationState(lc,cfg,state,smessage);
			}
		});
	}
	public void callState(final LinphoneCore lc,final LinphoneCall call, final State state, final String message) {
		Log.i(TAG, "new state ["+state+"]");
		if (state == LinphoneCall.State.IncomingReceived && !call.equals(mLinphoneCore.getCurrentCall())) {
			if (call.getReplacedCall()==null){
				//no multicall support, just decline
				mLinphoneCore.terminateCall(call);
			}//otherwise it will be accepted automatically.
			
			return;
		}
		mHandler.post(new Runnable() {
			public void run() {
				if (DialerActivity.getDialer()!=null) DialerActivity.getDialer().callState(lc,call,state,message);
			}
		});
		if (state == LinphoneCall.State.IncomingReceived) {
			//wakeup linphone
			Intent lIntent = new Intent();
			lIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			lIntent.setClass(this, LinphoneActivity.class);
			startActivity(lIntent);
			startRinging();
		}
		if (mCurrentCallState ==  LinphoneCall.State.IncomingReceived) { 
			//previous state was ringing, so stop ringing
			stopRinging();
			//routeAudioToReceiver();
		}
		mCurrentCallState=state;
	}
	public void show(LinphoneCore lc) {
		// TODO Auto-generated method stub

	}


	private void enableDisableAudioCodec(String codec, int rate, int key) throws LinphoneCoreException {
		PayloadType pt = mLinphoneCore.findPayloadType(codec, rate);
		if (pt !=null) {
			boolean enable= mPref.getBoolean(getString(key),false);
			mLinphoneCore.enablePayloadType(pt, enable);
		}
	}

	private void enableDisableVideoCodecs(PayloadType videoCodec) throws LinphoneCoreException {
		String mime = videoCodec.getMime();
		int key;
		
		if ("MP4V-ES".equals(mime)) {
			key = R.string.pref_video_codec_mpeg4_key;
		} else if ("H264".equals(mime)) {
			key = R.string.pref_video_codec_h264_key;
		} else if ("H263-1998".equals(mime)) {
			key = R.string.pref_video_codec_h263_key;
		} else {
			Log.e(TAG, "Unhandled video codec " + mime);
			mLinphoneCore.enablePayloadType(videoCodec, false);
			return;
		}

		boolean enable= mPref.getBoolean(getString(key),false);
		mLinphoneCore.enablePayloadType(videoCodec, enable);
	}

	public void initFromConf() throws LinphoneConfigException, LinphoneException {
		//traces
		boolean lIsDebug = mPref.getBoolean(getString(R.string.pref_debug_key), false);
		LinphoneCoreFactory.instance().setDebugMode(lIsDebug);
		
		try {
			// Configure audio codecs
			enableDisableAudioCodec("speex", 32000, R.string.pref_codec_speex32_key);
			enableDisableAudioCodec("speex", 16000, R.string.pref_codec_speex16_key);
			enableDisableAudioCodec("speex", 8000, R.string.pref_codec_speex8_key);
			enableDisableAudioCodec("iLBC", 8000, R.string.pref_codec_ilbc_key);
			enableDisableAudioCodec("GSM", 8000, R.string.pref_codec_gsm_key);
			enableDisableAudioCodec("PCMU", 8000, R.string.pref_codec_pcmu_key);
			enableDisableAudioCodec("PCMA", 8000, R.string.pref_codec_pcma_key);
			
			// Configure video codecs
			for (PayloadType videoCodec : mLinphoneCore.listVideoCodecs()) {
				enableDisableVideoCodecs(videoCodec);
			}
			
	           
	        mLinphoneCore.enableEchoCancellation(mPref.getBoolean(getString(R.string.pref_echo_cancellation_key),false)); 
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(getString(R.string.wrong_settings),e);
		}
		boolean isVideoEnabled = mPref.getBoolean(getString(R.string.pref_video_enable_key),false);
		mLinphoneCore.enableVideo(isVideoEnabled, isVideoEnabled);
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

		String lStun = mPref.getString(getString(R.string.pref_stun_server_key), null);

		//stun server
		mLinphoneCore.setStunServer(lStun);
		mLinphoneCore.setFirewallPolicy((lStun!=null && lStun.length()>0) ? FirewallPolicy.UseStun : FirewallPolicy.NoFirewall);
		
		//auth
		mLinphoneCore.clearAuthInfos();
		LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(lUserName, lPasswd,null);
		mLinphoneCore.addAuthInfo(lAuthInfo);


		//proxy
		mLinphoneCore.clearProxyConfigs();
		String lProxy = mPref.getString(getString(R.string.pref_proxy_key),null);
		if (lProxy == null || lProxy.length() == 0) {
			lProxy = "sip:"+lDomain;
		}
		if (!lProxy.startsWith("sip:")) {
			lProxy = "sip:"+lProxy;
		}
		//get Default proxy if any
		LinphoneProxyConfig lDefaultProxyConfig = mLinphoneCore.getDefaultProxyConfig();
		String lIdentity = "sip:"+lUserName+"@"+lDomain;
		try {
			if (lDefaultProxyConfig == null) {
				lDefaultProxyConfig = LinphoneCoreFactory.instance().createProxyConfig(lIdentity, lProxy, null,true);
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
				if (lPrefix != null) {
					lDefaultProxyConfig.setDialPrefix(lPrefix);
				}
				//escape +
				lDefaultProxyConfig.setDialEscapePlus(mPref.getBoolean(getString(R.string.pref_escape_plus_key),false));
				//outbound proxy
				if (mPref.getBoolean(getString(R.string.pref_enable_outbound_proxy_key), false)) {
					lDefaultProxyConfig.setRoute(lProxy);
				} else {
					lDefaultProxyConfig.setRoute(null);
				}
				
			}
			//init network state
			ConnectivityManager lConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo lInfo = lConnectivityManager.getActiveNetworkInfo();
			mLinphoneCore.setNetworkReachable( lInfo !=null? lConnectivityManager.getActiveNetworkInfo().getState() ==NetworkInfo.State.CONNECTED:false); 
			
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
		mNotificationManager.cancel(NOTIFICATION_ID);
		unregisterReceiver(mKeepAliveMgrReceiver);
	}
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf,
			String url) {
		// TODO Auto-generated method stub
		
	}
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
		// TODO Auto-generated method stub
		
	}
	public void textReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneAddress from, String message) {
		// TODO Auto-generated method stub
		
	}

	public static LinphoneCore getLc() {
		return instance().getLinphoneCore();
	}

	private synchronized void startRinging()  {
		try {
			if (mAudioManager.shouldVibrate(AudioManager.VIBRATE_TYPE_RINGER) && mVibrator !=null) {
				long[] patern = {0,1000,1000};
				mVibrator.vibrate(patern, 1);
			}
			if (mRingerPlayer == null) {
				mRingerPlayer = new MediaPlayer();
				mRingerPlayer.setAudioStreamType(AudioManager.STREAM_RING);
				mRingerPlayer.setDataSource(getApplicationContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
				mRingerPlayer.prepare();
				mRingerPlayer.setLooping(true);
				mRingerPlayer.start();
			} else {
				Log.w(LinphoneService.TAG,"already ringing");
			}
		} catch (Exception e) {
			Log.e(LinphoneService.TAG, "cannot handle incoming call",e);
		}

	}
	private synchronized void stopRinging() {
		if (mRingerPlayer !=null) {
			mRingerPlayer.stop();
			mRingerPlayer.release();
			mRingerPlayer=null;
		}
		if (mVibrator!=null) {
			mVibrator.cancel();
		}
	}
	public void ecCalibrationStatus(final LinphoneCore lc,final EcCalibratorStatus status, final int delay_ms,
			final Object data) {
		final CheckBoxPreference pref = (CheckBoxPreference) data;
		mHandler.post(new Runnable() {
			public void run() {
				 if (status == EcCalibratorStatus.Done) {
					pref.setSummary(String.format(getString(R.string.ec_calibrated), delay_ms));
					pref.setChecked(true);
					
				} else if (status == EcCalibratorStatus.Failed) {
					pref.setSummary(R.string.failed);
					pref.setChecked(false);
				}
			}
		});
	}
}

