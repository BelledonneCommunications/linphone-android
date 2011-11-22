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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.core.LinphoneCall.State;
import org.linphone.mediastream.Version;
import org.linphone.ui.IncallTimer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * List participants of a conference call.
 *
 * @author Guillaume Beraudo
 *
 */
public class ConferenceDetailsActivity extends ListActivity implements LinphoneOnCallStateChangedListener, OnClickListener  {

	private ConfListAdapter mListAdapter;
	private List<LinphoneCall> mLinphoneCalls = Collections.emptyList();
	private Handler mHandler = new Handler();
	public static boolean active;

	private ToggleButton mMuteMicButton;
	private ToggleButton mSpeakerButton;
	private Runnable mDurationUpdateRunnable;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.conference_details_layout);
		setListAdapter(mListAdapter = new ConfListAdapter());
		mMuteMicButton = (ToggleButton) findViewById(R.id.toggleMuteMic);
		mMuteMicButton.setOnClickListener(this);
		mSpeakerButton = (ToggleButton) findViewById(R.id.toggleSpeaker);
		mSpeakerButton.setOnClickListener(this);
		mDurationUpdateRunnable = new Runnable() {
			@Override
			public void run() {
				updateCallDurations();
			}
		};
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onResume() {
		mLinphoneCalls = LinphoneUtils.getLinphoneCallsInConf(lc());
		if (mLinphoneCalls.size() != 0) {
			active = true;
			updateUI();
			mSpeakerButton.setChecked(LinphoneManager.getInstance().isSpeakerOn());
			mMuteMicButton.setChecked(LinphoneManager.getLc().isMicMuted());
			LinphoneManager.addListener(this);
			LinphoneManager.startProximitySensorForActivity(this);
			mHandler.post(mDurationUpdateRunnable);
		} else {
			finish();
		}
		super.onResume();
	}

	@Override
	protected void onPause() {
		LinphoneManager.removeListener(this);
		LinphoneManager.stopProximitySensorForActivity(this);
		active = false;
		super.onPause();
	}

	@Override
	public void onCallStateChanged(LinphoneCall c, State s, String m) {
		mHandler.post(new Runnable() {
			public void run() {
				mLinphoneCalls = LinphoneUtils.getLinphoneCallsInConf(lc());
				updateUI();
			}
		});
	}

	private LinphoneCore lc() {
		return LinphoneManager.getLc();
	}

	private void updateUI() {
		mCallDurationsMap.clear();
		mListAdapter.notifyDataSetChanged();
	}

	private Map<LinphoneCall, IncallTimer> mCallDurationsMap = new HashMap<LinphoneCall, IncallTimer>();
	private void updateCallDurations() {
		for (LinphoneCall call : mCallDurationsMap.keySet()) {
			IncallTimer timer = mCallDurationsMap.get(call);
			timer.setDuration(call.getDuration());
		}
		if (active) mHandler.postDelayed(mDurationUpdateRunnable, 1000);
	}

	private class ConfListAdapter extends BaseAdapter {
		public int getCount() {
			return mLinphoneCalls.size();
		}

		public Object getItem(int position) {
			return mLinphoneCalls.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View v, ViewGroup parent) {
			Log.i("IncallActivity.getView(",position,") out of ", mLinphoneCalls.size());
			if (v == null) {
				if (Version.sdkAboveOrEqual(Version.API06_ECLAIR_201)) {
					v = getLayoutInflater().inflate(R.layout.conf_details_participant, null);
				} else {
					throw new RuntimeException("to implement");
//					v = getLayoutInflater().inflate(R.layout.conf_callee_older_devices, null);
				}
			}

			final LinphoneCall call = mLinphoneCalls.get(position);
			LinphoneAddress address = call.getRemoteAddress();
			String mainText = address.getDisplayName();
			String complText = address.getUserName();
			if (Version.sdkAboveOrEqual(Version.API05_ECLAIR_20) 
					&& getResources().getBoolean(R.bool.show_full_remote_address_on_incoming_call)) {
				complText += "@" + address.getDomain();
			}
			TextView mainTextView = (TextView) v.findViewById(R.id.name);
			TextView complTextView = (TextView) v.findViewById(R.id.address);
			if (TextUtils.isEmpty(mainText)) {
				mainTextView.setText(complText);
				complTextView.setVisibility(View.GONE);
			} else {
				mainTextView.setText(mainText);
				complTextView.setText(complText);
				complTextView.setVisibility(View.VISIBLE);
			}

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

				private void enableView(View root, int id, OnClickListener l, boolean enable) {
					LinphoneUtils.enableView(root, id, l, enable);
				}
			});

			ImageView pictureView = (ImageView) v.findViewById(R.id.picture);
			// May be greatly sped up using a drawable cache
			Uri uri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, getContentResolver());
			LinphoneUtils.setImagePictureFromUri(ConferenceDetailsActivity.this, pictureView, uri, R.drawable.unknown_person);

			mCallDurationsMap.put(call, (IncallTimer) findViewById(R.id.callee_duration));
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
			switch (v.getId()) {
			case R.id.terminate_call:
				lc().terminateCall(call);
				break;
			case R.id.remove_from_conference:
				lc().removeFromConference(call);
				if (LinphoneUtils.countConferenceCalls(lc()) == 0) {
					finish();
				}
				break;
			default:
				throw new RuntimeException("unknown id " + v.getId());
			}
			if (dialog != null) dialog.dismiss();
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.toggleMuteMic:
			lc().muteMic(((ToggleButton) v).isChecked());
			break;
		case R.id.toggleSpeaker:
			if (((ToggleButton) v).isChecked()) {
				LinphoneManager.getInstance().routeAudioToSpeaker();
			} else {
				LinphoneManager.getInstance().routeAudioToReceiver();
			}
			break;
		default:
			break;
		}
	}
}