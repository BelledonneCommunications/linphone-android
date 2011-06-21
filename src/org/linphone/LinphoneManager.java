/*
LinphoneManager.java
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

import static android.media.AudioManager.MODE_IN_CALL;
import static android.media.AudioManager.MODE_NORMAL;
import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.ROUTE_EARPIECE;
import static android.media.AudioManager.ROUTE_SPEAKER;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;
import static android.media.AudioManager.VIBRATE_TYPE_RINGER;
import static org.linphone.R.string.pref_codec_amr_key;
import static org.linphone.R.string.pref_codec_ilbc_key;
import static org.linphone.R.string.pref_codec_speex16_key;
import static org.linphone.R.string.pref_codec_speex32_key;
import static org.linphone.R.string.pref_echo_cancellation_key;
import static org.linphone.R.string.pref_video_enable_key;
import static org.linphone.core.LinphoneCall.State.CallEnd;
import static org.linphone.core.LinphoneCall.State.Error;
import static org.linphone.core.LinphoneCall.State.IncomingReceived;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.core.Hacks;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.Log;
import org.linphone.core.PayloadType;
import org.linphone.core.Version;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.FirewallPolicy;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.video.AndroidCameraRecordManager;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.hardware.SensorEvent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

/**
 * 
 * Manager of the low level LibLinphone stuff.<br />
 * Including:<ul>
 * <li>Starting C liblinphone</li>
 * <li>Reacting to C liblinphone state changes</li>
 * <li>Calling Linphone android service listener methods</li>
 * <li>Interacting from Android GUI/service with low level SIP stuff/</li>
 * </ul> 
 * 
 * Add Service Listener to react to Linphone state changes.
 * 
 * @author Guillaume Beraudo
 *
 */
public final class LinphoneManager implements LinphoneCoreListener {

	private static LinphoneManager instance;
	private AudioManager mAudioManager;
	private PowerManager mPowerManager;
	private SharedPreferences mPref;
	private Resources mR;
	private LinphoneCore mLc;
	private static Transports initialTransports;
	private static LinphonePreferenceManager lpm;
	private String lastLcStatusMessage;


	
	private LinphoneManager(final Context c) {
		String basePath = c.getFilesDir().getAbsolutePath();
		linphoneInitialConfigFile = basePath + "/linphonerc";
		linphoneConfigFile = basePath + "/.linphonerc";
		ringSoundFile = basePath + "/oldphone_mono.wav"; 
		ringbackSoundFile = basePath + "/ringback.wav";

		lpm = LinphonePreferenceManager.getInstance(c);
		mAudioManager = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE));
		mVibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
		mPref = PreferenceManager.getDefaultSharedPreferences(c);
		mPowerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
		mR = c.getResources();

		AndroidCameraRecordManager.getInstance().startOrientationSensor(c.getApplicationContext());
	}
	
	private static final int LINPHONE_VOLUME_STREAM = STREAM_VOICE_CALL;
	private static final int dbStep = 4;
	/** Called when the activity is first created. */
	private final String linphoneInitialConfigFile;
	private final String linphoneConfigFile;
	private final String ringSoundFile; 
	private final String ringbackSoundFile;

	private Timer mTimer = new Timer("Linphone scheduler");

	private  BroadcastReceiver mKeepAliveReceiver = new KeepAliveReceiver();

	private synchronized void routeAudioToSpeakerHelper(boolean speakerOn) {
		LinphoneCall call = mLc.getCurrentCall();
		boolean paused = false;
		if (call != null && call.getState() == State.StreamsRunning && Hacks.needPausingCallForSpeakers()) {
			Log.d("Hack pausing call to have speaker=",speakerOn);
			mLc.pauseCall(call);
			paused = true;
		}

		if (Hacks.needGalaxySAudioHack() || lpm.useGalaxySHack())
			setAudioModeIncallForGalaxyS();

		if (lpm.useSpecificAudioModeHack() != -1)
			mAudioManager.setMode(lpm.useSpecificAudioModeHack());

		if (Hacks.needRoutingAPI() || lpm.useAudioRoutingAPIHack()) {
			mAudioManager.setRouting(
					MODE_NORMAL,
					speakerOn? ROUTE_SPEAKER : ROUTE_EARPIECE,
					AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(speakerOn); 
		}

		if (paused) {
			Log.d("Hack resuming call to have speaker=",speakerOn);
			mLc.resumeCall(call);
		}
	}
	
	public void routeAudioToSpeaker() {
		routeAudioToSpeakerHelper(true);
		if (mLc.isIncall()) {
			/*disable EC*/  
			mLc.getCurrentCall().enableEchoCancellation(false);
			mLc.getCurrentCall().enableEchoLimiter(true);
		}
		
	}

	public void routeAudioToReceiver() {
		routeAudioToSpeakerHelper(false);
		if (mLc.isIncall()) {
			//Restore default value
			mLc.getCurrentCall().enableEchoCancellation(mLc.isEchoCancellationEnabled());
			mLc.getCurrentCall().enableEchoLimiter(false);
		}
	}
	
	public synchronized static final LinphoneManager createAndStart(
			Context c, LinphoneServiceListener listener) {
		if (instance != null)
			throw new RuntimeException("Linphone Manager is already initialized");

		instance = new LinphoneManager(c);
		instance.serviceListener = listener;
		instance.startLibLinphone(c);
		return instance;
	}
	
	public static final LinphoneManager getInstance() {
		if (instance != null) return instance;

		throw new RuntimeException("Linphone Manager should be created before accessed");
	}
	
	public static final LinphoneCore getLc() {
		return getInstance().mLc;
	}


	
	public boolean isSpeakerOn() {
		return (Integer.parseInt(Build.VERSION.SDK) <=4 && mAudioManager.getRouting(MODE_NORMAL) == ROUTE_SPEAKER) 
		|| Integer.parseInt(Build.VERSION.SDK) >4 &&mAudioManager.isSpeakerphoneOn();
	}

	
	public void newOutgoingCall(AddressType address) {
		String to = address.getText().toString();

		if (mLc.isIncall()) {
			serviceListener.tryingNewOutgoingCallButAlreadyInCall();
			return;
		}
		LinphoneAddress lAddress;
		try {
			lAddress = mLc.interpretUrl(to);
		} catch (LinphoneCoreException e) {
			serviceListener.tryingNewOutgoingCallButWrongDestinationAddress();
			return;
		}
		lAddress.setDisplayName(address.getDisplayedName());

		try {
			boolean prefVideoEnable = isVideoEnabled();
			boolean prefInitiateWithVideo = mPref.getBoolean(mR.getString(R.string.pref_video_initiate_call_with_video_key), false);
			resetCameraFromPreferences();
			CallManager.getInstance().inviteAddress(lAddress, prefVideoEnable && prefInitiateWithVideo);

		} catch (LinphoneCoreException e) {
			serviceListener.tryingNewOutgoingCallButCannotGetCallParameters();
			return;
		}
	}

	
	public void resetCameraFromPreferences() {
		boolean useFrontCam = mPref.getBoolean(mR.getString(R.string.pref_video_use_front_camera_key), false);
		AndroidCameraRecordManager.getInstance().setUseFrontCamera(useFrontCam);
	}

	public static interface AddressType {
		void setText(CharSequence s);
		CharSequence getText();
		void setDisplayedName(String s);
		String getDisplayedName();
	}


	public static interface NewOutgoingCallUiListener {
		public void onWrongDestinationAddress();
		public void onCannotGetCallParameters();
		public void onAlreadyInCall();
	}


	public void sendStaticImage(boolean send) {
		if (mLc.isIncall()) {
			mLc.getCurrentCall().enableCamera(!send);
		}
	}

	public void playDtmf(ContentResolver r, char dtmf) {
		try {
			if (Settings.System.getInt(r, Settings.System.DTMF_TONE_WHEN_DIALING) == 0) {
				// audible touch disabled: don't play on speaker, only send in outgoing stream
				return;
			}
		} catch (SettingNotFoundException e) {}

		getLc().playDtmf(dtmf, -1);
	}

	
	public void changeResolution() {
		BandwidthManager manager = BandwidthManager.getInstance();
		manager.setUserRestriction(!manager.isUserRestriction());
	}

	public void terminateCall() {
		if (mLc.isIncall()) {
			mLc.terminateCall(mLc.getCurrentCall());
		}
	}

	/**
	 * Camera will be restarted when mediastreamer chain is recreated and setParameters is called.
	 */
	public void switchCamera() {
		AndroidCameraRecordManager.getInstance().stopVideoRecording();
		AndroidCameraRecordManager.getInstance().toggleUseFrontCamera();
		CallManager.getInstance().updateCall();
	}

	public void toggleCameraMuting() {
		AndroidCameraRecordManager rm = AndroidCameraRecordManager.getInstance();
		sendStaticImage(rm.toggleMute());
	}

	private synchronized void startLibLinphone(final Context context) {
		try {
			copyAssetsFromPackage(context);

			mLc = LinphoneCoreFactory.instance().createLinphoneCore(
					this, linphoneConfigFile, linphoneInitialConfigFile, null);

			mLc.enableIpv6(mPref.getBoolean(getString(R.string.pref_ipv6_key), false));

			mLc.setPlaybackGain(3);   
			mLc.setRing(null);

			try {
				initFromConf(context);
			} catch (LinphoneException e) {
				Log.w("no config ready yet");
			}
			TimerTask lTask = new TimerTask() {
				@Override
				public void run() {
					mLc.iterate();
				}
			};

			mTimer.scheduleAtFixedRate(lTask, 0, 100); 

			IntentFilter lFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
	        lFilter.addAction(Intent.ACTION_SCREEN_OFF);
			context.registerReceiver(mKeepAliveReceiver, lFilter);
		}
		catch (Exception e) {
			Log.e(e,"Cannot start linphone");
		}
	}
	
	private void copyAssetsFromPackage(Context context) throws IOException {
		copyIfNotExist(context, R.raw.oldphone_mono,ringSoundFile);
		copyIfNotExist(context, R.raw.ringback,ringbackSoundFile);
		copyFromPackage(context, R.raw.linphonerc, new File(linphoneInitialConfigFile).getName());
	}
	private  void copyIfNotExist(Context context, int ressourceId,String target) throws IOException {
		File lFileToCopy = new File(target);
		if (!lFileToCopy.exists()) {		
		   copyFromPackage(context, ressourceId,lFileToCopy.getName()); 
		}
	}
	private void copyFromPackage(Context context, int ressourceId,String target) throws IOException{
		FileOutputStream lOutputStream = context.openFileOutput (target, 0); 
		InputStream lInputStream = mR.openRawResource(ressourceId);
		int readByte;
		byte[] buff = new byte[8048];
		while (( readByte = lInputStream.read(buff))!=-1) {
			lOutputStream.write(buff,0, readByte);
		}
		lOutputStream.flush();
		lOutputStream.close();
		lInputStream.close();
	}

	public boolean detectVideoCodec(String mime) {
		for (PayloadType videoCodec : mLc.getVideoCodecs()) {
			if (mime.equals(videoCodec.getMime())) return true;
		}
		return false;
	}
	
	public boolean detectAudioCodec(String mime){
		for (PayloadType audioCodec : mLc.getAudioCodecs()) {
			if (mime.equals(audioCodec.getMime())) return true;
		}
		return false;
	}

	public void initFromConf(Context context) throws LinphoneConfigException {
		//traces
		boolean lIsDebug = mPref.getBoolean(getString(R.string.pref_debug_key), false);
		LinphoneCoreFactory.instance().setDebugMode(lIsDebug);
		
		if (initialTransports == null)
			initialTransports = mLc.getSignalingTransportPorts();
		
		setSignalingTransportsFromConfiguration(initialTransports);
		
		
		try {
			// Configure audio codecs
			enableDisableAudioCodec("speex", 32000, R.string.pref_codec_speex32_key);
			enableDisableAudioCodec("speex", 16000, R.string.pref_codec_speex16_key);
			enableDisableAudioCodec("speex", 8000, R.string.pref_codec_speex8_key);
			enableDisableAudioCodec("iLBC", 8000, R.string.pref_codec_ilbc_key);
			enableDisableAudioCodec("GSM", 8000, R.string.pref_codec_gsm_key);
			enableDisableAudioCodec("PCMU", 8000, R.string.pref_codec_pcmu_key);
			enableDisableAudioCodec("PCMA", 8000, R.string.pref_codec_pcma_key);
			enableDisableAudioCodec("AMR", 8000, R.string.pref_codec_amr_key);
			
			// Configure video codecs
			for (PayloadType videoCodec : mLc.getVideoCodecs()) {
				enableDisableVideoCodecs(videoCodec);
			}
			

			mLc.enableEchoCancellation(mPref.getBoolean(getString(R.string.pref_echo_cancellation_key),false)); 
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(getString(R.string.wrong_settings),e);
		}
		boolean isVideoEnabled = isVideoEnabled();
		mLc.enableVideo(isVideoEnabled, isVideoEnabled);
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
		mLc.setStunServer(lStun);
		mLc.setFirewallPolicy((lStun!=null && lStun.length()>0) ? FirewallPolicy.UseStun : FirewallPolicy.NoFirewall);
		
		//auth
		mLc.clearAuthInfos();
		LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(lUserName, lPasswd,null);
		mLc.addAuthInfo(lAuthInfo);


		//proxy
		mLc.clearProxyConfigs();
		String lProxy = mPref.getString(getString(R.string.pref_proxy_key),null);
		if (lProxy == null || lProxy.length() == 0) {
			lProxy = "sip:"+lDomain;
		}
		if (!lProxy.startsWith("sip:")) {
			lProxy = "sip:"+lProxy;
		}
		//get Default proxy if any
		LinphoneProxyConfig lDefaultProxyConfig = mLc.getDefaultProxyConfig();
		String lIdentity = "sip:"+lUserName+"@"+lDomain;
		try {
			if (lDefaultProxyConfig == null) {
				lDefaultProxyConfig = LinphoneCoreFactory.instance().createProxyConfig(lIdentity, lProxy, null,true);
				mLc.addProxyConfig(lDefaultProxyConfig);
				mLc.setDefaultProxyConfig(lDefaultProxyConfig);

			} else {
				lDefaultProxyConfig.edit();
				lDefaultProxyConfig.setIdentity(lIdentity);
				lDefaultProxyConfig.setProxy(lProxy);
				lDefaultProxyConfig.enableRegister(true);
				lDefaultProxyConfig.done();
			}
			lDefaultProxyConfig = mLc.getDefaultProxyConfig();

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
			ConnectivityManager lConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo lInfo = lConnectivityManager.getActiveNetworkInfo();
			mLc.setNetworkReachable( lInfo !=null? lConnectivityManager.getActiveNetworkInfo().getState() ==NetworkInfo.State.CONNECTED:false); 
			
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(getString(R.string.wrong_settings),e);
		}
	}
	
	private boolean getBool(int key, boolean defValue) {
		return mPref.getBoolean(getString(key), defValue);
	}

	private void setSignalingTransportsFromConfiguration(Transports t) {
		Transports ports = new Transports(t);
		boolean useStandardPort = getBool(R.string.pref_transport_use_standard_ports_key, false);

		if (!getBool(R.string.pref_transport_udp_key, false)) {
			ports.udp = 0;
		} else if (useStandardPort) {
			ports.udp = 5060;
		}
		
		if (!getBool(R.string.pref_transport_tcp_key, false)) {
			ports.tcp = 0;
		} else if (useStandardPort) {
			ports.tcp = 5060;
		}

		if (!getBool(R.string.pref_transport_tls_key, false)) {
			ports.tls = 0;
		} else if (useStandardPort) {
			ports.tls = 5060;
		}

		mLc.setSignalingTransportPorts(ports);
	}

	private void enableDisableAudioCodec(String codec, int rate, int key) throws LinphoneCoreException {
		PayloadType pt = mLc.findPayloadType(codec, rate);
		if (pt !=null) {
			boolean enable= mPref.getBoolean(getString(key),false);
			mLc.enablePayloadType(pt, enable);
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
			Log.e("Unhandled video codec ", mime);
			mLc.enablePayloadType(videoCodec, false);
			return;
		}

		boolean enable= mPref.getBoolean(getString(key),false);
		mLc.enablePayloadType(videoCodec, enable);
	}

	public static synchronized void destroy(Context context) {
		if (instance == null) return;

		try {
			instance.mTimer.cancel();
			instance.mLc.destroy();
			context.unregisterReceiver(instance.mKeepAliveReceiver);
		} finally {
			instance.mLc = null;
			instance = null;
		}
	}

	private String getString(int key) {
		return mR.getString(key);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public interface LinphoneServiceListener {
		void onGlobalStateChanged(GlobalState state, String message);
		void tryingNewOutgoingCallButCannotGetCallParameters();
		void tryingNewOutgoingCallButWrongDestinationAddress();
		void tryingNewOutgoingCallButAlreadyInCall();
		void onRegistrationStateChanged(RegistrationState state, String message);
		void onCallStateChanged(LinphoneCall call, State state, String message);
		void onRingerPlayerCreated(MediaPlayer mRingerPlayer);
		void onDisplayStatus(String message);
		void onAlreadyInVideoCall();
	}

	public interface EcCalibrationListener {
		void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs);
	}

	private LinphoneServiceListener serviceListener;
	private LinphoneCall.State mCurrentCallState;

	private MediaPlayer mRingerPlayer;
	private Vibrator mVibrator;

	

	public void displayWarning(LinphoneCore lc, String message) {}
	public void authInfoRequested(LinphoneCore lc, String realm, String username) {}
	public void byeReceived(LinphoneCore lc, String from) {}
	public void displayMessage(LinphoneCore lc, String message) {}
	public void show(LinphoneCore lc) {}
	public void newSubscriptionRequest(LinphoneCore lc,LinphoneFriend lf,String url) {}
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {}
	public void textReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneAddress from, String message) {}


	public String getLastLcStatusMessage() {
		return lastLcStatusMessage;
	}
	public void displayStatus(final LinphoneCore lc, final String message) {
		Log.i(message);
		lastLcStatusMessage=message;
		serviceListener.onDisplayStatus(message);
	}


	public void globalState(final LinphoneCore lc, final LinphoneCore.GlobalState state, final String message) {
		Log.i("new state [",state,"]");
		serviceListener.onGlobalStateChanged(state, message);
	}



	public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig cfg,final LinphoneCore.RegistrationState state,final String message) {
		Log.i("new state ["+state+"]");
		serviceListener.onRegistrationStateChanged(state, message);
	}


	public void callState(final LinphoneCore lc,final LinphoneCall call, final State state, final String message) {
		Log.i("new state [",state,"]");
		if (state == IncomingReceived && !call.equals(lc.getCurrentCall())) {
			if (call.getReplacedCall()==null){
				//no multicall support, just decline
				lc.terminateCall(call);
			}//otherwise it will be accepted automatically.
			
			return;
		}

		if (state == IncomingReceived) {
			// Brighten screen for at least 10 seconds
			WakeLock wl = mPowerManager.newWakeLock(
					PowerManager.ACQUIRE_CAUSES_WAKEUP
					|PowerManager.ON_AFTER_RELEASE
					|PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
					"incoming_call");
			wl.acquire(10000);

			startRinging();
		}

		if (mCurrentCallState == IncomingReceived) { 
			//previous state was ringing, so stop ringing
			stopRinging();
			routeAudioToReceiver();
		}
		
		if (state == CallEnd || state == Error) {
			mAudioManager.setMode(MODE_NORMAL);
		}

		if (state == State.Connected) {
			if (Hacks.needSoftvolume() || LinphonePreferenceManager.getInstance().useSoftvolume()) {
				adjustSoftwareVolume(0); // Synchronize
			}
		}

		mCurrentCallState=state;
		serviceListener.onCallStateChanged(call, state, message);
	}


	public void ecCalibrationStatus(final LinphoneCore lc,final EcCalibratorStatus status, final int delayMs,
			final Object data) {
		EcCalibrationListener listener = (EcCalibrationListener) data;
		listener.onEcCalibrationStatus(status, delayMs);
	}
	


	public void startEcCalibration(EcCalibrationListener l) throws LinphoneCoreException {
		int oldVolume = mAudioManager.getStreamVolume(STREAM_VOICE_CALL);
		int maxVolume = mAudioManager.getStreamMaxVolume(STREAM_VOICE_CALL);
		mAudioManager.setStreamVolume(STREAM_VOICE_CALL, maxVolume, 0);

		mLc.startEchoCalibration(l);

		mAudioManager.setStreamVolume(STREAM_VOICE_CALL, oldVolume, 0);
	}









	private synchronized void startRinging()  {
		if (Hacks.needGalaxySAudioHack()) {
			mAudioManager.setMode(MODE_RINGTONE);
		}

		try {
			if (mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER) && mVibrator !=null) {
				long[] patern = {0,1000,1000};
				mVibrator.vibrate(patern, 1);
			}
			if (mRingerPlayer == null) {
				mRingerPlayer = new MediaPlayer();
				mRingerPlayer.setAudioStreamType(STREAM_RING);
				serviceListener.onRingerPlayerCreated(mRingerPlayer);
				mRingerPlayer.prepare();
				mRingerPlayer.setLooping(true);
				mRingerPlayer.start();
			} else {
				Log.w("already ringing");
			}
		} catch (Exception e) {
			Log.e(e,"cannot handle incoming call");
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

		// You may need to call galaxys audio hack after this method
	}

	
	public String extractADisplayName() {
		final LinphoneAddress remote = mLc.getRemoteAddress();
		if (remote == null) return mR.getString(R.string.unknown_incoming_call_name);

		final String displayName = remote.getDisplayName();
		if (displayName!=null) {
			return displayName;
		} else  if (remote.getUserName() != null){
			return remote.getUserName();
		} else {
			String rms = remote.toString();
			if (rms != null && rms.length() > 1)
				return rms;
				
			return mR.getString(R.string.unknown_incoming_call_name);
		}
	}

	public static boolean reinviteWithVideo() {
		return CallManager.getInstance().reinviteWithVideo();
	}

	public boolean isVideoEnabled() {
		return mPref.getBoolean(getString(R.string.pref_video_enable_key), false);
	}

	public void setAudioModeIncallForGalaxyS() {
		stopRinging();
		mAudioManager.setMode(MODE_IN_CALL);
	}

	// Called on first launch only
	public void initializePayloads() {
		Log.i("Initializing supported payloads");
		Editor e = mPref.edit();
		boolean fastCpu = Version.isArmv7();

		e.putBoolean(getString(pref_echo_cancellation_key), fastCpu);

		e.putBoolean(getString(R.string.pref_codec_gsm_key), true);
		e.putBoolean(getString(R.string.pref_codec_pcma_key), true);
		e.putBoolean(getString(R.string.pref_codec_pcmu_key), true);
		e.putBoolean(getString(R.string.pref_codec_speex8_key), true);
		e.putBoolean(getString(pref_codec_speex16_key), fastCpu);
		e.putBoolean(getString(pref_codec_speex32_key), fastCpu);

		boolean ilbc = LinphoneService.isReady() && LinphoneManager.getLc()
		.findPayloadType("iLBC", 8000)!=null;
		e.putBoolean(getString(pref_codec_ilbc_key), ilbc);
		
		boolean amr = LinphoneService.isReady() && LinphoneManager.getLc()
		.findPayloadType("AMR", 8000)!=null;
		e.putBoolean(getString(pref_codec_amr_key), amr);

		if (Version.sdkStrictlyBelow(5) || !Version.hasNeon() || !Hacks.hasCamera()) {
			e.putBoolean(getString(pref_video_enable_key), false);
		}
		
		e.commit();
	}

	public void addVideo() {
		if (!LinphoneManager.getLc().isIncall()) return;
		if (!reinviteWithVideo()) {
			serviceListener.onAlreadyInVideoCall();
		}
	}

	public boolean acceptCallIfIncomingPending() throws LinphoneCoreException {
		if (Hacks.needGalaxySAudioHack() || lpm.useGalaxySHack())
			setAudioModeIncallForGalaxyS();
		
		if (mLc.isInComingInvitePending()) {
			mLc.acceptCall(mLc.getCurrentCall());
			return true;
		}
		return false;
	}

	public String extractIncomingRemoteName() {
		if (!mR.getBoolean(R.bool.show_full_remote_address_on_incoming_call))
			return extractADisplayName();

		LinphoneAddress remote = mLc.getRemoteAddress();
		if (remote != null)
			return remote.toString();

		return mR.getString(R.string.unknown_incoming_call_name);
	}

	public void adjustSoftwareVolume(int i) {
		int oldVolume = mAudioManager.getStreamVolume(LINPHONE_VOLUME_STREAM);
		int maxVolume = mAudioManager.getStreamMaxVolume(LINPHONE_VOLUME_STREAM);

		int nextVolume = oldVolume +i;
		if (nextVolume > maxVolume) nextVolume = maxVolume;
		if (nextVolume < 0) nextVolume = 0;

		mLc.adjustSoftwareVolume((nextVolume - maxVolume)* dbStep);
	}

	public static Boolean isProximitySensorNearby(final SensorEvent event) {
		float threshold = 4.001f; // <= 4 cm is near

		final float distanceInCm = event.values[0];
		final float maxDistance = event.sensor.getMaximumRange();
		Log.d("Proximity sensor report [",distanceInCm,"] , for max range [",maxDistance,"]");

		if (maxDistance <= threshold) {
			// Case binary 0/1 and short sensors
			threshold = maxDistance;
		}

		return distanceInCm < threshold;
	}

}
