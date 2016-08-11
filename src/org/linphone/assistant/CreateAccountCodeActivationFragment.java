package org.linphone.assistant;
/*
CreateAccountCodeActivationFragment.java
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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.core.LinphoneXmlRpcRequest;
import org.linphone.core.LinphoneXmlRpcRequest.LinphoneXmlRpcRequestListener;
import org.linphone.core.LinphoneXmlRpcRequestImpl;
import org.linphone.core.LinphoneXmlRpcSession;
import org.linphone.core.LinphoneXmlRpcSession;
import org.linphone.core.LinphoneXmlRpcSessionImpl;
import org.linphone.mediastream.Log;

public class CreateAccountCodeActivationFragment extends Fragment {
	private String username, phone, ha1;
	private EditText code;
	private int code_length;
	private Handler mHandler = new Handler();
	private Button checkAccount;
	private LinphoneXmlRpcSession xmlRpcSession;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_account_creation_code_activation, container, false);
		
		username = getArguments().getString("Username");
		phone = getArguments().getString("Phone");
		code_length = LinphonePreferences.instance().getCodeLength();

		if(username == null || username.length() == 0){
			username = phone;
		}

		code = (EditText) view.findViewById(R.id.assistant_code);
		code.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				if(s.length() == code_length){
					checkAccount.setEnabled(true);
				} else {
					checkAccount.setEnabled(false);
				}
			}
		});

		checkAccount = (Button) view.findViewById(R.id.assistant_check);
		checkAccount.setEnabled(false);
		checkAccount.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				checkAccount.setEnabled(false);
				activateAccount(phone, username, code.getText().toString(), false);
			}
		});

		xmlRpcSession = new LinphoneXmlRpcSessionImpl(LinphoneManager.getLcIfManagerNotDestroyedOrNull(), getString(R.string.wizard_url));

		return view;
	}

	private void activateAccount(final String phone, final String username, final String code, boolean suscribe) {
		final Runnable runNotOk = new Runnable() {
			public void run() {
				Toast.makeText(getActivity(), getString(R.string.assistant_account_not_validated), Toast.LENGTH_LONG).show();
			}
		};
		final Runnable runOk = new Runnable() {
			public void run() {
				checkAccount.setEnabled(true);
				AssistantActivity.instance().saveCreatedAccount(username, null, null, ha1, getString(R.string.default_domain), null);
				AssistantActivity.instance().isAccountVerified(username);
			}
		};
		final Runnable runNotReachable = new Runnable() {
			public void run() {
				Toast.makeText(getActivity(), getString(R.string.wizard_server_unavailable), Toast.LENGTH_LONG).show();
			}
		};

		LinphoneXmlRpcRequest xmlRpcRequest = new LinphoneXmlRpcRequestImpl("activate_phone_account", LinphoneXmlRpcRequest.ArgType.String);
		xmlRpcRequest.setListener(new LinphoneXmlRpcRequestListener() {
			@Override
			public void onXmlRpcRequestResponse(LinphoneXmlRpcRequest request) {
				if (request.getStatus() == LinphoneXmlRpcRequest.Status.Ok) {
					String response = request.getStringResponse();
					if (response.contains("ERROR")) {
						mHandler.post(runNotOk);
					} else {
						ha1 = response;
						mHandler.post(runOk);
					}
				} else if (request.getStatus() == LinphoneXmlRpcRequest.Status.Failed) {
					mHandler.post(runNotReachable);
				}
			}
		});

		xmlRpcRequest.addStringArg(phone);
		xmlRpcRequest.addStringArg(username);
		xmlRpcRequest.addStringArg(code);
		xmlRpcRequest.addStringArg(getString(R.string.default_domain));
		xmlRpcSession.sendRequest(xmlRpcRequest);
	}
}
