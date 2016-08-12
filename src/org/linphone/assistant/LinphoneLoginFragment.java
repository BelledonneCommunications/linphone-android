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
	private EditText login, password, displayName;
	private Button apply;
	private CheckBox useUsername, usePassword;
	private LinearLayout phoneNumberLayout, usernameLayout, passwordLayout;
	private TextView forgotPassword;
	private EditText phoneNumberEdit, usernameEdit, passwordEdit, passwordConfirmEdit, emailEdit, dialCode;
	private TextView phoneNumberError, usernameError, passwordError, passwordConfirmError, emailError, sipUri;
	private CountryListFragment.Country country;
	private Boolean recoverAccount = true;

	private LinphoneAccountCreator accountCreator;


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

		if(getResources().getBoolean(R.bool.assistant_allow_username)) {
			useUsername = (CheckBox) view.findViewById(R.id.use_username);
			useUsername.setVisibility(View.VISIBLE);
			useUsername.setOnCheckedChangeListener(this);

			usernameLayout = (LinearLayout) view.findViewById(R.id.username_layout);
		}

		password = (EditText) view.findViewById(R.id.assistant_password);
		password.addTextChangedListener(this);
		forgotPassword = (TextView) view.findViewById(R.id.forgot_password);
		forgotPassword.setText(Compatibility.fromHtml("<a href=\"" + url + "\"'>"+ getString(R.string.forgot_password) + "</a>"));
		forgotPassword.setMovementMethod(LinkMovementMethod.getInstance());
		displayName = (EditText) view.findViewById(R.id.assistant_display_name);

		apply = (Button) view.findViewById(R.id.assistant_apply);
		apply.setEnabled(false);
		apply.setOnClickListener(this);

		return view;
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
		
		AssistantActivity.instance().linphoneLogIn(login.getText().toString(), password.getText().toString(), null, displayName.getText().toString(), getResources().getBoolean(R.bool.assistant_account_validation_mandatory));
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
					Log.w("Set PhoneNmuber " + status.toString());
					if (status.equals(LinphoneAccountCreator.Status.Ok)) {
						status = accountCreator.isAccountUsed();
						Log.w("Is account activated " + status.toString());
						if (status.equals(LinphoneAccountCreator.Status.Ok)) {

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

	}

	@Override
	public void onAccountCreatorIsAccountUsed(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
		recoverAccount = true;
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
		Log.w("IS ACTIVATED " + status.toString());
		apply.setEnabled(true);
	}

	@Override
	public void onAccountCreatorPhoneAccountRecovered(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
		AssistantActivity.instance().displayAssistantCodeConfirm(getPhoneNumber(), getPhoneNumber(), true);

	}
}
