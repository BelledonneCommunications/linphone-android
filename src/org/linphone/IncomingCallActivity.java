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

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.Log;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity displayed when a call comes in.
 * It should bypass the screen lock mechanism.
 *
 * @author Guillaume Beraudo
 */
public class IncomingCallActivity extends Activity implements OnClickListener {

	private TextView mNameView;
	private TextView mNumberView;
	private LinphoneCall mCall;

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
		findIncomingCall(getIntent());

		super.onCreate(savedInstanceState);
		setContentView(R.layout.incoming);

		mNameView = (TextView) findViewById(R.id.incoming_caller_name);
		mNumberView = (TextView) findViewById(R.id.incoming_caller_number);

		findViewById(R.id.Decline).setOnClickListener(this);
		findViewById(R.id.Answer).setOnClickListener(this);
		
        // set this flag so this activity will stay in front of the keyguard
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        flags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
        getWindow().addFlags(flags);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		findIncomingCall(intent);
		super.onNewIntent(intent);
	}
	
	@Override
	protected void onResume() {
		LinphoneAddress address = mCall.getRemoteAddress();
		String from = LinphoneManager.extractADisplayName(getResources(), address);
		mNameView.setText(from);
		mNumberView.setText(address.asStringUriOnly());
		super.onResume();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
			LinphoneManager.getLc().terminateCall(mCall);
			finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.Answer:
			if (!LinphoneManager.getInstance().acceptCall(mCall)) {
				// the above method takes care of Samsung Galaxy S
				Toast.makeText(this, R.string.couldnt_accept_call, Toast.LENGTH_LONG);
			}
			break;
		case R.id.Decline:
			LinphoneManager.getLc().terminateCall(mCall);
			break;
		default:
			throw new RuntimeException();
		}
		finish();
	}
}
