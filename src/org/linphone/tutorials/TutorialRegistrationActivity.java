/*
TutorialRegistrationActivity.java
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
import org.linphone.core.tutorials.TutorialNotifier;
import org.linphone.core.tutorials.TutorialRegistration;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity for displaying and starting the registration example on Android phone.
 * 
 * @author Guillaume Beraudo
 *
 */
public class TutorialRegistrationActivity extends TutorialHelloWorldActivity {

	private static final String defaultSipAddress = "sip:";
	private static final String defaultSipPassword = "";
	private TextView sipAddressWidget;
	private TextView sipPasswordWidget;
	private TutorialRegistration tutorial;
	private Button buttonCall;
	private Handler mHandler =  new Handler();
	private TextView outputText;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.hello_world);
		sipAddressWidget = (TextView) findViewById(R.id.AddressId);
		sipAddressWidget.setText(defaultSipAddress);
		sipPasswordWidget = (TextView) findViewById(R.id.Password);
		sipPasswordWidget.setVisibility(TextView.VISIBLE);
		sipPasswordWidget.setText(defaultSipPassword);

		// Output text to the outputText widget
		outputText = (TextView) findViewById(R.id.OutputText);
		final TutorialNotifier notifier = new AndroidTutorialNotifier(mHandler, outputText);

		
		// Create Tutorial object
		tutorial = new TutorialRegistration(notifier);

		
		
		// Assign call action to call button
		buttonCall = (Button) findViewById(R.id.CallButton);
		buttonCall.setText("Register");
		buttonCall.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				TutorialLaunchingThread thread = new TutorialLaunchingThread();
				buttonCall.setEnabled(false);
				thread.start();
			}
		});


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
				tutorial.launchTutorial(
						sipAddressWidget.getText().toString(),
						sipPasswordWidget.getText().toString());
			} catch (LinphoneCoreException e) {
				e.printStackTrace();
				outputText.setText(e.getMessage() +"\n"+outputText.getText());
			}
		}
	}
}
