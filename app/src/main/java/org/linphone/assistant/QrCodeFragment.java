package org.linphone.assistant;

/*
QrCodeFragment.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;

public class QrCodeFragment extends Fragment {
    private TextureView mQrcodeView;
    private CoreListenerStub mListener;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.qrcode, container, false);

        mQrcodeView = view.findViewById(R.id.qrcodeCaptureSurface);

        LinphoneManager.getLc().setNativePreviewWindowId(mQrcodeView);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onQrcodeFound(Core lc, String result) {
                        enableQrcodeReader(false);
                        AssistantActivity.instance().displayRemoteProvisioning(result);
                    }
                };

        return view;
    }

    private void enableQrcodeReader(boolean enable) {
        LinphoneManager.getLc().enableQrcodeVideoPreview(enable);
        LinphoneManager.getLc().enableVideoPreview(enable);
        if (enable) {
            LinphoneManager.getLc().addListener(mListener);
        } else {
            LinphoneManager.getLc().removeListener(mListener);
        }
    }

    private void setBackCamera() {
        int camId = 0;
        AndroidCameraConfiguration.AndroidCamera[] cameras =
                AndroidCameraConfiguration.retrieveCameras();
        for (AndroidCameraConfiguration.AndroidCamera androidCamera : cameras) {
            if (!androidCamera.frontFacing) camId = androidCamera.id;
        }
        String[] devices = LinphoneManager.getLc().getVideoDevicesList();
        String newDevice = devices[camId];
        LinphoneManager.getLc().setVideoDevice(newDevice);
    }

    private void launchQrcodeReader() {
        setBackCamera();

        enableQrcodeReader(true);
    }

    @Override
    public void onResume() {
        launchQrcodeReader();
        super.onResume();
    }

    @Override
    public void onPause() {
        enableQrcodeReader(false);
        // setBackCamera(false);
        super.onPause();
    }
}
