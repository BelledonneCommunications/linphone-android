package org.linphone.assistant;
/*
RemoteProvisioningLoginActivity.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.ThemableActivity;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

public class RemoteProvisioningLoginActivity extends ThemableActivity implements OnClickListener {
    private EditText mLogin, mPassword, mDomain;
    private Button mConnect;
    private CoreListenerStub mListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.assistant_remote_provisioning_login);

        mLogin = findViewById(R.id.assistant_username);
        mPassword = findViewById(R.id.assistant_password);
        mDomain = findViewById(R.id.assistant_domain);

        mConnect = findViewById(R.id.assistant_connect);
        mConnect.setOnClickListener(this);

        String defaultDomain = getIntent().getStringExtra("Domain");
        if (defaultDomain != null) {
            mDomain.setText(defaultDomain);
            mDomain.setEnabled(false);
        }

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onConfiguringStatus(
                            Core lc, final ConfiguringState state, String message) {
                        if (state == ConfiguringState.Successful) {
                            // TODO
                        } else if (state == ConfiguringState.Failed) {
                            Toast.makeText(
                                            RemoteProvisioningLoginActivity.this,
                                            R.string.remote_provisioning_failure,
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                };
    }

    private void cancelWizard() {
        if (getResources().getBoolean(R.bool.allow_cancel_remote_provisioning_login_activity)) {
            LinphonePreferences.instance().disableProvisioningLoginView();
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    private void storeAccount(String username, String password, String domain) {
        XmlRpcHelper xmlRpcHelper = new XmlRpcHelper();
        xmlRpcHelper.getRemoteProvisioningFilenameAsync(
                new XmlRpcListenerBase() {
                    @Override
                    public void onRemoteProvisioningFilenameSent(String result) {
                        LinphonePreferences.instance().setRemoteProvisioningUrl(result);
                        LinphoneManager.getInstance().restartCore();
                    }
                },
                username,
                password,
                domain);

        LinphonePreferences.instance().firstLaunchSuccessful();
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }
    }

    @Override
    protected void onPause() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.cancel) {
            cancelWizard();
        }
        if (id == R.id.assistant_connect) {
            storeAccount(
                    mLogin.getText().toString(),
                    mPassword.getText().toString(),
                    mDomain.getText().toString());
        }
    }

    @Override
    public void onBackPressed() {
        cancelWizard();
    }
}
