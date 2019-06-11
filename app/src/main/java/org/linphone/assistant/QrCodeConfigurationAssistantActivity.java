package org.linphone.assistant;

/*
QrCodeConfigurationAssistantActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;

public class QrCodeConfigurationAssistantActivity extends AssistantActivity {
    private TextureView mQrcodeView;

    private CoreListenerStub mListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mAbortCreation) {
            return;
        }

        setContentView(R.layout.assistant_qr_code_remote_configuration);

        mQrcodeView = findViewById(R.id.qr_code_capture_texture);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onQrcodeFound(Core core, String result) {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("URL", result);
                        setResult(Activity.RESULT_OK, resultIntent);
                        finish();
                    }
                };

        ImageView changeCamera = findViewById(R.id.qr_code_capture_change_camera);
        changeCamera.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphoneManager.getCallManager().switchCamera();
                    }
                });
        Core core = LinphoneManager.getCore();
        if (core != null && core.getVideoDevicesList().length > 1) {
            changeCamera.setVisibility(View.VISIBLE);
        }

        setBackCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();

        enableQrcodeReader(true);
    }

    @Override
    public void onPause() {
        enableQrcodeReader(false);

        super.onPause();
    }

    private void enableQrcodeReader(boolean enable) {
        Core core = LinphoneManager.getCore();
        if (core == null) return;

        core.setNativePreviewWindowId(enable ? mQrcodeView : null);
        core.enableQrcodeVideoPreview(enable);
        core.enableVideoPreview(enable);

        if (enable) {
            core.addListener(mListener);
        } else {
            core.removeListener(mListener);
        }
    }

    private void setBackCamera() {
        Core core = LinphoneManager.getCore();
        if (core == null) return;

        int camId = 0;
        AndroidCameraConfiguration.AndroidCamera[] cameras =
                AndroidCameraConfiguration.retrieveCameras();
        for (AndroidCameraConfiguration.AndroidCamera androidCamera : cameras) {
            if (!androidCamera.frontFacing) camId = androidCamera.id;
        }
        String[] devices = core.getVideoDevicesList();
        String newDevice = devices[camId];

        String currentDevice = core.getVideoDevice();
        if (currentDevice != null && currentDevice.equals(newDevice)) {
            return;
        }
        core.setVideoDevice(newDevice);
    }
}
