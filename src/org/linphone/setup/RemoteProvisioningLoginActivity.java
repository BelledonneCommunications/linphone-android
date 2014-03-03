package org.linphone.setup;
/*
RemoteProvisioningLoginActivity.java
Copyright (C) 2014  Belledonne Communications, Grenoble, France

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

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

/**
 * @author Sylvain Berfini
 */
public class RemoteProvisioningLoginActivity extends Activity implements OnClickListener {
	private EditText login, password, domain;
	private RelativeLayout next, cancel;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.setup_remote_provisioning_login);
		
		login = (EditText) findViewById(R.id.setup_username);
		password = (EditText) findViewById(R.id.setup_password);
		domain = (EditText) findViewById(R.id.setup_domain);
		
		next = (RelativeLayout) findViewById(R.id.setup_next);
		next.setOnClickListener(this);
		cancel = (RelativeLayout) findViewById(R.id.setup_cancel);
		cancel.setOnClickListener(this);
		
		String defaultDomain = getIntent().getStringExtra("Domain");
		if (defaultDomain != null) {
			domain.setText(defaultDomain);
			domain.setEnabled(false);
		}
	}
	
	private void cancelWizard(boolean bypassCheck) {
		if (bypassCheck || getResources().getBoolean(R.bool.allow_cancel_remote_provisioning_login_activity)) {
			LinphonePreferences.instance().disableProvisioningLoginView();
			setResult(bypassCheck ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
			finish();
		}
	}
	
	private boolean storeAccount(String username, String password, String domain) {
		LinphoneCore lc = LinphoneManager.getLc();
		
		String identity = "sip:" + username + "@" + domain;
		LinphoneProxyConfig prxCfg = lc.createProxyConfig();
		try {
			prxCfg.setIdentity(identity);
			lc.addProxyConfig(prxCfg);
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
			return false;
		}
		
		LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(username, null, password, null, null, domain);
		lc.addAuthInfo(authInfo);
		
		if (LinphonePreferences.instance().getAccountCount() == 1)
			lc.setDefaultProxyConfig(prxCfg);
		
		return true;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.setup_next) {
			if (login.getText() == null || login.length() == 0 || password.getText() == null || password.length() == 0 || domain.getText() == null || domain.length() == 0) {
				Toast.makeText(this, getString(R.string.first_launch_no_login_password), Toast.LENGTH_LONG).show();
				return;
			}
			
			storeAccount(login.getText().toString(), password.getText().toString(), domain.getText().toString());
			cancelWizard(true);
		} else if (id == R.id.setup_cancel) {
			cancelWizard(false);
		}
	}

	@Override
	public void onBackPressed() {
		cancelWizard(false);
	}
}
