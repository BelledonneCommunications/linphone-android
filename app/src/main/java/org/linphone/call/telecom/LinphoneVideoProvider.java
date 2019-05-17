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

@TargetApi(Build.VERSION_CODES.M)
public class LinphoneVideoProvider extends Connection.VideoProvider {
    public LinphoneVideoProvider() {}

    @Override
    public void onSetCamera(String cameraId) {}

    @Override
    public void onSetPreviewSurface(Surface surface) {}

    @Override
    public void onSetDisplaySurface(Surface surface) {}

    @Override
    public void onSetDeviceOrientation(int rotation) {}

    @Override
    public void onSetZoom(float value) {}

    @Override
    public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {}

    @Override
    public void onSendSessionModifyResponse(VideoProfile responseProfile) {}

    @Override
    public void onRequestCameraCapabilities() {}

    @Override
    public void onRequestConnectionDataUsage() {}

    @Override
    public void onSetPauseImage(Uri uri) {}
}
