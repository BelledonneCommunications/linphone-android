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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
import java.net.URL;

import org.linphone.R;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import de.timroes.axmlrpc.XMLRPCCallback;
import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLRPCServerException;
/**
 * @author Sylvain Berfini
 */
public class CreateAccountActivationFragment extends Fragment {
	private String username, password, domain;
	private Handler mHandler = new Handler();
	private Button checkAccount;
	
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
		
		return view;
	}	
	
	private void isAccountVerified(final String username) {
		final Runnable runNotReachable = new Runnable() {
			public void run() {
				Toast.makeText(getActivity(), getString(R.string.wizard_server_unavailable), Toast.LENGTH_LONG).show();
			}
		};
		
		try {
			XMLRPCClient client = new XMLRPCClient(new URL(getString(R.string.wizard_url)));
			
			XMLRPCCallback listener = new XMLRPCCallback() {
				Runnable runNotOk = new Runnable() {
    				public void run() {
						checkAccount.setEnabled(true);
    					Toast.makeText(getActivity(), getString(R.string.assistant_account_not_validated), Toast.LENGTH_LONG).show();
					}
	    		};
	    		
	    		Runnable runOk = new Runnable() {
    				public void run() {
						checkAccount.setEnabled(true);
						AssistantActivity.instance().saveCreatedAccount(username,password,null, getString(R.string.default_domain),null);
    					AssistantActivity.instance().isAccountVerified(username);
					}
	    		};
	    		
			    public void onResponse(long id, Object result) {
			    	int answer = (Integer) result;
			    	if (answer != 1) {
			    		mHandler.post(runNotOk);
			    	} else {
			    		mHandler.post(runOk);
			    	}
			    }
			    
			    public void onError(long id, XMLRPCException error) {
			    	mHandler.post(runNotReachable);
			    }
			   
			    public void onServerError(long id, XMLRPCServerException error) {
			    	mHandler.post(runNotReachable);
			    }
			};

		    client.callAsync(listener, "check_account_validated", username + "@" + getString(R.string.default_domain));
		} 
		catch(Exception ex) {
			mHandler.post(runNotReachable);
		}
	}
}
