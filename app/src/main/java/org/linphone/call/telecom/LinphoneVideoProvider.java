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
import org.linphone.core.VideoDefinition;
import org.linphone.core.tools.Log;

@TargetApi(Build.VERSION_CODES.M)
public class LinphoneVideoProvider extends Connection.VideoProvider {
    private String mCallId;

    public LinphoneVideoProvider(String callId) {
        mCallId = callId;
    }

    private Call getCall() {
        Call call = LinphoneManager.getCallManager().findCallFromId(mCallId);
        if (call == null) {
            Log.w("[Telecom Manager] Call not found from id " + mCallId);
        }
        call = LinphoneManager.getCore().getCurrentCall();
        return call;
    }

    @Override
    public void onSetCamera(String cameraId) {
        Core core = LinphoneManager.getCore();
        if (core == null) return;
        if (cameraId == null) return;

        // In linphone, cameras are Android0, Android1 etc...
        // Camera id given as parameter is only 0, 1, etc...
        cameraId = "Android" + cameraId;

        String currentDevice = core.getVideoDevice();
        boolean updateRequired = !currentDevice.equals(cameraId);

        Call call = getCall();
        if (call == null) {
            Log.w("[Telecom Manager] Trying to switch camera while not in call");
            return;
        }

        VideoDefinition size = call.getCurrentParams().getSentVideoDefinition();
        Log.i(
                "[Telecom Manager] Call video definition is "
                        + size.getWidth()
                        + "x"
                        + size.getHeight());
        changeCameraCapabilities(
                new VideoProfile.CameraCapabilities(size.getWidth(), size.getHeight()));

        if (updateRequired) {
            Log.i("[Telecom Manager] Camera switched from " + currentDevice + " to " + cameraId);
            core.setVideoDevice(cameraId);
            call.update(null);
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

        Call call = getCall();
        if (call == null) return;

        Log.i("[Telecom Manager] Zooming to " + value);
        call.zoom(value, (float) 0.5, (float) 0.5);
    }

    @Override
    public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {
        Core core = LinphoneManager.getCore();
        if (core == null) return;

        Call call = getCall();
        if (call == null) {
            return;
        }
        Log.i("[Telecom Manager] Receiving request");
    }

    @Override
    public void onSendSessionModifyResponse(VideoProfile responseProfile) {
        Log.i("[Telecom Manager] Sending response");
    }

    @Override
    public void onRequestCameraCapabilities() {
        Core core = LinphoneManager.getCore();
        if (core == null) return;

        Call call = getCall();
        if (call == null) {
            return;
        }

        VideoDefinition size = call.getCurrentParams().getSentVideoDefinition();
        Log.i(
                "[Telecom Manager] Call video definition is "
                        + size.getWidth()
                        + "x"
                        + size.getHeight());
        changeCameraCapabilities(
                new VideoProfile.CameraCapabilities(size.getWidth(), size.getHeight()));
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
