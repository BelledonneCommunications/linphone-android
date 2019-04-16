package org.linphone.purchase;
/*
InAppPurchaseFragment.java
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

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.ProxyConfig;
import org.linphone.settings.LinphonePreferences;

public class InAppPurchaseFragment extends Fragment implements View.OnClickListener {
    private LinearLayout mUsernameLayout;
    private EditText mUsername, mEmail;
    private TextView mErrorMessage;

    private boolean mUsernameOk = false, mEmailOk = false;
    private String mDefaultUsername, mDefaultEmail;
    private Button mBuyItemButton;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        View view = inflater.inflate(R.layout.in_app_store, container, false);

        String id = getArguments().getString("item_id");
        Purchasable item = InAppPurchaseActivity.instance().getPurchasedItem(id);
        mBuyItemButton = view.findViewById(R.id.inapp_button);

        displayBuySubscriptionButton(item);

        mDefaultEmail = InAppPurchaseActivity.instance().getGmailAccount();
        mDefaultUsername =
                LinphonePreferences.instance()
                        .getAccountUsername(
                                LinphonePreferences.instance().getDefaultAccountIndex());

        mUsernameLayout = view.findViewById(R.id.username_layout);
        mUsername = view.findViewById(R.id.username);
        if (!getResources().getBoolean(R.bool.hide_username_in_inapp)) {
            mUsernameLayout.setVisibility(View.VISIBLE);
            mUsername.setText(
                    LinphonePreferences.instance()
                            .getAccountUsername(
                                    LinphonePreferences.instance().getDefaultAccountIndex()));

            addUsernameHandler(mUsername, mErrorMessage);
        } else {
            if (mDefaultUsername != null) {
                mUsernameLayout.setVisibility(View.GONE);
                mUsernameOk = true;
            }
        }

        mEmail = view.findViewById(R.id.email);
        if (mDefaultEmail != null) {
            mEmail.setText(mDefaultEmail);
            mEmailOk = true;
        }

        mBuyItemButton.setEnabled(mEmailOk && mUsernameOk);
        mErrorMessage = view.findViewById(R.id.username_error);

        return view;
    }

    private void addUsernameHandler(final EditText field, final TextView errorMessage) {
        field.addTextChangedListener(
                new TextWatcher() {
                    public void afterTextChanged(Editable s) {}

                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int count, int after) {
                        mUsernameOk = false;
                        String username = s.toString();
                        if (isUsernameCorrect(username)) {
                            mUsernameOk = true;
                            errorMessage.setText("");
                        } else {
                            errorMessage.setText(R.string.wizard_username_incorrect);
                        }
                        if (mBuyItemButton != null) mBuyItemButton.setEnabled(mUsernameOk);
                    }
                });
    }

    private boolean isUsernameCorrect(String username) {
        ProxyConfig lpc = LinphoneManager.getLc().createProxyConfig();
        return lpc.isPhoneNumber(username);
    }

    private void displayBuySubscriptionButton(Purchasable item) {
        mBuyItemButton.setText("Buy account (" + item.getPrice() + ")");
        mBuyItemButton.setTag(item);
        mBuyItemButton.setOnClickListener(this);
        mBuyItemButton.setEnabled(mUsernameOk && mEmailOk);
    }

    @Override
    public void onClick(View v) {
        Purchasable item = (Purchasable) v.getTag();
        InAppPurchaseActivity.instance().buyInapp(getUsername(), item);
    }

    private String getUsername() {
        String username = this.mUsername.getText().toString();
        ProxyConfig lpc = LinphoneManager.getLc().createProxyConfig();
        username = lpc.normalizePhoneNumber(username);
        return username.toLowerCase(Locale.getDefault());
    }
}
