package org.linphone.assistant;

/*
EchoCancellerCalibrationFragment.java
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

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneXmlRpcRequest;
import org.linphone.core.LinphoneXmlRpcRequest.LinphoneXmlRpcRequestListener;
import org.linphone.core.LinphoneXmlRpcRequestImpl;
import org.linphone.core.LinphoneXmlRpcSession;
import org.linphone.core.LinphoneXmlRpcSessionImpl;
import org.linphone.mediastream.Log;

import android.app.Fragment;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class EchoCancellerCalibrationFragment extends Fragment implements LinphoneXmlRpcRequestListener {
	private Handler mHandler = new Handler();
	private boolean mSendEcCalibrationResult = false;
	private LinphoneCoreListenerBase mListener;
	private LinphoneXmlRpcSession xmlRpcSession;
	private LinphoneXmlRpcRequest xmlRpcRequest;
	private Runnable runFinished;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_ec_calibration, container, false);

		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void ecCalibrationStatus(LinphoneCore lc, LinphoneCore.EcCalibratorStatus status, int delay_ms, Object data) {
				LinphoneManager.getInstance().routeAudioToReceiver();
				if (mSendEcCalibrationResult) {
					sendEcCalibrationResult(status, delay_ms);
				} else {
					AssistantActivity.instance().isEchoCalibrationFinished();
				}
			}
		};
		runFinished = new Runnable() {
			public void run() {
				AssistantActivity.instance().isEchoCalibrationFinished();
			}
		};

		xmlRpcSession = new LinphoneXmlRpcSessionImpl(LinphoneManager.getLcIfManagerNotDestroyedOrNull(), LinphonePreferences.instance().getXmlrpcUrl());
		xmlRpcRequest = new LinphoneXmlRpcRequestImpl("add_ec_calibration_result", LinphoneXmlRpcRequest.ArgType.None);
		xmlRpcRequest.setListener(this);

		try {
			LinphoneManager.getInstance().startEcCalibration(mListener);
		} catch (LinphoneCoreException e) {
			Log.e(e, "Unable to calibrate EC");
			AssistantActivity.instance().isEchoCalibrationFinished();
		}
		return view;
	}

	public void enableEcCalibrationResultSending(boolean enabled) {
		mSendEcCalibrationResult = enabled;
	}

	@Override
	public void onXmlRpcRequestResponse(LinphoneXmlRpcRequest request) {
		mHandler.post(runFinished);
	}

	private void sendEcCalibrationResult(EcCalibratorStatus status, int delayMs) {
		Boolean hasBuiltInEchoCanceler = LinphoneManager.getLc().hasBuiltInEchoCanceler();
		Log.i("Add echo canceller calibration result: manufacturer=" + Build.MANUFACTURER + " model=" + Build.MODEL + " status=" + status + " delay=" + delayMs + "ms" + " hasBuiltInEchoCanceler " + hasBuiltInEchoCanceler);
		xmlRpcRequest.addStringArg(Build.MANUFACTURER);
		xmlRpcRequest.addStringArg(Build.MODEL);
		xmlRpcRequest.addStringArg(status.toString());
		xmlRpcRequest.addIntArg(delayMs);
		xmlRpcRequest.addIntArg(hasBuiltInEchoCanceler ? 1 : 0);
		xmlRpcSession.sendRequest(xmlRpcRequest);
	}
}
