package org.linphone.assistant;
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
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.R;
import org.linphone.core.LinphoneCore;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * @author Sylvain Berfini
 */
public class RemoteProvisioningLoginActivity extends Activity implements OnClickListener {
	private EditText login, password, domain;
	private ImageView  cancel;
	private Button connect;
	private LinphoneCoreListenerBase mListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.assistant_remote_provisioning_login);
		
		login = (EditText) findViewById(R.id.assistant_username);
		password = (EditText) findViewById(R.id.assistant_password);
		domain = (EditText) findViewById(R.id.assistant_domain);

		//cancel = (ImageView) findViewById(R.id.cancel);
		//cancel.setOnClickListener(this);

		connect = (Button) findViewById(R.id.assistant_connect);
		connect.setOnClickListener(this);

		String defaultDomain = getIntent().getStringExtra("Domain");
		if (defaultDomain != null) {
			domain.setText(defaultDomain);
			domain.setEnabled(false);
		}

		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void configuringStatus(LinphoneCore lc, final LinphoneCore.RemoteProvisioningState state, String message) {
				if (state == LinphoneCore.RemoteProvisioningState.ConfiguringSuccessful) {
					//TODO
				} else if (state == LinphoneCore.RemoteProvisioningState.ConfiguringFailed) {
					Toast.makeText(RemoteProvisioningLoginActivity.this, R.string.remote_provisioning_failure, Toast.LENGTH_LONG).show();
				}
			}
		};
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

		XmlRpcHelper xmlRpcHelper = new XmlRpcHelper(null);
		xmlRpcHelper.getRemoteProvisioningFilenameAsync(new XmlRpcListenerBase() {
			@Override
			public void onRemoteProvisioningFilenameSent(String result) {
				LinphonePreferences.instance().setRemoteProvisioningUrl(result);
				LinphoneManager.getInstance().restartLinphoneCore();
			}
		}, username.toString(), password.toString(), domain.toString());

		LinphonePreferences.instance().firstLaunchSuccessful();
		setResult(Activity.RESULT_OK);
		finish();
		/*String identity = "sip:" + username + "@" + domain;
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
		*/
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
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
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.cancel) {
			cancelWizard(false);
		}
		if (id == R.id.assistant_connect){
			storeAccount(login.getText().toString(), password.getText().toString(), domain.getText().toString());
		}
	}

	@Override
	public void onBackPressed() {
		cancelWizard(false);
	}
}
