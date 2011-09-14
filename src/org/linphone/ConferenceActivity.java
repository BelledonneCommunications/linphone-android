/*
AbstractLinphoneConferenceActivity.java
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
import java.util.Comparator;
import java.util.List;

import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCall.State;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ConferenceActivity extends ListActivity implements
		LinphoneOnCallStateChangedListener, Comparator<LinphoneCall>,
		OnClickListener {

	private View confHeaderView;

	// Start Override to test block
	protected LinphoneCore lc() {
		return LinphoneManager.getLc();
	}

	@SuppressWarnings("unchecked")
	protected List<LinphoneCall> getInitialCalls() {
		return lc().getCalls();
	}

	// End override to test block

	private static final int ACTIVE_BG_COLOR = Color.parseColor("#777777");
	public static final int INACTIVE_BG_COLOR = Color.BLACK;
	public static final int INCOMING_BG_COLOR = Color.parseColor("#336600");
	public static final int CONFERENCE_BG_COLOR = Color.parseColor("#444444");
	private static final int numpad_dialog_id = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.conferencing);
		super.onCreate(savedInstanceState);
		confHeaderView = findViewById(R.id.conf_header);
		confHeaderView.findViewById(R.id.terminate_conference)
				.setOnClickListener(this);
		confHeaderView.findViewById(R.id.conf_enter_or_leave_button)
				.setOnClickListener(this);

		findViewById(R.id.addCall).setOnClickListener(this);
		findViewById(R.id.incallHang).setOnClickListener(this);

		findViewById(R.id.incallNumpadShow).setOnClickListener(this);

		List<LinphoneCall> calls = getInitialCalls();
		setListAdapter(new CalleeListAdapter(calls));

		updateConfState();
	}

	protected void registerLinphoneListener(boolean register) {
		if (register)
			LinphoneManager.getInstance().addListener(this);
		else
			LinphoneManager.getInstance().removeListener(this);
	}

	@Override
	protected void onResume() {
		registerLinphoneListener(true);
		super.onResume();
	}

	@Override
	protected void onPause() {
		registerLinphoneListener(false);
		super.onPause();
	}

	@Override
	protected Dialog onCreateDialog(final int id) {
		return new AlertDialog.Builder(this).setView(
				getLayoutInflater().inflate(R.layout.numpad, null))
		// .setIcon(R.drawable.logo_linphone_57x57)
				// .setTitle("Send DTMFs")
				// .setPositiveButton("hide", new
				// DialogInterface.OnClickListener() {
				// public void onClick(DialogInterface dialog, int whichButton)
				// {
				// dismissDialog(id);
				// }
				// })
				.create();
	}

	// protected void conferenceMerge(boolean hostInTheConference, LinphoneCall
	// ... calls) {
	// for (LinphoneCall call: calls) {
	// getLc().addToConference(call, false);
	// }
	// getLc().enterConference(hostInTheConference);
	// }

	// FIXME hack; should have an event?
	protected final void hackTriggerConfStateUpdate() {
		updateConfState();
	}

	private final void updateConfState() {
		if (lc().getCallsNb() == 0)
			finish();
		boolean inConf = lc().isInConference();
		confHeaderView.setBackgroundColor(inConf ? ACTIVE_BG_COLOR
				: INACTIVE_BG_COLOR);

		confHeaderView
				.setVisibility(lc().getConferenceSize() > 0 ? View.VISIBLE
						: View.GONE);

		TextView v = (TextView) confHeaderView
				.findViewById(R.id.conf_self_attending);
		v.setText(inConf ? R.string.in_conf : R.string.out_conf);

		v = (TextView) confHeaderView
				.findViewById(R.id.conf_enter_or_leave_button);
		v.setText(inConf ? R.string.in_conf_leave : R.string.out_conf_enter);
	}

	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.addCall:
			Toast.makeText(this,
					"Should now finish this activity to go back to dialer",
					Toast.LENGTH_LONG).show();
			// startActivityForResult(new Intent().setClass(this,
			// LinphoneContactSelectorActivity.class), 0);
			break;
		case R.id.conf_enter_or_leave_button:
			if (lc().isInConference()) {
				lc().leaveConference();
			} else {
				lc().enterConference();
			}
			break;
		case R.id.terminate_conference:
			lc().terminateConference();
			findViewById(R.id.conf_header).setVisibility(View.GONE);
			break;
		case R.id.incallHang:
			lc().terminateAllCalls();
			finish();
			break;
		case R.id.incallNumpadShow:
			showDialog(numpad_dialog_id);
			break;
		default:
			break;
		}

	}

	private class CalleeListAdapter extends BaseAdapter {
		private List<LinphoneCall> linphoneCalls;

		public CalleeListAdapter(List<LinphoneCall> calls) {
			linphoneCalls = calls;

		}

		public int getCount() {
			return linphoneCalls != null ? linphoneCalls.size() : 0;
		}

		public Object getItem(int position) {
			return linphoneCalls.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		private boolean aConferenceIsPossible() {
			if (lc().getCallsNb() < 2)
				return false;
			int count = 0;
			for (LinphoneCall call : linphoneCalls) {
				int stateId = call.getState().value();
				boolean connectionEstablished = stateId == State.ID_STREAMS_RUNNING
						|| stateId == State.ID_PAUSED
						|| stateId == State.ID_PAUSED_BY_REMOTE;
				if (connectionEstablished)
					count++;
				if (count >= 2)
					return true;
			}
			return false;
		}

		public View getView(int position, View v, ViewGroup parent) {
			if (v == null) {
				v = getLayoutInflater().inflate(R.layout.conf_callee, null);
			}

			LinphoneCall call = linphoneCalls.get(position);
			int stateId = call.getState().value();

			((TextView) v.findViewById(R.id.name)).setText(call
					.getRemoteAddress().getDisplayName());
			((TextView) v.findViewById(R.id.address)).setText(call
					.getRemoteAddress().getUserName());

			boolean isInConference = call.isInConference();
			boolean currentlyActiveCall = !isInConference
					&& stateId == State.ID_STREAMS_RUNNING;
			int bgColor = INACTIVE_BG_COLOR;
			if (stateId == State.ID_INCOMING_RECEIVED) {
				bgColor = INCOMING_BG_COLOR;
			} else if (currentlyActiveCall) {
				bgColor = ACTIVE_BG_COLOR;
			} else if (isInConference) {
				bgColor = CONFERENCE_BG_COLOR;
			}
			v.setBackgroundColor(bgColor);

			boolean connectionEstablished = stateId == State.ID_STREAMS_RUNNING
					|| stateId == State.ID_PAUSED
					|| stateId == State.ID_PAUSED_BY_REMOTE;
			View confButton = v.findViewById(R.id.merge_to_conference);
			boolean showMergeToConf = !isInConference && connectionEstablished
					&& aConferenceIsPossible();
			confButton
					.setVisibility(showMergeToConf ? View.VISIBLE : View.GONE);

			View unhookCallButton = v.findViewById(R.id.unhook_call);
			boolean showUnhook = stateId == State.ID_INCOMING_RECEIVED;
			unhookCallButton.setVisibility(showUnhook ? View.VISIBLE
					: View.GONE);

			View terminateCallButton = v.findViewById(R.id.terminate_call);
			terminateCallButton.setVisibility(!isInConference ? View.VISIBLE
					: View.GONE);

			View pauseButton = v.findViewById(R.id.pause);
			boolean showPause = !isInConference
					&& call.getState().equals(State.StreamsRunning);
			pauseButton.setVisibility(showPause ? View.VISIBLE : View.GONE);

			View resumeButton = v.findViewById(R.id.resume);
			boolean showResume = !isInConference
					&& call.getState().equals(State.Paused);
			resumeButton.setVisibility(showResume ? View.VISIBLE : View.GONE);

			v.findViewById(R.id.addVideo).setVisibility(
					!showUnhook && linphoneCalls.size() == 1 ? View.VISIBLE
							: View.GONE);

			createAndAttachCallViewClickListener(position, confButton,
					unhookCallButton, terminateCallButton, pauseButton,
					resumeButton);
			return v;
		}

		private void createAndAttachCallViewClickListener(final int position,
				final View confButton, final View unhookCallButton,
				final View terminateCallButton, final View pauseButton,
				final View resumeButton) {
			OnClickListener l = new OnClickListener() {
				public void onClick(View v) {
					LinphoneCall call = linphoneCalls.get(position);
					if (v == confButton) {
						lc().addToConference(call);
					} else if (v == terminateCallButton) {
						lc().terminateCall(call);
					} else if (v == pauseButton) {
						lc().pauseCall(call);
					} else if (v == resumeButton) {
						lc().resumeCall(call);
					} else if (v == unhookCallButton) {
						LinphoneCall currentCall = lc().getCurrentCall();
						if (currentCall != null) lc().pauseCall(currentCall);
						try {
							lc().acceptCall(call);
						} catch (LinphoneCoreException e) {
							throw new RuntimeException(e);
						}
					}
				}
			};
			confButton.setOnClickListener(l);
			terminateCallButton.setOnClickListener(l);
			pauseButton.setOnClickListener(l);
			resumeButton.setOnClickListener(l);
			unhookCallButton.setOnClickListener(l);
		}
	}

	private Handler mHandler = new Handler();

	public void onCallStateChanged(final LinphoneCall call, final State state,
			final String message) {
		mHandler.post(new Runnable() {
			public void run() {
				CalleeListAdapter adapter = (CalleeListAdapter) getListAdapter();

				switch (state.value()) {
				case State.ID_INCOMING_RECEIVED:
				case State.ID_OUTGOING_RINGING:
					adapter.linphoneCalls.add(call);
					Collections.sort(adapter.linphoneCalls,
							ConferenceActivity.this);
					adapter.notifyDataSetInvalidated();
					break;
				case State.ID_PAUSED:
				case State.ID_PAUSED_BY_REMOTE:
				case State.ID_STREAMS_RUNNING:
					Collections.sort(adapter.linphoneCalls,
							ConferenceActivity.this);
					adapter.notifyDataSetChanged();
					break;
				case State.ID_CALL_END:
					adapter.linphoneCalls.remove(call);
					Collections.sort(adapter.linphoneCalls,
							ConferenceActivity.this);
					adapter.notifyDataSetInvalidated();
					break;
				default:
					break;
				}

				updateConfState();
			}
		});
	}

	public int compare(LinphoneCall c1, LinphoneCall c2) {
		if (c1 == c2)
			return 0;

		boolean inConfC1 = c1.isInConference();
		boolean inConfC2 = c2.isInConference();
		if (inConfC1 && !inConfC2)
			return -1;
		if (!inConfC1 && inConfC2)
			return 1;

		int durationDiff = c1.getDuration() - c2.getDuration();
		return durationDiff;

	}
	/*
	 * public int compare(LinphoneCall c1, LinphoneCall c2) { if (c1 == c2)
	 * return 0;
	 * 
	 * boolean inConfC1 = c1.isInConference(); boolean inConfC2 =
	 * c2.isInConference(); if (inConfC1 && !inConfC2) return -1; if (!inConfC1
	 * && inConfC2) return 1;
	 * 
	 * int compUserName =
	 * c1.getRemoteAddress().getUserName().compareToIgnoreCase
	 * (c2.getRemoteAddress().getUserName()); if (inConfC1 && inConfC2) { return
	 * compUserName; }
	 * 
	 * // bellow, ringings and incoming int c1State = c1.getState().value(); int
	 * c2State = c2.getState().value();
	 * 
	 * boolean c1StateIsEstablishing = c1State == State.ID_INCOMING_RECEIVED ||
	 * c1State == State.ID_OUTGOING_RINGING; boolean c2StateIsEstablishing =
	 * c2State == State.ID_INCOMING_RECEIVED || c2State ==
	 * State.ID_OUTGOING_RINGING;
	 * 
	 * // Xor only one establishing state if (c1StateIsEstablishing ^
	 * c2StateIsEstablishing) { // below return !c1StateIsEstablishing ? -1 : 1;
	 * }
	 * 
	 * // Xor only one paused state if (c1State == State.ID_PAUSED ^ c2State ==
	 * State.ID_PAUSED) { return c1State == State.ID_PAUSED ? -1 : 1; }
	 * 
	 * return compUserName; //Duration() - c1.getDuration(); }
	 */
}
