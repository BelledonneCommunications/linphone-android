package org.linphone.assistant;
/*
RemoteProvisioningFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.settings.LinphonePreferences;

public class RemoteProvisioningFragment extends Fragment implements OnClickListener, TextWatcher {
    private EditText mRemoteProvisioningUrl;
    private Button mApply, mQrcode;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.assistant_remote_provisioning, container, false);

        mRemoteProvisioningUrl = view.findViewById(R.id.assistant_remote_provisioning_url);
        mRemoteProvisioningUrl.addTextChangedListener(this);
        mQrcode = view.findViewById(R.id.assistant_qrcode);
        mQrcode.setOnClickListener(this);
        mApply = view.findViewById(R.id.assistant_apply);
        mApply.setEnabled(false);
        mApply.setOnClickListener(this);

        if (getArguments() != null && !getArguments().getString("RemoteUrl").isEmpty()) {
            mRemoteProvisioningUrl.setText(getArguments().getString("RemoteUrl"));
        }

        return view;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.assistant_apply) {
            String url = mRemoteProvisioningUrl.getText().toString();
            AssistantActivity.instance().displayRemoteProvisioningInProgressDialog();
            LinphonePreferences.instance().setRemoteProvisioningUrl(url);
            LinphoneManager.getLc().getConfig().sync();
            LinphoneManager.getInstance().restartCore();
            AssistantActivity.instance().setCoreListener();
        } else if (id == R.id.assistant_qrcode) {
            AssistantActivity.instance().displayQRCodeReader();
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        mApply.setEnabled(!mRemoteProvisioningUrl.getText().toString().isEmpty());
    }

    @Override
    public void afterTextChanged(Editable s) {}
}
