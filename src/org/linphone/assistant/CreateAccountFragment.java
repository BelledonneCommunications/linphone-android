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

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.core.LinphoneAccountCreator;
import org.linphone.core.LinphoneAccountCreatorImpl;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Fragment;
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
import android.widget.Toast;

import static org.linphone.core.LinphoneAccountCreator.*;

/**
 * @author Sylvain Berfini
 */
public class CreateAccountFragment extends Fragment implements CompoundButton.OnCheckedChangeListener, OnClickListener, LinphoneAccountCreatorListener {
	private EditText phoneNumberEdit, usernameEdit, passwordEdit, passwordConfirmEdit, emailEdit, dialCode;
	private TextView phoneNumberError, usernameError, passwordError, passwordConfirmError, emailError, assisstantTitle, sipUri;
	private ImageView phoneNumberInfo;
	
	private boolean phoneNumberOk = false;
	private boolean usernameOk = false;
	private boolean passwordOk = false;
	private boolean emailOk = false;
	private boolean confirmPasswordOk = false;
	private boolean linkAccount = false;
	private Button createAccount, selectCountry;
	private CheckBox useUsername, useEmail;
	private int countryCode;
	private LinearLayout phoneNumberLayout, usernameLayout, emailLayout, passwordLayout, passwordConfirmLayout;
	private final Pattern UPPER_CASE_REGEX = Pattern.compile("[A-Z]");
	private CountryListFragment.Country country;
	private LinphoneAccountCreator accountCreator;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_account_creation, container, false);

		//Initialize accountCreator
		accountCreator = new LinphoneAccountCreatorImpl(LinphoneManager.getLc(), getResources().getString(R.string.wizard_url));
		accountCreator.setDomain(getResources().getString(R.string.default_domain));
		accountCreator.setListener(this);

		createAccount = (Button) view.findViewById(R.id.assistant_create);

		phoneNumberLayout = (LinearLayout) view.findViewById(R.id.phone_number_layout);
		usernameLayout = (LinearLayout) view.findViewById(R.id.username_layout);
		emailLayout = (LinearLayout) view.findViewById(R.id.email_layout);
		passwordLayout = (LinearLayout) view.findViewById(R.id.password_layout);
		passwordConfirmLayout = (LinearLayout) view.findViewById(R.id.password_confirm_layout);

		useUsername = (CheckBox) view.findViewById(R.id.use_username);
		useEmail = (CheckBox) view.findViewById(R.id.use_email);

		usernameError = (TextView) view.findViewById(R.id.username_error);
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

		//Phone number
		if(getResources().getBoolean(R.bool.use_phone_number_validation)){
			//Automatically get the country code from the phone
			TelephonyManager tm = (TelephonyManager) getActivity().getApplicationContext().getSystemService(getActivity().getApplicationContext().TELEPHONY_SERVICE);
			String countryIso = tm.getNetworkCountryIso();
			LinphoneProxyConfig proxyConfig = LinphoneManager.getLc().createProxyConfig();
			countryCode = proxyConfig.lookupCCCFromIso(countryIso.toUpperCase());

			phoneNumberLayout.setVisibility(View.VISIBLE);

			phoneNumberInfo.setOnClickListener(this);
			addPhoneNumberHandler(phoneNumberEdit, null);
			addPhoneNumberHandler(dialCode, null);
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

		//Password & email address
		if (getResources().getBoolean(R.bool.isTablet) || !getResources().getBoolean(R.bool.use_phone_number_validation)){
			useEmail.setVisibility(View.VISIBLE);
			useEmail.setOnCheckedChangeListener(this);

			addPasswordHandler(passwordEdit, null);
			addConfirmPasswordHandler(passwordEdit, passwordConfirmEdit, null);
			addEmailHandler(emailEdit, null);

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
		}

		//Hide phone number and display username/email/password
		if(!getResources().getBoolean(R.bool.use_phone_number_validation)){
			useEmail.setVisibility(View.GONE);
			useUsername.setVisibility(View.GONE);

			usernameLayout.setVisibility(View.VISIBLE);
			passwordLayout.setVisibility(View.VISIBLE);
			passwordConfirmLayout.setVisibility(View.VISIBLE);
			emailLayout.setVisibility(View.VISIBLE);
		}

		//Link account with phone number
		if(getArguments().getBoolean("LinkPhoneNumber")){
			linkAccount = true;
			useEmail.setVisibility(View.GONE);
			useUsername.setVisibility(View.GONE);

			usernameLayout.setVisibility(View.GONE);
			passwordLayout.setVisibility(View.GONE);
			passwordConfirmLayout.setVisibility(View.GONE);
			emailLayout.setVisibility(View.GONE);

			createAccount.setText(getResources().getString(R.string.assistant_link_account));
			assisstantTitle.setText(getResources().getString(R.string.assistant_link_account));
		}
		addUsernameHandler(usernameEdit, null);

    	createAccount.setEnabled(false);
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

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if(buttonView.getId() == R.id.use_username) {
			if(isChecked) {
				usernameLayout.setVisibility(View.VISIBLE);
				if(getResources().getBoolean(R.bool.isTablet)){
					passwordLayout.setVisibility(View.INVISIBLE);
				}
			} else {
				usernameLayout.setVisibility(View.GONE);
			}
		} else if(buttonView.getId() == R.id.use_email){
			if(isChecked) {
				emailLayout.setVisibility(View.VISIBLE);
				passwordLayout.setVisibility(View.VISIBLE);
				passwordConfirmLayout.setVisibility(View.VISIBLE);
				usernameLayout.setVisibility(View.VISIBLE);
				useUsername.setEnabled(false);
			} else {
				if(!useUsername.isChecked()) {
					usernameLayout.setVisibility(View.GONE);
				}
				emailLayout.setVisibility(View.GONE);
				passwordLayout.setVisibility(View.GONE);
				passwordConfirmLayout.setVisibility(View.GONE);
				usernameLayout.setVisibility(View.GONE);
				useUsername.setEnabled(true);
			}
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
			case R.id.select_country: {
				AssistantActivity.instance().displayCountryChooser();
				break;
			}

			case R.id.info_phone_number: {
				new AlertDialog.Builder(getActivity())
					.setTitle(getString(R.string.phone_number_info_title))
					.setMessage(getString(R.string.phone_number_info_content))
						.show();
				break;
			}

			case R.id.assistant_create: {
				if(linkAccount){
					addAlias();
				} else {
					createAccount();
				}
				break;
			}
		}
	}

	private void displayError(Boolean isOk, TextView error, EditText editText, String errorText){
		if(isOk || editText.getText().toString().equals("")){
			error.setVisibility(View.INVISIBLE);
			error.setText(errorText);
			editText.setBackgroundResource(R.drawable.resizable_textfield);
		} else {
			error.setVisibility(View.VISIBLE);
			error.setText(errorText);
			editText.setBackgroundResource(R.drawable.resizable_textfield_error);
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
		accountCreator.setUsername(LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()));
		String countryCode = dialCode.getText().toString();
		if(countryCode != null && countryCode.startsWith("+")) {
			countryCode = countryCode.substring(1);
		}
		Status status = accountCreator.setPhoneNumber(phoneNumberEdit.getText().toString(), countryCode);
		if(status.equals(Status.Ok)){
			accountCreator.linkPhoneNumberWithAccount();
		}
	}

	private void createAccount() {
		if(accountCreator.getUsername() == null && !useUsername.isChecked()){
			accountCreator.setUsername(accountCreator.getPhoneNumber());
		}
		accountCreator.createAccount();
	}

	private void addPhoneNumberHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			public void onTextChanged(CharSequence s, int start, int count, int after) {
				if (s.length() > 0) {
					phoneNumberOk = false;
					Status status = accountCreator.setPhoneNumber(phoneNumberEdit.getText().toString(), getCountryCode());

					if(status.equals(Status.Ok)){
						accountCreator.isAccountUsed();
						if(useUsername.isChecked()){
							accountCreator.setUsername(field.getText().toString());
						}
					} else {
						displayError(phoneNumberOk, phoneNumberError, phoneNumberEdit, errorForStatus(status));
						sipUri.setText("");
					}
				} else {
					displayError(phoneNumberOk, phoneNumberError, phoneNumberEdit, "");
				}
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
				if(s.length() > 0){
					usernameOk = false;
					Status status = accountCreator.setUsername(field.getText().toString());
					if(status.equals(Status.Ok)){
						accountCreator.isAccountUsed();
					} else {
						displayError(usernameOk, usernameError, usernameEdit, errorForStatus(status));
						sipUri.setText("");
					}
				} else {
					displayError(true, usernameError, usernameEdit, "");
				}
			}
		});
	}
	
	private void addEmailHandler(final EditText field, final ImageView icon) {
		field.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) 
			{
				emailOk = false;
				Status status = accountCreator.setEmail(field.getText().toString());
				if (status.equals(Status.Ok)) {
					emailOk = true;
					displayError(emailOk, emailError, emailEdit, "");
				}
				else {
					displayError(emailOk, emailError, emailEdit, errorForStatus(status));
				}
				createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
			}
		});
	}
	
	private void addPasswordHandler(final EditText field1, final ImageView icon) {
		TextWatcher passwordListener = new TextWatcher() {
			public void afterTextChanged(Editable s) {
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) 
			{
				passwordOk = false;
				Status status = accountCreator.setPassword(field1.getText().toString());
				if (isPasswordCorrect(field1.getText().toString())) {
					passwordOk = true;
					displayError(passwordOk, passwordError, passwordEdit, "");
				}
				else {
					displayError(passwordOk, passwordError, passwordEdit, errorForStatus(status));
				}
				createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);
			}
		};
		
		field1.addTextChangedListener(passwordListener);
	}
	
	private void addConfirmPasswordHandler(final EditText field1, final EditText field2, final ImageView icon) {
		TextWatcher passwordListener = new TextWatcher() {
			public void afterTextChanged(Editable s) {
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int count, int after) 
			{
				confirmPasswordOk = false;
				if (field1.getText().toString().equals(field2.getText().toString())) {
					confirmPasswordOk = true;
					if (!isPasswordCorrect(field1.getText().toString())) {
						displayError(passwordOk, passwordError, passwordEdit, getString(R.string.wizard_password_incorrect));
					} else {
						displayError(confirmPasswordOk, passwordConfirmError, passwordConfirmEdit, "");
					}
				} else {
					displayError(confirmPasswordOk, passwordConfirmError, passwordConfirmEdit, getString(R.string.wizard_passwords_unmatched));
				}
				createAccount.setEnabled(usernameOk && passwordOk && confirmPasswordOk && emailOk);


			}
		};
		field1.addTextChangedListener(passwordListener);
		field2.addTextChangedListener(passwordListener);
	}

	String errorForStatus(Status status) {
		if (status.equals(Status.EmailInvalid))
				return getString(R.string.invalid_email);
		if (status.equals(Status.UsernameInvalid)){
				return getString(R.string.invalid_username);
		}
		if (status.equals(Status.UsernameTooShort)){
				return getString(R.string.username_too_short);
		}
		if (status.equals(Status.UsernameTooLong)){
			return getString(R.string.username_too_long);
		}
		if (status.equals(Status.UsernameInvalidSize))
				return getString(R.string.username_invalid_size);
		if (status.equals(Status.PhoneNumberTooShort))
				return getString(R.string.phone_number_too_short);
		if (status.equals(Status.PhoneNumberTooLong))
				return getString(R.string.phone_number_too_long);
		if (status.equals(Status.PhoneNumberInvalid))
				return getString(R.string.phone_number_invalid);
		if (status.equals(Status.PasswordTooShort))
				return getString(R.string.username_too_short);
		if (status.equals(Status.PasswordTooLong))
				return getString(R.string.username_too_long);
		if (status.equals(Status.DomainInvalid))
				return getString(R.string.invalid_domain);
		if (status.equals(Status.RouteInvalid))
				return getString(R.string.invalid_route);
		if (status.equals(Status.DisplayNameInvalid))
				return getString(R.string.invalid_route);
		if (status.equals(Status.Failed))
				return getString(R.string.request_failed);
		if (status.equals(Status.TransportNotSupported))
				return getString(R.string.transport_unsupported);
		if (status.equals(Status.AccountExist))
			return getString(R.string.account_already_exist);
		if (status.equals(Status.AccountExistWithAlias))
			return getString(R.string.account_already_exist);
		if (status.equals(Status.AccountCreated)
		 		|| status.equals(Status.AccountNotCreated)
				|| status.equals(Status.AccountNotExist)
				|| status.equals(Status.AccountNotActivated)
				|| status.equals(Status.AccountAlreadyActivated)
				|| status.equals(Status.AccountActivated)
				|| status.equals(Status.Ok)){
			return "";
		}
		return null;
	}

	@Override
	public void onAccountCreatorIsAccountUsed(LinphoneAccountCreator accountCreator, final Status status) {
		if(getResources().getBoolean(R.bool.isTablet) || useEmail.isChecked()){
			if (status.equals(Status.AccountExist) || status.equals(Status.AccountExistWithAlias)) {
				usernameOk = false;
				displayError(usernameOk, usernameError, usernameEdit, errorForStatus(status));
				sipUri.setText("");
			} else {
				usernameOk = true;
				displayError(usernameOk, usernameError, usernameEdit, errorForStatus(status));
				sipUri.setText("Sip uri is sip:" + accountCreator.getUsername() + "@" + getResources().getString(R.string.default_domain));
			}
			createAccount.setEnabled(usernameOk && emailOk);
		}
		if(getResources().getBoolean(R.bool.assistant_allow_username) && useUsername.isChecked()){
			if (status.equals(Status.AccountExist) || status.equals(Status.AccountExistWithAlias)) {
				usernameOk = false;
				displayError(usernameOk, usernameError, usernameEdit, errorForStatus(status));
				sipUri.setText("");
			} else {
				usernameOk = true;
				displayError(usernameOk, usernameError, usernameEdit, errorForStatus(status));
				sipUri.setText("Sip uri is sip:" + accountCreator.getUsername() + "@" + getResources().getString(R.string.default_domain));
			}
			createAccount.setEnabled(usernameOk);
		} else {
			if (status.equals(Status.AccountExist) || status.equals(Status.AccountExistWithAlias)) {
				phoneNumberOk = false;
				displayError(phoneNumberOk, phoneNumberError, phoneNumberEdit, errorForStatus(status));
				sipUri.setText("");
			} else {
				phoneNumberOk = true;
				displayError(phoneNumberOk, phoneNumberError, phoneNumberEdit, errorForStatus(status));
				sipUri.setText("Sip uri is sip:" + accountCreator.getPhoneNumber() + "@" + getResources().getString(R.string.default_domain));
			}
			createAccount.setEnabled(phoneNumberOk);
		}
	}

	@Override
	public void onAccountCreatorAccountCreated(LinphoneAccountCreator accountCreator, Status status) {
		if(status.equals(Status.AccountCreated)) {
			if(useEmail.isChecked()){
				AssistantActivity.instance().displayAssistantConfirm(getUsername(), passwordEdit.getText().toString());
			} else {
				AssistantActivity.instance().displayAssistantCodeConfirm(getUsername(), phoneNumberEdit.getText().toString(), getCountryCode(), false);
			}
		} else {
			Toast.makeText(getActivity().getApplicationContext(), errorForStatus(status), Toast.LENGTH_SHORT);
		}
	}

	@Override
	public void onAccountCreatorAccountActivated(LinphoneAccountCreator accountCreator, Status status) {
	}

	@Override
	public void onAccountCreatorAccountLinkedWithPhoneNumber(LinphoneAccountCreator accountCreator, Status status) {
		if(status.equals(Status.Ok)){
			AssistantActivity.instance().displayAssistantCodeConfirm(getUsername(), phoneNumberEdit.getText().toString(), getCountryCode(), false);
		}
	}

	@Override
	public void onAccountCreatorPhoneNumberLinkActivated(LinphoneAccountCreator accountCreator, Status status) {
		if(status.equals(Status.Ok)){
			AssistantActivity.instance().displayAssistantCodeConfirm(getUsername(), phoneNumberEdit.getText().toString(), getCountryCode(), false);
		}
	}

	@Override
	public void onAccountCreatorIsAccountActivated(LinphoneAccountCreator accountCreator, Status status) {
	}

	@Override
	public void onAccountCreatorPhoneAccountRecovered(LinphoneAccountCreator accountCreator, Status status) {

	}
}
