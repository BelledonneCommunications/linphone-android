package org.linphone.mini;

/*
LinphoneMiniManager.java
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

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.RemoteProvisioningState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

public class LinphoneMiniManager implements LinphoneCoreListener {
	private static LinphoneMiniManager mInstance;
	private Context mContext;
	private LinphoneCore mLinphoneCore;
	private Timer mTimer;

	public LinphoneMiniManager(Context c) {
		mContext = c;
		LinphoneCoreFactory.instance().setDebugMode(true, "Linphone Mini");

		try {
			String basePath = mContext.getFilesDir().getAbsolutePath();
			copyAssetsFromPackage(basePath);
			mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(this, basePath + "/.linphonerc", basePath + "/linphonerc", null, mContext);
			initLinphoneCoreValues(basePath);

			setUserAgent();
			setFrontCamAsDefault();
			startIterate();
			mInstance = this;
	        mLinphoneCore.setNetworkReachable(true); // Let's assume it's true
		} catch (LinphoneCoreException e) {
		} catch (IOException e) {
		}
	}

	public static LinphoneMiniManager getInstance() {
		return mInstance;
	}

	public void destroy() {
		try {
			mTimer.cancel();
			mLinphoneCore.destroy();
		}
		catch (RuntimeException e) {
		}
		finally {
			mLinphoneCore = null;
			mInstance = null;
		}
	}

	private void startIterate() {
		TimerTask lTask = new TimerTask() {
			@Override
			public void run() {
				mLinphoneCore.iterate();
			}
		};

		/*use schedule instead of scheduleAtFixedRate to avoid iterate from being call in burst after cpu wake up*/
		mTimer = new Timer("LinphoneMini scheduler");
		mTimer.schedule(lTask, 0, 20);
	}

	private void setUserAgent() {
		try {
			String versionName = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
			if (versionName == null) {
				versionName = String.valueOf(mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode);
			}
			mLinphoneCore.setUserAgent("LinphoneMiniAndroid", versionName);
		} catch (NameNotFoundException e) {
		}
	}

	private void setFrontCamAsDefault() {
		int camId = 0;
		AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
		for (AndroidCamera androidCamera : cameras) {
			if (androidCamera.frontFacing)
				camId = androidCamera.id;
		}
		mLinphoneCore.setVideoDevice(camId);
	}

	private void copyAssetsFromPackage(String basePath) throws IOException {
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.oldphone_mono, basePath + "/oldphone_mono.wav");
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.ringback, basePath + "/ringback.wav");
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.toy_mono, basePath + "/toy_mono.wav");
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.linphonerc_default, basePath + "/.linphonerc");
		LinphoneMiniUtils.copyFromPackage(mContext, R.raw.linphonerc_factory, new File(basePath + "/linphonerc").getName());
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.lpconfig, basePath + "/lpconfig.xsd");
		LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.rootca, basePath + "/rootca.pem");
	}

	private void initLinphoneCoreValues(String basePath) {
		mLinphoneCore.setContext(mContext);
		mLinphoneCore.setRing(null);
		mLinphoneCore.setRootCA(basePath + "/rootca.pem");
		mLinphoneCore.setPlayFile(basePath + "/toy_mono.wav");
		mLinphoneCore.setChatDatabasePath(basePath + "/linphone-history.db");

		int availableCores = Runtime.getRuntime().availableProcessors();
		mLinphoneCore.setCpuCount(availableCores);
	}

	@Override
	public void authInfoRequested(LinphoneCore lc, String realm, String username) {

	}

	@Override
	public void globalState(LinphoneCore lc, GlobalState state, String message) {
		Log.d("Global state: " + state + "(" + message + ")");
	}

	@Override
	public void callState(LinphoneCore lc, LinphoneCall call, State cstate,
			String message) {
		Log.d("Call state: " + cstate + "(" + message + ")");
	}

	@Override
	public void callStatsUpdated(LinphoneCore lc, LinphoneCall call,
			LinphoneCallStats stats) {

	}

	@Override
	public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call,
			boolean encrypted, String authenticationToken) {

	}

	@Override
	public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg,
			RegistrationState cstate, String smessage) {
		Log.d("Registration state: " + cstate + "(" + smessage + ")");
	}

	@Override
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf,
			String url) {

	}

	@Override
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {

	}

	@Override
	public void textReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneAddress from, String message) {

	}

	@Override
	public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneChatMessage message) {
		Log.d("Message received from " + cr.getPeerAddress().asString() + " : " + message.getText() + "(" + message.getExternalBodyUrl() + ")");
	}

	@Override
	public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {
		Log.d("Composing received from " + cr.getPeerAddress().asString());
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {

	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status,
			int delay_ms, Object data) {

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
	public void infoReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneInfoMessage info) {

	}

	@Override
	public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev,
			SubscriptionState state) {

	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev,
			String eventName, LinphoneContent content) {
		Log.d("Notify received: " + eventName + " -> " + content.getDataAsString());
	}

	@Override
	public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev,
			PublishState state) {

	}

	@Override
	public void configuringStatus(LinphoneCore lc,
			RemoteProvisioningState state, String message) {
		Log.d("Configuration state: " + state + "(" + message + ")");
	}

	@Override
	public void show(LinphoneCore lc) {

	}

	@Override
	public void displayStatus(LinphoneCore lc, String message) {

	}

	@Override
	public void displayMessage(LinphoneCore lc, String message) {

	}

	@Override
	public void displayWarning(LinphoneCore lc, String message) {

	}

}
