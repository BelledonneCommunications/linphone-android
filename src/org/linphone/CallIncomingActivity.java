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
import org.linphone.ui.LinphoneSliders.LinphoneSliderTriggered;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
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
	private StatusFragment status;
	private boolean isActive;
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
			isActive = pm.isInteractive();
		} else {
			isActive = pm.isScreenOn();
		}

		final int screenWidth = getResources().getDisplayMetrics().widthPixels;

		acceptUnlock = (LinearLayout) findViewById(R.id.acceptUnlock);
		declineUnlock = (LinearLayout) findViewById(R.id.declineUnlock);

		accept = (ImageView) findViewById(R.id.accept);
		decline = (ImageView) findViewById(R.id.decline);
		accept.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(isActive) {
					answer();
				} else {
					decline.setVisibility(View.GONE);
					acceptUnlock.setVisibility(View.VISIBLE);
				}
			}
		});

		if(!isActive) {
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
				if(isActive) {
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
			Log.e("Couldn't find incoming call");
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
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneManager.isInstanciated() && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)) {
			LinphoneManager.getLc().terminateCall(mCall);
			finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	public void updateStatusFragment(StatusFragment fragment) {
		status = fragment;
	}

	private void decline() {
		LinphoneManager.getLc().terminateCall(mCall);
		finish();
	}

	private void answer() {
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
			final LinphoneCallParams remoteParams = mCall.getRemoteParams();
			if (remoteParams != null && remoteParams.getVideoEnabled() && LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()) {
				LinphoneActivity.instance().startVideoActivity(mCall);
			} else {
				LinphoneActivity.instance().startIncallActivity(mCall);
			}
		}
	}

	@Override
	public void onLeftHandleTriggered() {

	}

	@Override
	public void onRightHandleTriggered() {

	}
}