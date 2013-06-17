package org.linphone.test;

import java.util.Timer;
import java.util.TimerTask;

import org.linphone.LinphoneException;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneManager.LinphoneConfigException;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
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
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.AndroidVideoApi5JniWrapper;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.telephony.TelephonyManager;

public class LinphoneTestManager implements LinphoneCoreListener {

	private static LinphoneTestManager instance;
	private Context mContext, mIContext;
	private LinphoneCore mLc;
	private static Transports initialTransports;
	
	public String lastMessageReceived;
	public boolean isDTMFReceived = false;
	public boolean autoAnswer = true;
	public boolean declineCall = false;

	private Timer mTimer = new Timer("Linphone scheduler");
	
	private LinphoneTestManager(Context ac, Context ic) {
		mContext = ac;
		mIContext = ic;
	}
	
	public static LinphoneTestManager createAndStart(Context ac, Context ic) {
		if (instance != null)
			throw new RuntimeException("Linphone Manager is already initialized");

		instance = new LinphoneTestManager(ac, ic);
		instance.startLibLinphone(ic);
		TelephonyManager tm = (TelephonyManager) ac.getSystemService(Context.TELEPHONY_SERVICE);
		boolean gsmIdle = tm.getCallState() == TelephonyManager.CALL_STATE_IDLE;
		setGsmIdle(gsmIdle);
		
		if (Version.isVideoCapable())
			AndroidVideoApi5JniWrapper.setAndroidSdkVersion(Version.sdk());
		return instance;
	}
	
	private synchronized void startLibLinphone(Context c) {
		try {
			LinphoneCoreFactory.instance().setDebugMode(true, "LinphoneTester");
			
			mLc = LinphoneCoreFactory.instance().createLinphoneCore(this);
			mLc.getConfig().setInt("sip", "store_auth_info", 0);
			mLc.setContext(c);
			try {
				String versionName = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
				if (versionName == null) {
					versionName = String.valueOf(c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode);
				}
				mLc.setUserAgent("LinphoneAndroid", versionName);
			} catch (NameNotFoundException e) {
				Log.e(e, "cannot get version name");
			}

			mLc.enableIpv6(false);
			mLc.setRing(null);

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
			mTimer.scheduleAtFixedRate(lTask, 0, 20); 

			IntentFilter lFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
	        lFilter.addAction(Intent.ACTION_SCREEN_OFF);
			
	        resetCameraFromPreferences();
		}
		catch (Exception e) {
			Log.e(e, "Cannot start linphone");
		}
	}
	
	private void resetCameraFromPreferences() {
		boolean useFrontCam = true;
		int camId = 0;
		AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
		for (AndroidCamera androidCamera : cameras) {
			if (androidCamera.frontFacing == useFrontCam)
				camId = androidCamera.id;
		}
		LinphoneManager.getLc().setVideoDevice(camId);
	}
	
	public void initFromConf() throws LinphoneConfigException {
		LinphoneCoreFactory.instance().setDebugMode(true, "LinphoneTester");

		if (initialTransports == null)
			initialTransports = mLc.getSignalingTransportPorts();
		
		setSignalingTransportsFromConfiguration(initialTransports);
		initMediaEncryption();

		mLc.setVideoPolicy(true, true);
		
		readAndSetAudioAndVideoPorts();
		
		String defaultIncomingCallTimeout = mContext.getString(org.linphone.R.string.pref_incoming_call_timeout_default);
		int incomingCallTimeout = tryToParseIntValue(defaultIncomingCallTimeout, defaultIncomingCallTimeout);
		mLc.setIncomingTimeout(incomingCallTimeout);
		
		try {
			// Configure audio codecs
//			enableDisableAudioCodec("speex", 32000, 1, R.string.pref_codec_speex32_key);
			enableDisableAudioCodec("speex", 32000, 1, false);
			enableDisableAudioCodec("speex", 16000, 1, R.string.pref_codec_speex16_key);
			enableDisableAudioCodec("speex", 8000, 1, R.string.pref_codec_speex8_key);
			enableDisableAudioCodec("iLBC", 8000, 1, R.string.pref_codec_ilbc_key);
			enableDisableAudioCodec("GSM", 8000, 1, R.string.pref_codec_gsm_key);
			enableDisableAudioCodec("G722", 8000, 1, R.string.pref_codec_g722_key);
			enableDisableAudioCodec("G729", 8000, 1, R.string.pref_codec_g729_key); 
			enableDisableAudioCodec("PCMU", 8000, 1, R.string.pref_codec_pcmu_key);
			enableDisableAudioCodec("PCMA", 8000, 1, R.string.pref_codec_pcma_key);
			enableDisableAudioCodec("AMR", 8000, 1, R.string.pref_codec_amr_key);
			enableDisableAudioCodec("AMR-WB", 16000, 1, R.string.pref_codec_amrwb_key);
			//enableDisableAudioCodec("SILK", 24000, 1, R.string.pref_codec_silk24_key);
			enableDisableAudioCodec("SILK", 24000, 1, false);
			enableDisableAudioCodec("SILK", 16000, 1, R.string.pref_codec_silk16_key);
			//enableDisableAudioCodec("SILK", 12000, 1, R.string.pref_codec_silk12_key);
			enableDisableAudioCodec("SILK", 12000, 1, false);
			enableDisableAudioCodec("SILK", 8000, 1, R.string.pref_codec_silk8_key);

			// Configure video codecs
			for (PayloadType videoCodec : mLc.getVideoCodecs()) {
				enableDisableVideoCodecs(videoCodec);
			}
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(mContext.getString(R.string.wrong_settings),e);
		}
		boolean isVideoEnabled = true;
		mLc.enableVideo(isVideoEnabled, isVideoEnabled);
		
		//stun server
		String lStun = mContext.getString(R.string.default_stun);
		mLc.setStunServer(lStun);
		if (lStun!=null && lStun.length()>0) {
			mLc.setFirewallPolicy(FirewallPolicy.UseIce);
		} else {
			mLc.setFirewallPolicy(FirewallPolicy.NoFirewall);
		}
		
		mLc.setUseRfc2833ForDtmfs(false);
		mLc.setUseSipInfoForDtmfs(true);
		
		//accounts
		try {
			initAccounts();
			
			//init network state
			mLc.setNetworkReachable(true); 
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(mContext.getString(R.string.wrong_settings),e);
		}
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
		MediaEncryption me=MediaEncryption.None;
		mLc.setMediaEncryption(me);
	}
	
	public void initAccounts() throws LinphoneCoreException {
		mLc.clearAuthInfos();
		mLc.clearProxyConfigs();
		
		String username = mIContext.getString(org.linphone.test.R.string.account_test_calls_login);
		String password = mIContext.getString(org.linphone.test.R.string.account_test_calls_pwd);
		String domain = mIContext.getString(org.linphone.test.R.string.account_test_calls_domain);
		LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(username, password, null);
		mLc.addAuthInfo(lAuthInfo);
		String identity = "sip:" + username +"@" + domain;
		String proxy = "sip:" + domain;
		LinphoneProxyConfig proxycon = LinphoneCoreFactory.instance().createProxyConfig(identity, proxy, null, true);
		mLc.addProxyConfig(proxycon);
		mLc.setDefaultProxyConfig(proxycon);
		
		LinphoneProxyConfig lDefaultProxyConfig = mLc.getDefaultProxyConfig();
		if (lDefaultProxyConfig != null) {
			//escape +
			lDefaultProxyConfig.setDialEscapePlus(false);
		} else if (LinphoneService.isReady()) {
			LinphoneService.instance().onRegistrationStateChanged(RegistrationState.RegistrationNone, null);
		}
	}

	private void readAndSetAudioAndVideoPorts() throws NumberFormatException {
		int aPortStart, aPortEnd, vPortStart, vPortEnd;
		int defaultAudioPort, defaultVideoPort;
		defaultAudioPort = Integer.parseInt(mContext.getString(R.string.default_audio_port));
		defaultVideoPort = Integer.parseInt(mContext.getString(R.string.default_video_port));
		aPortStart = aPortEnd = defaultAudioPort;
		vPortStart = vPortEnd = defaultVideoPort;

		String audioPort = String.valueOf(aPortStart);
		String videoPort = String.valueOf(vPortStart);

		if (audioPort.contains("-")) {
			// Port range
			aPortStart = Integer.parseInt(audioPort.split("-")[0]);
			aPortEnd = Integer.parseInt(audioPort.split("-")[1]);
		} else {
			try {
				aPortStart = aPortEnd = Integer.parseInt(audioPort);
			} catch (NumberFormatException nfe) {
				aPortStart = aPortEnd = defaultAudioPort;
			}
		}
		
		if (videoPort.contains("-")) {
			// Port range
			vPortStart = Integer.parseInt(videoPort.split("-")[0]);
			vPortEnd = Integer.parseInt(videoPort.split("-")[1]);
		} else {
			try {
				vPortStart = vPortEnd = Integer.parseInt(videoPort);
			} catch (NumberFormatException nfe) {
				vPortStart = vPortEnd = defaultVideoPort;
			}
		}
		
		if (aPortStart >= aPortEnd) {
			mLc.setAudioPort(aPortStart);
		} else {
			mLc.setAudioPortRange(aPortStart, aPortEnd);
		}
	
		if (vPortStart >= vPortEnd) {
			mLc.setVideoPort(vPortStart);
		} else {
			mLc.setVideoPortRange(vPortStart, vPortEnd);
		}
	}
	
	private int tryToParseIntValue(String valueToParse, String defaultValue) {
		return tryToParseIntValue(valueToParse, Integer.parseInt(defaultValue));
	}
	
	private int tryToParseIntValue(String valueToParse, int defaultValue) {
		try {
			int returned = Integer.parseInt(valueToParse);
			return returned;
		} catch (NumberFormatException nfe) {
			
		}
		return defaultValue;
	}

	public static synchronized final LinphoneTestManager getInstance() {
		return instance;
	}
	
	private void setSignalingTransportsFromConfiguration(Transports t) {
		Transports ports = new Transports(t);
		boolean useRandomPort = true;
		int lPreviousPort =  5060;
		if (lPreviousPort>0xFFFF || useRandomPort) {
			lPreviousPort=(int)(Math.random() * (0xFFFF - 1024)) + 1024;
			Log.w("Using random port " + lPreviousPort);
		}
		
		ports.udp = 0;
		ports.tls = 0;
		ports.tcp = lPreviousPort;

		mLc.setSignalingTransportPorts(ports);
	}
	
	public static synchronized final LinphoneCore getLc() {
		return getInstance().mLc;
	}

	private void enableDisableAudioCodec(String codec, int rate, int channels, int key) throws LinphoneCoreException {
		PayloadType pt = mLc.findPayloadType(codec, rate, channels);
		if (pt !=null) {
			boolean enable = true;
			mLc.enablePayloadType(pt, enable);
		}
	}
	private void enableDisableAudioCodec(String codec, int rate, int channels, boolean enable) throws LinphoneCoreException {
		PayloadType pt = mLc.findPayloadType(codec, rate, channels);
		if (pt !=null) {
			mLc.enablePayloadType(pt, enable);
		}
	}

	private void enableDisableVideoCodecs(PayloadType videoCodec) throws LinphoneCoreException {
		boolean enable = true;
		mLc.enablePayloadType(videoCodec, enable);
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
		LinphoneTestManager mThis = instance;
		if (mThis == null) return;
		if (gsmIdle) {
			mThis.allowSIPCalls();
		} else {
			mThis.preventSIPCalls();
		}
	}

	private void doDestroy() {
		try {
			mTimer.cancel();
			mLc.destroy();
		} 
		catch (RuntimeException e) {
			e.printStackTrace();
		}
		finally {
			mLc = null;
			instance = null;
		}
	}

	public static synchronized void destroy() {
		if (instance == null) return;
		instance.doDestroy();
	}

	@Override
	public void authInfoRequested(LinphoneCore lc, String realm, String username) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void globalState(LinphoneCore lc, GlobalState state, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void callState(LinphoneCore lc, LinphoneCall call, State cstate,
			String message) {
		// TODO Auto-generated method stub
		Log.e("Call state = " + cstate.toString());
		if (cstate == LinphoneCall.State.IncomingReceived) {
			if (declineCall) {
				mLc.terminateCall(call);
			} else if (autoAnswer) {
				try {
					mLc.acceptCall(call);
				} catch (LinphoneCoreException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void callStatsUpdated(LinphoneCore lc, LinphoneCall call,
			LinphoneCallStats stats) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call,
			boolean encrypted, String authenticationToken) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg,
			RegistrationState cstate, String smessage) {
		// TODO Auto-generated method stub
		Log.e("Registration state = " + cstate.toString());
	}

	@Override
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf,
			String url) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void textReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneAddress from, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneChatMessage message) {
		// TODO Auto-generated method stub
		Log.e("Message received = " + message.getText());
		lastMessageReceived = message.getText();
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {
		// TODO Auto-generated method stub
		Log.e("DTMF received = " + dtmf);
		isDTMFReceived = true;
	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status,
			int delay_ms, Object data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneAddress from, byte[] event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void show(LinphoneCore lc) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayStatus(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayMessage(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayWarning(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void transferState(LinphoneCore lc, LinphoneCall call,
			State new_call_state) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void infoReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneInfoMessage info) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev,
			SubscriptionState state) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev,
			String eventName, LinphoneContent content) {
		// TODO Auto-generated method stub
		
	}
}
