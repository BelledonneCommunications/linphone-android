package com.belledonne_communications.aaar_tester;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;

public class MainActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		LinphoneCoreFactory.instance().setLogCollectionPath(getFilesDir().getAbsolutePath());
		LinphoneCoreFactory.instance().enableLogCollection(true);
		LinphoneCoreFactory.instance().setDebugMode(true, getString(R.string.app_name));

		try {
			LinphoneCore core = LinphoneCoreFactory.instance().createLinphoneCore(null, this);
			((TextView)findViewById(R.id.version)).setText(core.getVersion());
		} catch (Exception e) {
			((TextView)findViewById(R.id.version)).setText("Error ! " + e);
		}
	}
}
