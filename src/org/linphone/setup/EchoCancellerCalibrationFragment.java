package org.linphone.setup;

/*
EchoCancellerCalibrationFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
import org.linphone.LinphoneManager.EcCalibrationListener;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCoreException;
import org.linphone.mediastream.Log;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
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
public class EchoCancellerCalibrationFragment extends Fragment implements EcCalibrationListener {
	private Handler mHandler = new Handler();
	private boolean mSendEcCalibrationResult = false;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.setup_ec_calibration, container, false);

		try {
			LinphoneManager.getInstance().startEcCalibration(this);
		} catch (LinphoneCoreException e) {
			Log.e(e, "Unable to calibrate EC");
		}

		return view;
	}

	@Override
	public void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs) {
		if (status == EcCalibratorStatus.DoneNoEcho) {
			LinphonePreferences.instance().setEchoCancellation(false);
		} else if ((status == EcCalibratorStatus.Done) || (status == EcCalibratorStatus.Failed)) {
			LinphonePreferences.instance().setEchoCancellation(true);
		}
		if (mSendEcCalibrationResult) {
			sendEcCalibrationResult(status, delayMs);
		} else {
			SetupActivity.instance().isEchoCalibrationFinished();
		}
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
    					SetupActivity.instance().isEchoCalibrationFinished();
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

			Log.i("Add echo canceller calibration result: manufacturer=" + Build.MANUFACTURER + " model=" + Build.MODEL + " status=" + status + " delay=" + delayMs + "ms");
		    client.callAsync(listener, "add_ec_calibration_result", Build.MANUFACTURER, Build.MODEL, status.toString(), delayMs);
		} 
		catch(Exception ex) {}
	}
}
