package org.linphone.setup;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneLauncherActivity;
import org.linphone.LinphonePreferences;
import org.linphone.R;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

public class RemoteProvisioningFragment extends Fragment implements OnClickListener {
	private EditText remoteProvisioningUrl;
	private ImageView apply;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.setup_remote_provisioning, container, false);
		
		remoteProvisioningUrl = (EditText) view.findViewById(R.id.setup_remote_provisioning_url);
		apply = (ImageView) view.findViewById(R.id.setup_apply);
		apply.setOnClickListener(this);
		
		return view;
	}
	
	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.setup_apply) {
			String url = remoteProvisioningUrl.getText().toString();
			LinphonePreferences.instance().setRemoteProvisioningUrl(url);
			
			// Restart Linphone
			Intent intent = new Intent();
			intent.setClass(getActivity(), LinphoneLauncherActivity.class);
			getActivity().finish();
			LinphoneActivity.instance().exit();
			startActivity(intent);
		}
	}
}
