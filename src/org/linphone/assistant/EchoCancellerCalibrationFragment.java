package org.linphone.assistant;

/*
EchoCancellerCalibrationFragment.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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

import java.net.URL;

import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.mediastream.Log;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import de.timroes.axmlrpc.XMLRPCCallback;
import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLRPCServerException;

/**
 * @author Ghislain MARY
 */
public class EchoCancellerCalibrationFragment extends Fragment {
	private Handler mHandler = new Handler();
	private boolean mSendEcCalibrationResult = false;
	private LinphoneCoreListenerBase mListener;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_ec_calibration, container, false);
		
		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void ecCalibrationStatus(LinphoneCore lc,LinphoneCore.EcCalibratorStatus status, int delay_ms, Object data) {
				LinphoneManager.getInstance().routeAudioToReceiver();
				if (mSendEcCalibrationResult) {
					sendEcCalibrationResult(status, delay_ms);
				} else {
					AssistantActivity.instance().isEchoCalibrationFinished();
				}
			}
		};

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

	private void sendEcCalibrationResult(EcCalibratorStatus status, int delayMs) {
		try {
			XMLRPCClient client = new XMLRPCClient(new URL(getString(R.string.wizard_url)));

			XMLRPCCallback listener = new XMLRPCCallback() {
				Runnable runFinished = new Runnable() {
    				public void run() {
    					AssistantActivity.instance().isEchoCalibrationFinished();
					}
	    		};

			    public void onResponse(long id, Object result) {
		    		mHandler.post(runFinished);
			    }

			    public void onError(long id, XMLRPCException error) {
			    	mHandler.post(runFinished);
			    }

			    public void onServerError(long id, XMLRPCServerException error) {
			    	mHandler.post(runFinished);
			    }
			};

			Boolean hasBuiltInEchoCanceler = LinphoneManager.getLc().hasBuiltInEchoCanceler();
			Log.i("Add echo canceller calibration result: manufacturer=" + Build.MANUFACTURER + " model=" + Build.MODEL + " status=" + status + " delay=" + delayMs + "ms" + " hasBuiltInEchoCanceler " + hasBuiltInEchoCanceler);
		    client.callAsync(listener, "add_ec_calibration_result", Build.MANUFACTURER, Build.MODEL, status.toString(), delayMs, hasBuiltInEchoCanceler);
		}
		catch(Exception ex) {}
	}
}
