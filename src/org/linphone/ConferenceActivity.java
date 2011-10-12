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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.linphone.LinphoneManagerWaitHelper.LinphoneManagerReadyListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.Log;
import org.linphone.core.LinphoneCall.State;
import org.linphone.mediastream.Version;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * @author Guillaume Beraudo
 */
public class ConferenceActivity extends ListActivity implements
		LinphoneManagerReadyListener,
		LinphoneOnCallStateChangedListener, Comparator<LinphoneCall>,
		OnClickListener {

	private View confHeaderView;
	static boolean active;

	private boolean unMuteOnReturnFromUriPicker;

	// Start Override to test block
	protected LinphoneCore lc() {
		return LinphoneManager.getLc();
	}

	protected List<LinphoneCall> getInitialCalls() {
		return LinphoneUtils.getLinphoneCalls(lc());
	}

	// End override to test block

	private static final int numpad_dialog_id = 1;
	public static final String ADD_CALL = "add_call";
	public static final String TRANSFER_TO_NEW_CALL = "transfer_to_new_call";
	public static final String CALL_NATIVE_ID = "call_native_id";
	private static final int ID_ADD_CALL = 1;
	private static final int ID_TRANSFER_CALL = 2;



	@SuppressWarnings("unused")
	private void workaroundStatusBarBug() {
		// call from onCreate to get a clean display on full screen no icons
		// otherwise the upper side of the activity may be corrupted
		getWindow().setFlags(
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
	}

//	private void pauseCurrentCallOrLeaveConference() {
//		LinphoneCall call = lc().getCurrentCall();
//		if (call != null) lc().pauseCall(call);
//		lc().leaveConference();
//	}

	private LinphoneManagerWaitHelper waitHelper;
	private ToggleButton mMuteMicButton;
	private ToggleButton mSpeakerButton;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.conferencing);

		confHeaderView = findViewById(R.id.conf_header);
		confHeaderView.setOnClickListener(this);

		findViewById(R.id.addCall).setOnClickListener(this);

		findViewById(R.id.incallNumpadShow).setOnClickListener(this);
		findViewById(R.id.conf_simple_merge).setOnClickListener(this);
		findViewById(R.id.conf_simple_transfer).setOnClickListener(this);
		findViewById(R.id.conf_simple_permute).setOnClickListener(this);

		mMuteMicButton = (ToggleButton) findViewById(R.id.toggleMuteMic);
		mMuteMicButton.setOnClickListener(this);
		mSpeakerButton = (ToggleButton) findViewById(R.id.toggleSpeaker);
		mSpeakerButton.setOnClickListener(this);

		waitHelper = new LinphoneManagerWaitHelper(this, this);
		waitHelper.doManagerDependentOnCreate();
//		workaroundStatusBarBug();
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onCreateWhenManagerReady() {
		List<LinphoneCall> calls = getInitialCalls();
		setListAdapter(new CalleeListAdapter(calls));
		
		findViewById(R.id.incallHang).setOnClickListener(this);
	}
	@Override
	public void onResumeWhenManagerReady() {
		registerLinphoneListener(true);
		mSpeakerButton.setChecked(LinphoneManager.getInstance().isSpeakerOn());
		mMuteMicButton.setChecked(LinphoneManager.getLc().isMicMuted());
		updateCalleeImage();
		updateConfState();
		updateSimpleControlButtons();
		CalleeListAdapter adapter = (CalleeListAdapter) getListAdapter();
		adapter.notifyDataSetInvalidated();
		adapter.notifyDataSetChanged();
		LinphoneManager.startProximitySensorForActivity(this);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
	}

	protected void registerLinphoneListener(boolean register) {
		if (register)
			LinphoneManager.getInstance().addListener(this);
		else
			LinphoneManager.getInstance().removeListener(this);
	}



	@Override
	protected void onResume() {
		active=true;
		waitHelper.doManagerDependentOnResume();
		super.onResume();
	}

	@Override
	protected void onPause() {
		active=false;
		registerLinphoneListener(false);
		LinphoneManager.stopProximitySensorForActivity(this);
		super.onPause();
	}

	private void updateCalleeImage() {
		ImageView view = (ImageView) findViewById(R.id.incall_picture);
		LinphoneCall currentCall = lc().getCurrentCall();

		if (lc().getCallsNb() != 1 || currentCall == null) {
			view.setVisibility(GONE);
			return;
		}

		Uri picture = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(
				currentCall.getRemoteAddress(),	getContentResolver());
		LinphoneUtils.setImagePictureFromUri(this, view, picture, R.drawable.unknown_person);
		view.setVisibility(VISIBLE);
	}

	private void enableView(View root, int id, OnClickListener l, boolean enable) {
		View v = root.findViewById(id);
		v.setVisibility(enable ? VISIBLE : GONE);
		v.setOnClickListener(l);
	}
	@Override
	protected Dialog onCreateDialog(final int id) {
		if (id == LinphoneManagerWaitHelper.DIALOG_ID) {
			return waitHelper.createWaitDialog();
		}

		switch (id) {
		case numpad_dialog_id:
			return new AlertDialog.Builder(this).setView(
					getLayoutInflater().inflate(R.layout.numpad, null))
			// .setIcon(R.drawable.logo_linphone_57x57)
					// .setTitle("Send DTMFs")
					 .setPositiveButton(getString(R.string.close_button_text), new
					 DialogInterface.OnClickListener() {
					 public void onClick(DialogInterface dialog, int whichButton)
					 {
					 dismissDialog(id);
					 }
					 })
					.create();
		default:
			throw new RuntimeException("unkown dialog id " + id);
		}

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
		if (lc().getCallsNb() == 0) {
			setResult(RESULT_OK);
			finish();
		}
			
		boolean inConf = lc().isInConference();

		int bgColor = getResources().getColor(inConf? R.color.conf_active_bg_color : android.R.color.transparent);
		confHeaderView.setBackgroundColor(bgColor);
		confHeaderView.setVisibility(lc().getConferenceSize() > 0 ? VISIBLE: GONE);

//		TextView v = (TextView) confHeaderView
//				.findViewById(R.id.conf_self_attending);
//		v.setText(inConf ? R.string.in_conf : R.string.out_conf);
	}

	private void openUriPicker(String pickerType, int requestCode) {
		Intent intent = new Intent().setClass(this, UriPickerActivity.class);
		intent.putExtra(UriPickerActivity.EXTRA_PICKER_TYPE, pickerType);
		startActivityForResult(intent, requestCode);
		if (!lc().isMicMuted()) {
			unMuteOnReturnFromUriPicker = true;
			lc().muteMic(true);
			((ToggleButton) findViewById(R.id.toggleMuteMic)).setChecked(true);
		}
	}

	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.addCall:
			openUriPicker(UriPickerActivity.EXTRA_PICKER_TYPE_ADD, ID_ADD_CALL);
			break;
		case R.id.conf_header:
			View content = getLayoutInflater().inflate(R.layout.conf_choices_admin, null);
			final Dialog dialog = new AlertDialog.Builder(ConferenceActivity.this).setView(content).create();
			boolean isInConference = lc().isInConference();
			OnClickListener l = new OnClickListener() {
				public void onClick(View v) {
					switch (v.getId()) {
					case R.id.conf_add_all_to_conference_button:
						lc().addAllToConference();
						updateConfState();
						break;
					case R.id.conf_enter_button:
						lc().enterConference();
						updateConfState();
						break;
					case R.id.conf_leave_button:
						lc().leaveConference();
						updateConfState();
						break;
					case R.id.conf_terminate_button:
						lc().terminateConference();
						findViewById(R.id.conf_header).setVisibility(GONE);
						break;
					default:
						break;
					}
					dialog.dismiss();
				}
			};
			enableView(content, R.id.conf_enter_button, l, !isInConference);
			enableView(content, R.id.conf_leave_button, l, isInConference);
			content.findViewById(R.id.conf_terminate_button).setOnClickListener(l);
			content.findViewById(R.id.conf_add_all_to_conference_button).setOnClickListener(l);

			dialog.show();
			break;
		case R.id.incallHang:
			lc().terminateAllCalls();
			setResult(RESULT_OK);
			finish();
			break;
		case R.id.incallNumpadShow:
			showDialog(numpad_dialog_id);
			break;
		case R.id.conf_simple_merge:
			findViewById(R.id.conf_control_buttons).setVisibility(GONE);
			lc().addAllToConference();
			break;
		case R.id.conf_simple_transfer:
			findViewById(R.id.conf_control_buttons).setVisibility(GONE);
			LinphoneCall tCall = lc().getCurrentCall();
			if (tCall != null) {
				prepareForTransferingExistingCall(tCall);
			} else {
				Toast.makeText(this, R.string.conf_simple_no_current_call, Toast.LENGTH_SHORT).show();
			}
			break;
		case R.id.conf_simple_permute:
			findViewById(R.id.conf_control_buttons).setVisibility(GONE);
			for (LinphoneCall call : LinphoneUtils.getLinphoneCalls(lc())) {
				if (State.Paused == call.getState()) {
					lc().resumeCall(call);
					break;
				}
			}
			break;
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

	private void prepareForTransferingExistingCall(final LinphoneCall call) {
		final List<LinphoneCall> existingCalls = LinphoneUtils.getLinphoneCalls(lc());
		existingCalls.remove(call);
		final List<String> numbers = new ArrayList<String>(existingCalls.size());
		Resources r = getResources();
		for(LinphoneCall c : existingCalls) {
			numbers.add(LinphoneManager.extractADisplayName(r, c.getRemoteAddress()));
		}
		ListAdapter adapter = new ArrayAdapter<String>(ConferenceActivity.this, android.R.layout.select_dialog_item, numbers);
		DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				lc().transferCallToAnother(call, existingCalls.get(which));
			}
		};
		new AlertDialog.Builder(ConferenceActivity.this).setTitle(R.string.transfer_dialog_title).setAdapter(adapter, l).create().show();
	}

	private class CallActionListener implements OnClickListener {
		private LinphoneCall call;
		private Dialog dialog;
		public CallActionListener(LinphoneCall call, Dialog dialog) {
			this.call = call;
			this.dialog = dialog;
		}
		public CallActionListener(LinphoneCall call) {
			this.call = call;
		}
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.merge_to_conference:
				lc().addToConference(call);
				break;
			case R.id.terminate_call:
				lc().terminateCall(call);
				break;
			case R.id.pause:
				lc().pauseCall(call);
				break;
			case R.id.resume:
				lc().resumeCall(call);
				break;
			case R.id.unhook_call:
				try {
					lc().acceptCall(call);
				} catch (LinphoneCoreException e) {
					throw new RuntimeException(e);
				}
				break;
			case R.id.transfer_existing:
				prepareForTransferingExistingCall(call);
				break;
			case R.id.transfer_new:
				openUriPicker(UriPickerActivity.EXTRA_PICKER_TYPE_TRANSFER, ID_TRANSFER_CALL);
				callToTransfer = call;	
				break;
			case R.id.remove_from_conference:
				lc().removeFromConference(call);
				break;
			default:
				throw new RuntimeException("unknown id " + v.getId());
			}
			if (dialog != null) dialog.dismiss();
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
				final LinphoneCall.State state = call.getState();
				boolean connectionEstablished = state == State.StreamsRunning
						|| state == State.Paused
						|| state == State.PausedByRemote;
				if (connectionEstablished)
					count++;
				if (count >= 2)
					return true;
			}
			return false;
		}

		private void setVisibility(View v, int id, boolean visible) {
			v.findViewById(id).setVisibility(visible ? VISIBLE : GONE);
		}
		private void setVisibility(View v, boolean visible) {
			v.setVisibility(visible ? VISIBLE : GONE);
		}
		private void setStatusLabel(View v, State state, boolean inConf, boolean activeOne) {
			String statusLabel = getStateText(state);

			if (activeOne)
				statusLabel=getString(R.string.status_active_call);

			if (inConf)
				statusLabel=getString(R.string.status_conf_call);
			
			((TextView) v.findViewById(R.id.status_label)).setText(statusLabel);
		}

		public View getView(int position, View v, ViewGroup parent) {
			Log.i("ConferenceActivity.getView(",position,") out of ", linphoneCalls.size());
			if (v == null) {
				if (Version.sdkAboveOrEqual(Version.API06_ECLAIR_20)) {
					v = getLayoutInflater().inflate(R.layout.conf_callee, null);
				} else {
					v = getLayoutInflater().inflate(R.layout.conf_callee_older_devices, null);
				}
			}

			final LinphoneCall call = linphoneCalls.get(position);
			final LinphoneCall.State state = call.getState();

			LinphoneAddress address = call.getRemoteAddress();
			String mainText = address.getDisplayName();
			String complText;
			if ((getResources().getBoolean(R.bool.show_full_remote_address_on_incoming_call))) {
				complText = address.getUserName() + "@" + address.getDomain();
			} else {
				complText = address.getUserName();
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

			final boolean isInConference = call.isInConference();
			boolean currentlyActiveCall = !isInConference
					&& state == State.StreamsRunning;

			setStatusLabel(v, state, isInConference, currentlyActiveCall);


			int bgDrawableId = R.drawable.conf_callee_selector_normal;
			if (state == State.IncomingReceived) {
				bgDrawableId = R.drawable.conf_callee_selector_incoming;
			} else if (currentlyActiveCall) {
				bgDrawableId = R.drawable.conf_callee_selector_active;
			} else if (isInConference) {
				bgDrawableId = R.drawable.conf_callee_selector_inconf;
			}
			v.setBackgroundResource(bgDrawableId);

			boolean connectionEstablished = state == State.StreamsRunning
					|| state == State.Paused
					|| state == State.PausedByRemote;
			View confButton = v.findViewById(R.id.merge_to_conference);
			final boolean showMergeToConf = !isInConference && connectionEstablished
					&& aConferenceIsPossible();
			setVisibility(confButton, false);

			View unhookCallButton = v.findViewById(R.id.unhook_call);
			boolean showUnhook = state == State.IncomingReceived;
			setVisibility(unhookCallButton, showUnhook);

			View terminateCallButton = v.findViewById(R.id.terminate_call);
			boolean showTerminate = state == State.IncomingReceived;
			setVisibility(terminateCallButton, showTerminate);

			View pauseButton = v.findViewById(R.id.pause);
			final boolean showPause = !isInConference
					&& state == State.StreamsRunning;
			setVisibility(pauseButton, false);

			View resumeButton = v.findViewById(R.id.resume);
			final boolean showResume = !isInConference
					&& state == State.Paused;
			setVisibility(resumeButton, false);

			View removeFromConfButton = v.findViewById(R.id.remove_from_conference);
			setVisibility(removeFromConfButton, false);
			
			final int numberOfCalls = linphoneCalls.size();
			boolean showAddVideo = !isInConference && !showUnhook && numberOfCalls == 1
					&& Version.isVideoCapable() && LinphoneManager.getInstance().isVideoEnabled();
			setVisibility(v, R.id.addVideo, showAddVideo);

			boolean statusPaused = state== State.Paused || state == State.PausedByRemote;
			setVisibility(v, R.id.callee_status_paused, statusPaused);

			setVisibility(v, R.id.callee_status_inconf, isInConference);
			
			final OnClickListener l = new CallActionListener(call);
			confButton.setOnClickListener(l);
			terminateCallButton.setOnClickListener(l);
			pauseButton.setOnClickListener(l);
			resumeButton.setOnClickListener(l);
			unhookCallButton.setOnClickListener(l);
			removeFromConfButton.setOnClickListener(l);

			v.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					View content = getLayoutInflater().inflate(R.layout.conf_choices_dialog, null);
					Dialog dialog = new AlertDialog.Builder(ConferenceActivity.this).setView(content).create();
					OnClickListener l = new CallActionListener(call, dialog);
					enableView(content, R.id.transfer_existing, l, !isInConference && numberOfCalls >=2);
					enableView(content, R.id.transfer_new, l, !isInConference);
					enableView(content, R.id.remove_from_conference, l, isInConference);
					enableView(content, R.id.merge_to_conference, l, showMergeToConf);
					enableView(content, R.id.pause, l,!isInConference && showPause);
					enableView(content, R.id.resume, l, !isInConference && showResume);
					enableView(content, R.id.terminate_call, l, true);
					dialog.show();
				}
			});

			ImageView pictureView = (ImageView) v.findViewById(R.id.picture);
			if (numberOfCalls != 1) {
				// May be greatly sped up using a drawable cache
				Uri uri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, getContentResolver());
				LinphoneUtils.setImagePictureFromUri(ConferenceActivity.this, pictureView, uri, R.drawable.unknown_person);
				pictureView.setVisibility(VISIBLE);
			} else {
				pictureView.setVisibility(GONE);
			}

			return v;
		}
	}

	private String getStateText(State state) {
		int id;
		if (state == State.IncomingReceived) {
			id=R.string.state_incoming_received;
		} else if (state == State.OutgoingRinging) {
			id=R.string.state_outgoing_ringing;
		} else if (state == State.Paused) {
			id=R.string.state_paused;
		} else if (state == State.PausedByRemote) {
			id=R.string.state_paused_by_remote;
		} else {
			return "";
		}
		return getString(id);
	}

	private Handler mHandler = new Handler();

	private void updateSimpleControlButtons() {
		LinphoneCall activeCall = lc().getCurrentCall();
		View controlLayout = findViewById(R.id.conf_control_buttons);
		int callNb = lc().getCallsNb();
		boolean hide = activeCall == null || callNb !=2 || lc().getConferenceSize() > 0;
		controlLayout.setVisibility(hide ? GONE : VISIBLE);
	}

	public void onCallStateChanged(final LinphoneCall call, final State state,
			final String message) {
		final String stateStr = call + " " + state.toString();
		Log.d("ConferenceActivity received state ",stateStr);
		mHandler.post(new Runnable() {
			public void run() {
				CalleeListAdapter adapter = (CalleeListAdapter) getListAdapter();
				Log.d("ConferenceActivity applying state ",stateStr);
				updateSimpleControlButtons();
				updateCalleeImage();
				if (state == State.IncomingReceived || state == State.OutgoingRinging) {
					if (!adapter.linphoneCalls.contains(call)) {
						adapter.linphoneCalls.add(call);
						Collections.sort(adapter.linphoneCalls,
								ConferenceActivity.this);
						adapter.notifyDataSetInvalidated();
						adapter.notifyDataSetChanged();
					} else {
						Log.e("Call should not be in the call lists : " + stateStr);
					}
				} else if (state == State.Paused || state == State.PausedByRemote || state == State.StreamsRunning) {
					Collections.sort(adapter.linphoneCalls,
							ConferenceActivity.this);
					adapter.notifyDataSetChanged();
				} else if (state == State.CallEnd) {
					adapter.linphoneCalls.remove(call);
					Collections.sort(adapter.linphoneCalls,
							ConferenceActivity.this);
					adapter.notifyDataSetInvalidated();
					adapter.notifyDataSetChanged();
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

		int durationDiff = c2.getDuration() - c1.getDuration();
		return durationDiff;

	}

	private LinphoneCall callToTransfer;
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (unMuteOnReturnFromUriPicker) {
			lc().muteMic(false);
			((ToggleButton) findViewById(R.id.toggleMuteMic)).setChecked(false);
		}

		if (resultCode != RESULT_OK) {
			callToTransfer = null;
			Toast.makeText(this, R.string.uri_picking_canceled, Toast.LENGTH_LONG).show();
			return;
		}

		String uri = data.getStringExtra(UriPickerActivity.EXTRA_CALLEE_URI);
		switch (requestCode) {
		case ID_ADD_CALL:
			try {
				lc().invite(uri);
			} catch (LinphoneCoreException e) {
				Log.e(e);
				Toast.makeText(this, R.string.error_adding_new_call, Toast.LENGTH_LONG).show();
			}
			break;
		case ID_TRANSFER_CALL:
			lc().transferCall(callToTransfer, uri);
			Toast.makeText(this, R.string.transfer_started, Toast.LENGTH_LONG).show();
			break;
		default:
			throw new RuntimeException("unhandled request code " + requestCode);
		}
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyBackGoHome(this, keyCode)) return true;
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeSoftAdjust(keyCode)) return true;
		return super.onKeyDown(keyCode, event);
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
	 * boolean c1StateIsEstablishing = c1State == State.IncomingReceived ||
	 * c1State == State.ID_OUTGOING_RINGING; boolean c2StateIsEstablishing =
	 * c2State == State.IncomingReceived || c2State ==
	 * State.ID_OUTGOING_RINGING;
	 * 
	 * // Xor only one establishing state if (c1StateIsEstablishing ^
	 * c2StateIsEstablishing) { // below return !c1StateIsEstablishing ? -1 : 1;
	 * }
	 * 
	 * // Xor only one paused state if (c1State == State.Paused ^ c2State ==
	 * State.Paused) { return c1State == State.Paused ? -1 : 1; }
	 * 
	 * return compUserName; //Duration() - c1.getDuration(); }
	 */
}
