package org.linphone.assistant;
/*
CreateAccountFragment.java
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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.core.DialPlan;
import org.linphone.core.LinphoneAccountCreator;
import org.linphone.core.LinphoneAccountCreator.LinphoneAccountCreatorListener;
import org.linphone.core.LinphoneAccountCreator.Status;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
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

/**
 * @author Sylvain Berfini
 */
public class CreateAccountFragment extends Fragment implements CompoundButton.OnCheckedChangeListener
		, OnClickListener, LinphoneAccountCreatorListener {
	private EditText phoneNumberEdit, usernameEdit, passwordEdit, passwordConfirmEdit
			, emailEdit, dialCode;
	private TextView phoneNumberError, passwordError, passwordConfirmError
			, emailError, assisstantTitle, sipUri, skip, instruction;
	private ImageView phoneNumberInfo;

	private boolean passwordOk = false;
	private boolean emailOk = false;
	private boolean confirmPasswordOk = false;
	private boolean linkAccount = false;
	private Button createAccount, selectCountry;
	private CheckBox useUsername, useEmail;
	private String addressSip = "";
	private int countryCode;
	private LinearLayout phoneNumberLayout, usernameLayout, emailLayout, passwordLayout, passwordConfirmLayout;
	private final Pattern UPPER_CASE_REGEX = Pattern.compile("[A-Z]");
	private LinphoneAccountCreator accountCreator;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_account_creation, container, false);

		//Initialize accountCreator
		accountCreator = LinphoneCoreFactory.instance().createAccountCreator(LinphoneManager.getLc()
				, LinphonePreferences.instance().getXmlrpcUrl());
		accountCreator.setDomain(getResources().getString(R.string.default_domain));
		accountCreator.setListener(this);

		instruction = (TextView) view.findViewById(R.id.message_create_account);

		createAccount = (Button) view.findViewById(R.id.assistant_create);

		phoneNumberLayout = (LinearLayout) view.findViewById(R.id.phone_number_layout);
		usernameLayout = (LinearLayout) view.findViewById(R.id.username_layout);
		emailLayout = (LinearLayout) view.findViewById(R.id.email_layout);
		passwordLayout = (LinearLayout) view.findViewById(R.id.password_layout);
		passwordConfirmLayout = (LinearLayout) view.findViewById(R.id.password_confirm_layout);

		useUsername = (CheckBox) view.findViewById(R.id.use_username);
		useEmail = (CheckBox) view.findViewById(R.id.use_email);

		usernameEdit = (EditText) view.findViewById(R.id.username);

		phoneNumberError = (TextView) view.findViewById(R.id.phone_number_error);
		phoneNumberEdit = (EditText) view.findViewById(R.id.phone_number);
		sipUri = (TextView) view.findViewById(R.id.sip_uri);

		phoneNumberInfo = (ImageView) view.findViewById(R.id.info_phone_number);

		selectCountry = (Button) view.findViewById(R.id.select_country);
		dialCode = (EditText) view.findViewById(R.id.dial_code);
		assisstantTitle = (TextView) view.findViewById(R.id.assistant_title);

		passwordError = (TextView) view.findViewById(R.id.password_error);
		passwordEdit = (EditText) view.findViewById(R.id.password);

		passwordConfirmError = (TextView) view.findViewById(R.id.confirm_password_error);
		passwordConfirmEdit = (EditText) view.findViewById(R.id.confirm_password);

		emailError = (TextView) view.findViewById(R.id.email_error);
		emailEdit = (EditText) view.findViewById(R.id.email);

		skip = (TextView) view.findViewById(R.id.assistant_skip);

		//Phone number
		if (getResources().getBoolean(R.bool.use_phone_number_validation)) {
			getActivity().getApplicationContext();
			//Automatically get the country code from the phone
			TelephonyManager tm =
					(TelephonyManager) getActivity().getApplicationContext().getSystemService(
							Context.TELEPHONY_SERVICE);
			String countryIso = tm.getNetworkCountryIso();
			LinphoneProxyConfig proxyConfig = LinphoneManager.getLc().createProxyConfig();
			countryCode = proxyConfig.lookupCCCFromIso(countryIso.toUpperCase());

			phoneNumberLayout.setVisibility(View.VISIBLE);

			phoneNumberInfo.setOnClickListener(this);
			selectCountry.setOnClickListener(this);

			String previousPhone = AssistantActivity.instance().phone_number;
			if (previousPhone != null ) {
				phoneNumberEdit.setText(previousPhone);
			}
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

			//Allow user to enter a username instead use the phone number as username
			if (getResources().getBoolean(R.bool.assistant_allow_username)) {
				useUsername.setVisibility(View.VISIBLE);
				useUsername.setOnCheckedChangeListener(this);
			}
			addPhoneNumberHandler(phoneNumberEdit, null);
			addPhoneNumberHandler(dialCode, null);
		}

		//Password & email address
		if (getResources().getBoolean(R.bool.isTablet)
				|| !getResources().getBoolean(R.bool.use_phone_number_validation)) {
			useEmail.setVisibility(View.VISIBLE);
			useEmail.setOnCheckedChangeListener(this);

			if (getResources().getBoolean(R.bool.pre_fill_email_in_assistant)) {
				Account[] accounts = AccountManager.get(getActivity()).getAccountsByType("com.google");

				for (Account account: accounts) {
					if (isEmailCorrect(account.name)) {
						String possibleEmail = account.name;
						emailEdit.setText(possibleEmail);
						accountCreator.setEmail(possibleEmail);
						emailOk = true;
						break;
					}
				}
			}

			addPasswordHandler(passwordEdit, null);
			addConfirmPasswordHandler(passwordEdit, passwordConfirmEdit, null);
			addEmailHandler(emailEdit, null);
		}

		//Hide phone number and display username/email/password
		if (!getResources().getBoolean(R.bool.use_phone_number_validation)) {
			useEmail.setVisibility(View.GONE);
			useUsername.setVisibility(View.GONE);

			usernameLayout.setVisibility(View.VISIBLE);
			passwordLayout.setVisibility(View.VISIBLE);
			passwordConfirmLayout.setVisibility(View.VISIBLE);
			emailLayout.setVisibility(View.VISIBLE);
		}

		//Link account with phone number
		if (getArguments().getBoolean("LinkPhoneNumber")) {
			linkAccount = true;
			useEmail.setVisibility(View.GONE);
			useUsername.setVisibility(View.GONE);

			usernameLayout.setVisibility(View.GONE);
			passwordLayout.setVisibility(View.GONE);
			passwordConfirmLayout.setVisibility(View.GONE);
			emailLayout.setVisibility(View.GONE);

			skip.setVisibility(View.VISIBLE);
			skip.setOnClickListener(this);

			createAccount.setText(getResources().getString(R.string.link_account));
			assisstantTitle.setText(getResources().getString(R.string.link_account));
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			accountCreator.setLanguage(Locale.getDefault().toLanguageTag());
		}

		addUsernameHandler(usernameEdit, null);

		createAccount.setEnabled(true);
		createAccount.setOnClickListener(this);

		return view;
	}

	@Override
	public void onPause() {
		super.onPause();
		accountCreator.setListener(null);
	}

	private String getUsername() {
		if(usernameEdit != null) {
			String username = usernameEdit.getText().toString();
			return username.toLowerCase(Locale.getDefault());
		}
		return null;
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (buttonView.getId() == R.id.use_username) {
			if (isChecked) {
				usernameLayout.setVisibility(View.VISIBLE);
				onTextChanged2();
			} else {
				usernameLayout.setVisibility(View.GONE);
				accountCreator.setUsername(null);
				onTextChanged2();
			}
		} else if (buttonView.getId() == R.id.use_email) {
			if (isChecked) {
				dialCode.setBackgroundResource(R.drawable.resizable_textfield);
				phoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
				useUsername.setEnabled(false);
				dialCode.setEnabled(false);
				selectCountry.setEnabled(false);
				phoneNumberEdit.setEnabled(false);
				emailLayout.setVisibility(View.VISIBLE);
				passwordLayout.setVisibility(View.VISIBLE);
				passwordConfirmLayout.setVisibility(View.VISIBLE);
				usernameLayout.setVisibility(View.VISIBLE);
				useUsername.setVisibility(CheckBox.GONE);
				phoneNumberLayout.setVisibility(LinearLayout.GONE);
				instruction.setText(getString(R.string.assistant_create_account_part_email));
			} else {
				if (!useUsername.isChecked()) {
					usernameLayout.setVisibility(View.GONE);
				}
				useUsername.setEnabled(true);
				dialCode.setEnabled(true);
				selectCountry.setEnabled(true);
				phoneNumberEdit.setEnabled(true);
				emailLayout.setVisibility(View.GONE);
				passwordLayout.setVisibility(View.GONE);
				passwordConfirmLayout.setVisibility(View.GONE);
				useUsername.setVisibility(CheckBox.VISIBLE);
				phoneNumberLayout.setVisibility(LinearLayout.VISIBLE);
				instruction.setText(getString(R.string.assistant_create_account_part_1));
			}
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if(id == R.id.select_country){
			AssistantActivity.instance().displayCountryChooser();
		}
		else if (id == R.id.assistant_skip){
			if (getArguments().getBoolean("LinkFromPref")) {
				startActivity(new Intent().setClass(AssistantActivity.instance(), LinphoneActivity.class));
				AssistantActivity.instance().finish();
			} else {
				AssistantActivity.instance().success();
			}
		}
		else if(id == R.id.info_phone_number){
			if (linkAccount) {
				new AlertDialog.Builder(getActivity())
						.setTitle(getString(R.string.phone_number_info_title))
						.setMessage(getString(R.string.phone_number_link_info_content) + "\n"
								+ getString(R.string.phone_number_link_info_content_already_account))
						.show();
			} else {
				new AlertDialog.Builder(getActivity())
						.setTitle(getString(R.string.phone_number_info_title))
						.setMessage(getString(R.string.phone_number_info_content))
						.show();
			}
		}
		else if(id == R.id.assistant_create){
			createAccount.setEnabled(false);
			if (linkAccount) {
				addAlias();
			} else {
				if (!getResources().getBoolean(R.bool.isTablet) || getUsername().length() > 0) {
					accountCreator.isAccountUsed();
				} else {
					LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(Status.UsernameTooShort)
							, AssistantActivity.instance());
					createAccount.setEnabled(true);
				}
			}
		}
	}

	private boolean isEmailCorrect(String email) {
		Pattern emailPattern = Patterns.EMAIL_ADDRESS;
		return emailPattern.matcher(email).matches();
	}

	private boolean isPasswordCorrect(String password) {
		return password.length() >= 1;
	}

	private void addAlias() {
		accountCreator.setUsername(LinphonePreferences.instance().getAccountUsername(
				LinphonePreferences.instance().getDefaultAccountIndex())
		);
		Status status = accountCreator.setPhoneNumber(
				phoneNumberEdit.getText().toString(), LinphoneUtils.getCountryCode(dialCode));
		boolean isOk = status.equals(LinphoneAccountCreator.Status.Ok);
		if (isOk) {
			accountCreator.linkPhoneNumberWithAccount();
		} else {
			createAccount.setEnabled(true);
			LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(status), AssistantActivity.instance());
			LinphoneUtils.displayError(isOk, phoneNumberError, LinphoneUtils.errorForStatus(status));
		}
	}

	private void createAccount() {
		if ((getResources().getBoolean(R.bool.isTablet) || !getResources().getBoolean(R.bool.use_phone_number_validation))
				&& useEmail.isChecked()) {
			Status emailStatus;
			Status passwordStatus;

			passwordStatus = accountCreator.setPassword(passwordEdit.getText().toString());
			emailStatus = accountCreator.setEmail(emailEdit.getText().toString());

			if (!emailOk) {
				LinphoneUtils.displayError(false, emailError, LinphoneUtils.errorForStatus(emailStatus));
				LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(emailStatus)
						, AssistantActivity.instance());
			} else if (!passwordOk) {
				LinphoneUtils.displayError(false, passwordError, LinphoneUtils.errorForStatus(passwordStatus));
				LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(passwordStatus)
						, AssistantActivity.instance());
			} else if (!confirmPasswordOk) {
				String msg;
				if (passwordConfirmEdit.getText().toString().equals(passwordEdit.getText().toString())) {
					msg = getString(R.string.wizard_password_incorrect);
				} else {
					msg = getString(R.string.wizard_passwords_unmatched);
				}
				LinphoneUtils.displayError(false, passwordError, msg);
				LinphoneUtils.displayErrorAlert(msg, AssistantActivity.instance());
			} else {
				accountCreator.createAccount();
			}
		} else {
			if (phoneNumberEdit.length() > 0 || dialCode.length() > 1) {
				Status phoneStatus;
				boolean isOk;
				phoneStatus = accountCreator.setPhoneNumber(
						phoneNumberEdit.getText().toString(), LinphoneUtils.getCountryCode(dialCode));
				isOk = phoneStatus.equals(LinphoneAccountCreator.Status.Ok);
				if (!useUsername.isChecked() && accountCreator.getUsername() == null) {
					accountCreator.setUsername(accountCreator.getPhoneNumber());
				} else {
					accountCreator.setUsername(usernameEdit.getText().toString());
					accountCreator.setPhoneNumber(
							phoneNumberEdit.getText().toString(), dialCode.getText().toString());
				}
				if (isOk) {
					accountCreator.createAccount();
				} else {
					LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(phoneStatus)
							, AssistantActivity.instance());
					LinphoneUtils.displayError(isOk, phoneNumberError
							, LinphoneUtils.errorForStatus(phoneStatus));
				}
			} else {
				LinphoneUtils.displayErrorAlert(getString(R.string.assistant_create_account_part_1)
						, AssistantActivity.instance());
			}
		}
		createAccount.setEnabled(true);
	}

	private LinphoneAccountCreator.Status getPhoneNumberStatus() {
		LinphoneAccountCreator.Status status = accountCreator.setPhoneNumber(
				phoneNumberEdit.getText().toString(), LinphoneUtils.getCountryCode(dialCode));
		addressSip = accountCreator.getPhoneNumber();
		return status;
	}

	public void onTextChanged2() {
		String msg = "";
		accountCreator.setUsername(getUsername());

		if (!useEmail.isChecked() && getResources().getBoolean(R.bool.use_phone_number_validation)) {
			LinphoneAccountCreator.Status status = getPhoneNumberStatus();
			boolean isOk = status.equals(LinphoneAccountCreator.Status.Ok);
			LinphoneUtils.displayError(isOk, phoneNumberError, LinphoneUtils.errorForStatus(status));

			// Username or phone number
			if (getResources().getBoolean(R.bool.assistant_allow_username) && useUsername.isChecked()) {
				addressSip = getUsername();
			}

			if (!isOk) {
				if (status.equals(LinphoneAccountCreator.Status.CountryCodeInvalid)) {
					dialCode.setBackgroundResource(R.drawable.resizable_textfield_error);
					phoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
				} else {
					dialCode.setBackgroundResource(R.drawable.resizable_textfield);
					phoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield_error);
				}

			} else {
				dialCode.setBackgroundResource(R.drawable.resizable_textfield);
				phoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
				if (!linkAccount && addressSip.length() > 0) {
					msg = getResources().getString(R.string.assistant_create_account_phone_number_address)
							+ " <" + addressSip + "@" + getResources().getString(R.string.default_domain) + ">";
				}
			}
		} else {
			addressSip = getUsername();
			if (addressSip.length() > 0) {
				msg = getResources().getString(R.string.assistant_create_account_phone_number_address)
						+ " <sip:" + addressSip + "@" + getResources().getString(R.string.default_domain) + ">";
			}
		}
		sipUri.setText(msg);
	}

	private void addPhoneNumberHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				if (field.equals(dialCode)) {
					DialPlan c = AssistantActivity.instance().getCountryListAdapter()
							.getCountryFromCountryCode(dialCode.getText().toString());
					if (c != null) {
						AssistantActivity.instance().country = c;
						selectCountry.setText(c.getCountryName());
					} else {
						selectCountry.setText(R.string.select_your_country);
					}
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			public void onTextChanged(CharSequence s, int start, int count, int after) {
				onTextChanged2();
			}
		});
	}

	private void addUsernameHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				Matcher matcher = UPPER_CASE_REGEX.matcher(s);
				while (matcher.find()) {
					CharSequence upperCaseRegion = s.subSequence(matcher.start(), matcher.end());
					s.replace(matcher.start(), matcher.end(), upperCaseRegion.toString().toLowerCase());
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) {
				onTextChanged2();
			}
		});
	}

	private void addEmailHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				emailOk = false;
				Status status = accountCreator.setEmail(field.getText().toString());
				if (status.equals(Status.Ok)) {
					emailOk = true;
					LinphoneUtils.displayError(emailOk, emailError, "");
				}
				else {
					LinphoneUtils.displayError(emailOk
							, emailError, LinphoneUtils.errorForStatus(status));
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) {
			}
		});
	}

	private void addPasswordHandler(final EditText field1, final ImageView icon) {
		TextWatcher passwordListener = new TextWatcher() {
			public void afterTextChanged(Editable s) {
				passwordOk = false;
				Status status = accountCreator.setPassword(field1.getText().toString());
				if (isPasswordCorrect(field1.getText().toString())) {
					passwordOk = true;
					LinphoneUtils.displayError(passwordOk, passwordError, "");
				}
				else {
					LinphoneUtils.displayError(passwordOk
							, passwordError, LinphoneUtils.errorForStatus(status));
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) {
			}
		};

		field1.addTextChangedListener(passwordListener);
	}

	private void addConfirmPasswordHandler(final EditText field1, final EditText field2, final ImageView icon) {
		TextWatcher passwordListener = new TextWatcher() {
			public void afterTextChanged(Editable s) {
				confirmPasswordOk = false;
				if (field1.getText().toString().equals(field2.getText().toString())) {
					confirmPasswordOk = true;
					if (!isPasswordCorrect(field1.getText().toString())) {
						LinphoneUtils.displayError(passwordOk
								, passwordError, getString(R.string.wizard_password_incorrect));
					} else {
						LinphoneUtils.displayError(confirmPasswordOk, passwordConfirmError, "");
					}
				} else {
					LinphoneUtils.displayError(confirmPasswordOk
							, passwordConfirmError, getString(R.string.wizard_passwords_unmatched));
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) {}
		};
		field1.addTextChangedListener(passwordListener);
		field2.addTextChangedListener(passwordListener);
	}

	@Override
	public void onAccountCreatorIsAccountUsed(LinphoneAccountCreator accountCreator, final Status status) {
		if (status.equals(Status.AccountExist) || status.equals(Status.AccountExistWithAlias)) {
			if (useEmail.isChecked()) {
				createAccount.setEnabled(true);
				LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(status)
						, AssistantActivity.instance());
			} else {
				accountCreator.isPhoneNumberUsed();
			}
		} else {
			createAccount();
		}
	}

	@Override
	public void onAccountCreatorAccountCreated(LinphoneAccountCreator accountCreator, Status status) {
		if (status.equals(Status.AccountCreated)) {
			if (useEmail.isChecked() || !getResources().getBoolean(R.bool.use_phone_number_validation)) {
				AssistantActivity.instance().displayAssistantConfirm(getUsername()
						, passwordEdit.getText().toString(), emailEdit.getText().toString());
			} else {
				AssistantActivity.instance().displayAssistantCodeConfirm(getUsername()
						, phoneNumberEdit.getText().toString()
						, LinphoneUtils.getCountryCode(dialCode), false);
			}
		} else {
			createAccount.setEnabled(true);
			LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(status)
					, AssistantActivity.instance());
		}
	}

	@Override
	public void onAccountCreatorAccountActivated(LinphoneAccountCreator accountCreator, Status status) {
	}

	@Override
	public void onAccountCreatorAccountLinkedWithPhoneNumber(LinphoneAccountCreator accountCreator, Status status) {
		if (status.equals(Status.Ok)) {
			AssistantActivity.instance().displayAssistantCodeConfirm(getUsername()
					, phoneNumberEdit.getText().toString()
					, LinphoneUtils.getCountryCode(dialCode), false);
		}
	}

	@Override
	public void onAccountCreatorPhoneNumberLinkActivated(LinphoneAccountCreator accountCreator, Status status) {
		if (status.equals(Status.Ok)) {
			AssistantActivity.instance().displayAssistantCodeConfirm(getUsername()
					, phoneNumberEdit.getText().toString()
					, LinphoneUtils.getCountryCode(dialCode), false);
		}
	}

	@Override
	public void onAccountCreatorIsAccountActivated(LinphoneAccountCreator accountCreator, Status status) {
		if (status.equals(Status.AccountNotActivated)) {
			if (getResources().getBoolean(R.bool.isTablet)
					|| !getResources().getBoolean(R.bool.use_phone_number_validation)) {
				//accountCreator.activateAccount(); // Resend email TODO
			} else {
				accountCreator.recoverPhoneAccount(); // Resend SMS
			}
		} else {
			createAccount.setEnabled(true);
			LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(status)
					, AssistantActivity.instance());
		}
	}

	@Override
	public void onAccountCreatorPhoneAccountRecovered(LinphoneAccountCreator accountCreator, Status status) {
		if (status.equals(Status.Ok)) {
			AssistantActivity.instance().displayAssistantCodeConfirm(getUsername()
					, dialCode.getText().toString() , phoneNumberEdit.getText().toString(), false);
		} else {
			createAccount.setEnabled(true);
			//SMS error
			LinphoneUtils.displayErrorAlert(getString(R.string.request_failed)
					, AssistantActivity.instance());
		}
	}

	@Override
	public void onAccountCreatorIsAccountLinked(LinphoneAccountCreator accountCreator, Status status) {
	}

	@Override
	public void onAccountCreatorIsPhoneNumberUsed(LinphoneAccountCreator ac, Status status) {
		if (status.equals(Status.PhoneNumberUsedAccount) || status.equals(Status.PhoneNumberUsedAlias)) {
			if (accountCreator.getPhoneNumber() != null && accountCreator.getUsername() != null
					&& accountCreator.getPhoneNumber().compareTo(accountCreator.getUsername()) == 0) {
				accountCreator.isAccountActivated();
			} else {
				createAccount.setEnabled(true);
				LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(status)
						, AssistantActivity.instance());
			}
		} else {
			accountCreator.isAccountActivated();
		}
	}

	@Override
	public void onAccountCreatorPasswordUpdated(LinphoneAccountCreator accountCreator, Status status) {

	}
}
