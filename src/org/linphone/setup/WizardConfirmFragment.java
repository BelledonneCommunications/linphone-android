package org.linphone.setup;
/*
WizardConfirmFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import de.timroes.axmlrpc.XMLRPCClient;
/**
 * @author Sylvain Berfini
 */
public class WizardConfirmFragment extends Fragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.setup_wizard_confirm, container, false);
		
		return view;
	}
	
	private boolean isAccountVerified(String username) {
		try {
			XMLRPCClient client = new XMLRPCClient(new URL(getString(R.string.wizard_url)));
		    Object resultO = client.call("check_account_validated", "sip:" + username + "@" + getString(R.string.default_domain));
		    Integer result = Integer.parseInt(resultO.toString());
		    
		    return result == 1;
		} catch(Exception ex) {

		}
		return false;
	}
}
