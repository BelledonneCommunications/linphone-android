/*
ConferenceActivity.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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
package org.linphone;

import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.mediastream.Version;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * List participants of a conference call.
 *
 * @author Guillaume Beraudo
 *
 */
public class ConferenceDetailsActivity extends AbstractCalleesActivity  {

	public static boolean active;
	@Override protected void setActive(boolean a) {active = a;}
	@Override protected boolean isActive() {return active;}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (finishIfAutoRestartAfterACrash(savedInstanceState)) {
			return;
		}
		setContentView(R.layout.conference_details_layout);
		View v=findViewById(R.id.incallHang);
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				lc().terminateConference();
			}
		});
		super.onCreate(savedInstanceState);
	}

	protected CalleeListAdapter createCalleeListAdapter() {
		return new ConfListAdapter();
	}

	private class ConfListAdapter extends CalleeListAdapter {
		@Override
		public View getView(int position, View v, ViewGroup parent) {
			if (v == null) {
				if (Version.sdkAboveOrEqual(Version.API06_ECLAIR_201)) {
					v = getLayoutInflater().inflate(R.layout.conf_callee, null);
				} else {
					v = getLayoutInflater().inflate(R.layout.conf_callee_older_devices, null);
				}
			}

			final LinphoneCall call = getItem(position);
			LinphoneAddress address = call.getRemoteAddress();
			final String mainText = getCalleeDisplayOrUserName(address);
			((TextView) v.findViewById(R.id.name)).setText(mainText);
			((TextView) v.findViewById(R.id.address)).setText("");

			v.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					if (lc().soundResourcesLocked()) {
						return;
					}
					View content = getLayoutInflater().inflate(R.layout.conf_details_participant_choices_dialog, null);
					Dialog dialog = new AlertDialog.Builder(ConferenceDetailsActivity.this).setView(content).create();
					OnClickListener l = new CallActionListener(call, dialog);
					enableView(content, R.id.remove_from_conference, l, true);
					enableView(content, R.id.terminate_call, l, true);
					dialog.show();
				}
			});
			
			setVisibility(v, R.id.callee_status_qos, true);

			// May be greatly sped up using a drawable cache
			ImageView pictureView = (ImageView) v.findViewById(R.id.picture);
			setCalleePicture(pictureView, address);

			registerCallDurationTimer(v, call);
			registerCallQualityListener(v, call);
			registerCallSpeakerListener();
			
			return v;
		}

	}

	private class CallActionListener implements OnClickListener {
		private LinphoneCall call;
		private Dialog dialog;
		public CallActionListener(LinphoneCall call, Dialog dialog) {
			this.call = call;
			this.dialog = dialog;
		}
		public void onClick(View v) {
			int id = v.getId();
			if (id == R.id.terminate_call) {
				lc().terminateCall(call);
			}
			else if (id == R.id.remove_from_conference) {
				lc().removeFromConference(call);
				if (LinphoneUtils.countConferenceCalls(lc()) == 0) {
					finish();
				}
			}
			else {
				throw new RuntimeException("unknown id " + v.getId());
			}
			if (dialog != null) dialog.dismiss();
		}
	}


	@Override
	protected List<LinphoneCall> updateSpecificCallsList() {
		return LinphoneUtils.getLinphoneCallsInConf(lc());
	}

}