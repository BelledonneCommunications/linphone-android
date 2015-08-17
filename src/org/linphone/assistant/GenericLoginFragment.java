package org.linphone.assistant;
/*
GenericLoginFragment.java
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
import org.linphone.R;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
/**
 * @author Sylvain Berfini
 */
public class GenericLoginFragment extends Fragment implements OnClickListener {
	private EditText login, password, displayName, domain;
	private Button apply;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_generic_login, container, false);
		
		login = (EditText) view.findViewById(R.id.assistant_username);
		password = (EditText) view.findViewById(R.id.assistant_password);
		displayName = (EditText) view.findViewById(R.id.assistant_display_name);
		domain = (EditText) view.findViewById(R.id.assistant_domain);
		apply = (Button) view.findViewById(R.id.assistant_apply);
		apply.setOnClickListener(this);
		
		return view;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.assistant_apply) {
			if (login.getText() == null || login.length() == 0 || password.getText() == null || password.length() == 0 || domain.getText() == null || domain.length() == 0) {
				Toast.makeText(getActivity(), getString(R.string.first_launch_no_login_password), Toast.LENGTH_LONG).show();
				return;
			}
			
			AssistantActivity.instance().genericLogIn(login.getText().toString(), password.getText().toString(), displayName.getText().toString(), domain.getText().toString());
		}
	}
}
