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
import org.linphone.R;
import org.linphone.compatibility.Compatibility;

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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
/**
 * @author Sylvain Berfini
 */
public class LinphoneLoginFragment extends Fragment implements OnClickListener, TextWatcher {
	private EditText login, password, displayName;
	private Button apply;
	private TextView forgotPassword;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_linphone_login, container, false);

		String url = "http://linphone.org/free-sip-service.html&action=recover";
		
		login = (EditText) view.findViewById(R.id.assistant_username);
		login.addTextChangedListener(this);
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
	
	public void linphoneLogIn() {
		if (login.getText() == null || login.length() == 0 || password.getText() == null || password.length() == 0) {
			Toast.makeText(getActivity(), getString(R.string.first_launch_no_login_password), Toast.LENGTH_LONG).show();
			return;
		}
		
		AssistantActivity.instance().linphoneLogIn(login.getText().toString(), password.getText().toString(), displayName.getText().toString(), getResources().getBoolean(R.bool.assistant_account_validation_mandatory));
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.assistant_apply) {
			linphoneLogIn();
		}
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		apply.setEnabled(!login.getText().toString().isEmpty() && !password.getText().toString().isEmpty());
	}

	@Override
	public void afterTextChanged(Editable s) {}
}
