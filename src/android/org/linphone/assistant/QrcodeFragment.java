package org.linphone.assistant;

/*
QrcodeFragment.java
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
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;

public class QrcodeFragment extends Fragment {
	private SurfaceView mQrcodeView;
	private AndroidVideoWindowImpl androidVideoWindowImpl;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.qrcode, container, false);

		mQrcodeView = (SurfaceView) view.findViewById(R.id.qrcodeCaptureSurface);

		return view;
	}

	private void enableQrcodeReader(boolean enable) {
		//LinphoneManager.getLc().enableQrcodeVideoPreview(enable);
		LinphoneManager.getLc().enableVideoPreview(enable);
	}

	private void setBackCamera(boolean useBackCamera) {
		int camId = 0;
		AndroidCameraConfiguration.AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
		for (AndroidCameraConfiguration.AndroidCamera androidCamera : cameras) {
			if (androidCamera.frontFacing == !useBackCamera)
				camId = androidCamera.id;
		}
		String[] devices = LinphoneManager.getLc().getVideoDevicesList();
		String newDevice = devices[camId];
		LinphoneManager.getLc().setVideoDevice(newDevice);
	}

	private void launchQrcodeReader() {
		setBackCamera(true);

		androidVideoWindowImpl = new AndroidVideoWindowImpl(null, mQrcodeView, new AndroidVideoWindowImpl.VideoWindowListener() {
			public void onVideoRenderingSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {

			}

			public void onVideoRenderingSurfaceDestroyed(AndroidVideoWindowImpl vw) {

			}

			public void onVideoPreviewSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
				mQrcodeView = surface;
				LinphoneManager.getLc().setNativePreviewWindowId(vw);
			}

			public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl vw) {

			}
		});

		enableQrcodeReader(true);
		LinphoneManager.getLc().setQrcodeDecodeRect(500,220,280,280);
	}

	@Override
	public void onStart() {
		AssistantActivity.instance().checkAndRequestVideoPermission();
		super.onStart();
	}

	@Override
	public void onResume() {
		launchQrcodeReader();
		super.onResume();
	}

	@Override
	public void onPause() {
		LinphoneManager.getLc().setNativePreviewWindowId(null);
		androidVideoWindowImpl.release();
		enableQrcodeReader(false);
		setBackCamera(false);
		super.onPause();
	}
}
