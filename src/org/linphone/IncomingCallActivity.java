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

import org.linphone.LinphoneManagerWaitHelper.LinphoneManagerReadyListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.Log;
import org.linphone.core.LinphoneCall.State;
import org.linphone.ui.SlidingTab;
import org.linphone.ui.SlidingTab.OnTriggerListener;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity displayed when a call comes in.
 * It should bypass the screen lock mechanism.
 *
 * @author Guillaume Beraudo
 */
public class IncomingCallActivity extends Activity implements LinphoneManagerReadyListener, LinphoneOnCallStateChangedListener, OnTriggerListener {

	private TextView mNameView;
	private TextView mNumberView;
	private ImageView mPictureView;
	private LinphoneCall mCall;
	private LinphoneManagerWaitHelper mHelper;
	private SlidingTab mIncomingCallWidget;

	private void findIncomingCall(Intent intent) {
		String stringUri = intent.getStringExtra("stringUri");
		mCall = LinphoneManager.getLc().findCallFromUri(stringUri);
		if (mCall == null) {
			Log.e("Couldn't find incoming call from ", stringUri);
			Toast.makeText(this, "Error", Toast.LENGTH_SHORT);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.incoming);

		mNameView = (TextView) findViewById(R.id.incoming_caller_name);
		mNumberView = (TextView) findViewById(R.id.incoming_caller_number);
		mPictureView = (ImageView) findViewById(R.id.incoming_picture);

        // set this flag so this activity will stay in front of the keyguard
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        flags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
        getWindow().addFlags(flags);


        // "Dial-to-answer" widget for incoming calls.
        mIncomingCallWidget = (SlidingTab) findViewById(R.id.sliding_widget);

        // For now, we only need to show two states: answer and decline.
        // TODO: make left hint work
//        mIncomingCallWidget.setLeftHintText(R.string.slide_to_answer_hint);
//        mIncomingCallWidget.setRightHintText(R.string.slide_to_decline_hint);

        mIncomingCallWidget.setOnTriggerListener(this);


        mHelper = new LinphoneManagerWaitHelper(this, this);
        super.onCreate(savedInstanceState);
	}

	@Override
	public void onCreateWhenManagerReady() {}

	@Override
	public void onResumeWhenManagerReady() {
		LinphoneManager.getInstance().addListener(this);
		findIncomingCall(getIntent());
		if (mCall == null) {
			finish();
			return;
		}
		LinphoneAddress address = mCall.getRemoteAddress();
		// May be greatly sped up using a drawable cache
		Uri uri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, getContentResolver());
		LinphoneUtils.setImagePictureFromUri(this, mPictureView, uri, R.drawable.unknown_person);

		// To be done after findUriPictureOfContactAndSetDisplayName called
		mNameView.setText(address.getDisplayName());
		if (getResources().getBoolean(R.bool.show_full_remote_address_on_incoming_call)) {
			mNumberView.setText(address.asStringUriOnly());
		} else {
			mNumberView.setText("");
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
        mHelper.doManagerDependentOnResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		LinphoneManager.getInstance().removeListener(this);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneManager.isInstanciated() && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)) {
			LinphoneManager.getLc().terminateCall(mCall);
			finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onCallStateChanged(LinphoneCall call, State state, String msg) {
		if (call == mCall && State.CallEnd == state) {
			finish();
		}
	}

	private void decline() {
		LinphoneManager.getLc().terminateCall(mCall);
	}
	private void answer() {
		if (!LinphoneManager.getInstance().acceptCall(mCall)) {
			// the above method takes care of Samsung Galaxy S
			Toast.makeText(this, R.string.couldnt_accept_call, Toast.LENGTH_LONG);
		}
	}
	@Override
	public void onGrabbedStateChange(View v, int grabbedState) {
	}

	@Override
	public void onTrigger(View v, int whichHandle) {
		switch (whichHandle) {
		case OnTriggerListener.LEFT_HANDLE:
			answer();
			finish();
			break;
		case OnTriggerListener.RIGHT_HANDLE:
			decline();
			finish();
			break;
		default:
			break;
		}
	}

}
