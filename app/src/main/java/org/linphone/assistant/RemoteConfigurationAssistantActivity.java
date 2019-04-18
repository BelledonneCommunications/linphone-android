package org.linphone.assistant;

/*
RemoteConfigurationAssistantActivity.java
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

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.ThemableActivity;

public class RemoteConfigurationAssistantActivity extends ThemableActivity {
    private View mTopBar;
    private ImageView mBack, mValid;
    private TextView mFetchAndApply, mQrCode;
    private EditText mRemoteConfigurationUrl;
    private RelativeLayout mWaitLayout;

    private CoreListenerStub mListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_remote_configuration);

        mTopBar = findViewById(R.id.top_bar);
        if (getResources().getBoolean(R.bool.assistant_hide_top_bar)) {
            mTopBar.setVisibility(View.GONE);
        }

        mBack = findViewById(R.id.back);
        mBack.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });

        mValid = findViewById(R.id.valid);
        mValid.setVisibility(View.INVISIBLE);

        mWaitLayout = findViewById(R.id.waitScreen);
        mWaitLayout.setVisibility(View.GONE);

        mFetchAndApply = findViewById(R.id.fetch_and_apply_remote_configuration);
        mFetchAndApply.setEnabled(false);
        mFetchAndApply.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String url = mRemoteConfigurationUrl.getText().toString();
                        if (Patterns.WEB_URL.matcher(url).matches()) {
                            mWaitLayout.setVisibility(View.VISIBLE);
                            mFetchAndApply.setEnabled(false);
                            LinphonePreferences.instance().setRemoteProvisioningUrl(url);
                            LinphoneManager.getLc().getConfig().sync();
                            Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            if (core != null) {
                                core.addListener(mListener);
                            }
                            LinphoneManager.getInstance().restartCore();
                        } else {
                            //TODO improve error text
                            Toast.makeText(
                                            RemoteConfigurationAssistantActivity.this,
                                            getString(R.string.remote_provisioning_failure),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                });

        mRemoteConfigurationUrl = findViewById(R.id.remote_configuration_url);
        mRemoteConfigurationUrl.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        mFetchAndApply.setEnabled(!s.toString().isEmpty());
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
        mRemoteConfigurationUrl.setText(LinphonePreferences.instance().getRemoteProvisioningUrl());

        mQrCode = findViewById(R.id.qr_code);
        mQrCode.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(
                                new Intent(
                                        RemoteConfigurationAssistantActivity.this,
                                        QrCodeConfigurationAssistantActivity.class));
                    }
                });

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onConfiguringStatus(
                            Core core, ConfiguringState status, String message) {
                        core.removeListener(mListener);
                        mWaitLayout.setVisibility(View.GONE);
                        mFetchAndApply.setEnabled(true);

                        if (status == ConfiguringState.Successful) {
                            Intent intent =
                                    new Intent(
                                            RemoteConfigurationAssistantActivity.this,
                                            LinphoneActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        } else if (status == ConfiguringState.Failed) {
                            Toast.makeText(
                                            RemoteConfigurationAssistantActivity.this,
                                            getString(R.string.remote_provisioning_failure),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    }
                };
    }
}
