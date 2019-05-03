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

import android.app.Fragment;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.XmlRpcArgType;
import org.linphone.core.XmlRpcRequest;
import org.linphone.core.XmlRpcRequestListener;
import org.linphone.core.XmlRpcSession;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public class EchoCancellerCalibrationFragment extends Fragment implements XmlRpcRequestListener {
    private final Handler mHandler = new Handler();
    private boolean mSendEcCalibrationResult = false;
    private CoreListenerStub mListener;
    private XmlRpcSession mXmlRpcSession;
    private XmlRpcRequest mXmlRpcRequest;
    private Runnable mRunFinished;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.assistant_ec_calibration, container, false);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onEcCalibrationResult(
                            Core lc, EcCalibratorStatus status, int delay_ms) {
                        lc.removeListener(mListener);
                        LinphoneManager.getInstance().routeAudioToReceiver();
                        if (mSendEcCalibrationResult) {
                            sendEcCalibrationResult(status, delay_ms);
                        } else {
                            AssistantActivity.instance().isEchoCalibrationFinished();
                        }
                    }
                };
        mRunFinished =
                new Runnable() {
                    public void run() {
                        AssistantActivity.instance().isEchoCalibrationFinished();
                    }
                };

        mXmlRpcSession =
                LinphoneManager.getLcIfManagerNotDestroyedOrNull()
                        .createXmlRpcSession(LinphonePreferences.instance().getXmlrpcUrl());
        mXmlRpcRequest =
                mXmlRpcSession.createRequest(XmlRpcArgType.None, "add_ec_calibration_result");
        mXmlRpcRequest.setListener(this);

        LinphoneManager.getLc().addListener(mListener);
        LinphoneManager.getInstance().startEcCalibration();
        return view;
    }

    public void enableEcCalibrationResultSending(boolean enabled) {
        mSendEcCalibrationResult = enabled;
    }

    @Override
    public void onResponse(XmlRpcRequest request) {
        mHandler.post(mRunFinished);
    }

    private void sendEcCalibrationResult(EcCalibratorStatus status, int delayMs) {
        Boolean hasBuiltInEchoCanceler = LinphoneManager.getLc().hasBuiltinEchoCanceller();
        Log.i(
                "Add echo canceller calibration result: manufacturer="
                        + Build.MANUFACTURER
                        + " model="
                        + Build.MODEL
                        + " status="
                        + status
                        + " delay="
                        + delayMs
                        + "ms"
                        + " hasBuiltInEchoCanceler "
                        + hasBuiltInEchoCanceler);
        mXmlRpcRequest.addStringArg(Build.MANUFACTURER);
        mXmlRpcRequest.addStringArg(Build.MODEL);
        mXmlRpcRequest.addStringArg(status.toString());
        mXmlRpcRequest.addIntArg(delayMs);
        mXmlRpcRequest.addIntArg(hasBuiltInEchoCanceler ? 1 : 0);
        mXmlRpcSession.sendRequest(mXmlRpcRequest);
    }
}
