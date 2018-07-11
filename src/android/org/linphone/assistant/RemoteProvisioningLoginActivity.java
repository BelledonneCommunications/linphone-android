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
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import static android.os.SystemClock.sleep;

public class RemoteProvisioningLoginActivity extends Activity implements OnClickListener {
	private static RemoteProvisioningLoginActivity instance;
	private EditText code_sms;
	private TextView step, instruction;
	private Button ok, back;
	private ProgressDialog progress;
	private String qrcodeString;
	private String remoteUrl;
	private RelativeLayout bottom;
	private CoreListenerStub mListener;
	private SurfaceView mQrcodeView;
	private AndroidVideoWindowImpl androidVideoWindowImpl;
	private boolean cameraAuthorize = false;
	private boolean readQRCode = true;
	private boolean backCamera = true;
	private int PERMISSION_CAMERA = 108;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		instance = this;
		setContentView(R.layout.assistant_remote_provisioning_login);

		if (getPackageManager().checkPermission(Manifest.permission.CAMERA,
				getPackageName()) != PackageManager.PERMISSION_GRANTED) {
			checkAndRequestVideoPermission();
		} else {
			cameraAuthorize = true;
		}

		mQrcodeView = (SurfaceView) findViewById(R.id.qrcodeCaptureSurface);
		bottom = (RelativeLayout) findViewById(R.id.bottom_text);
		code_sms = (EditText) findViewById(R.id.code_sms);
		step = (TextView) findViewById(R.id.step);
		instruction = (TextView) findViewById(R.id.instruction);

		ok = (Button) findViewById(R.id.valider);
		back = (Button) findViewById(R.id.retour);

		ok.setOnClickListener(this);
		back.setOnClickListener(this);

		mQrcodeView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (readQRCode && getPackageManager().checkPermission(Manifest.permission.CAMERA,
						getPackageName()) != PackageManager.PERMISSION_GRANTED) {
					checkAndRequestVideoPermission();
				} else {
					instance.setBackCamera(!backCamera);
				}
			}
		});

		mListener = new CoreListenerStub() {
			@Override
			public void onQrcodeFound(Core lc, String result) {
				//TODO check validitée du qrcode
				instance.qrcodeString = result;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						instance.enableQrcodeReader(false);
						instance.displayCodeSms();
					}
				});
			}

			@Override
			public void onConfiguringStatus(Core lc, final ConfiguringState state, String message) {
				if (state == ConfiguringState.Successful) {
					//TODO
				} else if (state == ConfiguringState.Failed) {
					Toast.makeText(RemoteProvisioningLoginActivity.this, R.string.remote_provisioning_failure, Toast.LENGTH_LONG).show();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							instance.ok.setEnabled(true);
						}
					});
					if (progress != null) progress.dismiss();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							instance.displayQrCode();
							instance.launchQrcodeReader();
						}
					});
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

		};


		displayQrCode();
	}

	void checkAndRequestVideoPermission() {
		int permissionGranted = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
		Log.i("[Permission] " + Manifest.permission.CAMERA + " is " + (permissionGranted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
		}
	}

	private void cancelWizard(boolean bypassCheck) {
		if (bypassCheck || getResources().getBoolean(R.bool.allow_cancel_remote_provisioning_login_activity)) {
			LinphonePreferences.instance().disableProvisioningLoginView();
			setResult(bypassCheck ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
			finish();
		}
	}

	private void displayQrCode() {
		mQrcodeView.setVisibility(View.VISIBLE);
		code_sms.setVisibility(View.GONE);
		ok.setVisibility(View.GONE);
		back.setVisibility(View.GONE);

		step.setText(getString(R.string.assistant_step1));
		instruction.setText(getString(R.string.assistant_instruction1));

		readQRCode = true;
	}

	private void displayCodeSms() {
		mQrcodeView.setVisibility(View.GONE);
		code_sms.setVisibility(View.VISIBLE);
		ok.setVisibility(View.VISIBLE);
		back.setVisibility(View.VISIBLE);

		step.setText(getString(R.string.assistant_step2));
		instruction.setText(getString(R.string.assistant_instruction2));

		readQRCode = false;
	}

	private void enableQrcodeReader(boolean enable) {
		if (cameraAuthorize && readQRCode) {
			LinphoneManager.getLc().enableQrcodeVideoPreview(enable);
			LinphoneManager.getLc().enableVideoPreview(enable);
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
		backCamera = useBackCamera;
	}

	private void launchQrcodeReader() {
		setBackCamera(true);

		androidVideoWindowImpl = new AndroidVideoWindowImpl(null, mQrcodeView, new AndroidVideoWindowImpl.VideoWindowListener() {
			public void onVideoRenderingSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {}

			public void onVideoRenderingSurfaceDestroyed(AndroidVideoWindowImpl vw) {}

			public void onVideoPreviewSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
				LinphoneManager.getLc().setNativePreviewWindowId(androidVideoWindowImpl);
			}

			public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl vw) {}
		});

		enableQrcodeReader(true);
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onResume() {
		super.onResume();
		launchQrcodeReader();
		if (androidVideoWindowImpl != null) {
			synchronized (androidVideoWindowImpl) {
				LinphoneManager.getLc().setNativePreviewWindowId(androidVideoWindowImpl);
			}
		}
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}
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
		if (id == R.id.retour) {
			displayQrCode();
			enableQrcodeReader(true);
		}
		if (id == R.id.valider) {
			displayRemoteProvisioningInProgressDialog();
			ok.setEnabled(false);
			if (qrcodeString != null
					&& (qrcodeString.startsWith("http://")
					|| qrcodeString.startsWith("https://"))) {
				storeAccount(qrcodeString);
			} else {
				if (decryptQrcode()) {
					storeAccount(remoteUrl);
				} else {
					ok.setEnabled(true);
					if (progress != null) progress.cancel();
				}
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		for (int i = 0; i < permissions.length; i++) {
			Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		}

		if (requestCode == PERMISSION_CAMERA) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				cameraAuthorize = true;
			}
		}
	}

	private byte[] removeUselessByte(byte[] tab, int wantedSize) {
		if (wantedSize == tab.length) return tab;
		byte[] newTab = new byte[wantedSize];
		for (int i = 1 ; i < tab.length ; i++) {
			newTab[i-1] = tab[i];
		}
		return newTab;
	}

	private boolean decryptQrcode() {
		try {
			/*byte[] unBased64Data = qrcodeString.getBytes();
			ByteArrayInputStream inputStream = new ByteArrayInputStream(unBased64Data);

			byte[] contentToDecrypt = new byte[unBased64Data.length];

			inputStream.read(contentToDecrypt);

			BigInteger saltHex = new BigInteger(, 16);
			BigInteger ivHex = new BigInteger("F58B8C9A49B321DBA000000000000000", 16);

			byte[] saltByte = removeUselessByte(saltHex.toByteArray(), 8);
			byte[] ivByte = removeUselessByte(ivHex.toByteArray(), 16);

			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBEwithMD5AND128BITAES-CBC-OPENSSL");
			KeySpec keySpec = new PBEKeySpec(code_sms.getText().toString().toCharArray(), saltByte, 10000, 128);
			SecretKey tmpSecretKey = factory.generateSecret(keySpec);
			SecretKeySpec secretKeySpec = new SecretKeySpec(tmpSecretKey.getEncoded(), "AES");

			Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(ivByte));

			remoteUrl = new String(cipher.doFinal(Base64.getDecoder().decode(contentToDecrypt)));*/
			final byte[] pass = code_sms.getText().toString().getBytes(StandardCharsets.US_ASCII);

			final Base64.Decoder decoder = Base64.getDecoder();
			final byte[] inBytes = decoder.decode(qrcodeString.getBytes());

			final byte[] salt = "F000000000000000".getBytes(StandardCharsets.US_ASCII);

			final byte[] passAndSalt = concat(pass, salt);

			byte[] hash = new byte[0];
			byte[] keyAndIv = new byte[0];
			for (int i = 0; i < 3; i++) {
				final byte[] data = concat(hash, passAndSalt);
				final MessageDigest md = MessageDigest.getInstance("MD5");
				hash = md.digest(data);
				keyAndIv = concat(keyAndIv, hash);
			}

			final byte[] keyValue = Arrays.copyOfRange(keyAndIv, 0, 32);
			final byte[] iv = Arrays.copyOfRange(keyAndIv, 32, 48);
			final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			final SecretKeySpec key = new SecretKeySpec(keyValue, "AES");
			cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
			final byte[] clear = cipher.doFinal(inBytes, 16, inBytes.length - 16);
			remoteUrl = new String(clear, StandardCharsets.ISO_8859_1);
		} catch (Exception ex) {
			Toast.makeText(RemoteProvisioningLoginActivity.this, "Code mauvais", Toast.LENGTH_LONG).show();
			Log.e("RemoteProvisioningLoginActivity: Decrypt problem: " + ex);
			remoteUrl = null;
			return false;
		}
		return true;
	}

	private static byte[] concat(final byte[] a, final byte[] b) {
		final byte[] c = new byte[a.length + b.length];
		System.arraycopy(a, 0, c, 0, a.length);
		System.arraycopy(b, 0, c, a.length, b.length);
		return c;
	}

	private boolean storeAccount(String url) {
		LinphonePreferences.instance().setRemoteProvisioningUrl(url);
		//TODO
		LinphoneManager.getLc().iterate();
		sleep(1000);
		LinphoneManager.getLc().iterate();
		//TODO

		LinphoneManager.getInstance().restartCore();
		LinphoneManager.getLc().addListener(mListener);

		return true;
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
