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
import org.linphone.ui.AvatarWithShadow;
import org.linphone.ui.LinphoneSliders;
import org.linphone.ui.LinphoneSliders.LinphoneSliderTriggered;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity displayed when a call comes in.
 * It should bypass the screen lock mechanism.
 *
 * @author Guillaume Beraudo
 */
public class IncomingCallActivity extends Activity implements LinphoneSliderTriggered {

	private static IncomingCallActivity instance;
	
	private TextView mNameView;
	private TextView mNumberView;
	private AvatarWithShadow mPictureView;
	private LinphoneCall mCall;
	private LinphoneSliders mIncomingCallWidget;
	private LinphoneCoreListenerBase mListener;
	
	public static IncomingCallActivity instance() {
		return instance;
	}
	
	public static boolean isInstanciated() {
		return instance != null;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.incoming);

		mNameView = (TextView) findViewById(R.id.incoming_caller_name);
		mNumberView = (TextView) findViewById(R.id.incoming_caller_number);
		mPictureView = (AvatarWithShadow) findViewById(R.id.incoming_picture);

        // set this flag so this activity will stay in front of the keyguard
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        getWindow().addFlags(flags);

        // "Dial-to-answer" widget for incoming calls.
        mIncomingCallWidget = (LinphoneSliders) findViewById(R.id.sliding_widget);
        mIncomingCallWidget.setOnTriggerListener(this);
        
        mListener = new LinphoneCoreListenerBase(){
        	@Override
        	public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
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
		// May be greatly sped up using a drawable cache
		Contact contact = ContactsManager.getInstance().findContactWithAddress(getContentResolver(), address);
		LinphoneUtils.setImagePictureFromUri(this, mPictureView.getView(), contact != null ? contact.getPhotoUri() : null, R.drawable.unknown_small);

		// To be done after findUriPictureOfContactAndSetDisplayName called
		mNameView.setText(contact != null ? contact.getName() : "");
		if (getResources().getBoolean(R.bool.only_display_username_if_unknown)) {
			mNumberView.setText(address.getUserName());
		} else {
			mNumberView.setText(address.asStringUriOnly());
		}
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
	
	private void answer() {
		LinphoneCallParams params = LinphoneManager.getLc().createDefaultCallParameters();
		
		boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(this);
		if (isLowBandwidthConnection) {
			params.enableLowBandwidth(true);
			Log.d("Low bandwidth enabled in call params");
		}
		
		if (!LinphoneManager.getInstance().acceptCallWithParams(mCall, params)) {
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
		answer();
		finish();
	}

	@Override
	public void onRightHandleTriggered() {
		decline();
		finish();
	}
}
