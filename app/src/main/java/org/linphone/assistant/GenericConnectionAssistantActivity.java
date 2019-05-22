package org.linphone.assistant;

/*
GenericConnectionAssistantActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.R;
import org.linphone.core.TransportType;

public class GenericConnectionAssistantActivity extends AssistantActivity implements TextWatcher {
    private TextView mLogin;
    private EditText mUsername, mPassword, mDomain, mDisplayName;
    private RadioGroup mTransport;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mAbortCreation) {
            return;
        }

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
        mAccountCreator.setUsername(mUsername.getText().toString());
        mAccountCreator.setDomain(mDomain.getText().toString());
        mAccountCreator.setPassword(mPassword.getText().toString());
        mAccountCreator.setDisplayName(mDisplayName.getText().toString());

        switch (mTransport.getCheckedRadioButtonId()) {
            case R.id.transport_udp:
                mAccountCreator.setTransport(TransportType.Udp);
                break;
            case R.id.transport_tcp:
                mAccountCreator.setTransport(TransportType.Tcp);
                break;
            case R.id.transport_tls:
                mAccountCreator.setTransport(TransportType.Tls);
                break;
        }

        createProxyConfigAndLeaveAssistant();
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
