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
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListenerStub;
import org.linphone.core.tools.Log;

public class EmailAccountValidationAssistantActivity extends AssistantActivity {
    private TextView mFinishCreation;

    private AccountCreatorListenerStub mListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_email_account_validation);

        TextView email = findViewById(R.id.send_email);
        email.setText(getAccountCreator().getEmail());

        mFinishCreation = findViewById(R.id.assistant_check);
        mFinishCreation.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mFinishCreation.setEnabled(false);

                        AccountCreator.Status status = getAccountCreator().isAccountActivated();
                        if (status != AccountCreator.Status.RequestOk) {
                            Log.e(
                                    "[Email Account Validation Assistant] activateAccount returned "
                                            + status);
                            mFinishCreation.setEnabled(true);
                            showGenericErrorDialog(status);
                        }
                    }
                });

        mListener =
                new AccountCreatorListenerStub() {
                    @Override
                    public void onIsAccountActivated(
                            AccountCreator creator, AccountCreator.Status status, String resp) {
                        Log.i(
                                "[Email Account Validation Assistant] onIsAccountActivated status is "
                                        + status);
                        if (status.equals(AccountCreator.Status.AccountActivated)) {
                            createProxyConfigAndLeaveAssistant();
                        } else if (status.equals(AccountCreator.Status.AccountNotActivated)) {
                            Toast.makeText(
                                            EmailAccountValidationAssistantActivity.this,
                                            getString(R.string.assistant_account_not_validated),
                                            Toast.LENGTH_LONG)
                                    .show();
                            mFinishCreation.setEnabled(true);
                        } else {
                            showGenericErrorDialog(status);
                            mFinishCreation.setEnabled(true);
                        }
                    }
                };
    }

    @Override
    protected void onResume() {
        super.onResume();
        getAccountCreator().addListener(mListener);

        // Prevent user to go back, it won't be able to come back here after...
        mBack.setEnabled(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getAccountCreator().removeListener(mListener);
    }
}
