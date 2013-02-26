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

import static android.media.AudioManager.MODE_NORMAL;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;
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
import org.linphone.LinphoneSimpleListener.LinphoneOnMessageReceivedListener;
import org.linphone.LinphoneSimpleListener.LinphoneServiceListener;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
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
import org.linphone.core.PayloadType;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.AndroidVideoApi5JniWrapper;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;
import org.linphone.mediastream.video.capture.hwconf.Hacks;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
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
import android.widget.Toast;

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

	private WakeLock mIncallWakeLock;
	
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothHeadset mBluetoothHeadset;
	private BluetoothProfile.ServiceListener mProfileListener;
	private BroadcastReceiver bluetoothReiceiver = new BluetoothManager();
	public boolean isBluetoothScoConnected;

	private static List<LinphoneSimpleListener> simpleListeners = new ArrayList<LinphoneSimpleListener>();
	public static void addListener(LinphoneSimpleListener listener) {
		if (!simpleListeners.contains(listener)) {
			simpleListeners.add(listener);
		}
	}
	public static void removeListener(LinphoneSimpleListener listener) {
		simpleListeners.remove(listener);
	}

	private LinphoneManager(final Context c, LinphoneServiceListener listener) {
		sExited=false;
		mServiceContext = c;
		mListenerDispatcher = new ListenerDispatcher(listener);
		basePath = c.getFilesDir().getAbsolutePath();
		mLPConfigXsd = basePath + "/lpconfig.xsd";
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
	private final String mLPConfigXsd;
	private final String mLinphoneInitialConfigFile;
	private final String mLinphoneRootCaFile;
	private final String mLinphoneConfigFile;
	private final String mRingSoundFile; 
	private final String mRingbackSoundFile;
	private final String mPauseSoundFile;

	private Timer mTimer = new Timer("Linphone scheduler");

	private  BroadcastReceiver mKeepAliveReceiver = new KeepAliveReceiver();

	private void routeAudioToSpeakerHelper(boolean speakerOn) {
		boolean routeToBluetoothEnabled = false;
		if (!speakerOn) {
			boolean routeToBT = mServiceContext.getResources().getBoolean(R.bool.route_audio_to_bluetooth_if_available);
			if (!routeToBT || (routeToBT && !routeToBluetoothIfAvailable())) {
				mLc.enableSpeaker(false);
				scoDisconnected();
			} else {
				Log.d("Routing audio to bluetooth headset");
				routeToBluetoothEnabled = true;
			}
		} else {
			mLc.enableSpeaker(true);
			scoDisconnected();
		}
		
		for (LinphoneOnAudioChangedListener listener : getSimpleListeners(LinphoneOnAudioChangedListener.class)) {
			listener.onAudioStateChanged(speakerOn ? AudioState.SPEAKER : (routeToBluetoothEnabled ? AudioState.BLUETOOTH : AudioState.EARPIECE));
		}
	}
	public void routeAudioToSpeaker() {
		routeAudioToSpeakerHelper(true);
	}
	

	public String getUserAgent() throws NameNotFoundException {
		StringBuilder userAgent = new StringBuilder();
		userAgent.append("LinphoneAndroid/" + mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(),0).versionCode);
		userAgent.append(" (");
		userAgent.append("Linphone/" + LinphoneManager.getLc().getVersion() + "; ");
		userAgent.append(Build.DEVICE + " " + Build.MODEL +  " Android/" + Build.VERSION.SDK_INT);
		userAgent.append(")");
		return userAgent.toString();
	}

	/**
	 * 
	 */
	public void routeAudioToReceiver() {
		routeAudioToSpeakerHelper(false);
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void startBluetooth() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter.isEnabled()) {
			if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
				mProfileListener = new BluetoothProfile.ServiceListener() {
					@TargetApi(Build.VERSION_CODES.HONEYCOMB)
					public void onServiceConnected(int profile, BluetoothProfile proxy) {
					    if (profile == BluetoothProfile.HEADSET) {
					        mBluetoothHeadset = (BluetoothHeadset) proxy;
					        Log.d("Bluetooth headset connected");
					        routeToBluetoothIfAvailable();
					    }
					}
					@TargetApi(Build.VERSION_CODES.HONEYCOMB)
					public void onServiceDisconnected(int profile) {
					    if (profile == BluetoothProfile.HEADSET) {
					        mBluetoothHeadset = null;
					        Log.d("Bluetooth headset disconnected");
					        routeAudioToReceiver();
					    }
					}
				};
				mBluetoothAdapter.getProfileProxy(mServiceContext, mProfileListener, BluetoothProfile.HEADSET);
			} else {
				@SuppressWarnings("deprecation")
				String actionScoConnected = AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED;
				Intent currentValue = mServiceContext.registerReceiver(bluetoothReiceiver, new IntentFilter(actionScoConnected));
				int state = currentValue == null ? 0 : currentValue.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, 0);
	        	if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
	        		isBluetoothScoConnected = true;
	        		scoConnected();
	        	}
			}
		}
		else {
			scoDisconnected();
			routeAudioToReceiver();
		}
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public boolean routeToBluetoothIfAvailable() {
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter.isEnabled() && mAudioManager.isBluetoothScoAvailableOffCall()) {
			mAudioManager.setBluetoothScoOn(true);	
			mAudioManager.startBluetoothSco();
			
			if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
				boolean connected = false;
				if (mBluetoothHeadset != null) {
					List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();                        
					for (final BluetoothDevice dev : devices) {           
						connected |= mBluetoothHeadset.getConnectionState(dev) == BluetoothHeadset.STATE_CONNECTED;
					}
				}
				
				if (!connected) {
					Log.d("No bluetooth device available");	
					scoDisconnected();
				}
				return connected;
			} else {
				return isBluetoothScoConnected;
			}
		}
		
		return false;
	}
	
	public void scoConnected() {
		Log.e("Bluetooth sco connected!");
		mAudioManager.setMode(AudioManager.MODE_IN_CALL);
		routeToBluetoothIfAvailable();
	}
	
	public void scoDisconnected() {
		Log.e("Bluetooth sco disconnected!");
		if (mAudioManager != null) {
			mAudioManager.setMode(AudioManager.MODE_NORMAL);
			mAudioManager.stopBluetoothSco();
			mAudioManager.setBluetoothScoOn(false);
		}
	}

	public synchronized static final LinphoneManager createAndStart(
			Context c, LinphoneServiceListener listener) {
		if (instance != null)
			throw new RuntimeException("Linphone Manager is already initialized");

		instance = new LinphoneManager(c, listener);
		instance.startLibLinphone(c);
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
	
	public String getLPConfigXsdPath() {
		return mLPConfigXsd;
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

		boolean isLowBandwidthConnection = !LinphoneUtils.isHightBandwidthConnection(LinphoneService.instance().getApplicationContext());
		
		if (mLc.isNetworkReachable()) {
			try {
				if (Version.isVideoCapable()) {
					boolean prefVideoEnable = isVideoEnabled();
					int key = R.string.pref_video_initiate_call_with_video_key;
					boolean prefInitiateWithVideo = getPrefBoolean(key, false);
					CallManager.getInstance().inviteAddress(lAddress, prefVideoEnable && prefInitiateWithVideo, isLowBandwidthConnection);
				} else {
					CallManager.getInstance().inviteAddress(lAddress, false, isLowBandwidthConnection);
				}
				
	
			} catch (LinphoneCoreException e) {
				mListenerDispatcher.tryingNewOutgoingCallButCannotGetCallParameters();
				return;
			}
		} else if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().displayCustomToast(getString(R.string.error_network_unreachable), Toast.LENGTH_LONG);
		} else {
			Log.e("Error: " + getString(R.string.error_network_unreachable));
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

	private synchronized void startLibLinphone(Context c) {
		try {
			copyAssetsFromPackage();
			//traces alway start with traces enable to not missed first initialization
			
			boolean isDebugLogEnabled = true;//!(mR.getBoolean(R.bool.disable_every_log)) && getPrefBoolean(R.string.pref_debug_key, mR.getBoolean(R.bool.pref_debug_default));
			LinphoneCoreFactory.instance().setDebugMode(isDebugLogEnabled, getString(R.string.app_name));
			
			// Try to get remote provisioning
			String remote_provisioning = (getPrefString(R.string.pref_remote_provisioning_key, mR.getString(R.string.pref_remote_provisioning_default)));
			if(remote_provisioning != null && remote_provisioning.length() > 0 && RemoteProvisioning.isAvailable()) {
				RemoteProvisioning.download(remote_provisioning, mLinphoneConfigFile);
			}
			
			mLc = LinphoneCoreFactory.instance().createLinphoneCore(this, mLinphoneConfigFile, mLinphoneInitialConfigFile, null);
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

			mLc.enableIpv6(getPrefBoolean(R.string.pref_ipv6_key, false));
			mLc.setZrtpSecretsCache(basePath + "/zrtp_secrets");
 
			mLc.setRing(null);
			mLc.setRootCA(mLinphoneRootCaFile);
			mLc.setPlayFile(mPauseSoundFile);

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
	        mServiceContext.registerReceiver(mKeepAliveReceiver, lFilter);
			
	        boolean routeToBT = mServiceContext.getResources().getBoolean(R.bool.route_audio_to_bluetooth_if_available);
	        if (routeToBT) {
	        	startBluetooth();
	        }
		}
		catch (Exception e) {
			Log.e(e, "Cannot start linphone");
		}
	}

	private void copyAssetsFromPackage() throws IOException {
		copyIfNotExist(R.raw.oldphone_mono,mRingSoundFile);
		copyIfNotExist(R.raw.ringback,mRingbackSoundFile);
		copyIfNotExist(R.raw.toy_mono,mPauseSoundFile);
		copyFromPackage(R.raw.linphonerc, new File(mLinphoneInitialConfigFile).getName());
		copyIfNotExist(R.raw.lpconfig, new File(mLPConfigXsd).getName());
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
		while (( readByte = lInputStream.read(buff)) != -1) {
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
		String pref = getPrefString(R.string.pref_media_encryption_key, R.string.pref_media_encryption_key_none);
		MediaEncryption me=MediaEncryption.None;
		if (pref.equals(getString(R.string.pref_media_encryption_key_srtp)))
			me = MediaEncryption.SRTP;
		else if (pref.equals(getString(R.string.pref_media_encryption_key_zrtp)))
			me = MediaEncryption.ZRTP;
		Log.i("Media encryption set to " + pref);
		mLc.setMediaEncryption(me);
	}

	private void initFromConfTunnel(){
		if (!mLc.isTunnelAvailable()) 
			return;
		
		NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
		mLc.tunnelCleanServers();
		String host = getString(R.string.tunnel_host);
		if (host == null || host.length() == 0)
			host = mPref.getString(getString(R.string.pref_tunnel_host_key), "");
		int port = Integer.parseInt(getPrefString(R.string.pref_tunnel_port_key, "443"));
		mLc.tunnelAddServerAndMirror(host, port, 12345,500);
		manageTunnelServer(info);
	}
	
	public void initAccounts() throws LinphoneCoreException {
		mLc.clearAuthInfos();
		mLc.clearProxyConfigs();
		
		for (int i = 0; i < getPrefExtraAccountsNumber(); i++) {
			String key = i == 0 ? "" : String.valueOf(i);
			if (!getPrefBoolean(getString(R.string.pref_disable_account_key) + key, false)) {
				initAccount(key, i == getPrefInt(R.string.pref_default_account_key, 0));
			}
		}
		
		LinphoneProxyConfig lDefaultProxyConfig = mLc.getDefaultProxyConfig();
		if (lDefaultProxyConfig !=null) {
			//prefix      
			String lPrefix = getPrefString(R.string.pref_prefix_key, null);
			if (lPrefix != null) {
				lDefaultProxyConfig.setDialPrefix(lPrefix);
			}
			//escape +
			lDefaultProxyConfig.setDialEscapePlus(getPrefBoolean(R.string.pref_escape_plus_key, false));
		}
	}

	private void initAccount(String key, boolean defaultAccount) throws LinphoneCoreException {
		String username = getPrefString(getString(R.string.pref_username_key) + key, null);
		String password = getPrefString(getString(R.string.pref_passwd_key) + key, null);
		String domain = getPrefString(getString(R.string.pref_domain_key) + key, null);
		if (username != null && username.length() > 0 && password != null) {
			LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(username, password, null);
			mLc.addAuthInfo(lAuthInfo);
			
			if (domain != null && domain.length() > 0) {
				String identity = "sip:" + username +"@" + domain;
				String proxy = getPrefString(getString(R.string.pref_proxy_key) + key, null);
				if (proxy == null || proxy.length() == 0) {
					proxy = "sip:" + domain;
				}
				if (!proxy.startsWith("sip:")) {
					proxy = "sip:" + proxy;
				}
				
				LinphoneProxyConfig proxycon = LinphoneCoreFactory.instance().createProxyConfig(identity, proxy, null, true);
				String defaultExpire = getString(R.string.pref_expire_default);
				proxycon.setExpires(tryToParseIntValue(getPrefString(R.string.pref_expire_key, defaultExpire), defaultExpire));
				
				// Add parameters for push notifications
				if (mR.getBoolean(R.bool.enable_push_id)) {
					String regId = getPrefString(R.string.push_reg_id_key, null);
					String appId = getString(R.string.push_sender_id);
					if (regId != null && getPrefBoolean(R.string.pref_push_notification_key, mR.getBoolean(R.bool.pref_push_notification_default))) {
						String contactInfos = "app-id=" + appId + ";pn-type=google;pn-tok=" + regId + ";pn-msg-str=IM_MSG;pn-call-str=IC_MSG;pn-call-snd=ring.caf;pn-msg-snd=msg.caf;";
						proxycon.setContactParameters(contactInfos);
					}
				}
				mLc.addProxyConfig(proxycon);
				
				//outbound proxy
				if (getPrefBoolean(getString(R.string.pref_enable_outbound_proxy_key) + key, false)) {
					proxycon.setRoute(proxy);
				} else {
					proxycon.setRoute(null);
				}
				proxycon.done();
				
				if (defaultAccount) {
					mLc.setDefaultProxyConfig(proxycon);
				}
			}
		}
	}

	private void readAndSetAudioAndVideoPorts() throws NumberFormatException {
		int aPortStart, aPortEnd, vPortStart, vPortEnd;
		int defaultAudioPort, defaultVideoPort;
		defaultAudioPort = Integer.parseInt(getString(R.string.default_audio_port));
		defaultVideoPort = Integer.parseInt(getString(R.string.default_video_port));
		aPortStart = aPortEnd = defaultAudioPort;
		vPortStart = vPortEnd = defaultVideoPort;

		String audioPort = getPrefString(R.string.pref_audio_port_key, String.valueOf(aPortStart));
		String videoPort = getPrefString(R.string.pref_video_port_key, String.valueOf(vPortStart));

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

	public void initFromConf() throws LinphoneConfigException {
		boolean isDebugLogEnabled = !(mR.getBoolean(R.bool.disable_every_log)) && getPrefBoolean(R.string.pref_debug_key, mR.getBoolean(R.bool.pref_debug_default));
		LinphoneCoreFactory.instance().setDebugMode(isDebugLogEnabled, getString(R.string.app_name));
		initFromConfTunnel();

		if (initialTransports == null)
			initialTransports = mLc.getSignalingTransportPorts();
		
		setSignalingTransportsFromConfiguration(initialTransports);
		initMediaEncryption();

		mLc.setVideoPolicy(isAutoInitiateVideoCalls(), isAutoAcceptCamera());
		
		readAndSetAudioAndVideoPorts();
		
		String defaultIncomingCallTimeout = getString(R.string.pref_incoming_call_timeout_default);
		int incomingCallTimeout = tryToParseIntValue(getPrefString(R.string.pref_incoming_call_timeout_key, defaultIncomingCallTimeout), defaultIncomingCallTimeout);
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

			boolean useEC = getPrefBoolean(R.string.pref_echo_cancellation_key, mR.getBoolean(R.bool.pref_echo_canceller_default));
			mLc.enableEchoCancellation(useEC);
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(getString(R.string.wrong_settings),e);
		}
		boolean isVideoEnabled = isVideoEnabled();
		mLc.enableVideo(isVideoEnabled, isVideoEnabled);
		
		//stun server
		String lStun = getPrefString(R.string.pref_stun_server_key, getString(R.string.default_stun));
		boolean useICE = getPrefBoolean(R.string.pref_ice_enable_key, mR.getBoolean(R.bool.pref_ice_enabled_default));
		mLc.setStunServer(lStun);
		if (lStun!=null && lStun.length()>0) {
			mLc.setFirewallPolicy(useICE ? FirewallPolicy.UseIce : FirewallPolicy.UseStun);
		} else {
			mLc.setFirewallPolicy(FirewallPolicy.NoFirewall);
		}
		
		mLc.setUseRfc2833ForDtmfs(getPrefBoolean(R.string.pref_rfc2833_dtmf_key, mR.getBoolean(R.bool.pref_rfc2833_dtmf_default)));
		mLc.setUseSipInfoForDtmfs(getPrefBoolean(R.string.pref_sipinfo_dtmf_key, mR.getBoolean(R.bool.pref_sipinfo_dtmf_default)));

		String displayName = getPrefString(R.string.pref_display_name_key, getString(R.string.pref_display_name_default));
		String username = getPrefString(R.string.pref_user_name_key, getString(R.string.pref_user_name_default));
		mLc.setPrimaryContact(displayName, username);
		
		//accounts
		try {
			initAccounts();
			
			//init network state
			NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mServiceContext);
			boolean wifiOnly = pref.getBoolean(getString(R.string.pref_wifi_only_key), mR.getBoolean(R.bool.pref_wifi_only_default));
			boolean isConnected = false;
			if (networkInfo != null) {
				isConnected = networkInfo.getState() == NetworkInfo.State.CONNECTED && (networkInfo.getTypeName().equals("WIFI") || (networkInfo.getTypeName().equals("mobile") && !wifiOnly));
			}
			mLc.setNetworkReachable(isConnected); 
		} catch (LinphoneCoreException e) {
			throw new LinphoneConfigException(getString(R.string.wrong_settings),e);
		}
	}
	
	private void setSignalingTransportsFromConfiguration(Transports t) {
		Transports ports = new Transports(t);
		boolean useRandomPort = getPrefBoolean(R.string.pref_transport_use_random_ports_key, mR.getBoolean(R.bool.pref_transport_use_random_ports_default));
		int lPreviousPort = tryToParseIntValue(getPrefString(R.string.pref_sip_port_key, getString(R.string.pref_sip_port_default)), 5060);
		if (lPreviousPort>0xFFFF || useRandomPort) {
			lPreviousPort=(0xDFFF & (int)Math.random())+1024;
			Log.w("Using random port " + lPreviousPort);
		}
		
		String transport = getPrefString(R.string.pref_transport_key, getString(R.string.pref_transport_udp_key));
		if (transport.equals(getString(R.string.pref_transport_tcp_key)))
		{
			ports.udp = 0;
			ports.tls = 0;
			ports.tcp = lPreviousPort;
		} else if (transport.equals(getString(R.string.pref_transport_udp_key)))
		{
			ports.tcp = 0;
			ports.tls = 0;
			ports.udp = lPreviousPort;
		} else if (transport.equals(getString(R.string.pref_transport_tls_key)))
		{
			ports.udp = 0;
			ports.tcp = 0;
			ports.tls = lPreviousPort;
		}

		mLc.setSignalingTransportPorts(ports);
	}

	private void enableDisableAudioCodec(String codec, int rate, int channels, int key) throws LinphoneCoreException {
		PayloadType pt = mLc.findPayloadType(codec, rate, channels);
		if (pt !=null) {
			boolean enable= getPrefBoolean(key,false);
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
		String mime = videoCodec.getMime();
		int key;
		int defaultValueKey;
		
		if ("MP4V-ES".equals(mime)) {
			key = R.string.pref_video_codec_mpeg4_key;
			defaultValueKey = R.bool.pref_video_codec_mpeg4_default;
		} else if ("H264".equals(mime)) {
			key = R.string.pref_video_codec_h264_key;
			defaultValueKey = R.bool.pref_video_codec_h264_default;
		} else if ("H263-1998".equals(mime)) {
			key = R.string.pref_video_codec_h263_key;
			defaultValueKey = R.bool.pref_video_codec_h263_default;
		} else if ("VP8".equals(mime)) {
			key = R.string.pref_video_codec_vp8_key;
			defaultValueKey = R.bool.pref_video_codec_vp8_default;
		} else {
			Log.e("Unhandled video codec ", mime);
			mLc.enablePayloadType(videoCodec, false);
			return;
		}

		boolean enable = getPrefBoolean(key, mR.getBoolean(defaultValueKey));
		mLc.enablePayloadType(videoCodec, enable);
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
			mServiceContext.unregisterReceiver(instance.mKeepAliveReceiver);
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
		return mPref.getInt(getString(R.string.pref_extra_accounts), 1);
	}


	/* Simple implementation as Android way seems very complicate:
	For example: with wifi and mobile actives; when pulling mobile down:
	I/Linphone( 8397): WIFI connected: setting network reachable
	I/Linphone( 8397): new state [RegistrationProgress]
	I/Linphone( 8397): mobile disconnected: setting network unreachable
	I/Linphone( 8397): Managing tunnel
	I/Linphone( 8397): WIFI connected: setting network reachable
	*/
	public void connectivityChanged(ConnectivityManager cm, boolean noConnectivity) {
		NetworkInfo eventInfo = cm.getActiveNetworkInfo();

		if (noConnectivity || eventInfo == null || eventInfo.getState() == NetworkInfo.State.DISCONNECTED) {
			Log.i("No connectivity: setting network unreachable");
			mLc.setNetworkReachable(false);
		} else if (eventInfo.getState() == NetworkInfo.State.CONNECTED){
			manageTunnelServer(eventInfo);
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mServiceContext);
			boolean wifiOnly = pref.getBoolean(getString(R.string.pref_wifi_only_key), mR.getBoolean(R.bool.pref_wifi_only_default));
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
	
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf, String url) {
		for (LinphoneSimpleListener listener : getSimpleListeners(LinphoneActivity.class)) {
			((LinphoneActivity) listener).onNewSubscriptionRequestReceived(lf, url);
		}
	}
	
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
		for (LinphoneSimpleListener listener : getSimpleListeners(LinphoneActivity.class)) {
			((LinphoneActivity) listener).onNotifyPresenceReceived(lf);
		}
	}
	
	public void textReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneAddress from, String message) {
		//deprecated
	}
	
	@Override
	public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {
		if (mServiceContext.getResources().getBoolean(R.bool.disable_chat)) {
			return;
		}
		
		LinphoneAddress from = message.getFrom();
		ChatStorage chatStorage = new ChatStorage(mServiceContext);

		String textMessage = message.getText();
		String url = message.getExternalBodyUrl();
		String notificationText = null;
		int id = -1;
		if (textMessage != null && textMessage.length() > 0) {
			id = chatStorage.saveMessage(from.asStringUriOnly(), "", textMessage);
			notificationText = textMessage;
		} else if (url != null && url.length() > 0) {
			Bitmap bm = ChatFragment.downloadImage(url);
			id = chatStorage.saveMessage(from.asStringUriOnly(), "", bm);
			notificationText = url;
		}
		
		LinphoneUtils.findUriPictureOfContactAndSetDisplayName(from, mServiceContext.getContentResolver());
		LinphoneService.instance().displayMessageNotification(from.asStringUriOnly(), from.getDisplayName(), notificationText);

		for (LinphoneSimpleListener listener : getSimpleListeners(LinphoneActivity.class)) {
			((LinphoneOnMessageReceivedListener) listener).onMessageReceived(from, message, id);
		}
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {
		Log.d("DTMF received: " + dtmf);
	}

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
	
	public Context getContext() {
		try {
			if (LinphoneActivity.isInstanciated())
				return LinphoneActivity.instance();
			else if (InCallActivity.isInstanciated())
				return InCallActivity.instance();
			else if (IncomingCallActivity.isInstanciated())
				return IncomingCallActivity.instance();
			else
				return LinphoneService.instance().getApplicationContext();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@SuppressLint("Wakelock")
	public void callState(final LinphoneCore lc,final LinphoneCall call, final State state, final String message) {
		Log.i("new state [",state,"]");
		if (state == IncomingReceived && !call.equals(lc.getCurrentCall())) {
			if (call.getReplacedCall()!=null){
				// attended transfer
				// it will be accepted automatically.
				return;
			} 
		}

		if (state == IncomingReceived || (state == State.CallIncomingEarlyMedia && mR.getBoolean(R.bool.allow_ringing_while_early_media))) {
			// Brighten screen for at least 10 seconds
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
			Context activity = getContext();
			if (activity != null) {
				TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
				if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
					mAudioManager.setMode(MODE_NORMAL);
				}
			}
		}
		if (state == State.Connected) {
			if (Hacks.needSoftvolume() || sLPref.useSoftvolume()) {
				adjustVolume(0); // Synchronize
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

	public void callStatsUpdated(final LinphoneCore lc, final LinphoneCall call, final LinphoneCallStats stats) {}

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

		mLc.startRinging();

		try {
			if ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE || mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) && mVibrator !=null) {
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
	
	public boolean isAutoAcceptCamera() {
		return isVideoEnabled() && getPrefBoolean(R.string.pref_video_automatically_accept_video_key, false);
	}
	
	public boolean isAutoInitiateVideoCalls() {
		return isVideoEnabled() && getPrefBoolean(R.string.pref_video_initiate_call_with_video_key, false);
	}

	// Called on first launch only
	public void initializePayloads() {
		Log.i("Initializing supported payloads");
		Editor e = mPref.edit();
		boolean fastCpu = Version.hasFastCpu();

		e.putBoolean(getString(R.string.pref_codec_gsm_key), true);
		e.putBoolean(getString(R.string.pref_codec_pcma_key), true);
		e.putBoolean(getString(R.string.pref_codec_pcmu_key), true);
		e.putBoolean(getString(R.string.pref_codec_speex8_key), true);
		e.putBoolean(getString(R.string.pref_codec_g722_key), false);
		e.putBoolean(getString(pref_codec_speex16_key), fastCpu);
		e.putBoolean(getString(pref_codec_speex32_key), fastCpu);

		boolean ilbc = LinphoneService.isReady() && LinphoneManager.getLc()
		.findPayloadType("iLBC", 8000, 1)!=null;
		e.putBoolean(getString(pref_codec_ilbc_key), ilbc);
		
		boolean amr = LinphoneService.isReady() && LinphoneManager.getLc()
		.findPayloadType("AMR", 8000, 1)!=null;
		e.putBoolean(getString(pref_codec_amr_key), amr);

        boolean amrwb = LinphoneService.isReady() && LinphoneManager.getLc()
        .findPayloadType("AMR-WB", 16000, 1)!=null;
        e.putBoolean(getString(pref_codec_amrwb_key), amrwb);
        
        boolean g729 = LinphoneService.isReady() && LinphoneManager.getLc()
        .findPayloadType("G729", 8000, 1)!=null;
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
		return reinviteWithVideo();
	}
	
	public boolean acceptCallIfIncomingPending() throws LinphoneCoreException {
		if (mLc.isInComingInvitePending()) {
			mLc.acceptCall(mLc.getCurrentCall());
			return true;
		}
		return false;
	}

	public boolean acceptCallWithParams(LinphoneCall call, LinphoneCallParams params) {
		try {
			mLc.acceptCallWithParams(call, params);
			return true;
		} catch (LinphoneCoreException e) {
			Log.i(e, "Accept call failed");
		}
		return false;
	}

	public static String extractIncomingRemoteName(Resources r, LinphoneAddress linphoneAddress) {
		return extractADisplayName(r, linphoneAddress);
	}

	public void adjustVolume(int i) {
		if (Build.VERSION.SDK_INT<15) {
			int oldVolume = mAudioManager.getStreamVolume(LINPHONE_VOLUME_STREAM);
			int maxVolume = mAudioManager.getStreamMaxVolume(LINPHONE_VOLUME_STREAM);

			int nextVolume = oldVolume +i;
			if (nextVolume > maxVolume) nextVolume = maxVolume;
			if (nextVolume < 0) nextVolume = 0;

			mLc.setPlaybackGain((nextVolume - maxVolume)* dbStep);
		} else
			// starting from ICS, volume must be adjusted by the application, at least for STREAM_VOICE_CALL volume stream
			mAudioManager.adjustStreamVolume(LINPHONE_VOLUME_STREAM, i<0?AudioManager.ADJUST_LOWER:AudioManager.ADJUST_RAISE, 0);
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
		WindowManager.LayoutParams params = window.getAttributes();
		View view = ((ViewGroup) window.getDecorView().findViewById(android.R.id.content)).getChildAt(0);
		if (nearby) {
            params.screenBrightness = 0.1f;
            view.setVisibility(View.INVISIBLE);
		} else  {
			params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            view.setVisibility(View.VISIBLE);
		}
        window.setAttributes(params);
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

		public void onCallStateChanged(LinphoneCall call, State state, String message) {
			if (state == State.OutgoingInit || state == State.IncomingReceived) {
				boolean sendCamera = mLc.getConferenceSize() == 0;
				enableCamera(call, sendCamera);
			}
			
			Context activity = getContext();
			if (activity != null) {
			TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
				if (state == State.CallEnd && mLc.getCallsNb() == 0 && tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
					routeAudioToReceiver();
				}
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
			for (LinphoneOnRegistrationStateChangedListener listener : getSimpleListeners(LinphoneActivity.class)) {
				listener.onRegistrationStateChanged(state);
			}
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

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneAddress from, byte[] event) {
	}
}
