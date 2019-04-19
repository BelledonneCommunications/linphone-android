package org.linphone.assistant;

/*
PhoneAccountValidationAssistantActivity.java
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListenerStub;
import org.linphone.core.tools.Log;

public class PhoneAccountValidationAssistantActivity extends AssistantActivity {
    private TextView mPhoneNumber, mFinishCreation;
    private EditText mSmsCode;
    private ClipboardManager mClipboard;

    private int mActivationCodeLength;
    private AccountCreatorListenerStub mListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_phone_account_validation);

        if (getIntent() != null && getIntent().getBooleanExtra("isLoginVerification", false)) {
            findViewById(R.id.title_account_creation).setVisibility(View.GONE);
        } else {
            findViewById(R.id.title_account_activation).setVisibility(View.GONE);
        }

        mActivationCodeLength =
                getResources().getInteger(R.integer.phone_number_validation_code_length);

        mPhoneNumber = findViewById(R.id.phone_number);
        mPhoneNumber.setText(mAccountCreator.getPhoneNumber());

        mSmsCode = findViewById(R.id.sms_code);
        mSmsCode.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        mFinishCreation.setEnabled(s.length() == mActivationCodeLength);
                    }
                });

        mFinishCreation = findViewById(R.id.finish_account_creation);
        mFinishCreation.setEnabled(false);
        mFinishCreation.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mFinishCreation.setEnabled(false);
                        mAccountCreator.setActivationCode(mSmsCode.getText().toString());

                        AccountCreator.Status status = mAccountCreator.activateAccount();
                        if (status != AccountCreator.Status.RequestOk) {
                            Log.e("[Phone Account Validation] activateAccount returned " + status);
                            mFinishCreation.setEnabled(true);
                            showGenericErrorDialog(status);
                        }
                    }
                });

        mListener =
                new AccountCreatorListenerStub() {
                    @Override
                    public void onActivateAccount(
                            AccountCreator creator, AccountCreator.Status status, String resp) {
                        Log.i("[Phone Account Validation] onActivateAccount status is " + status);
                        if (status.equals(AccountCreator.Status.AccountActivated)) {
                            createProxyConfigAndLeaveAssistant();
                        } else {
                            mFinishCreation.setEnabled(true);
                            showGenericErrorDialog(status);

                            if (status.equals(AccountCreator.Status.WrongActivationCode)) {
                                // TODO
                            }
                        }
                    }
                };

        mClipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        mClipboard.addPrimaryClipChangedListener(
                new ClipboardManager.OnPrimaryClipChangedListener() {
                    @Override
                    public void onPrimaryClipChanged() {
                        ClipData data = mClipboard.getPrimaryClip();
                        if (data != null && data.getItemCount() > 0) {
                            String clip = data.getItemAt(0).getText().toString();
                            if (clip.length() == 4) {
                                mSmsCode.setText(clip);
                            }
                        }
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAccountCreator.addListener(mListener);

        // Prevent user to go back, it won't be able to come back here after...
        mBack.setEnabled(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAccountCreator.removeListener(mListener);
    }
}
