package org.linphone.call.telecom;

/*
LinphoneVideoProvider.java
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

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.telecom.Connection;
import android.telecom.VideoProfile;
import android.view.Surface;
import org.linphone.LinphoneManager;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;

@TargetApi(Build.VERSION_CODES.M)
public class LinphoneVideoProvider extends Connection.VideoProvider {
    public LinphoneVideoProvider() {}

    @Override
    public void onSetCamera(String cameraId) {
        Core core = LinphoneManager.getCore();
        if (core == null) return;
        if (cameraId == null) return;

        String currentDevice = core.getVideoDevice();
        if (currentDevice.equals(cameraId)) {
            Log.w(
                    "[Telecom Manager] Camera id "
                            + cameraId
                            + " is already the one being used, skipping");
        } else {
            Log.i("[Telecom Manager] Changing camera from " + currentDevice + " to " + cameraId);
            String[] devices = core.getVideoDevicesList();
            for (String device : devices) {
                if (device.equals(cameraId)) {
                    Log.i("[Telecom Manager] Found requested camera");
                    core.setVideoDevice(device);

                    Call call = core.getCurrentCall();
                    if (call == null) {
                        Log.w("[Telecom Manager] Trying to switch camera while not in call");
                        return;
                    }
                    // TODO: we must call the following !
                    // changeCameraCapabilities(new VideoProfile.CameraCapabilities(width, height,
                    // zoomSupported, maxZoom));

                    call.update(null);
                    return;
                }
            }
        }
    }

    @Override
    public void onSetPreviewSurface(Surface surface) {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            Log.i("[Telecom Manager] Changing rendering surface to " + surface);
            core.setNativePreviewWindowId(surface);
        }
    }

    @Override
    public void onSetDisplaySurface(Surface surface) {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            Log.i("[Telecom Manager] Changing display surface to " + surface);
            core.setNativeVideoWindowId(surface);
        }
    }

    @Override
    public void onSetDeviceOrientation(int rotation) {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            Log.i("[Telecom Manager] Changing device rotation to " + rotation);
            core.setDeviceRotation(rotation);
        }
    }

    @Override
    public void onSetZoom(float value) {
        Core core = LinphoneManager.getCore();
        if (core == null) return;

        Call call = core.getCurrentCall();
        if (call == null) return;

        Log.i("[Telecom Manager] Zooming to " + value);
        call.zoom(value, (float) 0.5, (float) 0.5);
    }

    @Override
    public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {
        Log.i("[Telecom Manager] TODO: onSendSessionModifyRequest");
    }

    @Override
    public void onSendSessionModifyResponse(VideoProfile responseProfile) {
        Log.i("[Telecom Manager] TODO: onSendSessionModifyResponse");
    }

    @Override
    public void onRequestCameraCapabilities() {
        Log.i("[Telecom Manager] TODO: onRequestCameraCapabilities");
    }

    @Override
    public void onRequestConnectionDataUsage() {
        Log.i("[Telecom Manager] TODO: onRequestConnectionDataUsage");
    }

    @Override
    public void onSetPauseImage(Uri uri) {
        Log.i("[Telecom Manager] TODO: onSetPauseImage");
    }
}
