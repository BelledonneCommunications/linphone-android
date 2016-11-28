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
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.os.Build;
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

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.DialPlan;
import org.linphone.core.LinphoneAccountCreator;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;

import java.util.Locale;

/**
 * @author Sylvain Berfini
 */
public class LinphoneLoginFragment extends Fragment implements CompoundButton.OnCheckedChangeListener, OnClickListener, TextWatcher, LinphoneAccountCreator.LinphoneAccountCreatorListener {
	private EditText login, password, phoneNumberEdit, dialCode;
	private Button apply, selectCountry;
	private CheckBox useUsername;
	private LinearLayout phoneNumberLayout, usernameLayout, passwordLayout;
	private TextView forgotPassword, messagePhoneNumber, phoneNumberError;
	private Boolean recoverAccount;
	private LinphoneAccountCreator accountCreator;
	private int countryCode;
	private String username, phone, dialcode;
	private ImageView phoneNumberInfo;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_linphone_login, container, false);

		accountCreator = LinphoneCoreFactory.instance().createAccountCreator(LinphoneManager.getLc(), LinphonePreferences.instance().getXmlrpcUrl());
		accountCreator.setListener(this);

		String url = "http://linphone.org/free-sip-service.html&action=recover";

		login = (EditText) view.findViewById(R.id.assistant_username);
		login.addTextChangedListener(this);

		recoverAccount = true;

		dialCode = (EditText) view.findViewById(R.id.dial_code);

		phoneNumberEdit = (EditText) view.findViewById(R.id.phone_number);
		phoneNumberLayout = (LinearLayout) view.findViewById(R.id.phone_number_layout);
		phoneNumberError = (TextView) view.findViewById(R.id.phone_number_error_2);

		phoneNumberInfo = (ImageView) view.findViewById(R.id.info_phone_number);

		useUsername = (CheckBox) view.findViewById(R.id.use_username);
		usernameLayout = (LinearLayout) view.findViewById(R.id.username_layout);
		passwordLayout = (LinearLayout) view.findViewById(R.id.password_layout);
		password = (EditText) view.findViewById(R.id.assistant_password);
		messagePhoneNumber = (TextView) view.findViewById(R.id.message_phone_number);

		forgotPassword = (TextView) view.findViewById(R.id.forgot_password);
		selectCountry = (Button) view.findViewById(R.id.select_country);

		apply = (Button) view.findViewById(R.id.assistant_apply);
		apply.setEnabled(true);
		apply.setOnClickListener(this);

		//Phone number
		if(getResources().getBoolean(R.bool.use_phone_number_validation)){
			phone = getArguments().getString("Phone");
			dialcode = getArguments().getString("Dialcode");

			getActivity().getApplicationContext();
			//Automatically get the country code from the phone
			TelephonyManager tm =
					(TelephonyManager) getActivity().getApplicationContext().getSystemService(
							Context.TELEPHONY_SERVICE);
			String countryIso = tm.getNetworkCountryIso();
			LinphoneProxyConfig proxyConfig = LinphoneManager.getLc().createProxyConfig();
			countryCode = proxyConfig.lookupCCCFromIso(countryIso.toUpperCase());


			DialPlan c = AssistantActivity.instance().country;
			if (c != null) {
				selectCountry.setText(c.getCountryName());
				dialCode.setText(c.getCountryCallingCode().contains("+") ?
						c.getCountryCallingCode() : "+" + c.getCountryCallingCode());
			} else {
				c = AssistantActivity.instance().getCountryListAdapter()
						.getCountryFromCountryCode(String.valueOf(countryCode));
				if (c != null) {
					selectCountry.setText(c.getCountryName());
					dialCode.setText(c.getCountryCallingCode().contains("+") ?
							c.getCountryCallingCode() : "+" + c.getCountryCallingCode());
				}
			}

			phoneNumberLayout.setVisibility(View.VISIBLE);
			selectCountry.setOnClickListener(this);
			phoneNumberInfo.setOnClickListener(this);

			String previousPhone = AssistantActivity.instance().phone_number;
			if (previousPhone != null ) {
				phoneNumberEdit.setText(previousPhone);
			}

			//Allow user to enter a username instead use the phone number as username
			if (getResources().getBoolean(R.bool.assistant_allow_username) ) {
				useUsername.setVisibility(View.VISIBLE);
				useUsername.setOnCheckedChangeListener(this);
			}

			if (phone != null)
				phoneNumberEdit.setText(phone);
			if (dialcode != null)
				dialCode.setText("+"+dialcode);
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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			accountCreator.setLanguage(Locale.getDefault().toLanguageTag());
		}

		addPhoneNumberHandler(dialCode, null);
		addPhoneNumberHandler(phoneNumberEdit, null);

		return view;
	}

	public void linphoneLogIn() {
		if (login.getText() == null || login.length() == 0 || password.getText() == null || password.length() == 0) {
			LinphoneUtils.displayErrorAlert(getString(R.string.first_launch_no_login_password), AssistantActivity.instance());
			apply.setEnabled(true);
			return;
		}
		accountCreator.setUsername(login.getText().toString());
		accountCreator.setPassword(password.getText().toString());
		accountCreator.isAccountUsed();
	}

	private LinphoneAccountCreator.Status getPhoneNumberStatus() {
		return accountCreator.setPhoneNumber(phoneNumberEdit.getText().toString(), LinphoneUtils.getCountryCode(dialCode));
	}

	private void addPhoneNumberHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				if (field.equals(dialCode)) {
					DialPlan c = AssistantActivity.instance().getCountryListAdapter().getCountryFromCountryCode(dialCode.getText().toString());
					if (c != null) {
						AssistantActivity.instance().country = c;
						selectCountry.setText(c.getCountryName());
					} else {
						selectCountry.setText(R.string.select_your_country);
					}
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) {
				onTextChanged2();
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		if (useUsername != null && useUsername.isChecked())
			recoverAccount = false;
		else
			recoverAccount = true;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if(id == R.id.assistant_apply){
			apply.setEnabled(false);
			if (recoverAccount) {
				recoverAccount();
			} else {
				linphoneLogIn();
			}
		}
		else if(id == R.id.info_phone_number){
			new AlertDialog.Builder(getActivity())
					.setTitle(getString(R.string.phone_number_info_title))
					.setMessage(getString(R.string.phone_number_link_info_content))
					.show();
		}
		else if(id == R.id.select_country){
			AssistantActivity.instance().displayCountryChooser();
		}

	}

	private void recoverAccount() {
		if (phoneNumberEdit.length() > 0 || dialCode.length() > 1) {
			LinphoneAccountCreator.Status status = getPhoneNumberStatus();
			boolean isOk = status.equals(LinphoneAccountCreator.Status.Ok);
			if (isOk) {
				accountCreator.isPhoneNumberUsed();
			} else {
				apply.setEnabled(true);
				LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(status),
						AssistantActivity.instance());
				LinphoneUtils.displayError(isOk, phoneNumberError,
						LinphoneUtils.errorForStatus(status));
			}
		} else {
			apply.setEnabled(true);
			LinphoneUtils.displayErrorAlert(getString(R.string.assistant_create_account_part_1), AssistantActivity.instance());
		}
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

	public void onTextChanged2() {
		LinphoneAccountCreator.Status status = getPhoneNumberStatus();
		boolean isOk = status.equals(LinphoneAccountCreator.Status.Ok);
		LinphoneUtils.displayError(isOk, phoneNumberError, LinphoneUtils.errorForStatus(status));
		if (!isOk) {
			if (status.equals(LinphoneAccountCreator.Status.CountryCodeInvalid)) {
				dialCode.setBackgroundResource(R.drawable.resizable_textfield_error);
				phoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
			} else {
				dialCode.setBackgroundResource(R.drawable.resizable_textfield);
				phoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield_error);
			}
		} else {
			accountCreator.setPhoneNumber(phoneNumberEdit.getText().toString(), dialCode.getText().toString());
			dialCode.setBackgroundResource(R.drawable.resizable_textfield);
			phoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
		}
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		onTextChanged2();
	}

	@Override
	public void afterTextChanged(Editable s) {}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if(buttonView.getId() == R.id.use_username) {
			if(isChecked) {
				usernameLayout.setVisibility(View.VISIBLE);
				passwordLayout.setVisibility(View.VISIBLE);
				phoneNumberEdit.setVisibility(EditText.GONE);
				phoneNumberLayout.setVisibility(LinearLayout.GONE);
				messagePhoneNumber.setText(getString(R.string.assistant_linphone_login_desc));
				recoverAccount = false;
			} else {
				usernameLayout.setVisibility(View.GONE);
				passwordLayout.setVisibility(View.GONE);
				phoneNumberEdit.setVisibility(EditText.VISIBLE);
				phoneNumberLayout.setVisibility(LinearLayout.VISIBLE);
				messagePhoneNumber.setText(getString(R.string.assistant_create_account_part_1));
				recoverAccount = true;
			}
		}
	}

	@Override
	public void onAccountCreatorIsAccountUsed(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
		if (status.equals(LinphoneAccountCreator.Status.AccountExist) || status.equals(LinphoneAccountCreator.Status.AccountExistWithAlias)) {
			String phone = accountCreator.getPhoneNumber();
			String dial = null;
			if (phone != null && phone.length() > 0)
				dial = accountCreator.getPrefix(phone);
			AssistantActivity.instance().linphoneLogIn(login.getText().toString(), password.getText().toString(), dial, null, getResources().getBoolean(R.bool.assistant_account_validation_mandatory));
		} else {
			LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(status), AssistantActivity.instance());
		}
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
	}

	@Override
	public void onAccountCreatorPhoneAccountRecovered(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
		if (status.equals(LinphoneAccountCreator.Status.ErrorServer)) {
			LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(LinphoneAccountCreator.Status.Failed), AssistantActivity.instance());
			apply.setEnabled(true);
		} else {
			AssistantActivity.instance().displayAssistantCodeConfirm(accountCreator.getUsername(), phoneNumberEdit.getText().toString(), LinphoneUtils.getCountryCode(dialCode), true);
		}
	}

	@Override
	public void onAccountCreatorIsAccountLinked(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
	}

	@Override
	public void onAccountCreatorIsPhoneNumberUsed(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
		if (status.equals(LinphoneAccountCreator.Status.PhoneNumberUsedAccount) || status.equals(LinphoneAccountCreator.Status.PhoneNumberUsedAlias)) {
			accountCreator.recoverPhoneAccount();
		} else {
			apply.setEnabled(true);
			LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(status), AssistantActivity.instance());
		}
	}

	@Override
	public void onAccountCreatorPasswordUpdated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}
}
