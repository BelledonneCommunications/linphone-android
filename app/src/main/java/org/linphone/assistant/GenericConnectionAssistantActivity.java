/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.assistant;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.Core;
import org.linphone.core.TransportType;
import org.linphone.core.tools.Log;

public class GenericConnectionAssistantActivity extends AssistantActivity implements TextWatcher {
    private TextView mLogin;
    private EditText mUsername, mPassword, mDomain, mDisplayName;
    private RadioGroup mTransport;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_generic_connection);

        mLogin = findViewById(R.id.assistant_login);
        mLogin.setEnabled(false);
        mLogin.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        configureAccount();
                    }
                });

        mUsername = findViewById(R.id.assistant_username);
        mUsername.addTextChangedListener(this);
        mDisplayName = findViewById(R.id.assistant_display_name);
        mDisplayName.addTextChangedListener(this);
        mPassword = findViewById(R.id.assistant_password);
        mPassword.addTextChangedListener(this);
        mDomain = findViewById(R.id.assistant_domain);
        mDomain.addTextChangedListener(this);
        mTransport = findViewById(R.id.assistant_transports);
    }

    private void configureAccount() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            Log.i("[Generic Connection Assistant] Reloading configuration with default");
            reloadDefaultAccountCreatorConfig();
        }

        AccountCreator accountCreator = getAccountCreator();
        accountCreator.setUsername(mUsername.getText().toString());
        accountCreator.setDomain(mDomain.getText().toString());
        accountCreator.setPassword(mPassword.getText().toString());
        accountCreator.setDisplayName(mDisplayName.getText().toString());

        switch (mTransport.getCheckedRadioButtonId()) {
            case R.id.transport_udp:
                accountCreator.setTransport(TransportType.Udp);
                break;
            case R.id.transport_tcp:
                accountCreator.setTransport(TransportType.Tcp);
                break;
            case R.id.transport_tls:
                accountCreator.setTransport(TransportType.Tls);
                break;
        }

        createProxyConfigAndLeaveAssistant(true);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        mLogin.setEnabled(
                !mUsername.getText().toString().isEmpty()
                        && !mDomain.getText().toString().isEmpty());
    }

    @Override
    public void afterTextChanged(Editable s) {}
}
