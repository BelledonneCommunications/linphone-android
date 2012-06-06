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
import static org.linphone.R.string.pref_codec_amrwb_key;
import static org.linphone.R.string.pref_codec_ilbc_key;
import static org.linphone.R.string.pref_codec_speex16_key;
import static org.linphone.R.string.pref_codec_speex32_key;
import static org.linphone.R.string.pref_video_enable_key;
import static org.linphone.core.LinphoneCall.State.CallEnd;
import static org.linphone.core.LinphoneCall.State.Error;
import static org.linphone.core.LinphoneCall.State.IncomingReceived;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.LinphoneSimpleListener.LinphoneOnAudioChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnAudioChangedListener.AudioState;
import org.linphone.LinphoneSimpleListener.LinphoneServiceListener;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.FirewallPolicy;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.Log;
import org.linphone.core.PayloadType;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.AndroidVideoApi5JniWrapper;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;
import org.linphone.mediastream.video.capture.hwconf.Hacks;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

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
	private Context mServiceContext;
	private AudioManager mAudioManager;
	private PowerManager mPowerManager;
	private ConnectivityManager mConnectivityManager;
	private SharedPreferences mPref;
	private Resources mR;
	private LinphoneCore mLc;
	private static Transports initialTransports;
	private static LinphonePreferenceManager sLPref;
	private String lastLcStatusMessage;
	private String basePath;
	private static boolean sExited;
	private boolean videoInitiator = false;

	private WakeLock mIncallWakeLock;

	private static List<LinphoneSimpleListener> simpleListeners = new ArrayList<LinphoneSimpleListener>();
	public static void addListener(LinphoneSimpleListener listener) {
		if (!simpleListeners.contains(listener)) {
			simpleListeners.add(listener);
		}
	}
	public static void removeListener(LinphoneSimpleListener listener) {
		simpleListeners.remove(listener);
	}
	
	public boolean isVideoInitiator() {
		return videoInitiator;
	}
	
	public void setVideoInitiator(boolean b) {
		videoInitiator = b;
	}


	private LinphoneManager(final Context c, LinphoneServiceListener listener) {
		sExited=false;
		mServiceContext = c;
		mListenerDispatcher = new ListenerDispatcher(listener);
		basePath = c.getFilesDir().getAbsolutePath();
		mLinphoneInitialConfigFile = basePath + "/linphonerc";
		mLinphoneConfigFile = basePath + "/.linphonerc";
		mLinphoneRootCaFile = basePath + "/rootca.pem";
		mRingSoundFile = basePath + "/oldphone_mono.wav"; 
		mRingbackSoundFile = basePath + "/ringback.wav";
		mPauseSoundFile = basePath + "/toy_mono.wav";

		sLPref = LinphonePreferenceManager.getInstance(c);
		mAudioManager = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE));
		mVibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
		mPref = PreferenceManager.getDefaultSharedPreferences(c);
		mPowerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
		mConnectivityManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
		mR = c.getResources();
	}

	private static final int LINPHONE_VOLUME_STREAM = STREAM_VOICE_CALL;
	private static final int dbStep = 4;
	/** Called when the activity is first created. */
	private final String mLinphoneInitialConfigFile;
	private final String mLinphoneRootCaFile;
	private final String mLinphoneConfigFile;
	private final String mRingSoundFile; 
	private final String mRingbackSoundFile;
	private final String mPauseSoundFile;

	private Timer mTimer = new Timer("Linphone scheduler");

	private  BroadcastReceiver mKeepAliveReceiver = new KeepAliveReceiver();

	private native void hackSpeakerState(boolean speakerOn);
	private static void sRouteAudioToSpeakerHelperHelper(boolean speakerOn) {
		getInstance().routeAudioToSpeakerHelperHelper(speakerOn);
	}
	private void routeAudioToSpeakerHelperHelper(boolean speakerOn) {
		boolean different = isSpeakerOn() ^ speakerOn;
		if (!different) {
			Log.d("Skipping change audio route by the same route ",
					speakerOn ? "speaker" : "earpiece");
			return;
		}
		if (Hacks.needGalaxySAudioHack() || sLPref.useGalaxySHack())
			setAudioModeIncallForGalaxyS();

		if (sLPref.useSpecificAudioModeHack() != -1)
			mAudioManager.setMode(sLPref.useSpecificAudioModeHack());

		if (Hacks.needRoutingAPI() || sLPref.useAudioRoutingAPIHack()) {
			mAudioManager.setRouting(
					MODE_NORMAL,
					speakerOn? ROUTE_SPEAKER : ROUTE_EARPIECE,
					AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(speakerOn); 
		}
		for (LinphoneOnAudioChangedListener listener : getSimpleListeners(LinphoneOnAudioChangedListener.class)) {
			listener.onAudioStateChanged(speakerOn ? AudioState.SPEAKER : AudioState.EARPIECE);
		}
	}
	private synchronized void routeAudioToSpeakerHelper(boolean speakerOn) {
		final LinphoneCall call = mLc.getCurrentCall();
		if (call != null && call.getState() == State.StreamsRunning && Hacks.needPausingCallForSpeakers()) {
			Log.d("Hack to have speaker=",speakerOn," while on call");
			hackSpeakerState(speakerOn);
		} else {
			routeAudioToSpeakerHelperHelper(speakerOn);
		}
	}

	/**
	 * 
	 * @param isUserRequest true if the setting is permanent, otherwise it can be lost
	 * 	eg: video activity imply speaker on, which is not a request from the user.
	 * 	when the activity stops, the sound is routed to the previously user requested route.
	 */
	public void routeAudioToSpeaker() {
		routeAudioToSpeakerHelper(true);
		LinphoneCall currentCall = mLc.getCurrentCall();
		if (currentCall != null && !Hacks.hasBuiltInEchoCanceller()) {
			/*disable EC, it is not efficient enough on speaker mode due to bad quality of speakers and saturation*/  
			currentCall.enableEchoCancellation(false);
			/* instead we prefer the echo limiter */
			currentCall.enableEchoLimiter(true);
		}
	}

	/**
	 * 
	 */
	public void routeAudioToReceiver() {
		routeAudioToSpeakerHelper(false);
		LinphoneCall call=mLc.getCurrentCall();
		if (call!=null && !Hacks.hasBuiltInEchoCanceller()) {
			//Restore default value
			call.enableEchoCancellation(mLc.isEchoCancellationEnabled());
			call.enableEchoLimiter(mLc.isEchoLimiterEnabled());
		}
	}

	public synchronized static final LinphoneManager createAndStart(
			Context c, LinphoneServiceListener listener) {
		if (instance != null)
			throw new RuntimeException("Linphone Manager is already initialized");

		instance = new LinphoneManager(c, listener);
		instance.startLibLinphone();
		TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
		boolean gsmIdle = tm.getCallState() == TelephonyManager.CALL_STATE_IDLE;
		setGsmIdle(gsmIdle);
		
		if (Version.isVideoCapable())
			AndroidVideoApi5JniWrapper.setAndroidSdkVersion(Version.sdk());
		return instance;
	}

	public static synchronized final LinphoneManager getInstance() {
		if (instance != null) return instance;

		if (sExited) {
			throw new RuntimeException("Linphone Manager was already destroyed. "
					+ "Better use getLcIfManagerNotDestroyed and check returned value");
		}

		throw new RuntimeException("Linphone Manager should be created before accessed");
	}
	
	public static synchronized final LinphoneCore getLc() {
		return getInstance().mLc;
	}


	
	public boolean isSpeakerOn() {
		if (Hacks.needRoutingAPI() || sLPref.useAudioRoutingAPIHack()) {
			return mAudioManager.getRouting(MODE_NORMAL) == ROUTE_SPEAKER;
		} else {
			return mAudioManager.isSpeakerphoneOn(); 
		}
	}

	
	public void newOutgoingCall(AddressType address) {
		String to = address.getText().toString();

//		if (mLc.isIncall()) {
//			listenerDispatcher.tryingNewOutgoingCallButAlreadyInCall();
//			return;
//		}
		LinphoneAddress lAddress;
		try {
			lAddress = mLc.interpretUrl(to);
			if (mR.getBoolean(R.bool.forbid_self_call) && mLc.isMyself(lAddress.asStringUriOnly())) {
				mListenerDispatcher.tryingNewOutgoingCallButWrongDestinationAddress();
				return;
			}
		} catch (LinphoneCoreException e) {
			mListenerDispatcher.tryingNewOutgoingCallButWrongDestinationAddress();
			return;
		}
		lAddress.setDisplayName(address.getDisplayedName());

		try {
			if (Version.isVideoCapable()) {
				boolean prefVideoEnable = isVideoEnabled();
				int key = R.string.pref_video_initiate_call_with_video_key;
				boolean prefInitiateWithVideo = getPrefBoolean(key, false);
				CallManager.getInstance().inviteAddress(lAddress, prefVideoEnable && prefInitiateWithVideo);
			} else {
				CallManager.getInstance().inviteAddress(lAddress, false);
			}
			

		} catch (LinphoneCoreException e) {
			mListenerDispatcher.tryingNewOutgoingCallButCannotGetCallParameters();
			return;
		}
	}
	
	public void resetCameraFromPreferences() {
		boolean useFrontCam = getPrefBoolean(R.string.pref_video_use_front_camera_key, false);
		
		int camId = 0;
		AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
		for (AndroidCamera androidCamera : cameras) {
			if (androidCamera.frontFacing == useFrontCam)
				camId = androidCamera.id;
		}
		LinphoneManager.getLc().setVideoDevice(camId);
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

	public boolean toggleEnableCamera() { 
		if (mLc.isIncall()) {
			boolean enabled = !mLc.getCurrentCall().cameraEnabled();
			enableCamera(mLc.getCurrentCall(), enabled);
			return enabled;
		}
		return false;
	}
	public void enableCamera(LinphoneCall call, boolean enable) {
		if (call != null) {
			call.enableCamera(enable);
			LinphoneService.instance().refreshIncallIcon(mLc.getCurrentCall());
		}
	}

	public void sendStaticImage(boolean send) {
		if (mLc.isIncall()) {
			enableCamera(mLc.getCurrentCall(), !send);
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


	private boolean isTunnelNeeded(NetworkInfo info) {
		if (info == null) {
			Log.i("No connectivity: tunnel should be disabled");
			return false;
		}

		String pref = getPrefString(R.string.pref_tunnel_mode_key, R.string.default_tunnel_mode_entry_value);

		if (getString(R.string.tunnel_mode_entry_value_always).equals(pref)) {
			return true;
		}

		if (info.getType() != ConnectivityManager.TYPE_WIFI
				&& getString(R.string.tunnel_mode_entry_value_3G_only).equals(pref)) {
			Log.i("need tunnel: 'no wifi' connection");
			return true;
		}

		return false;
	}

	public void manageTunnelServer(NetworkInfo info) {
		if (mLc == null) return;
		if (!mLc.isTunnelAvailable()) return;

		Log.i("Managing tunnel");
		if (isTunnelNeeded(info)) {
			Log.i("Tunnel need to be activated");
			mLc.tunnelEnable(true);
		} else {
			Log.i("Tunnel should not be used");
			String pref = getPrefString(R.string.pref_tunnel_mode_key, R.string.default_tunnel_mode_entry_value);
			mLc.tunnelEnable(false);
			if (getString(R.string.tunnel_mode_entry_value_auto).equals(pref)) {
				mLc.tunnelAutoDetect();
			}
		}
	}


	private synchronized void startLibLinphone() {
		try {
			copyAssetsFromPackage();
			//traces alway start with traces enable to not missed first initialization
			;
			LinphoneCoreFactory.instance().setDebugMode(getPrefBoolean(R.string.pref_debug_key,true));
			
			mLc = LinphoneCoreFactory.instance().createLinphoneCore(
					this, mLinphoneConfigFile, mLinphoneInitialConfigFile, null);

			mLc.enableIpv6(getPrefBoolean(R.string.pref_ipv6_key, false));
			mLc.setZrtpSecretsCache(basePath+"/zrtp_secrets");

			mLc.setPlaybackGain(3);   
			mLc.setRing(null);
			mLc.setRootCA(mLinphoneRootCaFile);
			mLc.setPlayFile(mPauseSoundFile);
			mLc.setVideoPolicy(isAutoInitiateVideoCalls(), isAutoAcceptCamera());

			int availableCores = Runtime.getRuntime().availableProcessors();
			Log.w("MediaStreamer : " + availableCores + " cores detected and configured");
			mLc.setCpuCount(availableCores);
			
			try {
				initFromConf();
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
	        mServiceContext.registerReceiver(mKeepAliveReceiver, lFilter);
		}
		catch (Exception e) {
			Log.e(e,"Cannot start linphone");
		}
	}

	private void copyAssetsFromPackage() throws IOException {
		copyIfNotExist(R.raw.oldphone_mono,mRingSoundFile);
		copyIfNotExist(R.raw.ringback,mRingbackSoundFile);
		copyIfNotExist(R.raw.toy_mono,mPauseSoundFile);
		copyFromPackage(R.raw.linphonerc, new File(mLinphoneInitialConfigFile).getName());
		copyIfNotExist(R.raw.rootca, new File(mLinphoneRootCaFile).getName());
	}
	private  void copyIfNotExist(int ressourceId,String target) throws IOException {
		File lFileToCopy = new File(target);
		if (!lFileToCopy.exists()) {		
		   copyFromPackage(ressourceId,lFileToCopy.getName()); 
		}
	}
	private void copyFromPackage(int ressourceId,String target) throws IOException{
		FileOutputStream lOutputStream = mServiceContext.openFileOutput (target, 0); 
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

	void initMediaEncryption(){
		String pref = getPrefString(R.string.pref_media_encryption_key,
				R.string.pref_media_encryption_key_none);
		MediaEncryption me=MediaEncryption.None;
		if (pref.equals(getString(R.string.pref_media_encryption_key_srtp)))
			me=MediaEncryption.SRTP;
		else if (pref.equals(getString(R.string.pref_media_encryption_key_zrtp)))
			me=MediaEncryption.ZRTP;
		Log.i("Media encryption set to "+pref);
		mLc.setMediaEncryption(me);
	}

	private void initFromConfTunnel(){
		if (!mLc.isTunnelAvailable()) return;
		NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
		mLc.tunnelCleanServers();
		String host = getString(R.string.tunnel_host);
		if (host == null || host.length() == 0)
			host = mPref.getString(getString(R.string.pref_tunnel_host_key), "");
		int port = Integer.parseInt(getPrefString(R.string.pref_tunnel_port_key, "443"));
		mLc.tunnelAddServerAndMirror(host, port, 12345,500);
		manageTunnelServer(info);
	}

	public void initFromConf() throws LinphoneConfigException {


		initFromConfTunnel();

		if (initialTransports == null)
			initialTransports = mLc.getSignalingTransportPorts();
		
		setSignalingTransportsFromConfiguration(initialTransports);
		initMediaEncryption();
		
		try {
			// Configure audio codecs
//			enableDisableAudioCodec("speex", 32000, R.string.pref_codec_speex32_key);
			enableDisableAudioCodec("speex", 32000, false);
			enableDisableAudioCodec("speex", 16000, R.string.pref_codec_speex16_key);
			enableDisableAudioCodec("speex", 8000, R.string.pref_codec_speex8_key);
			enableDisableAudioCodec("iLBC", 8000, R.string.pref_codec_ilbc_key);
			enableDisableAudioCodec("GSM", 8000, R.string.pref_codec_gsm_key);
			enableDisableAudioCodec("G722", 8000, R.string.pref_codec_g722_key);
			enableDisableAudioCodec("G729", 8000, R.string.pref_codec_g729_key); 
			enableDisableAudioCodec("PCMU", 8000, R.string.pref_codec_pcmu_key);
			enableDisableAudioCodec("PCMA", 8000, R.string.pref_codec_pcma_key);
			enableDisableAudioCodec("AMR", 8000, R.string.pref_codec_amr_key);
            enableDisableAudioCodec("AMR-WB", 16000, R.string.pref_codec_amrwb_key);
			enableDisableAudioCodec("SILK", 24000, R.string.pref_codec_silk24_key);
			enableDisableAudioCodec("SILK", 16000, R.string.pref_codec_silk16_key);
			enableDisableAudioCodec("SILK", 12000, R.string.pref_codec_silk12_key);
			enableDisableAudioCodec("SILK", 8000, R.string.pref_codec_silk8_key);


			// Configure video codecs
			for (PayloadType videoCodec : mLc.getVideoCodecs()) {
				enableDisableVideoCodecs(videoCodec);
			}

			boolean useEC = getPrefBoolean(R.string.pref_echo_cancellation_key, false);
			boolean useEL = getPrefBoolean(R.string.pref_echo_limiter_key, false);
			mLc.enableEchoCancellation(useEC);
			mLc.enableEchoLimiter(useEL);
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(getString(R.string.wrong_settings),e);
		}
		boolean isVideoEnabled = isVideoEnabled();
		mLc.enableVideo(isVideoEnabled, isVideoEnabled);
		//1 read proxy config from preferences
		String lUserName = getPrefString(R.string.pref_username_key, null);
		if (lUserName == null || lUserName.length()==0) {
			throw new LinphoneConfigException(getString(R.string.wrong_username));
		}

		String lPasswd = getPrefString(R.string.pref_passwd_key, null);
		// we have the right of having no password
		//if (lPasswd == null || lPasswd.length()==0) {
		//	throw new LinphoneConfigException(getString(R.string.wrong_passwd));
		//}

		String lDomain = getPrefString(R.string.pref_domain_key, null);
		if (lDomain == null || lDomain.length()==0) {
			throw new LinphoneConfigException(getString(R.string.wrong_domain));
		}

		String lStun = getPrefString(R.string.pref_stun_server_key, null);

		//stun server
		mLc.setStunServer(lStun);
		mLc.setFirewallPolicy((lStun!=null && lStun.length()>0) ? FirewallPolicy.UseStun : FirewallPolicy.NoFirewall);
		
		//auth
		mLc.clearAuthInfos();
		if (lPasswd!=null && lPasswd.length()>0){
			LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(lUserName, lPasswd,null);
			mLc.addAuthInfo(lAuthInfo);
		}

		//proxy
		mLc.clearProxyConfigs();
		String lProxy = getPrefString(R.string.pref_proxy_key,null);
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
				int defaultAccount = getPrefInt(R.string.pref_default_account, 0);
				if (defaultAccount == 0 || defaultAccount >= getPrefInt(R.string.pref_extra_accounts, 0)) {
					//outbound proxy
					if (getPrefBoolean(R.string.pref_enable_outbound_proxy_key, false)) {
						lDefaultProxyConfig.setRoute(lProxy);
					} else {
						lDefaultProxyConfig.setRoute(null);
					}
					mLc.setDefaultProxyConfig(lDefaultProxyConfig);
				}

			} else {
				lDefaultProxyConfig.edit();
				lDefaultProxyConfig.setIdentity(lIdentity);
				lDefaultProxyConfig.setProxy(lProxy);
				lDefaultProxyConfig.enableRegister(true);
				lDefaultProxyConfig.done();
			}

			// Extra accounts
			for (int i = 1; i < getPrefExtraAccountsNumber(); i++) {
				if (getPrefBoolean(getString(R.string.pref_disable_account_key) + i, false)) {
					continue;
				}
				lUserName = getPrefString(getString(R.string.pref_username_key) + i, null);
				lPasswd = getPrefString(getString(R.string.pref_passwd_key) + i, null);
				if (lUserName != null && lUserName.length() > 0) {
					LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(lUserName, lPasswd, null);
					mLc.addAuthInfo(lAuthInfo);
					
					lDomain = getPrefString(getString(R.string.pref_domain_key) + i, null);
					if (lDomain != null && lDomain.length() > 0) {
						lIdentity = "sip:"+lUserName+"@"+lDomain;
						lProxy = getPrefString(getString(R.string.pref_proxy_key) + i, null);
						if (lProxy == null || lProxy.length() == 0) {
							lProxy = "sip:" + lDomain;
						}
						if (!lProxy.startsWith("sip:")) {
							lProxy = "sip:" + lProxy;
						}
						lDefaultProxyConfig = LinphoneCoreFactory.instance().createProxyConfig(lIdentity, lProxy, null, true);
						mLc.addProxyConfig(lDefaultProxyConfig);
						
						//outbound proxy
						if (getPrefBoolean(getString(R.string.pref_enable_outbound_proxy_key) + i, false)) {
							lDefaultProxyConfig.setRoute(lProxy);
						} else {
							lDefaultProxyConfig.setRoute(null);
						}
						
						if (i == getPrefInt(R.string.pref_default_account, 0)) {
							mLc.setDefaultProxyConfig(lDefaultProxyConfig);
						}
					}
				}
			}
			
			lDefaultProxyConfig = mLc.getDefaultProxyConfig();
			if (lDefaultProxyConfig !=null) {
				//prefix      
				String lPrefix = getPrefString(R.string.pref_prefix_key, null);
				if (lPrefix != null) {
					lDefaultProxyConfig.setDialPrefix(lPrefix);
				}
				//escape +
				lDefaultProxyConfig.setDialEscapePlus(getPrefBoolean(R.string.pref_escape_plus_key,false));
			}
			
			//init network state
			NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
			mLc.setNetworkReachable(networkInfo !=null? networkInfo.getState() == NetworkInfo.State.CONNECTED:false); 
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(getString(R.string.wrong_settings),e);
		}
	}
	
	private void setSignalingTransportsFromConfiguration(Transports t) {
		Transports ports = new Transports(t);
		boolean useStandardPort = getPrefBoolean(R.string.pref_transport_use_standard_ports_key, false);
		int lPreviousPort = ports.tcp +ports.udp +ports.tls; // assume only one port is active 
		
		if (!getPrefBoolean(R.string.pref_transport_tcp_key, false)) {
			ports.tcp = 0;
		} else if (useStandardPort) {
			ports.tcp = 5060;
		} else if (ports.tcp==0){
			ports.tcp=lPreviousPort;
		}

		if (!getPrefBoolean(R.string.pref_transport_udp_key, false)) {
			ports.udp = 0;
		} else if (useStandardPort) {
			ports.udp = 5060;
		} else if (ports.udp==0) {
			ports.udp = lPreviousPort;
		}

		if (!getPrefBoolean(R.string.pref_transport_tls_key, false)) {
			ports.tls = 0;
		} else if (useStandardPort) {
			ports.tls = 5061;
		} else if (ports.tls==0) {
			ports.tls=lPreviousPort;
		}

		mLc.setSignalingTransportPorts(ports);
	}

	private void enableDisableAudioCodec(String codec, int rate, int key) throws LinphoneCoreException {
		PayloadType pt = mLc.findPayloadType(codec, rate);
		if (pt !=null) {
			boolean enable= getPrefBoolean(key,false);
			mLc.enablePayloadType(pt, enable);
		}
	}
	private void enableDisableAudioCodec(String codec, int rate, boolean enable) throws LinphoneCoreException {
		PayloadType pt = mLc.findPayloadType(codec, rate);
		if (pt !=null) {
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
		} else if ("VP8".equals(mime)) {
			key = R.string.pref_video_codec_vp8_key;
		} else {
			Log.e("Unhandled video codec ", mime);
			mLc.enablePayloadType(videoCodec, false);
			return;
		}

		boolean enable= getPrefBoolean(key,false);
		mLc.enablePayloadType(videoCodec, enable);
	}

	private void doDestroy() {
		try {
			mTimer.cancel();
			mLc.destroy();
			mServiceContext.unregisterReceiver(instance.mKeepAliveReceiver);
		} finally {
			mLc = null;
			instance = null;
		}
	}

	public static synchronized void destroy() {
		if (instance == null) return;
		sExited=true;
		instance.doDestroy();
	}

	private String getString(int key) {
		return mR.getString(key);
	}
	private boolean getPrefBoolean(int key, boolean value) {
		return mPref.getBoolean(mR.getString(key), value);
	}
	private boolean getPrefBoolean(String key, boolean value) {
		return mPref.getBoolean(key, value);
	}
	private String getPrefString(int key, String value) {
		return mPref.getString(mR.getString(key), value);
	}
	private int getPrefInt(int key, int value) {
		return mPref.getInt(mR.getString(key), value);
	}
	private String getPrefString(int key, int value) {
		return mPref.getString(mR.getString(key), mR.getString(value));
	}
	private String getPrefString(String key, String value) {
		return mPref.getString(key, value);
	}
	private int getPrefExtraAccountsNumber() {
		return mPref.getInt(getString(R.string.pref_extra_accounts), 0);
	}


	/* Simple implementation as Android way seems very complicate:
	For example: with wifi and mobile actives; when pulling mobile down:
	I/Linphone( 8397): WIFI connected: setting network reachable
	I/Linphone( 8397): new state [RegistrationProgress]
	I/Linphone( 8397): mobile disconnected: setting network unreachable
	I/Linphone( 8397): Managing tunnel
	I/Linphone( 8397): WIFI connected: setting network reachable
	*/
	public void connectivityChanged(NetworkInfo eventInfo, ConnectivityManager cm) {
		NetworkInfo activeInfo = cm.getActiveNetworkInfo();

		if (eventInfo.getState() == NetworkInfo.State.DISCONNECTED) {
			Log.i(eventInfo.getTypeName()," disconnected: setting network unreachable");
			mLc.setNetworkReachable(false);
		} else if (eventInfo.getState() == NetworkInfo.State.CONNECTED){
			manageTunnelServer(activeInfo);
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mServiceContext);
			boolean wifiOnly = pref.getBoolean(getString(R.string.pref_wifi_only_key), false);
			if (eventInfo.getTypeName().equals("WIFI") || (eventInfo.getTypeName().equals("mobile") && !wifiOnly)) {
				mLc.setNetworkReachable(true);
				Log.i(eventInfo.getTypeName()," connected: setting network reachable");
			} else {
				mLc.setNetworkReachable(false);
				Log.i(eventInfo.getTypeName()," connected: wifi only activated, setting network unreachable");
			}
		}
	}















	public interface EcCalibrationListener {
		void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs);
	}

	private ListenerDispatcher mListenerDispatcher;
	private LinphoneCall ringingCall;

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
		mListenerDispatcher.onDisplayStatus(message);
	}


	public void globalState(final LinphoneCore lc, final LinphoneCore.GlobalState state, final String message) {
		Log.i("new state [",state,"]");
		mListenerDispatcher.onGlobalStateChanged(state, message);
	}



	public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig cfg,final LinphoneCore.RegistrationState state,final String message) {
		Log.i("new state ["+state+"]");
		mListenerDispatcher.onRegistrationStateChanged(state, message);
	}

	private int savedMaxCallWhileGsmIncall;
	private synchronized void preventSIPCalls() {
		if (savedMaxCallWhileGsmIncall != 0) {
			Log.w("SIP calls are already blocked due to GSM call running");
			return;
		}
		savedMaxCallWhileGsmIncall = mLc.getMaxCalls();
		mLc.setMaxCalls(0);
	}
	private synchronized void allowSIPCalls() {
		if (savedMaxCallWhileGsmIncall == 0) {
			Log.w("SIP calls are already allowed as no GSM call knowned to be running");
			return;
		}
		mLc.setMaxCalls(savedMaxCallWhileGsmIncall);
		savedMaxCallWhileGsmIncall = 0;
	}
	public static void setGsmIdle(boolean gsmIdle) {
		LinphoneManager mThis = instance;
		if (mThis == null) return;
		if (gsmIdle) {
			mThis.allowSIPCalls();
		} else {
			mThis.preventSIPCalls();
		}
	}

	public void callState(final LinphoneCore lc,final LinphoneCall call, final State state, final String message) {
		Log.i("new state [",state,"]");
		if (state == IncomingReceived && !call.equals(lc.getCurrentCall())) {
			if (call.getReplacedCall()!=null){
				// attended transfer
				// it will be accepted automatically.
				return;
			} 
		}

		if (state == IncomingReceived) {
			// Brighten screen for at least 10 seconds
			WakeLock wl = mPowerManager.newWakeLock(
					PowerManager.ACQUIRE_CAUSES_WAKEUP
					|PowerManager.ON_AFTER_RELEASE
					|PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
					"incoming_call");
			wl.acquire(10000);

			if (mLc.getCallsNb() == 1) {
				ringingCall = call;
				startRinging();
				// otherwise there is the beep
			}
		} else if (call == ringingCall && isRinging) { 
			//previous state was ringing, so stop ringing
			stopRinging();
		}

		if (state == CallEnd || state == Error) {
			mAudioManager.setMode(MODE_NORMAL);
		}

		if (state == State.Connected) {
			if (Hacks.needSoftvolume() || sLPref.useSoftvolume()) {
				adjustSoftwareVolume(0); // Synchronize
			}
		}

		if (state == CallEnd) {
			if (mLc.getCallsNb() == 0) {
				if (mIncallWakeLock != null && mIncallWakeLock.isHeld()) {
					mIncallWakeLock.release();
					Log.i("Last call ended: releasing incall (CPU only) wake lock");
				} else {
					Log.i("Last call ended: no incall (CPU only) wake lock were held");
				}
			}
		}
		if (state == State.StreamsRunning) {
			if (mIncallWakeLock == null) {
				mIncallWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,	"incall");
			}
			if (!mIncallWakeLock.isHeld()) {
				Log.i("New call active : acquiring incall (CPU only) wake lock");
				mIncallWakeLock.acquire();
			} else {
				Log.i("New call active while incall (CPU only) wake lock already active");
			}
		}
		mListenerDispatcher.onCallStateChanged(call, state, message);
	}

	public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call,
			boolean encrypted, String authenticationToken) {
		mListenerDispatcher.onCallEncryptionChanged(call, encrypted, authenticationToken);
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









	private boolean isRinging;
	private boolean disableRinging = false;
	
	public void disableRinging() {
		disableRinging = true;
	}
	
	private synchronized void startRinging()  {
		if (disableRinging ) {
			return;
		}
		
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
				mListenerDispatcher.onRingerPlayerCreated(mRingerPlayer);
				mRingerPlayer.prepare();
				mRingerPlayer.setLooping(true);
				mRingerPlayer.start();
			} else {
				Log.w("already ringing");
			}
		} catch (Exception e) {
			Log.e(e,"cannot handle incoming call");
		}
		isRinging = true;
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

		isRinging = false;
		// You may need to call galaxys audio hack after this method
		routeAudioToReceiver();
	}

	
	public static String extractADisplayName(Resources r, LinphoneAddress address) {
		if (address == null) return r.getString(R.string.unknown_incoming_call_name);

		final String displayName = address.getDisplayName();
		if (displayName!=null) {
			return displayName;
		} else  if (address.getUserName() != null){
			return address.getUserName();
		} else {
			String rms = address.toString();
			if (rms != null && rms.length() > 1)
				return rms;
				
			return r.getString(R.string.unknown_incoming_call_name);
		}
	}

	public static boolean reinviteWithVideo() {
		return CallManager.getInstance().reinviteWithVideo();
	}

	public boolean isVideoEnabled() {
		return getPrefBoolean(R.string.pref_video_enable_key, false);
	}
	
	public boolean shareMyCamera() {
		return isVideoEnabled() && getPrefBoolean(R.string.pref_video_automatically_share_my_video_key, false);
	}
	
	public boolean isAutoAcceptCamera() {
		return isVideoEnabled() && getPrefBoolean(R.string.pref_video_automatically_accept_video_key, false);
	}
	
	public boolean isAutoInitiateVideoCalls() {
		return isVideoEnabled() && getPrefBoolean(R.string.pref_video_initiate_call_with_video_key, false);
	}

	public void setAudioModeIncallForGalaxyS() {
		mAudioManager.setMode(MODE_IN_CALL);
	}

	// Called on first launch only
	public void initializePayloads() {
		Log.i("Initializing supported payloads");
		Editor e = mPref.edit();
		boolean fastCpu = Version.isArmv7();

		e.putBoolean(getString(R.string.pref_codec_gsm_key), true);
		e.putBoolean(getString(R.string.pref_codec_pcma_key), true);
		e.putBoolean(getString(R.string.pref_codec_pcmu_key), true);
		e.putBoolean(getString(R.string.pref_codec_speex8_key), true);
		e.putBoolean(getString(R.string.pref_codec_g722_key), false);
		e.putBoolean(getString(pref_codec_speex16_key), fastCpu);
		e.putBoolean(getString(pref_codec_speex32_key), fastCpu);

		boolean ilbc = LinphoneService.isReady() && LinphoneManager.getLc()
		.findPayloadType("iLBC", 8000)!=null;
		e.putBoolean(getString(pref_codec_ilbc_key), ilbc);
		
		boolean amr = LinphoneService.isReady() && LinphoneManager.getLc()
		.findPayloadType("AMR", 8000)!=null;
		e.putBoolean(getString(pref_codec_amr_key), amr);

        boolean amrwb = LinphoneService.isReady() && LinphoneManager.getLc()
        .findPayloadType("AMR-WB", 16000)!=null;
        e.putBoolean(getString(pref_codec_amrwb_key), amrwb);
        
        boolean g729 = LinphoneService.isReady() && LinphoneManager.getLc()
        .findPayloadType("G729", 8000)!=null;
        e.putBoolean(getString(R.string.pref_codec_g729_key), g729);

		if (Version.sdkStrictlyBelow(5) || !Version.hasNeon() || !Hacks.hasCamera()) {
			e.putBoolean(getString(pref_video_enable_key), false);
		}
		
		e.commit();
	}

	/**
	 * 
	 * @return false if already in video call. 
	 */
	public boolean addVideo() {
		LinphoneCall call = mLc.getCurrentCall();
		enableCamera(call, true);
		setVideoInitiator(true);
		return reinviteWithVideo();
	}
	
	public boolean acceptCallIfIncomingPending() throws LinphoneCoreException {
		if (Hacks.needGalaxySAudioHack() || sLPref.useGalaxySHack())
			setAudioModeIncallForGalaxyS();
		
		if (mLc.isInComingInvitePending()) {
			mLc.acceptCall(mLc.getCurrentCall());
			return true;
		}
		return false;
	}

	public boolean acceptCall(LinphoneCall call) {
		if (Hacks.needGalaxySAudioHack() || sLPref.useGalaxySHack())
			setAudioModeIncallForGalaxyS();

		try {
			mLc.acceptCall(call);
			return true;
		} catch (LinphoneCoreException e) {
			Log.i(e, "Accept call failed");
		}
		return false;
	}

	public static String extractIncomingRemoteName(Resources r, LinphoneAddress linphoneAddress) {
		if (!r.getBoolean(R.bool.show_full_remote_address_on_incoming_call))
			return extractADisplayName(r, linphoneAddress);

		if (linphoneAddress != null)
			return linphoneAddress.asStringUriOnly();

		return r.getString(R.string.unknown_incoming_call_name);
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


	private static boolean sLastProximitySensorValueNearby;
	private static Set<Activity> sProximityDependentActivities = new HashSet<Activity>();
	private static SensorEventListener sProximitySensorListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {
			if (event.timestamp == 0) return; //just ignoring for nexus 1
			sLastProximitySensorValueNearby = isProximitySensorNearby(event);
			proximityNearbyChanged();
		}
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	};


	private static void simulateProximitySensorNearby(Activity activity, boolean nearby) {
		final Window window = activity.getWindow();
		WindowManager.LayoutParams lAttrs = activity.getWindow().getAttributes();
		View view = ((ViewGroup) window.getDecorView().findViewById(android.R.id.content)).getChildAt(0);
		if (nearby) {
			lAttrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
			view.setVisibility(View.INVISIBLE);
		} else  {
			lAttrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN); 
			view.setVisibility(View.VISIBLE);
		}
		window.setAttributes(lAttrs);
	}

	private static void proximityNearbyChanged() {
		boolean nearby = sLastProximitySensorValueNearby;
		for (Activity activity : sProximityDependentActivities) {
			simulateProximitySensorNearby(activity, nearby);
		}
	}

	public static synchronized void startProximitySensorForActivity(Activity activity) {
		if (sProximityDependentActivities.contains(activity)) {
			Log.i("proximity sensor already active for " + activity.getLocalClassName());
			return;
		}
		
		if (sProximityDependentActivities.isEmpty()) {
			SensorManager sm = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
			Sensor s = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
			if (s != null) {
				sm.registerListener(sProximitySensorListener,s,SensorManager.SENSOR_DELAY_UI);
				Log.i("Proximity sensor detected, registering");
			}
		} else if (sLastProximitySensorValueNearby){
			simulateProximitySensorNearby(activity, true);
		}

		sProximityDependentActivities.add(activity);
	}

	public static synchronized void stopProximitySensorForActivity(Activity activity) {
		sProximityDependentActivities.remove(activity);
		simulateProximitySensorNearby(activity, false);
		if (sProximityDependentActivities.isEmpty()) {
			SensorManager sm = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
			sm.unregisterListener(sProximitySensorListener);
			sLastProximitySensorValueNearby = false;
		}
	}


	public static synchronized LinphoneCore getLcIfManagerNotDestroyedOrNull() {
		if (sExited) {
			// Can occur if the UI thread play a posted event but in the meantime the LinphoneManager was destroyed
			// Ex: stop call and quickly terminate application.
			Log.w("Trying to get linphone core while LinphoneManager already destroyed");
			return null;
		}
		return getLc();
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> getSimpleListeners(Class<T> clazz) {
		List<T> list = new ArrayList<T>();
		for (LinphoneSimpleListener l : simpleListeners) {
			if (clazz.isInstance(l)) list.add((T) l);
		}
		return list;
	}

	private class ListenerDispatcher implements LinphoneServiceListener {
		private LinphoneServiceListener serviceListener;

		public ListenerDispatcher(LinphoneServiceListener listener) {
			this.serviceListener = listener;
		}

		public void onCallEncryptionChanged(LinphoneCall call,
				boolean encrypted, String authenticationToken) {
			if (serviceListener != null) {
				serviceListener.onCallEncryptionChanged(call, encrypted, authenticationToken);
			}
			for (LinphoneOnCallEncryptionChangedListener l : getSimpleListeners(LinphoneOnCallEncryptionChangedListener.class)) {
				l.onCallEncryptionChanged(call, encrypted, authenticationToken);
			}
		}

		public void onCallStateChanged(LinphoneCall call, State state,
				String message) {
			if (state == State.OutgoingInit || state == State.IncomingReceived) {
				setVideoInitiator(state == State.OutgoingInit);
				boolean sendCamera = mLc.getConferenceSize() == 0;
				enableCamera(call, sendCamera);
			}
			if (state == State.CallEnd && mLc.getCallsNb() == 0) {
				routeAudioToReceiver();
			}
			
			if (serviceListener != null) serviceListener.onCallStateChanged(call, state, message);
			for (LinphoneOnCallStateChangedListener l : getSimpleListeners(LinphoneOnCallStateChangedListener.class)) {
				l.onCallStateChanged(call, state, message);
			}
		}

		public void onDisplayStatus(String message) {
			if (serviceListener != null) serviceListener.onDisplayStatus(message);
		}

		public void onGlobalStateChanged(GlobalState state, String message) {
			if (serviceListener != null) serviceListener.onGlobalStateChanged( state, message);
		}

		public void onRegistrationStateChanged(RegistrationState state,
				String message) {
			if (serviceListener != null) serviceListener.onRegistrationStateChanged(state, message);
		}

		public void onRingerPlayerCreated(MediaPlayer mRingerPlayer) {
			if (serviceListener != null) serviceListener.onRingerPlayerCreated(mRingerPlayer);
		}

		public void tryingNewOutgoingCallButAlreadyInCall() {
			if (serviceListener != null) serviceListener.tryingNewOutgoingCallButAlreadyInCall();
		}

		public void tryingNewOutgoingCallButCannotGetCallParameters() {
			if (serviceListener != null) serviceListener.tryingNewOutgoingCallButCannotGetCallParameters();
		}

		public void tryingNewOutgoingCallButWrongDestinationAddress() {
			if (serviceListener != null) serviceListener.tryingNewOutgoingCallButWrongDestinationAddress();
		}
	}

	public static final boolean isInstanciated() {
		return instance != null;
	}
	
	public synchronized LinphoneCall getPendingIncomingCall() {
		LinphoneCall currentCall = mLc.getCurrentCall();
		if (currentCall == null) return null;

		LinphoneCall.State state = currentCall.getState();
		boolean incomingPending = currentCall.getDirection() == CallDirection.Incoming
			&& (state == State.IncomingReceived || state == State.CallIncomingEarlyMedia);

		return incomingPending ? currentCall : null;
	}

	
	
	
	@SuppressWarnings("serial")
	public static class LinphoneConfigException extends LinphoneException {

		public LinphoneConfigException() {
			super();
		}

		public LinphoneConfigException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
		}

		public LinphoneConfigException(String detailMessage) {
			super(detailMessage);
		}

		public LinphoneConfigException(Throwable throwable) {
			super(throwable);
		}
	}
}
