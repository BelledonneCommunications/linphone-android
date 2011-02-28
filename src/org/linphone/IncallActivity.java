/*
IncallActivity.java
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

import java.util.Timer;
import java.util.TimerTask;

import org.linphone.ui.AddVideoButton;
import org.linphone.ui.HangCallButton;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class IncallActivity extends Activity implements OnClickListener {

	public static final String CONTACT_KEY = "contact";
	private View numpadClose;
	private View numpadShow;
	private View numpad;
	private View buttonsZone;
	private HangCallButton hangButton;
	private Timer timer = new Timer();
	private TimerTask task;
	private TextView elapsedTime;
	private Handler handler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.incall_view);
		
		numpad = findViewById(R.id.incallDialer);
		buttonsZone = findViewById(R.id.incallButtonsZone);
		
		numpadClose = findViewById(R.id.incallNumpadClose);
		numpadClose.setOnClickListener(this);

		numpadShow = findViewById(R.id.incallNumpadShow);
		numpadShow.setOnClickListener(this);

		hangButton = (HangCallButton) findViewById(R.id.incallHang);
		hangButton.setOnClickListener(this);
		
		TextView contact = (TextView) findViewById(R.id.incallContactName);
		if (getIntent().getExtras() != null) {
			contact.setText(getIntent().getExtras().getCharSequence(CONTACT_KEY));
		} else {
			contact.setVisibility(View.GONE);
		}

		elapsedTime = (TextView) findViewById(R.id.incallElapsedTime);

		AddVideoButton addVideoButton = (AddVideoButton) findViewById(R.id.AddVideo);
		addVideoButton.setOnAlreadyInVideoCallListener(DialerActivity.instance());
	}

	public void onClick(View v) {
		if (v == numpadClose) {
			numpad.setVisibility(View.GONE);
			numpadClose.setVisibility(View.GONE);
			buttonsZone.setVisibility(View.VISIBLE);
		} else if (v == numpadShow) {
			buttonsZone.setVisibility(View.GONE);
			numpad.setVisibility(View.VISIBLE);
			numpadClose.setVisibility(View.VISIBLE);
		} else if (v == hangButton) {
			hangButton.onClick(v);
			finish();
		}
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
		
		task = new TimerTask() {
			@Override
			public void run() {
				if (LinphoneManager.getLc().isIncall()) {
					final int duration = LinphoneManager.getLc().getCurrentCall().getDuration();
					if (duration != 0) {
						handler.post(new Runnable() {
							public void run() {
								elapsedTime.setText(String.valueOf(duration));
							}
						});
						
					}
				}
			}
		};

		timer.scheduleAtFixedRate(task, 0, 1000);
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	void showVideoCallActivity() {
		startActivity(new Intent().setClass(this, VideoCallActivity.class));
	}
}
