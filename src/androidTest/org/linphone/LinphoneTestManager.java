package org.linphone;

/*
LinphoneTestManager.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.LinphoneException;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneManager.LinphoneConfigException;
import org.linphone.LinphoneService;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAddress.TransportType;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.AuthMethod;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.RemoteProvisioningState;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.telephony.TelephonyManager;

public class LinphoneTestManager implements LinphoneCoreListener{

	private static LinphoneTestManager instance;
	private Context mIContext;
	private LinphoneCore mLc1, mLc2;

	public String lastMessageReceived;
	public boolean isDTMFReceived = false;
	public boolean autoAnswer = true;
	public boolean declineCall = false;

	private final String linphoneRootCaFile;
	private LinphoneCoreListenerBase mListener;

	private Timer mTimer1 = new Timer("Linphone scheduler 1");
	private Timer mTimer2 = new Timer("Linphone scheduler 2");

	private LinphoneTestManager(Context ac, Context ic) {
		mIContext = ic;
		linphoneRootCaFile = ac.getFilesDir().getAbsolutePath() + "/rootca.pem";
	}

	public static LinphoneTestManager createAndStart(Context ac, Context ic, int id) {
		if (instance == null)
			instance = new LinphoneTestManager(ac, ic);

		instance.startLibLinphone(ic, id);
		TelephonyManager tm = (TelephonyManager) ac.getSystemService(Context.TELEPHONY_SERVICE);
		boolean gsmIdle = tm.getCallState() == TelephonyManager.CALL_STATE_IDLE;
		setGsmIdle(gsmIdle, id);

		return instance;
	}

	private synchronized void startLibLinphone(Context c, int id) {
		try {
			LinphoneCoreFactory.instance().setDebugMode(true, "LinphoneTester");

			final LinphoneCore mLc = LinphoneCoreFactory.instance().createLinphoneCore(this, c);
			if (id == 2) {
				mLc2 = mLc;
			} else {
				mLc1 = mLc;
			}

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
			mLc.setRootCA(linphoneRootCaFile);

			int availableCores = Runtime.getRuntime().availableProcessors();
			Log.w("MediaStreamer : " + availableCores + " cores detected and configured");
			mLc.setCpuCount(availableCores);

			Transports t = mLc.getSignalingTransportPorts();
			t.udp = -1;
			t.tcp = -1;
			mLc.setSignalingTransportPorts(t);

			try {
				initFromConf(mLc);
			} catch (LinphoneException e) {
				Log.w("no config ready yet");
			}

			TimerTask lTask = new TimerTask() {
				@Override
				public void run() {
					mLc.iterate();
				}
			};

			if (id == 2) {
				mTimer2.scheduleAtFixedRate(lTask, 0, 20);
			} else {
				mTimer1.scheduleAtFixedRate(lTask, 0, 20);
			}

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

	public void initFromConf(LinphoneCore mLc) throws LinphoneConfigException, LinphoneCoreException {
		LinphoneCoreFactory.instance().setDebugMode(true, "LinphoneTester");

		initAccounts(mLc);

		mLc.setVideoPolicy(true, true);
		mLc.enableVideo(true, true);

		mLc.setUseRfc2833ForDtmfs(false);
		mLc.setUseSipInfoForDtmfs(true);

		mLc.setNetworkReachable(true);
	}

	public boolean detectVideoCodec(String mime, LinphoneCore mLc) {
		for (PayloadType videoCodec : mLc.getVideoCodecs()) {
			if (mime.equals(videoCodec.getMime())) return true;
		}
		return false;
	}

	public boolean detectAudioCodec(String mime, LinphoneCore mLc){
		for (PayloadType audioCodec : mLc.getAudioCodecs()) {
			if (mime.equals(audioCodec.getMime())) return true;
		}
		return false;
	}

	void initMediaEncryption(LinphoneCore mLc){
		MediaEncryption me = MediaEncryption.None;
		mLc.setMediaEncryption(me);
	}

	private void initAccounts(LinphoneCore mLc) throws LinphoneCoreException {
		mLc.clearAuthInfos();
		mLc.clearProxyConfigs();

		String username, password, domain;
		if (mLc.equals(mLc1)) {
			username = mIContext.getString(R.string.account_test_calls_login);
			password = mIContext.getString(R.string.account_test_calls_pwd);
			domain = mIContext.getString(R.string.account_test_calls_domain);
		} else {
			username = mIContext.getString(R.string.conference_account_login);
			password = mIContext.getString(R.string.conference_account_password);
			domain = mIContext.getString(R.string.conference_account_domain);
		}

		LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(username, password, null, domain);
		mLc.addAuthInfo(lAuthInfo);
		String identity = "sip:" + username +"@" + domain;
		String proxy = "sip:" + domain;
		LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxy);
		proxyAddr.setTransport(TransportType.LinphoneTransportTls);
		LinphoneProxyConfig proxycon = mLc.createProxyConfig(identity, proxyAddr.asStringUriOnly(), proxyAddr.asStringUriOnly(), true);
		mLc.addProxyConfig(proxycon);
		mLc.setDefaultProxyConfig(proxycon);

		LinphoneProxyConfig lDefaultProxyConfig = mLc.getDefaultProxyConfig();
		if (lDefaultProxyConfig != null) {
			//escape +
			lDefaultProxyConfig.setDialEscapePlus(false);
		} else if (LinphoneService.isReady()) {
			getLc().addListener(this);
			this.registrationState(mLc, lDefaultProxyConfig, RegistrationState.RegistrationNone, null);
		}
	}

	public static synchronized final LinphoneTestManager getInstance() {
		return instance;
	}

	public static synchronized final LinphoneCore getLc(int i) {
		if (i == 2)
			return getInstance().mLc2;
		return getInstance().mLc1;
	}

	public static synchronized final LinphoneCore getLc() {
		return getLc(1);
	}

	private int savedMaxCallWhileGsmIncall;
	private synchronized void preventSIPCalls(LinphoneCore mLc) {
		if (savedMaxCallWhileGsmIncall != 0) {
			Log.w("SIP calls are already blocked due to GSM call running");
			return;
		}
		savedMaxCallWhileGsmIncall = mLc.getMaxCalls();
		mLc.setMaxCalls(0);
	}
	private synchronized void allowSIPCalls(LinphoneCore mLc) {
		if (savedMaxCallWhileGsmIncall == 0) {
			Log.w("SIP calls are already allowed as no GSM call knowned to be running");
			return;
		}
		mLc.setMaxCalls(savedMaxCallWhileGsmIncall);
		savedMaxCallWhileGsmIncall = 0;
	}
	public static void setGsmIdle(boolean gsmIdle, int id) {
		LinphoneTestManager mThis = instance;
		if (mThis == null) return;
		if (gsmIdle) {
			mThis.allowSIPCalls(LinphoneTestManager.getLc(id));
		} else {
			mThis.preventSIPCalls(LinphoneTestManager.getLc(id));
		}
	}

	private void doDestroy() {
		try {
			mTimer1.cancel();
			mTimer2.cancel();
			mLc1.destroy();
			mLc2.destroy();
		}
		catch (RuntimeException e) {
			e.printStackTrace();
		}
		finally {
			mLc1 = null;
			mLc2 = null;
			instance = null;
		}
	}

	public static synchronized void destroy() {
		if (instance == null) return;
		instance.doDestroy();
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
				lc.terminateCall(call);
			} else if (autoAnswer) {
				try {
					lc.acceptCall(call);
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

	@Override
	public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev,
			PublishState state) {
		// TODO Auto-generated method stub

	}

	@Override
	public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {
		// TODO Auto-generated method stub
	}

	@Override
	public void configuringStatus(LinphoneCore lc,
			RemoteProvisioningState state, String message) {
		// TODO Auto-generated method stub
	}

	@Override
	public void authInfoRequested(LinphoneCore lc, String realm,
			String username, String domain) {
		// TODO Auto-generated method stub

	}

	@Override
	public void authenticationRequested(LinphoneCore lc,
			LinphoneAuthInfo authInfo, AuthMethod method) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fileTransferProgressIndication(LinphoneCore lc,
			LinphoneChatMessage message, LinphoneContent content, int progress) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fileTransferRecv(LinphoneCore lc, LinphoneChatMessage message,
			LinphoneContent content, byte[] buffer, int size) {
		// TODO Auto-generated method stub

	}

	@Override
	public int fileTransferSend(LinphoneCore lc, LinphoneChatMessage message,
			LinphoneContent content, ByteBuffer buffer, int size) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void uploadProgressIndication(LinphoneCore lc, int offset, int total)  {
		// TODO Auto-generated method stub

	}


	@Override
	public void uploadStateChanged(LinphoneCore lc, LinphoneCore.LogCollectionUploadState state,
			String info) {
		// TODO Auto-generated method stub

	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status,
									int delay_ms, Object data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void friendListCreated(LinphoneCore lc, LinphoneFriendList list) {
		// TODO Auto-generated method stub
	}

	@Override
	public void friendListRemoved(LinphoneCore lc, LinphoneFriendList list) {
		// TODO Auto-generated method stub
	}

	@Override
	public void networkReachableChanged(LinphoneCore lc, boolean enable) {
		// TODO Auto-generated method stub
	}

	@Override
	public void messageReceivedUnableToDecrypted(LinphoneCore lc, LinphoneChatRoom cr,
												 LinphoneChatMessage message) {
		// TODO Auto-generated method stub
	}
}
