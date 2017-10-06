package org.linphone;

/*
CallOutgoingActivity.java
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

import java.util.ArrayList;
import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.Reason;
import org.linphone.mediastream.Log;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class CallOutgoingActivity extends LinphoneGenericActivity implements OnClickListener{
	private static CallOutgoingActivity instance;

	private TextView name, number;
	private ImageView contactPicture, micro, speaker, hangUp;
	private LinphoneCall mCall;
	private LinphoneCoreListenerBase mListener;
	private boolean isMicMuted, isSpeakerEnabled;

	public static CallOutgoingActivity instance() {
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
		setContentView(R.layout.call_outgoing);

		name = (TextView) findViewById(R.id.contact_name);
		number = (TextView) findViewById(R.id.contact_number);
		contactPicture = (ImageView) findViewById(R.id.contact_picture);

		isMicMuted = false;
		isSpeakerEnabled = false;

		micro = (ImageView) findViewById(R.id.micro);
		micro.setOnClickListener(this);
		speaker = (ImageView) findViewById(R.id.speaker);
		speaker.setOnClickListener(this);

		// set this flag so this activity will stay in front of the keyguard
		int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
		getWindow().addFlags(flags);

		hangUp = (ImageView) findViewById(R.id.outgoing_hang_up);
		hangUp.setOnClickListener(this);

		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
				if (call == mCall && State.Connected == state) {
					if (!LinphoneActivity.isInstanciated()) {
						return;
					}
					LinphoneActivity.instance().startIncallActivity(mCall);
					finish();
					return;
				} else if (state == State.Error) {
					// Convert LinphoneCore message for internalization
					if (call.getErrorInfo().getReason() == Reason.Declined) {
						displayCustomToast(getString(R.string.error_call_declined), Toast.LENGTH_SHORT);
						decline();
					} else if (call.getErrorInfo().getReason() == Reason.NotFound) {
						displayCustomToast(getString(R.string.error_user_not_found), Toast.LENGTH_SHORT);
						decline();
					} else if (call.getErrorInfo().getReason() == Reason.Media) {
						displayCustomToast(getString(R.string.error_incompatible_media), Toast.LENGTH_SHORT);
						decline();
					} else if (call.getErrorInfo().getReason() == Reason.Busy) {
						displayCustomToast(getString(R.string.error_user_busy), Toast.LENGTH_SHORT);
						decline();
					} else if (message != null) {
						displayCustomToast(getString(R.string.error_unknown) + " - " + message, Toast.LENGTH_SHORT);
						decline();
					}
				}else if (state == State.CallEnd) {
					// Convert LinphoneCore message for internalization
					if (call.getErrorInfo().getReason() == Reason.Declined) {
						displayCustomToast(getString(R.string.error_call_declined), Toast.LENGTH_SHORT);
						decline();
					}
				}

				if (LinphoneManager.getLc().getCallsNb() == 0) {
					finish();
					return;
				}
			}
		};
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

		mCall = null;

		// Only one call ringing at a time is allowed
		if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
			List<LinphoneCall> calls = LinphoneUtils.getLinphoneCalls(LinphoneManager.getLc());
			for (LinphoneCall call : calls) {
				State cstate = call.getState();
				if (State.OutgoingInit == cstate || State.OutgoingProgress == cstate
						|| State.OutgoingRinging == cstate || State.OutgoingEarlyMedia == cstate) {
					mCall = call;
					break;
				}
				if (State.StreamsRunning == cstate) {
					if (!LinphoneActivity.isInstanciated()) {
						return;
					}
					LinphoneActivity.instance().startIncallActivity(mCall);
					finish();
					return;
				}
			}
		}
		if (mCall == null) {
			Log.e("Couldn't find outgoing call");
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
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.micro) {
			isMicMuted = !isMicMuted;
			if(isMicMuted) {
				micro.setImageResource(R.drawable.micro_selected);
			} else {
				micro.setImageResource(R.drawable.micro_default);
			}
			LinphoneManager.getLc().muteMic(isMicMuted);
		}
		if (id == R.id.speaker) {
			isSpeakerEnabled = !isSpeakerEnabled;
			if(isSpeakerEnabled) {
				speaker.setImageResource(R.drawable.speaker_selected);
			} else {
				speaker.setImageResource(R.drawable.speaker_default);
			}
			LinphoneManager.getLc().enableSpeaker(isSpeakerEnabled);
		}
		if (id == R.id.outgoing_hang_up) {
			decline();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneManager.isInstanciated() && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)) {
			LinphoneManager.getLc().terminateCall(mCall);
			finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	public void displayCustomToast(final String message, final int duration) {
		LayoutInflater inflater = getLayoutInflater();
		View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

		TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
		toastText.setText(message);

		final Toast toast = new Toast(getApplicationContext());
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.setDuration(duration);
		toast.setView(layout);
		toast.show();
	}

	private void decline() {
		LinphoneManager.getLc().terminateCall(mCall);
		finish();
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
