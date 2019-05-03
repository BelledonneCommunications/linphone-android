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

        setContentView(R.layout.assistant_qr_code_remote_configuration);

        mQrcodeView = findViewById(R.id.qr_code_capture_texture);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onQrcodeFound(Core lc, String result) {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("URL", result);
                        setResult(Activity.RESULT_OK, resultIntent);
                        finish();
                    }
                };
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
        LinphoneManager.getLc().setNativePreviewWindowId(mQrcodeView);
        setBackCamera();

        enableQrcodeReader(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        launchQrcodeReader();
    }

    @Override
    public void onPause() {
        enableQrcodeReader(false);
        super.onPause();
    }
}
