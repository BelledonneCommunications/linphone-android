package org.linphone;
/*
AboutFragment.java
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

import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.LogCollectionUploadState;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.mediastream.Log;

import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class AboutFragment extends Fragment implements OnClickListener {
	private FragmentsAvailable about = FragmentsAvailable.ABOUT_INSTEAD_OF_CHAT;
	View exitButton = null;
	View sendLogButton = null;
	View resetLogButton = null;
	private LinphoneCoreListenerBase mListener;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (getArguments() != null && getArguments().getSerializable("About") != null) {
			about = (FragmentsAvailable) getArguments().getSerializable("About");
		}

		View view = inflater.inflate(R.layout.about, container, false);

		TextView aboutText = (TextView) view.findViewById(R.id.AboutText);
		try {
			aboutText.setText(String.format(getString(R.string.about_text), getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName));
		} catch (NameNotFoundException e) {
			Log.e(e, "cannot get version name");
		}

		sendLogButton = view.findViewById(R.id.send_log);
		sendLogButton.setOnClickListener(this);
		sendLogButton.setVisibility(LinphonePreferences.instance().isDebugEnabled() ? View.VISIBLE : View.GONE);

		resetLogButton = view.findViewById(R.id.reset_log);
		resetLogButton.setOnClickListener(this);
		resetLogButton.setVisibility(LinphonePreferences.instance().isDebugEnabled() ? View.VISIBLE : View.GONE);

		exitButton = view.findViewById(R.id.exit);
		exitButton.setOnClickListener(this);
		exitButton.setVisibility(View.VISIBLE);
		
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			mListener = new LinphoneCoreListenerBase(){
				@Override
				public void uploadProgressIndication(LinphoneCore linphoneCore, int offset, int total) {
					if(total > 0)
						Log.d("Log upload progress: currently uploaded = " + offset + " , total = " + total + ", % = " + String.valueOf((offset * 100) / total));
				}

				@Override
				public void uploadStateChanged(LinphoneCore linphoneCore, LogCollectionUploadState state, String info) {
					Log.d("Log upload state: " + state.toString() + ", info = " + info);
					
					if (state == LogCollectionUploadState.LogCollectionUploadStateDelivered) {
						final String appName = getString(R.string.app_name);
			        	
			            Intent i = new Intent(Intent.ACTION_SEND);
			            i.putExtra(Intent.EXTRA_EMAIL, new String[]{ getString(R.string.about_bugreport_email) });
			            i.putExtra(Intent.EXTRA_SUBJECT, appName + " Logs");
			            i.putExtra(Intent.EXTRA_TEXT, info);
			            i.setType("application/zip");
			            
			            try {
			                startActivity(Intent.createChooser(i, "Send mail..."));
			            } catch (android.content.ActivityNotFoundException ex) {
			            	Log.e(ex);
			            }
					}
				}
			};
			lc.addListener(mListener);
		}

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(about);

			if (getResources().getBoolean(R.bool.show_statusbar_only_on_dialer)) {
				LinphoneActivity.instance().hideStatusBar();
			}
		}
	}
	

	@Override
	public void onClick(View v) {
		if (LinphoneActivity.isInstanciated()) {
			LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
			if (v == sendLogButton) {
				//LinphoneUtils.collectLogs(LinphoneActivity.instance(), getString(R.string.about_bugreport_email));
				if (lc != null) {
					lc.uploadLogCollection();
				}
			} else if (v == resetLogButton) {
				if (lc != null) {
					lc.resetLogCollection();
				}
			} else {
				LinphoneActivity.instance().exit();
			}
		}
	}
	
	@Override
	public void onDestroy() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}
		
		super.onDestroy();
	}

	
}
