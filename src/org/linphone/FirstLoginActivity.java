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

import org.linphone.core.LinphoneCore.RegistrationState;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class FirstLoginActivity extends Activity implements OnClickListener {

	private TextView login;
	private TextView password;
	private SharedPreferences mPref;
	static FirstLoginActivity instance;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.first_login_view);
		mPref = PreferenceManager.getDefaultSharedPreferences(this);
		setDefaultDomain(getString(R.string.default_domain));
		
		login = (TextView) findViewById(R.id.login);
		login.setText(mPref.getString(getString(R.string.pref_username_key), ""));
		password = (TextView) findViewById(R.id.password);

		findViewById(R.id.connect).setOnClickListener(this);
		instance = this;
	}

	private void setDefaultDomain(String string) {
		String domain = mPref.getString(getString(R.string.pref_domain_key), "");
		if (domain.length() != 0) return;

		writePreference(R.string.pref_domain_key, getString(R.string.default_domain));
	}

	public void onClick(View v) {
		if (login.getText() == null || login.length() == 0
				|| password.getText() == null || password.length() == 0) {
			toast(R.string.first_launch_no_login_password);
			return;
		}

		writePreference(R.string.pref_username_key, login.getText().toString());
		writePreference(R.string.pref_passwd_key, password.getText().toString());

		try {
			LinphoneManager.getInstance().initFromConf(getApplicationContext());
		} catch (Throwable e) {
			Log.e(LinphoneManager.TAG, "Error while initializing from config in first login activity", e);
		}
	}

	
	
	private void writePreference(int key, String value) {
		mPref.edit().putString(getString(key), value).commit();
	}

	@Override
	protected void onDestroy() {
		instance = null;
		super.onDestroy();
	}

	public void onRegistrationStateChanged(RegistrationState state) {
		if (RegistrationState.RegistrationOk == state) {
			mPref.edit().putBoolean(getString(R.string.first_launch_suceeded_once_key), true).commit();
			finish();
		} else if (RegistrationState.RegistrationFailed == state) {
				toast(R.string.first_launch_bad_login_password);
		}
	}

	private void toast(int key) {
		Toast.makeText(instance, instance.getString(key), Toast.LENGTH_LONG).show();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			// Forbid user to press back button
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}
}
