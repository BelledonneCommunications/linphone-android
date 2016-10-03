/*
CallIncomingActivity.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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
package org.linphone;

import java.util.ArrayList;
import java.util.List;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.mediastream.Log;
import org.linphone.ui.LinphoneSliders.LinphoneSliderTriggered;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CallIncomingActivity extends Activity implements LinphoneSliderTriggered {
	private static CallIncomingActivity instance;

	private TextView name, number;
	private ImageView contactPicture, accept, decline;
	private LinphoneCall mCall;
	private LinphoneCoreListenerBase mListener;
	private LinearLayout acceptUnlock;
	private LinearLayout declineUnlock;
	private boolean isScreenActive, alreadyAcceptedOrDeniedCall;
	private float answerX;
	private float declineX;

	public static CallIncomingActivity instance() {
		return instance;
	}

	public static boolean isInstanciated() {
		return instance != null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.call_incoming);

		name = (TextView) findViewById(R.id.contact_name);
		number = (TextView) findViewById(R.id.contact_number);
		contactPicture = (ImageView) findViewById(R.id.contact_picture);

		// set this flag so this activity will stay in front of the keyguard
		int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
		getWindow().addFlags(flags);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		isScreenActive = Compatibility.isScreenOn(pm);

		final int screenWidth = getResources().getDisplayMetrics().widthPixels;

		acceptUnlock = (LinearLayout) findViewById(R.id.acceptUnlock);
		declineUnlock = (LinearLayout) findViewById(R.id.declineUnlock);

		accept = (ImageView) findViewById(R.id.accept);
		decline = (ImageView) findViewById(R.id.decline);
		accept.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(isScreenActive) {
					answer();
				} else {
					decline.setVisibility(View.GONE);
					acceptUnlock.setVisibility(View.VISIBLE);
				}
			}
		});

		if(!isScreenActive) {
			accept.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View view, MotionEvent motionEvent) {
					float curX;
					switch (motionEvent.getAction()) {
						case MotionEvent.ACTION_DOWN:
							acceptUnlock.setVisibility(View.VISIBLE);
							decline.setVisibility(View.GONE);
							answerX = motionEvent.getX();
							break;
						case MotionEvent.ACTION_MOVE:
							curX = motionEvent.getX();
							if((answerX - curX) >= 0)
								view.scrollBy((int) (answerX - curX), view.getScrollY());
							answerX = curX;
							if (curX < screenWidth/4) {
								answer();
								return true;
							}
							break;
						case MotionEvent.ACTION_UP:
							view.scrollTo(0, view.getScrollY());
							decline.setVisibility(View.VISIBLE);
							acceptUnlock.setVisibility(View.GONE);
							break;
					}
					return true;
				}
			});

			decline.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View view, MotionEvent motionEvent) {
					float curX;
					switch (motionEvent.getAction()) {
						case MotionEvent.ACTION_DOWN:
							declineUnlock.setVisibility(View.VISIBLE);
							accept.setVisibility(View.GONE);
							declineX = motionEvent.getX();
							break;
						case MotionEvent.ACTION_MOVE:
							curX = motionEvent.getX();
							view.scrollBy((int) (declineX - curX), view.getScrollY());
							declineX = curX;
							Log.w(curX);
							if (curX > (screenWidth/2)){
								decline();
								return true;
							}
							break;
						case MotionEvent.ACTION_UP:
							view.scrollTo(0, view.getScrollY());
							accept.setVisibility(View.VISIBLE);
							declineUnlock.setVisibility(View.GONE);
							break;

					}
					return true;
				}
			});
		}

		decline.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(isScreenActive) {
					decline();
				} else {
					accept.setVisibility(View.GONE);
					acceptUnlock.setVisibility(View.VISIBLE);
				}
			}
		});

		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void callState(LinphoneCore lc, LinphoneCall call, State state, String message) {
				if (call == mCall && State.CallEnd == state) {
					finish();
				}
				if (state == State.StreamsRunning) {
					// The following should not be needed except some devices need it (e.g. Galaxy S).
					LinphoneManager.getLc().enableSpeaker(LinphoneManager.getLc().isSpeakerEnabled());
				}
			}
		};

		super.onCreate(savedInstanceState);
		instance = this;
	}

	@Override
	protected void onResume() {
		super.onResume();
		instance = this;
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}
		
		alreadyAcceptedOrDeniedCall = false;
		mCall = null;

		// Only one call ringing at a time is allowed
		if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
			List<LinphoneCall> calls = LinphoneUtils.getLinphoneCalls(LinphoneManager.getLc());
			for (LinphoneCall call : calls) {
				if (State.IncomingReceived == call.getState()) {
					mCall = call;
					break;
				}
			}
		}
		if (mCall == null) {
			//The incoming call no longer exists.
			Log.d("Couldn't find incoming call");
			finish();
			return;
		}
		
		
		LinphoneAddress address = mCall.getRemoteAddress();
		LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
		if (contact != null) {
			LinphoneUtils.setImagePictureFromUri(this, contactPicture, contact.getPhotoUri(), contact.getThumbnailUri());
			name.setText(contact.getFullName());
		} else {
			name.setText(LinphoneUtils.getAddressDisplayName(address));
		}
		number.setText(address.asStringUriOnly());
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		checkAndRequestCallPermissions();
	}

	@Override
	protected void onPause() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		instance = null;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneManager.isInstanciated() && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)) {
			LinphoneManager.getLc().terminateCall(mCall);
			finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	private void decline() {
		if (alreadyAcceptedOrDeniedCall) {
			return;
		}
		alreadyAcceptedOrDeniedCall = true;
		
		LinphoneManager.getLc().terminateCall(mCall);
		finish();
	}

	private void answer() {
		if (alreadyAcceptedOrDeniedCall) {
			return;
		}
		alreadyAcceptedOrDeniedCall = true;
		
		LinphoneCallParams params = LinphoneManager.getLc().createCallParams(mCall);

		boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(LinphoneService.instance().getApplicationContext());

		if (params != null) {
			params.enableLowBandwidth(isLowBandwidthConnection);
		}else {
			Log.e("Could not create call params for call");
		}

		if (params == null || !LinphoneManager.getInstance().acceptCallWithParams(mCall, params)) {
			// the above method takes care of Samsung Galaxy S
			Toast.makeText(this, R.string.couldnt_accept_call, Toast.LENGTH_LONG).show();
		} else {
			if (!LinphoneActivity.isInstanciated()) {
				return;
			}
			LinphoneManager.getInstance().routeAudioToReceiver();
			LinphoneActivity.instance().startIncallActivity(mCall);
		}
	}

	@Override
	public void onLeftHandleTriggered() {

	}

	@Override
	public void onRightHandleTriggered() {

	}
	
	private void checkAndRequestCallPermissions() {
		ArrayList<String> permissionsList = new ArrayList<String>();
		
		int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
		Log.i("[Permission] Record audio permission is " + (recordAudio == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
		Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		
		if (recordAudio != PackageManager.PERMISSION_GRANTED) {
			if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.RECORD_AUDIO) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
				Log.i("[Permission] Asking for record audio");
				permissionsList.add(Manifest.permission.RECORD_AUDIO);
			}
		}
		if (LinphonePreferences.instance().shouldInitiateVideoCall() || LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()) {
			if (camera != PackageManager.PERMISSION_GRANTED) {
				if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.CAMERA) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
					Log.i("[Permission] Asking for camera");
					permissionsList.add(Manifest.permission.CAMERA);
				}
			}
		}
		
		if (permissionsList.size() > 0) {
			String[] permissions = new String[permissionsList.size()];
			permissions = permissionsList.toArray(permissions);
			ActivityCompat.requestPermissions(this, permissions, 0);
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		for (int i = 0; i < permissions.length; i++) {
			Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		}
	}
}