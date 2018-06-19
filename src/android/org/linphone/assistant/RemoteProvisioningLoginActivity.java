package org.linphone.assistant;
/*
RemoteProvisioningLoginActivity.java
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

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class RemoteProvisioningLoginActivity extends Activity implements OnClickListener {
	private EditText login, password, domain;
	private Button connect;
	private ProgressDialog progress;
	private CoreListenerStub mListener;
	private SurfaceView mQrcodeView;
	private AndroidVideoWindowImpl androidVideoWindowImpl;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.assistant_remote_provisioning_login);
		mQrcodeView = (SurfaceView) findViewById(R.id.qrcodeCaptureSurface);

		mListener = new CoreListenerStub() {
			@Override
			public void onQrcodeFound(Core lc, String result) {
				enableQrcodeReader(false);
				//AssistantActivity.instance().displayRemoteProvisioning(result);
			}

		};

		/*login = (EditText) findViewById(R.id.assistant_username);
		password = (EditText) findViewById(R.id.assistant_password);
		domain = (EditText) findViewById(R.id.assistant_domain);
		domain.setText(getString(R.string.default_domain));

		connect = (Button) findViewById(R.id.assistant_connect);
		connect.setOnClickListener(this);

		String defaultDomain = getIntent().getStringExtra("Domain");
		if (defaultDomain != null) {
			domain.setText(defaultDomain);
			domain.setEnabled(false);
		}

		mListener = new CoreListenerStub(){
			@Override
			public void onConfiguringStatus(Core lc, final ConfiguringState state, String message) {
				if (state == ConfiguringState.Successful) {
					//TODO
				} else if (state == ConfiguringState.Failed) {
					Toast.makeText(RemoteProvisioningLoginActivity.this, R.string.remote_provisioning_failure, Toast.LENGTH_LONG).show();
				}
			}

			@Override
			public void onRegistrationStateChanged(Core lc, ProxyConfig proxy, RegistrationState state, String smessage) {
				if (state.equals(RegistrationState.Ok)) {
					LinphonePreferences.instance().firstLaunchSuccessful();
					startActivity(new Intent().setClass(RemoteProvisioningLoginActivity.this, LinphoneActivity.class).setData(getIntent().getData()));
					finish();
				}
				if (progress != null) progress.dismiss();
			}
		};*/
	}

	private void cancelWizard(boolean bypassCheck) {
		if (bypassCheck || getResources().getBoolean(R.bool.allow_cancel_remote_provisioning_login_activity)) {
			LinphonePreferences.instance().disableProvisioningLoginView();
			setResult(bypassCheck ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
			finish();
		}
	}

	private boolean storeAccount(String username, String password, String domain) {
		/*XmlRpcHelper xmlRpcHelper = new XmlRpcHelper();
		xmlRpcHelper.getRemoteProvisioningFilenameAsync(new XmlRpcListenerBase() {
			@Override
			public void onRemoteProvisioningFilenameSent(String result) {
				LinphonePreferences.instance().setRemoteProvisioningUrl(result);
				LinphoneManager.getInstance().restartCore();
			}
		}, username.toString(), password.toString(), domain.toString());*/

		LinphonePreferences.instance().setRemoteProvisioningUrl("https://85.233.205.218/xmlrpc?username=" + username + "&password=" + password + "&domain=" + domain);
		///////// TODO
		LinphoneManager.getLc().iterate();
		LinphoneManager.getLc().iterate();
		LinphoneManager.getLc().iterate();
		LinphoneManager.getLc().iterate();
		LinphoneManager.getLc().iterate();
		LinphoneManager.getLc().iterate();
		///////// TODO
		LinphoneManager.getInstance().restartCore();
		LinphoneManager.getLc().addListener(mListener);
		//LinphonePreferences.instance().firstLaunchSuccessful();
		//setResult(Activity.RESULT_OK);
		//finish();
		/*String identity = "sip:" + username + "@" + domain;
		ProxyConfig prxCfg = lc.createProxyConfig();
		try {
			prxCfg.setIdentityAddress(identity);
			lc.addProxyConfig(prxCfg);
		} catch (CoreException e) {
			Log.e(e);
			return false;
		}

		AuthInfo authInfo = Factory.instance().createAuthInfo(username, null, password, null, null, domain);
		lc.addAuthInfo(authInfo);

		if (LinphonePreferences.instance().getAccountCount() == 1)
			lc.setDefaultProxyConfig(prxCfg);
		*/
		return true;
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
				LinphoneManager.getLc().setNativePreviewWindowId(androidVideoWindowImpl);
			}

			public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl vw) {

			}
		});

		enableQrcodeReader(true);
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onResume() {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}
		launchQrcodeReader();
		if (androidVideoWindowImpl != null) {
			synchronized (androidVideoWindowImpl) {
				LinphoneManager.getLc().setNativePreviewWindowId(androidVideoWindowImpl);
			}
		}
		super.onResume();
	}

	@Override
	public void onPause() {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}
		if (androidVideoWindowImpl != null) {
			synchronized (androidVideoWindowImpl) {
				LinphoneManager.getLc().setNativePreviewWindowId(null);
			}
		}
		enableQrcodeReader(false);
		setBackCamera(false);
		super.onPause();
	}

	@Override
	public void onDestroy() {
		if (androidVideoWindowImpl != null) {
			androidVideoWindowImpl.release();
			androidVideoWindowImpl = null;
		}
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.cancel) {
			cancelWizard(false);
		}
		/*if (id == R.id.assistant_connect){
			displayRemoteProvisioningInProgressDialog();
			connect.setEnabled(false);
			storeAccount(login.getText().toString(), password.getText().toString(), domain.getText().toString());
		}*/
	}

	@Override
	public void onBackPressed() {
		cancelWizard(false);
	}

	private void displayRemoteProvisioningInProgressDialog() {
		progress = ProgressDialog.show(this, null, null);
		Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorE));
		d.setAlpha(200);
		progress.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		progress.getWindow().setBackgroundDrawable(d);
		progress.setContentView(R.layout.progress_dialog);
		progress.show();
	}
}
