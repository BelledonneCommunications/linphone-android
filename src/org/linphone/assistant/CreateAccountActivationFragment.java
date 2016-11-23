package org.linphone.assistant;
/*
CreateAccountActivationFragment.java
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
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.core.LinphoneAccountCreator;
import org.linphone.core.LinphoneAccountCreator.LinphoneAccountCreatorListener;
import org.linphone.core.LinphoneCoreFactory;
/**
 * @author Sylvain Berfini
 */
public class CreateAccountActivationFragment extends Fragment implements OnClickListener, LinphoneAccountCreatorListener {
	private String username, password;
	private Button checkAccount;
	private TextView email;
	private LinphoneAccountCreator accountCreator;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_account_creation_email_activation, container, false);

		accountCreator = LinphoneCoreFactory.instance().createAccountCreator(LinphoneManager.getLc()
				, LinphonePreferences.instance().getXmlrpcUrl());
		accountCreator.setDomain(getResources().getString(R.string.default_domain));
		accountCreator.setListener(this);

		username = getArguments().getString("Username");
		password = getArguments().getString("Password");

		accountCreator.setUsername(username);
		accountCreator.setPassword(password);

		email = (TextView) view.findViewById(R.id.send_email);
		email.setText(getArguments().getString("Email"));

		checkAccount = (Button) view.findViewById(R.id.assistant_check);
		checkAccount.setOnClickListener(this);
		return view;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if(id == R.id.assistant_check) {
			checkAccount.setEnabled(false);
			accountCreator.isAccountActivated();
		}
	}

	@Override
	public void onAccountCreatorIsAccountUsed(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
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
		if (status.equals(LinphoneAccountCreator.Status.AccountNotActivated)) {
			Toast.makeText(getActivity(), getString(R.string.assistant_account_not_validated), Toast.LENGTH_LONG).show();
		} else if (status.equals(LinphoneAccountCreator.Status.AccountActivated)) {
			AssistantActivity.instance().saveCreatedAccount(username, password, null, null, getString(R.string.default_domain), null);
			AssistantActivity.instance().isAccountVerified(username);
		} else {
			Toast.makeText(getActivity(), getString(R.string.wizard_server_unavailable), Toast.LENGTH_LONG).show();
		}
		checkAccount.setEnabled(true);
	}

	@Override
	public void onAccountCreatorPhoneAccountRecovered(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
	}

	@Override
	public void onAccountCreatorIsAccountLinked(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
	}

	@Override
	public void onAccountCreatorIsPhoneNumberUsed(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
	}

	@Override
	public void onAccountCreatorPasswordUpdated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}
}
