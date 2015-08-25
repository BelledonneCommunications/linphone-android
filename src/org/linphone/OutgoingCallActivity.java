/*
IncomingCallActivity.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import de.timroes.axmlrpc.Call;

/**
 * Activity displayed when a call comes in.
 * It should bypass the screen lock mechanism.
 *
 * @author Guillaume Beraudo
 */
public class OutgoingCallActivity extends Activity {

	private static OutgoingCallActivity instance;

	private TextView mNameView, mNumberView;
	private ImageView mPictureView, micro, speaker, decline;
	private LinphoneCall mCall;
	private LinphoneCoreListenerBase mListener;
	private boolean isMicMuted, isSpeakerEnabled;

	public static OutgoingCallActivity instance() {
		return instance;
	}

	public static boolean isInstanciated() {
		return instance != null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.outgoing_call);

		mNameView = (TextView) findViewById(R.id.incoming_caller_name);
		mNumberView = (TextView) findViewById(R.id.incoming_caller_number);
		mPictureView = (ImageView) findViewById(R.id.incoming_picture);

		micro = (ImageView) findViewById(R.id.micro);
		micro.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

			}
		});
		speaker = (ImageView) findViewById(R.id.speaker);

		isMicMuted = false;
		isSpeakerEnabled = false;

		micro.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				isMicMuted = !isMicMuted;
				if(isMicMuted) {
					micro.setImageResource(R.drawable.micro_selected);
				} else {
					micro.setImageResource(R.drawable.micro_default);
				}
				LinphoneManager.getLc().muteMic(isMicMuted);
			}
		});

		speaker.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				isSpeakerEnabled = !isSpeakerEnabled;
				if(isSpeakerEnabled) {
					speaker.setImageResource(R.drawable.speaker_selected);
				} else {
					speaker.setImageResource(R.drawable.speaker_default);
				}
				LinphoneManager.getLc().enableSpeaker(isSpeakerEnabled);
			}
		});

		// set this flag so this activity will stay in front of the keyguard
		int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
		getWindow().addFlags(flags);

		// "Dial-to-answer" widget for incoming calls.

		ImageView decline = (ImageView) findViewById(R.id.hang_up);
		decline.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				decline();
			}
		});

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

				if (call == mCall && State.Connected == state || State.StreamsRunning == state){
					if (!LinphoneActivity.isInstanciated()) {
						return;
					}
					final LinphoneCallParams remoteParams = mCall.getRemoteParams();
					if (remoteParams != null && remoteParams.getVideoEnabled() && LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()) {
						LinphoneActivity.instance().startVideoActivity(mCall);
					} else {
						LinphoneActivity.instance().startIncallActivity(mCall);
					}
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
				if (State.OutgoingInit == call.getState() || State.OutgoingProgress == call.getState()) {
					mCall = call;
					break;
				}
			}
		}
		if (mCall == null) {
			Log.e("Couldn't find incoming call");
			finish();
			return;
		}
		LinphoneAddress address = mCall.getRemoteAddress();
		// May be greatly sped up using a drawable cache
		Contact contact = ContactsManager.getInstance().findContactWithAddress(getContentResolver(), address);
		//LinphoneUtils.setImagePictureFromUri(this, mPictureView, contact != null ? contact.getPhotoUri() : null,
		//		 contact != null ? contact.getThumbnailUri() : null, R.drawable.unknown_small);

		// To be done after findUriPictureOfContactAndSetDisplayName called
		mNameView.setText(contact != null ? contact.getName() : address.getUserName());
		mNumberView.setText(address.asStringUriOnly());

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
		LinphoneManager.getLc().terminateCall(mCall);
	}

}
