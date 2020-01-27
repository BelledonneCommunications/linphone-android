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

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.R;
import org.linphone.settings.LinphonePreferences;

public class MenuAssistantActivity extends AssistantActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_menu);

        TextView accountCreation = findViewById(R.id.account_creation);
        accountCreation.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent;
                        if (getResources().getBoolean(R.bool.isTablet)
                                || !getResources().getBoolean(R.bool.use_phone_number_validation)) {
                            intent =
                                    new Intent(
                                            MenuAssistantActivity.this,
                                            EmailAccountCreationAssistantActivity.class);
                        } else {
                            intent =
                                    new Intent(
                                            MenuAssistantActivity.this,
                                            PhoneAccountCreationAssistantActivity.class);
                        }
                        startActivity(intent);
                    }
                });

        TextView accountConnection = findViewById(R.id.account_connection);
        accountConnection.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(
                                new Intent(
                                        MenuAssistantActivity.this,
                                        AccountConnectionAssistantActivity.class));
                    }
                });
        if (getResources().getBoolean(R.bool.hide_linphone_accounts_in_assistant)) {
            accountConnection.setVisibility(View.GONE);
        }

        TextView genericConnection = findViewById(R.id.generic_connection);
        genericConnection.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(
                                new Intent(
                                        MenuAssistantActivity.this,
                                        GenericConnectionAssistantActivity.class));
                    }
                });
        if (getResources().getBoolean(R.bool.hide_generic_accounts_in_assistant)) {
            genericConnection.setVisibility(View.GONE);
        }

        TextView remoteConfiguration = findViewById(R.id.remote_configuration);
        remoteConfiguration.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(
                                new Intent(
                                        MenuAssistantActivity.this,
                                        RemoteConfigurationAssistantActivity.class));
                    }
                });
        if (getResources().getBoolean(R.bool.hide_remote_provisioning_in_assistant)) {
            remoteConfiguration.setVisibility(View.GONE);
        }

        if (getResources().getBoolean(R.bool.assistant_use_linphone_login_as_first_fragment)) {
            startActivity(
                    new Intent(
                            MenuAssistantActivity.this, AccountConnectionAssistantActivity.class));
            finish();
        } else if (getResources()
                .getBoolean(R.bool.assistant_use_generic_login_as_first_fragment)) {
            startActivity(
                    new Intent(
                            MenuAssistantActivity.this, GenericConnectionAssistantActivity.class));
            finish();
        } else if (getResources()
                .getBoolean(R.bool.assistant_use_create_linphone_account_as_first_fragment)) {
            startActivity(
                    new Intent(
                            MenuAssistantActivity.this,
                            PhoneAccountCreationAssistantActivity.class));
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (getResources()
                .getBoolean(R.bool.forbid_to_leave_assistant_before_account_configuration)) {
            mBack.setEnabled(false);
        }

        mBack.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinphonePreferences.instance().firstLaunchSuccessful();
                        goToLinphoneActivity();
                    }
                });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (getResources()
                    .getBoolean(R.bool.forbid_to_leave_assistant_before_account_configuration)) {
                // Do nothing
                return true;
            } else {
                LinphonePreferences.instance().firstLaunchSuccessful();
                goToLinphoneActivity();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
