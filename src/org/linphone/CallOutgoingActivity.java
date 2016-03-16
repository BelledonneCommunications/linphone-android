/*
CallOutgoingActivity.java
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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package org.linphone;

import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.mediastream.Log;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

public class CallOutgoingActivity extends Activity implements OnClickListener{

	private static CallOutgoingActivity instance;

	private TextView name, number;
	private ImageView contactPicture, micro, speaker, hangUp;
	private LinphoneCall mCall;
	private LinphoneCoreListenerBase mListener;
	private boolean isMicMuted, isSpeakerEnabled;
	private StatusFragment status;

	public static CallOutgoingActivity instance() {
		return instance;
	}

	public static boolean isInstanciated() {
		return instance != null;
	}

	public void updateStatusFragment(StatusFragment fragment) {
		status = fragment;
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
				if (LinphoneManager.getLc().getCallsNb() == 0) {
					finish();
					return;
				}
				if (call == mCall && State.CallEnd == state) {
					finish();
				}

				if (call == mCall && (State.Connected == state)){
					if (!LinphoneActivity.isInstanciated()) {
						return;
					}
					final LinphoneCallParams remoteParams = mCall.getRemoteParams();
					if (remoteParams != null && remoteParams.getVideoEnabled() && LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()) {
						LinphoneActivity.instance().startVideoActivity(mCall);
					} else {
						LinphoneActivity.instance().startIncallActivity(mCall);
					}
					finish();
					return;
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

		// Only one call ringing at a time is allowed
		if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
			List<LinphoneCall> calls = LinphoneUtils.getLinphoneCalls(LinphoneManager.getLc());
			for (LinphoneCall call : calls) {
				if (State.OutgoingInit == call.getState() || State.OutgoingProgress == call.getState() || State.OutgoingRinging == call.getState() || State.OutgoingEarlyMedia == call.getState()) {
					mCall = call;
					break;
				}
			}
		}
		if (mCall == null) {
			Log.e("Couldn't find outgoing call");
			finish();
			return;
		}

		LinphoneAddress address = mCall.getRemoteAddress();
		Contact contact = ContactsManager.getInstance().findContactWithAddress(getContentResolver(), address);
		if (contact != null) {
			LinphoneUtils.setImagePictureFromUri(this, contactPicture, contact.getPhotoUri(), contact.getThumbnailUri());
			name.setText(contact.getName());
		} else {
			name.setText(LinphoneUtils.getAddressDisplayName(address));
		}
		number.setText(address.asStringUriOnly());
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

	private void decline() {
		LinphoneManager.getLc().terminateCall(mCall);
	}
}
