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
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.LinphoneXmlRpcRequest;
import org.linphone.core.LinphoneXmlRpcRequest.LinphoneXmlRpcRequestListener;
import org.linphone.core.LinphoneXmlRpcRequestImpl;
import org.linphone.core.LinphoneXmlRpcSession;
import org.linphone.core.LinphoneXmlRpcSessionImpl;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
/**
 * @author Sylvain Berfini
 */
public class CreateAccountActivationFragment extends Fragment implements LinphoneXmlRpcRequestListener {
	private String username, password;
	private Handler mHandler = new Handler();
	private Button checkAccount;
	private LinphoneXmlRpcSession xmlRpcSession;
	private LinphoneXmlRpcRequest xmlRpcRequest;
	private Runnable runNotOk, runOk, runNotReachable;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_account_creation_activation, container, false);
		
		username = getArguments().getString("Username");
		password = getArguments().getString("Password");

		checkAccount = (Button) view.findViewById(R.id.assistant_check);
		checkAccount.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				checkAccount.setEnabled(false);
				isAccountVerified(username);
			}
		});

		runNotOk = new Runnable() {
			public void run() {
				checkAccount.setEnabled(true);
				Toast.makeText(getActivity(), getString(R.string.assistant_account_not_validated), Toast.LENGTH_LONG).show();
			}
		};
		runOk = new Runnable() {
			public void run() {
				checkAccount.setEnabled(true);
				AssistantActivity.instance().saveCreatedAccount(username,password,null, getString(R.string.default_domain),null);
				AssistantActivity.instance().isAccountVerified(username);
			}
		};
		runNotReachable = new Runnable() {
			public void run() {
				Toast.makeText(getActivity(), getString(R.string.wizard_server_unavailable), Toast.LENGTH_LONG).show();
			}
		};
		
		xmlRpcSession = new LinphoneXmlRpcSessionImpl(LinphoneManager.getLcIfManagerNotDestroyedOrNull(), getString(R.string.wizard_url));
		xmlRpcRequest = new LinphoneXmlRpcRequestImpl("check_account_validated", LinphoneXmlRpcRequest.ArgType.Int);
		xmlRpcRequest.setListener(this);
		
		return view;
	}

	@Override
	public void onXmlRpcRequestResponse(LinphoneXmlRpcRequest request) {		
		if (request.getStatus() == LinphoneXmlRpcRequest.Status.Ok) {
			int response = request.getIntResponse();
			if (response != 1) {
				mHandler.post(runNotOk);
			} else {
				mHandler.post(runOk);
			}
		} else if (request.getStatus() == LinphoneXmlRpcRequest.Status.Failed) {
			mHandler.post(runNotReachable);
		}
	}
	
	private void isAccountVerified(final String username) {
		xmlRpcRequest.addStringArg(username + "@" + getString(R.string.default_domain));
		xmlRpcSession.sendRequest(xmlRpcRequest);
	}
}
