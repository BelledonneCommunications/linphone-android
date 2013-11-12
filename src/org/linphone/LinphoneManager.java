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

import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;
import static org.linphone.core.LinphoneCall.State.CallEnd;
import static org.linphone.core.LinphoneCall.State.Error;
import static org.linphone.core.LinphoneCall.State.IncomingReceived;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.LinphoneSimpleListener.ConnectivityChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnAudioChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnAudioChangedListener.AudioState;
import org.linphone.LinphoneSimpleListener.LinphoneOnDTMFReceivedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnMessageReceivedListener;
import org.linphone.LinphoneSimpleListener.LinphoneServiceListener;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
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
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
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
public class LinphoneManager implements LinphoneCoreListener {

	private static LinphoneManager instance;
	private Context mServiceContext;
	private AudioManager mAudioManager;
	private PowerManager mPowerManager;
	private Resources mR;
	private LinphonePreferences mPrefs;
	private LinphoneCore mLc;
	private String lastLcStatusMessage;
	private String basePath;
	private static boolean sExited;
	private boolean mAudioFocused;
	private boolean isNetworkReachable;
	private ConnectivityManager mConnectivityManager;

	private WakeLock mIncallWakeLock;
	
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothHeadset mBluetoothHeadset;
	private BluetoothProfile.ServiceListener mProfileListener;
	private BroadcastReceiver bluetoothReiceiver = new BluetoothManager();
	public boolean isBluetoothScoConnected;
	public boolean isUsingBluetoothAudioRoute;
	private boolean mBluetoothStarted;

	private static List<LinphoneSimpleListener> simpleListeners = new ArrayList<LinphoneSimpleListener>();
	public static void addListener(LinphoneSimpleListener listener) {
		if (!simpleListeners.contains(listener)) {
			simpleListeners.add(listener);
		}
	}
	public static void removeListener(LinphoneSimpleListener listener) {
		simpleListeners.remove(listener);
	}

	protected LinphoneManager(final Context c, LinphoneServiceListener listener) {
		sExited=false;
		mServiceContext = c;
		mListenerDispatcher = new ListenerDispatcher(listener);
		basePath = c.getFilesDir().getAbsolutePath();
		mLPConfigXsd = basePath + "/lpconfig.xsd";
		mLinphoneFactoryConfigFile = basePath + "/linphonerc";
		mLinphoneConfigFile = basePath + "/.linphonerc";
		mLinphoneRootCaFile = basePath + "/rootca.pem";
		mRingSoundFile = basePath + "/oldphone_mono.wav"; 
		mRingbackSoundFile = basePath + "/ringback.wav";
		mPauseSoundFile = basePath + "/toy_mono.wav";
		mChatDatabaseFile = basePath + "/linphone-history.db";

		mPrefs = LinphonePreferences.instance();
		mAudioManager = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE));
		mVibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
		mPowerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
		mConnectivityManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
		mR = c.getResources();
	}

	private static final int LINPHONE_VOLUME_STREAM = STREAM_VOICE_CALL;
	private static final int dbStep = 4;
	/** Called when the activity is first created. */
	private final String mLPConfigXsd;
	private final String mLinphoneFactoryConfigFile;
	private final String mLinphoneRootCaFile;
	public final String mLinphoneConfigFile;
	private final String mRingSoundFile; 
	private final String mRingbackSoundFile;
	private final String mPauseSoundFile;
	private final String mChatDatabaseFile;

	private Timer mTimer;

	private  BroadcastReceiver mKeepAliveReceiver = new KeepAliveReceiver();

	private void routeAudioToSpeakerHelper(boolean speakerOn) {
		isUsingBluetoothAudioRoute = false;
		if (mAudioManager != null && mBluetoothStarted) {
			//Compatibility.setAudioManagerInCallMode(mAudioManager);
			mAudioManager.stopBluetoothSco();
			mAudioManager.setBluetoothScoOn(false);
			mBluetoothStarted = false;
		}
		
		if (!speakerOn) {
			mLc.enableSpeaker(false);
		} else {
			mLc.enableSpeaker(true);
		}
		
		for (LinphoneOnAudioChangedListener listener : getSimpleListeners(LinphoneOnAudioChangedListener.class)) {
			listener.onAudioStateChanged(speakerOn ? AudioState.SPEAKER : AudioState.EARPIECE);
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
	@SuppressWarnings("deprecation")
	public void startBluetooth() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter.isEnabled()) {
			if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
				mProfileListener = new BluetoothProfile.ServiceListener() {
					@TargetApi(Build.VERSION_CODES.HONEYCOMB)
					public void onServiceConnected(int profile, BluetoothProfile proxy) {
					    if (profile == BluetoothProfile.HEADSET) {
					        mBluetoothHeadset = (BluetoothHeadset) proxy;
			        		isBluetoothScoConnected = true;
					        Log.d("Bluetooth headset connected");
					    }
					}
					@TargetApi(Build.VERSION_CODES.HONEYCOMB)
					public void onServiceDisconnected(int profile) {
					    if (profile == BluetoothProfile.HEADSET) {
					        mBluetoothHeadset = null;
			        		isBluetoothScoConnected = false;
					        Log.d("Bluetooth headset disconnected");
					        routeAudioToReceiver();
					    }
					}
				};
				mBluetoothAdapter.getProfileProxy(mServiceContext, mProfileListener, BluetoothProfile.HEADSET);
			} else {
				try {
					mServiceContext.unregisterReceiver(bluetoothReiceiver);
				} catch (Exception e) {}
				
				Intent currentValue = mServiceContext.registerReceiver(bluetoothReiceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
				int state = currentValue == null ? 0 : currentValue.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, 0);
	        	if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
	        		isBluetoothScoConnected = true;
	        	}
			}
		} else {
    		isBluetoothScoConnected = false;
			scoDisconnected();
			routeAudioToReceiver();
		}
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public boolean routeAudioToBluetooth() {
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter.isEnabled() && mAudioManager.isBluetoothScoAvailableOffCall()) {
			mAudioManager.setBluetoothScoOn(true);	
			mAudioManager.startBluetoothSco();
			mBluetoothStarted=true;
			
			if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
				isUsingBluetoothAudioRoute = false;
				if (mBluetoothHeadset != null) {
					List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();                        
					for (final BluetoothDevice dev : devices) {           
						isUsingBluetoothAudioRoute |= mBluetoothHeadset.getConnectionState(dev) == BluetoothHeadset.STATE_CONNECTED;
					}
				}
				
				if (!isUsingBluetoothAudioRoute) {
					Log.d("No bluetooth device available");	
					scoDisconnected();
				} else {
					//Why is this for:
					//mAudioManager.setMode(AudioManager.MODE_IN_CALL);
					for (LinphoneOnAudioChangedListener listener : getSimpleListeners(LinphoneOnAudioChangedListener.class)) {
						listener.onAudioStateChanged(AudioState.SPEAKER);
					}
				}
			}
			return isUsingBluetoothAudioRoute;
		}
		
		return false;
	}
	
	public void scoConnected() {
		Log.i("Bluetooth sco connected!");
		isBluetoothScoConnected = true;
	}
	
	public void scoDisconnected() {
		Log.w("Bluetooth sco disconnected!");
		isUsingBluetoothAudioRoute = false;
		isBluetoothScoConnected = false;
		if (mAudioManager != null) {
			//why is this for ?
			//mAudioManager.setMode(AudioManager.MODE_NORMAL);
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
		
		if (Version.isVideoCapable() && getLc().isVideoSupported())
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
		newOutgoingCall(to, address.getDisplayedName());
	}
	
	public void newOutgoingCall(String to, String displayName) {
//		if (mLc.isIncall()) {
//			listenerDispatcher.tryingNewOutgoingCallButAlreadyInCall();
//			return;
//		}
		LinphoneAddress lAddress;
		try {
			lAddress = mLc.interpretUrl(to);
			if (mServiceContext.getResources().getBoolean(R.bool.override_domain_using_default_one)) {
				lAddress.setDomain(mServiceContext.getString(R.string.default_domain));
			}
			LinphoneProxyConfig lpc = mLc.getDefaultProxyConfig();

			if (mR.getBoolean(R.bool.forbid_self_call) && lpc!=null && lAddress.asStringUriOnly().equals(lpc.getIdentity())) {
				mListenerDispatcher.tryingNewOutgoingCallButWrongDestinationAddress();
				return;
			}
		} catch (LinphoneCoreException e) {
			mListenerDispatcher.tryingNewOutgoingCallButWrongDestinationAddress();
			return;
		}
		lAddress.setDisplayName(displayName);

		boolean isLowBandwidthConnection = !LinphoneUtils.isHightBandwidthConnection(LinphoneService.instance().getApplicationContext());
		
		if (mLc.isNetworkReachable()) {
			try {
				if (Version.isVideoCapable()) {
					boolean prefVideoEnable = mPrefs.isVideoEnabled();
					boolean prefInitiateWithVideo = mPrefs.shouldInitiateVideoCall();
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
	
	private void resetCameraFromPreferences() {
		boolean useFrontCam = mPrefs.useFrontCam();
		
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
			if (mServiceContext.getResources().getBoolean(R.bool.enable_call_notification))
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

	public void terminateCall() {
		if (mLc.isIncall()) {
			mLc.terminateCall(mLc.getCurrentCall());
		}
	}

	public void initTunnelFromConf() {
		if (!mLc.isTunnelAvailable()) 
			return;
		
		NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
		mLc.tunnelCleanServers();
		String host = mPrefs.getTunnelHost();
		if (host != null) {
			int port = mPrefs.getTunnelPort();
			mLc.tunnelAddServerAndMirror(host, port, 12345, 500);
			manageTunnelServer(info);
		}
	}

	private boolean isTunnelNeeded(NetworkInfo info) {
		if (info == null) {
			Log.i("No connectivity: tunnel should be disabled");
			return false;
		}

		String pref = mPrefs.getTunnelMode();

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

	private void manageTunnelServer(NetworkInfo info) {
		if (mLc == null) return;
		if (!mLc.isTunnelAvailable()) return;

		Log.i("Managing tunnel");
		if (isTunnelNeeded(info)) {
			Log.i("Tunnel need to be activated");
			mLc.tunnelEnable(true);
		} else {
			Log.i("Tunnel should not be used");
			String pref = mPrefs.getTunnelMode();
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
			boolean isDebugLogEnabled = !(mR.getBoolean(R.bool.disable_every_log));
			LinphoneCoreFactory.instance().setDebugMode(isDebugLogEnabled, getString(R.string.app_name));
			
			// Try to get remote provisioning
			// First check if there is a remote provisioning url in the old preferences API
			
			String remote_provisioning = mPrefs.getRemoteProvisioningUrl();
			if(remote_provisioning != null && remote_provisioning.length() > 0 && RemoteProvisioning.isAvailable()) {
				RemoteProvisioning.download(remote_provisioning, mLinphoneConfigFile);
			}
			
			initLiblinphone(c);
			
			PreferencesMigrator prefMigrator = new PreferencesMigrator(mServiceContext);
			if (prefMigrator.isMigrationNeeded()) {
				prefMigrator.doMigration();
			}
			
			if (mServiceContext.getResources().getBoolean(R.bool.enable_push_id)) {
				Compatibility.initPushNotificationService(mServiceContext);
			}

			IntentFilter lFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
	        lFilter.addAction(Intent.ACTION_SCREEN_OFF);
	        mServiceContext.registerReceiver(mKeepAliveReceiver, lFilter);
	        
			updateNetworkReachability();
			
	        startBluetooth();
	        resetCameraFromPreferences();
		}
		catch (Exception e) {
			Log.e(e, "Cannot start linphone");
		}
	}
	
	public synchronized void initLiblinphone(Context c) throws LinphoneCoreException {
		boolean isDebugLogEnabled = !(mR.getBoolean(R.bool.disable_every_log)) && mPrefs.isDebugEnabled();
		LinphoneCoreFactory.instance().setDebugMode(isDebugLogEnabled, getString(R.string.app_name));
		
		mLc = LinphoneCoreFactory.instance().createLinphoneCore(this, mLinphoneConfigFile, mLinphoneFactoryConfigFile, null);
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

		mLc.setZrtpSecretsCache(basePath + "/zrtp_secrets");

		mLc.setRing(null);
		mLc.setRootCA(mLinphoneRootCaFile);
		mLc.setPlayFile(mPauseSoundFile);
		mLc.setChatDatabasePath(mChatDatabaseFile);

		int availableCores = Runtime.getRuntime().availableProcessors();
		Log.w("MediaStreamer : " + availableCores + " cores detected and configured");
		mLc.setCpuCount(availableCores);
		
		int camId = 0;
		AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
		for (AndroidCamera androidCamera : cameras) {
			if (androidCamera.frontFacing == mPrefs.useFrontCam())
				camId = androidCamera.id;
		}
		LinphoneManager.getLc().setVideoDevice(camId);
		
		initTunnelFromConf();
        
		TimerTask lTask = new TimerTask() {
			@Override
			public void run() {
				mLc.iterate();
			}
		};
		/*use schedule instead of scheduleAtFixedRate to avoid iterate from being call in burst after cpu wake up*/
		mTimer = new Timer("Linphone scheduler");
		mTimer.schedule(lTask, 0, 20); 
	}

	private void copyAssetsFromPackage() throws IOException {
		copyIfNotExist(R.raw.oldphone_mono, mRingSoundFile);
		copyIfNotExist(R.raw.ringback, mRingbackSoundFile);
		copyIfNotExist(R.raw.toy_mono, mPauseSoundFile);
		copyIfNotExist(R.raw.linphonerc_default, mLinphoneConfigFile);
		copyFromPackage(R.raw.linphonerc_factory, new File(mLinphoneFactoryConfigFile).getName());
		copyIfNotExist(R.raw.lpconfig, mLPConfigXsd);
		copyIfNotExist(R.raw.rootca, mLinphoneRootCaFile);
	}
	
	private void copyIfNotExist(int ressourceId,String target) throws IOException {
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
	
	public void updateNetworkReachability() {
		ConnectivityManager cm = (ConnectivityManager) mServiceContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo eventInfo = cm.getActiveNetworkInfo();

		if (eventInfo == null || eventInfo.getState() == NetworkInfo.State.DISCONNECTED) {
			Log.i("No connectivity: setting network unreachable");
			if (isNetworkReachable) {
				isNetworkReachable = false;
				mLc.setNetworkReachable(isNetworkReachable);
			}
		} else if (eventInfo.getState() == NetworkInfo.State.CONNECTED){
			manageTunnelServer(eventInfo);
			boolean wifiOnly = LinphonePreferences.instance().isWifiOnlyEnabled();
			if ((eventInfo.getTypeName().equals("WIFI")) || (!eventInfo.getTypeName().equals("WIFI") && !wifiOnly)) {
				if (!isNetworkReachable) {
					isNetworkReachable = true;
					mLc.setNetworkReachable(isNetworkReachable);
					Log.i(eventInfo.getTypeName()," connected: setting network reachable (network = " + eventInfo.getTypeName() + ")");
				}
			} else {
				if (isNetworkReachable) {
					isNetworkReachable = false;
					mLc.setNetworkReachable(isNetworkReachable);
					Log.i(eventInfo.getTypeName()," connected: wifi only activated, setting network unreachable (network = " + eventInfo.getTypeName() + ")");
				}
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB) 
	private void doDestroy() {
		ChatStorage.getInstance().close();

		try {
			mServiceContext.unregisterReceiver(bluetoothReiceiver);
		} catch (Exception e) {}
		
		try {
			if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30))
				mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
		} catch (Exception e) {}
		
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
		updateNetworkReachability();
		
		if (connectivityListener != null) {
			connectivityListener.onConnectivityChanged(mServiceContext, eventInfo, cm);
		}
	}

	private ConnectivityChangedListener connectivityListener;
	public void addConnectivityChangedListener(ConnectivityChangedListener l) {
		connectivityListener = l;
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
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {
		Log.d("DTMF received: " + dtmf);
		if (dtmfReceivedListener != null)
			dtmfReceivedListener.onDTMFReceived(call, dtmf);
	}
	
	private LinphoneOnDTMFReceivedListener dtmfReceivedListener;
	public void setOnDTMFReceivedListener(LinphoneOnDTMFReceivedListener listener) {
		dtmfReceivedListener = listener;
	}
	
	@Override
	public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {
		if (mServiceContext.getResources().getBoolean(R.bool.disable_chat)) {
			return;
		}
		
		LinphoneAddress from = message.getFrom();

		String textMessage = message.getText();
		String url = message.getExternalBodyUrl();
		int id = -1;
		if (textMessage != null && textMessage.length() > 0) {
			id = ChatStorage.getInstance().saveTextMessage(from.asStringUriOnly(), "", textMessage, message.getTime());
		} else if (url != null && url.length() > 0) {
			//Bitmap bm = ChatFragment.downloadImage(url);
			id = ChatStorage.getInstance().saveImageMessage(from.asStringUriOnly(), "", null, message.getExternalBodyUrl(), message.getTime());
		}
		
		try {
			LinphoneUtils.findUriPictureOfContactAndSetDisplayName(from, mServiceContext.getContentResolver());
			if (!mServiceContext.getResources().getBoolean(R.bool.disable_chat__message_notification)) {
				LinphoneService.instance().displayMessageNotification(from.asStringUriOnly(), from.getDisplayName(), textMessage);
			}
		} catch (Exception e) { }

		for (LinphoneSimpleListener listener : getSimpleListeners(LinphoneOnMessageReceivedListener.class)) {
			((LinphoneOnMessageReceivedListener) listener).onMessageReceived(from, message, id);
		}
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
			else if (mServiceContext != null)
				return mServiceContext;
			else if (LinphoneService.isReady())
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
		
		if (state == IncomingReceived && mR.getBoolean(R.bool.auto_answer_calls)) {
			try {
				mLc.acceptCall(call);
			} catch (LinphoneCoreException e) {
				e.printStackTrace();
			}
		}
		else if (state == IncomingReceived || (state == State.CallIncomingEarlyMedia && mR.getBoolean(R.bool.allow_ringing_while_early_media))) {
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
		
		if (state == LinphoneCall.State.Connected) {
			if (mLc.getCallsNb() == 1) {
				requestAudioFocus();
				Compatibility.setAudioManagerInCallMode(mAudioManager);
			}
			
			if (Hacks.needSoftvolume()) {
				adjustVolume(0); // Synchronize
			}
		}

		if (state == CallEnd || state == Error) {
			if (mLc.getCallsNb() == 0) {
				if (mAudioFocused){
					int res=mAudioManager.abandonAudioFocus(null);
					Log.d("Audio focus released a bit later: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
					mAudioFocused=false;
				}
				
				Context activity = getContext();
				if (activity != null) {
					TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
					if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
						mAudioManager.setMode(AudioManager.MODE_NORMAL);
						Log.d("---AudioManager: back to MODE_NORMAL");
					}
				}
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
	
	private void requestAudioFocus(){
		if (!mAudioFocused){
			int res = mAudioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT );
			Log.d("Audio focus requested: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
			if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) mAudioFocused=true;
		}
	}
	
	private synchronized void startRinging()  {
		if (disableRinging) {
			return;
		}

		if (Hacks.needGalaxySAudioHack()) {
			mAudioManager.setMode(MODE_RINGTONE);
		}
		
		try {
			if ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE || mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) && mVibrator != null) {
				long[] patern = {0,1000,1000};
				mVibrator.vibrate(patern, 1);
			}
			if (mRingerPlayer == null) {
				requestAudioFocus();
				mRingerPlayer = new MediaPlayer();
				mRingerPlayer.setAudioStreamType(STREAM_RING);
				
				String ringtone = LinphonePreferences.instance().getRingtone(android.provider.Settings.System.DEFAULT_RINGTONE_URI.toString());
				try {
					if (ringtone.startsWith("content://")) {
						mRingerPlayer.setDataSource(mServiceContext, Uri.parse(ringtone));
					} else {
						FileInputStream fis = new FileInputStream(ringtone);
						mRingerPlayer.setDataSource(fis.getFD());
						fis.close();
					}
				} catch (IOException e) {
					Log.e(e, "Cannot set ringtone");
				}
				
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
		if (mRingerPlayer != null) {
			mRingerPlayer.stop();
			mRingerPlayer.release();
			mRingerPlayer = null;
		}
		if (mVibrator != null) {
			mVibrator.cancel();
		}
		
		if (Hacks.needGalaxySAudioHack())
			mAudioManager.setMode(AudioManager.MODE_NORMAL);
		
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
	
	public boolean acceptCall(LinphoneCall call) {
		try {
			mLc.acceptCall(call);
			return true;
		} catch (LinphoneCoreException e) {
			Log.i(e, "Accept call failed");
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
            Compatibility.hideNavigationBar(activity);
		} else  {
			params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            view.setVisibility(View.VISIBLE);
            Compatibility.showNavigationBar(activity);
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
			for (LinphoneOnRegistrationStateChangedListener listener : getSimpleListeners(LinphoneOnRegistrationStateChangedListener.class)) {
				listener.onRegistrationStateChanged(state);
			}
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
	@Override
	public void transferState(LinphoneCore lc, LinphoneCall call,
			State new_call_state) {
		
	}
	@Override
	public void infoReceived(LinphoneCore lc, LinphoneCall call, LinphoneInfoMessage info) {
		Log.d("Info message received from "+call.getRemoteAddress().asString());
		LinphoneContent ct=info.getContent();
		if (ct!=null){
			Log.d("Info received with body with mime type "+ct.getType()+"/"+ct.getSubtype()+" and data ["+ct.getDataAsString()+"]");
		}
	}
	@Override
	public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev,
			SubscriptionState state) {
		Log.d("Subscription state changed to "+state+" event name is "+ev.getEventName());
	}
	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev,
			String eventName, LinphoneContent content) {
		Log.d("Notify received for event "+eventName);
		if (content!=null) Log.d("with content "+content.getType()+"/"+content.getSubtype()+" data:"+content.getDataAsString());
	}
	@Override
	public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev,
			PublishState state) {
		// TODO Auto-generated method stub
		
	}
}
