package org.linphone.assistant;
/*
LinphoneLoginFragment.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAccountCreator;
import org.linphone.core.LinphoneAccountCreatorImpl;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;

import android.app.Fragment;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
/**
 * @author Sylvain Berfini
 */
public class LinphoneLoginFragment extends Fragment implements CompoundButton.OnCheckedChangeListener, OnClickListener, TextWatcher, LinphoneAccountCreator.LinphoneAccountCreatorListener {
	private EditText login, password, phoneNumberEdit, dialCode, displayName;
	private Button apply, selectCountry;
	private CheckBox useUsername, usePassword;
	private LinearLayout phoneNumberLayout, usernameLayout, passwordLayout;
	private TextView forgotPassword;
	private CountryListFragment.Country country;
	private Boolean recoverAccount = false;
	private LinphoneAccountCreator accountCreator;
	private int countryCode;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_linphone_login, container, false);

		accountCreator = new LinphoneAccountCreatorImpl(LinphoneManager.getLc(), getResources().getString(R.string.wizard_url));
		accountCreator.setListener(this);

		String url = "http://linphone.org/free-sip-service.html&action=recover";
		
		login = (EditText) view.findViewById(R.id.assistant_username);
		login.addTextChangedListener(this);

		dialCode = (EditText) view.findViewById(R.id.dial_code);

		phoneNumberEdit = (EditText) view.findViewById(R.id.phone_number);
		phoneNumberLayout = (LinearLayout) view.findViewById(R.id.phone_number_layout);
		addPhoneNumberHandler(phoneNumberEdit, null);

		useUsername = (CheckBox) view.findViewById(R.id.use_username);
		usernameLayout = (LinearLayout) view.findViewById(R.id.username_layout);
		passwordLayout = (LinearLayout) view.findViewById(R.id.password_layout);
		password = (EditText) view.findViewById(R.id.assistant_password);
		displayName = (EditText) view.findViewById(R.id.assistant_display_name);

		forgotPassword = (TextView) view.findViewById(R.id.forgot_password);
		selectCountry = (Button) view.findViewById(R.id.select_country);

		//Phone number
		if(getResources().getBoolean(R.bool.use_phone_number_validation)){
			//Automatically get the country code from the phone
			TelephonyManager tm = (TelephonyManager) getActivity().getApplicationContext().getSystemService(getActivity().getApplicationContext().TELEPHONY_SERVICE);
			String countryIso = tm.getNetworkCountryIso();
			LinphoneProxyConfig proxyConfig = LinphoneManager.getLc().createProxyConfig();
			countryCode = proxyConfig.lookupCCCFromIso(countryIso.toUpperCase());

			phoneNumberLayout.setVisibility(View.VISIBLE);
			selectCountry.setOnClickListener(this);

			String previousPhone = AssistantActivity.instance().phone_number;
			if(previousPhone != null ){
				phoneNumberEdit.setText(previousPhone);
			}
			setCountry(AssistantActivity.instance().country);

			//Allow user to enter a username instead use the phone number as username
			if(getResources().getBoolean(R.bool.assistant_allow_username) ) {
				useUsername.setVisibility(View.VISIBLE);
				useUsername.setOnCheckedChangeListener(this);
			}
		}

		if(getResources().getBoolean(R.bool.assistant_allow_username)) {
			useUsername.setVisibility(View.VISIBLE);
			useUsername.setOnCheckedChangeListener(this);
			password.addTextChangedListener(this);
			forgotPassword.setText(Compatibility.fromHtml("<a href=\"" + url + "\"'>" + getString(R.string.forgot_password) + "</a>"));
			forgotPassword.setMovementMethod(LinkMovementMethod.getInstance());
		}

		//Hide phone number and display username/email/password
		if(!getResources().getBoolean(R.bool.use_phone_number_validation)){
			phoneNumberLayout.setVisibility(View.GONE);
			useUsername.setVisibility(View.GONE);

			usernameLayout.setVisibility(View.VISIBLE);
			passwordLayout.setVisibility(View.VISIBLE);
		}

		apply = (Button) view.findViewById(R.id.assistant_apply);
		apply.setEnabled(false);
		apply.setOnClickListener(this);

		return view;
	}

	private String getCountryCode() {
		if(dialCode != null) {
			String code = dialCode.getText().toString();
			if(code != null && code.startsWith("+")) {
				code = code.substring(1);
			}
			return code;
		}
		return null;
	}

	public void setCountry(CountryListFragment.Country c) {
		country = c;
		if( c!= null) {
			dialCode.setText(c.dial_code);
			selectCountry.setText(c.name);
		} else {
			if(countryCode != -1){
				dialCode.setText("+" + countryCode);
			} else {
				dialCode.setText("+");
			}
		}
	}

	private String getPhoneNumber(){
		LinphoneProxyConfig proxyConfig = LinphoneManager.getLc().createProxyConfig();
		String countryCode = dialCode.getText().toString();
		if(countryCode != null && countryCode.startsWith("+")) {
			countryCode = countryCode.substring(1);
		}
		proxyConfig.setDialPrefix(countryCode);
		return proxyConfig.normalizePhoneNumber(phoneNumberEdit.getText().toString());
	}


	public void linphoneLogIn() {
		if (login.getText() == null || login.length() == 0 || password.getText() == null || password.length() == 0) {
			Toast.makeText(getActivity(), getString(R.string.first_launch_no_login_password), Toast.LENGTH_LONG).show();
			return;
		}
		AssistantActivity.instance().linphoneLogIn(login.getText().toString(), password.getText().toString(), null, null, getResources().getBoolean(R.bool.assistant_account_validation_mandatory));
	}

	private void addPhoneNumberHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				if (s.length() > 0) {
					//phoneNumberOk = false;
					String countryCode = dialCode.getText().toString();
					if (countryCode != null && countryCode.startsWith("+")) {
						countryCode = countryCode.substring(1);
					}
					LinphoneAccountCreator.Status status = accountCreator.setPhoneNumber(phoneNumberEdit.getText().toString(), countryCode);
					if (status.equals(LinphoneAccountCreator.Status.Ok)) {
						status = accountCreator.isAccountUsed();
						if (status.equals(LinphoneAccountCreator.Status.Ok)) {
							recoverAccount = true;
						}
					} else {
						//displayError(phoneNumberOk, phoneNumberError, phoneNumberEdit, errorForStatus(status));
					}
				} else {
					//displayError(phoneNumberOk, phoneNumberError, phoneNumberEdit, "");
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) {
			}
		});
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.assistant_apply) {
			if(recoverAccount){
				recoverAccount();
			} else {
				linphoneLogIn();
			}
		}
		if (id == R.id.select_country) {
			AssistantActivity.instance().displayCountryChooser();
		}
	}

	private void recoverAccount() {
		accountCreator.recoverPhoneAccount();
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		apply.setEnabled(!login.getText().toString().isEmpty() && !password.getText().toString().isEmpty());
	}

	@Override
	public void afterTextChanged(Editable s) {}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if(buttonView.getId() == R.id.use_username) {
			if(isChecked) {
				usernameLayout.setVisibility(View.VISIBLE);
				passwordLayout.setVisibility(View.VISIBLE);
				recoverAccount = false;
			} else {
				usernameLayout.setVisibility(View.GONE);
				passwordLayout.setVisibility(View.INVISIBLE);
			}
		}
	}

	@Override
	public void onAccountCreatorIsAccountUsed(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
		apply.setEnabled(true);
	}

	@Override
	public void onAccountCreatorAccountCreated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorAccountActivated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorAccountLinkedWithPhoneNumber(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorPhoneNumberLinkActivated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorIsAccountActivated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
		apply.setEnabled(true);
	}

	@Override
	public void onAccountCreatorPhoneAccountRecovered(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
		AssistantActivity.instance().displayAssistantCodeConfirm(accountCreator.getUsername(), phoneNumberEdit.getText().toString(), getCountryCode(), true);
	}
}
