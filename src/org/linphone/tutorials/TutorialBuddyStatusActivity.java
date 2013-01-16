/*
TutorialBuddyStatusActivity.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
package org.linphone.tutorials;

import org.linphone.R;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.tutorials.TutorialBuddyStatus;
import org.linphone.core.tutorials.TutorialNotifier;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity for displaying and starting the BuddyStatus example on Android phone.
 *
 * @author Guillaume Beraudo
 *
 */
public class TutorialBuddyStatusActivity extends Activity {

	private static final String defaultSipAddress = "sip:";
	private TextView sipAddressWidget;
	private TextView mySipAddressWidget;
	private TextView mySipPasswordWidget;
	
	private TutorialBuddyStatus tutorial;
	private Handler mHandler =  new Handler() ;
	private Button buttonCall;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.hello_world);
		sipAddressWidget = (TextView) findViewById(R.id.AddressId);
		sipAddressWidget.setText(defaultSipAddress);

		mySipAddressWidget = (TextView) findViewById(R.id.MyAddressId);
		mySipAddressWidget.setVisibility(View.VISIBLE);
		mySipPasswordWidget = (TextView) findViewById(R.id.Password);
		mySipPasswordWidget.setVisibility(TextView.VISIBLE);
		
		
		// Output text to the outputText widget
		final TextView outputText = (TextView) findViewById(R.id.OutputText);
		final TutorialNotifier notifier = new AndroidTutorialNotifier(mHandler, outputText);

		
		// Create BuddyStatus object
		tutorial = new TutorialBuddyStatus(notifier);

		
		
		// Assign call action to call button
		buttonCall = (Button) findViewById(R.id.CallButton);
		buttonCall.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				TutorialLaunchingThread thread = new TutorialLaunchingThread();
				buttonCall.setEnabled(false);
				thread.start();
			}
		});

		// Assign stop action to stop button
		Button buttonStop = (Button) findViewById(R.id.ButtonStop);
		buttonStop.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				tutorial.stopMainLoop();
			}
		});
	}
	
	
	private class TutorialLaunchingThread extends Thread {
		@Override
		public void run() {
			super.run();
			try {
				String myIdentity = mySipAddressWidget.getText().length()>0?mySipAddressWidget.getText().toString():null;
				String myPassword = mySipPasswordWidget.getText().length()>0?mySipPasswordWidget.getText().toString():null;
				tutorial.launchTutorial(sipAddressWidget.getText().toString(), myIdentity, myPassword);
				mHandler.post(new Runnable() {
					public void run() {
						buttonCall.setEnabled(true);
					}
				});
			} catch (LinphoneCoreException e) {
				e.printStackTrace();
			}
		}
	}
}
