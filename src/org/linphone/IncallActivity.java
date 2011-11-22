/*
IncallActivity.java
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

import org.linphone.LinphoneSimpleListener.LinphoneOnAudioChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnCallEncryptionChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.Log;
import org.linphone.core.LinphoneCall.State;
import org.linphone.mediastream.Version;
import org.linphone.ui.Numpad;

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
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
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
public class IncallActivity extends ListActivity implements
		LinphoneOnAudioChangedListener,
		LinphoneOnCallStateChangedListener,
		LinphoneOnCallEncryptionChangedListener,
		Comparator<LinphoneCall>,
		OnLongClickListener,
		OnClickListener {

	static boolean active;

	private boolean mUnMuteOnReturnFromUriPicker;

	// Start Override to test block
	protected LinphoneCore lc() {
		return LinphoneManager.getLc();
	}

	// End override to test block

	private static final int numpad_dialog_id = 1;
	private static final int ID_ADD_CALL = 1;
	private static final int ID_TRANSFER_CALL = 2;



	private void pauseCurrentCallOrLeaveConference() {
		LinphoneCall call = lc().getCurrentCall();
		if (call != null) lc().pauseCall(call);
		lc().leaveConference();
	}

	private ToggleButton mMuteMicButton;
	private ToggleButton mSpeakerButton;
	private View mConferenceVirtualCallee;
	private int mMultipleCallsLimit;
	private boolean mAllowTransfers;
	private List<LinphoneCall> mNotInConfCalls = Collections.emptyList();
	private CalleeListAdapter mListAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.incall_layout);

		mAllowTransfers = getResources().getBoolean(R.bool.allow_transfers);

		findViewById(R.id.addCall).setOnClickListener(this);

		findViewById(R.id.incallNumpadShow).setOnClickListener(this);
		findViewById(R.id.conf_simple_merge).setOnClickListener(this);
		View transferView = findViewById(R.id.conf_simple_transfer);
		transferView.setOnClickListener(this);
		if (!mAllowTransfers) {
			transferView.setVisibility(View.GONE);
		}

		mMuteMicButton = (ToggleButton) findViewById(R.id.toggleMuteMic);
		mMuteMicButton.setOnClickListener(this);
		mSpeakerButton = (ToggleButton) findViewById(R.id.toggleSpeaker);
		mSpeakerButton.setOnClickListener(this);

		setListAdapter(mListAdapter = new CalleeListAdapter());
		
		findViewById(R.id.incallHang).setOnClickListener(this);
		mMultipleCallsLimit = lc().getMaxCalls();

		mConferenceVirtualCallee = findViewById(R.id.conf_header);
		mConferenceVirtualCallee.setOnClickListener(this);
		mConferenceVirtualCallee.setOnLongClickListener(this);
//		workaroundStatusBarBug();
		super.onCreate(savedInstanceState);
	}

	private void updateSoundLock() {
		boolean locked = lc().soundResourcesLocked();
		findViewById(R.id.addCall).setEnabled(!locked);
	}

	private void updateAddCallButton() {
		boolean limitReached = false;
		if (mMultipleCallsLimit > 0) {
			limitReached = lc().getCallsNb() >= mMultipleCallsLimit;
		}

		int establishedCallsNb = LinphoneUtils.getRunningOrPausedCalls(lc()).size();
		boolean hideButton = limitReached || establishedCallsNb == 0;
		findViewById(R.id.addCall).setVisibility(hideButton? GONE : VISIBLE);
	}

	private void updateDtmfButton() {
		LinphoneCall currentCall = lc().getCurrentCall();
		boolean enableDtmf = currentCall != null && currentCall.getState() == State.StreamsRunning;
		findViewById(R.id.incallNumpadShow).setEnabled(enableDtmf);
	}
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
	}

	@Override
	protected void onResume() {
		active=true;
		mNotInConfCalls = LinphoneUtils.getLinphoneCallsNotInConf(lc());
		LinphoneManager.addListener(this);
		LinphoneManager.startProximitySensorForActivity(this);
		mSpeakerButton.setChecked(LinphoneManager.getInstance().isSpeakerOn());
		mMuteMicButton.setChecked(LinphoneManager.getLc().isMicMuted());

		recreateActivity();
		super.onResume();
	}

	@Override
	protected void onPause() {
		active=false;
		LinphoneManager.removeListener(this);
		LinphoneManager.stopProximitySensorForActivity(this);
		super.onPause();
	}

	private void updateCalleeImage() {
		ImageView view = (ImageView) findViewById(R.id.incall_picture);
		LinphoneCall currentCall = lc().getCurrentCall();

		if (currentCall == null || lc().getCallsNb() != 1) {
			view.setVisibility(GONE);
			return;
		}

		Uri picture = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(
				currentCall.getRemoteAddress(),	getContentResolver());
		LinphoneUtils.setImagePictureFromUri(this, view, picture, R.drawable.unknown_person);
		view.setVisibility(VISIBLE);
	}

	private void enableView(View root, int id, OnClickListener l, boolean enable) {
		LinphoneUtils.enableView(root, id, l, enable);
	}

	@Override
	protected Dialog onCreateDialog(final int id) {
		switch (id) {
		case numpad_dialog_id:
			Numpad numpad = new Numpad(this, true);
			return new AlertDialog.Builder(this).setView(numpad)
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

	private LinphoneCall activateCallOnReturnFromUriPicker;
	private boolean enterConferenceOnReturnFromUriPicker;
	private void openUriPicker(String pickerType, int requestCode) {
		if (lc().soundResourcesLocked()) {
			Toast.makeText(this, R.string.not_ready_to_make_new_call, Toast.LENGTH_LONG).show();
			return;
		}
		activateCallOnReturnFromUriPicker = lc().getCurrentCall();
		enterConferenceOnReturnFromUriPicker = lc().isInConference();
		pauseCurrentCallOrLeaveConference();
		Intent intent = new Intent().setClass(this, UriPickerActivity.class);
		intent.putExtra(UriPickerActivity.EXTRA_PICKER_TYPE, pickerType);
		startActivityForResult(intent, requestCode);
		if (!lc().isMicMuted()) {
			mUnMuteOnReturnFromUriPicker = true;
			lc().muteMic(true);
			((ToggleButton) findViewById(R.id.toggleMuteMic)).setChecked(true);
		}
	}

	
	@Override
	public boolean onLongClick(View v) {
		switch (v.getId()) {
		case R.id.conf_header:
			if (Version.sdkAboveOrEqual(Version.API05_ECLAIR_20)) {
				LinphoneActivity.instance().startConferenceDetailsActivity();
			} else {
				Log.i("conference details disabled for older phones");
			}
			break;
		default:
			break;
		}
		return false;
	}

	private void enterConferenceAndUpdateUI(boolean enterConf) {
		if (enterConf) {
			boolean success = lc().enterConference();
			if (success) {
				mConferenceVirtualCallee.setBackgroundResource(R.drawable.conf_callee_active_bg);
			}
		} else {
			lc().leaveConference();
			mConferenceVirtualCallee.setBackgroundResource(R.drawable.conf_callee_bg);
		}
	}
	
	private void terminateCurrentCallOrConferenceOrAll() {
		LinphoneCall currentCall = lc().getCurrentCall();
		if (currentCall != null) {
			lc().terminateCall(currentCall);
		} else if (lc().isInConference()) {
			lc().terminateConference();
		} else {
			lc().terminateAllCalls();
		}

		// activity should be closed automatically by LinphoneActivity when no more calls exist
		//		setResult(RESULT_OK);
		//		finish();
	}

	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.addCall:
			openUriPicker(UriPickerActivity.EXTRA_PICKER_TYPE_ADD, ID_ADD_CALL);
			break;
		case R.id.incallHang:
			terminateCurrentCallOrConferenceOrAll();
			break;
		case R.id.conf_header:
			boolean enterConf = !lc().isInConference();
			enterConferenceAndUpdateUI(enterConf);
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
				prepareForTransferingExistingOrNewCall(tCall);
			} else {
				Toast.makeText(this, R.string.conf_simple_no_current_call, Toast.LENGTH_SHORT).show();
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

	private void prepareForTransferingExistingOrNewCall(final LinphoneCall call) {
		final List<LinphoneCall> existingCalls = LinphoneUtils.getLinphoneCalls(lc());
		if (existingCalls.size() == 1) {
			// Only possible choice is transfer to new call: doing it directly.
			mCallToTransfer = call;
			openUriPicker(UriPickerActivity.EXTRA_PICKER_TYPE_TRANSFER, ID_TRANSFER_CALL);
			return;
		}
		existingCalls.remove(call);
		final List<String> numbers = new ArrayList<String>(existingCalls.size() + 1);
		Resources r = getResources();
		for(LinphoneCall c : existingCalls) {
			numbers.add(LinphoneManager.extractADisplayName(r, c.getRemoteAddress()));
		}
		numbers.add(getString(R.string.transfer_to_new_call));
		ListAdapter adapter = new ArrayAdapter<String>(IncallActivity.this, android.R.layout.select_dialog_item, numbers);
		DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (which == numbers.size() -1) {
					// Last one is transfer to new call
					mCallToTransfer = call;
					openUriPicker(UriPickerActivity.EXTRA_PICKER_TYPE_TRANSFER, ID_TRANSFER_CALL);
				} else {
					lc().transferCallToAnother(call, existingCalls.get(which));
				}
			}
		};
		new AlertDialog.Builder(IncallActivity.this).setTitle(R.string.transfer_dialog_title).setAdapter(adapter, l).create().show();
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
			case R.id.transfer_existing:
				prepareForTransferingExistingOrNewCall(call);
				break;
			case R.id.transfer_new:
				openUriPicker(UriPickerActivity.EXTRA_PICKER_TYPE_TRANSFER, ID_TRANSFER_CALL);
				mCallToTransfer = call;	
				break;
			case R.id.addVideo:
				if (!LinphoneManager.getInstance().addVideo()) {
					LinphoneActivity.instance().startVideoActivity(call, 0);
				}
				break;
			case R.id.set_auth_token_verified:
				call.setAuthenticationTokenVerified(true);
				break;
			case R.id.set_auth_token_not_verified:
				call.setAuthenticationTokenVerified(false);
				break;
			case R.id.encrypted:
				call.setAuthenticationTokenVerified(!call.isAuthenticationTokenVerified());
				break;
			default:
				throw new RuntimeException("unknown id " + v.getId());
			}
			if (dialog != null) dialog.dismiss();
		}
	}
	private class CalleeListAdapter extends BaseAdapter {
		public int getCount() {
			return mNotInConfCalls.size();
		}

		public Object getItem(int position) {
			return mNotInConfCalls.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		private boolean aConferenceIsPossible() {
			if (lc().getCallsNb() < 2) {
				return false;
			}
			int count = 0;
			for (LinphoneCall call : mNotInConfCalls) {
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
			LinphoneUtils.setVisibility(v, id, visible);
		}
		private void setVisibility(View v, boolean visible) {
			LinphoneUtils.setVisibility(v, visible);
		}
		private void setStatusLabel(View v, State state, boolean activeOne) {
			String statusLabel = getStateText(state);
			((TextView) v.findViewById(R.id.status_label)).setText(statusLabel);
		}

		private boolean highlightCall(LinphoneCall call) {
			final State state = call.getState();
			return state == State.StreamsRunning 
			|| state == State.OutgoingRinging
			|| state == State.OutgoingEarlyMedia
			|| state == State.OutgoingInit
			|| state == State.OutgoingProgress
			;
		}

		public View getView(int position, View v, ViewGroup parent) {
			Log.i("IncallActivity.getView(",position,") out of ", mNotInConfCalls.size());
			if (v == null) {
				if (Version.sdkAboveOrEqual(Version.API06_ECLAIR_201)) {
					v = getLayoutInflater().inflate(R.layout.conf_callee, null);
				} else {
					v = getLayoutInflater().inflate(R.layout.conf_callee_older_devices, null);
				}
			}

			final LinphoneCall call = mNotInConfCalls.get(position);
			final LinphoneCall.State state = call.getState();

			LinphoneAddress address = call.getRemoteAddress();
			String mainText = address.getDisplayName();
			String complText = address.getUserName();
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

			boolean highlighted = highlightCall(call);
			setStatusLabel(v, state, highlighted);


			int bgDrawableId = R.drawable.conf_callee_selector_normal;
			if (state == State.IncomingReceived) {
				bgDrawableId = R.drawable.conf_callee_selector_incoming;
			} else if (highlighted) {
				bgDrawableId = R.drawable.conf_callee_selector_active;
			}
			v.setBackgroundResource(bgDrawableId);

			boolean connectionEstablished = state == State.StreamsRunning
					|| state == State.Paused
					|| state == State.PausedByRemote;
			View confButton = v.findViewById(R.id.merge_to_conference);
			final boolean showMergeToConf = connectionEstablished && aConferenceIsPossible();
			setVisibility(confButton, false);

			final int numberOfCalls = mNotInConfCalls.size();
			boolean showAddVideo = State.StreamsRunning == state
					&& Version.isVideoCapable()
					&& LinphoneManager.getInstance().isVideoEnabled();
			View addVideoButton = v.findViewById(R.id.addVideo);
			setVisibility(addVideoButton, showAddVideo);

			boolean statusPaused = state== State.Paused || state == State.PausedByRemote;
			setVisibility(v, R.id.callee_status_paused, statusPaused);

			final OnClickListener l = new CallActionListener(call);
			confButton.setOnClickListener(l);
			addVideoButton.setOnClickListener(l);

			String mediaEncryption = call.getCurrentParamsCopy().getMediaEncryption();
			if ("none".equals(mediaEncryption)) {
				setVisibility(v, R.id.callee_status_secured, false);
				setVisibility(v, R.id.callee_status_maybe_secured, false);
				setVisibility(v, R.id.callee_status_not_secured, false);
			} else {
				boolean reallySecured = !Version.hasZrtp() || call.isAuthenticationTokenVerified();
				setVisibility(v, R.id.callee_status_secured, reallySecured);
				setVisibility(v, R.id.callee_status_maybe_secured, !reallySecured);
				setVisibility(v, R.id.callee_status_not_secured, false);
			}

			v.setOnLongClickListener(new OnLongClickListener() {
				public boolean onLongClick(View v) {
					if (lc().soundResourcesLocked()) {
						return false;
					}
					View content = getLayoutInflater().inflate(R.layout.conf_choices_dialog, null);
					Dialog dialog = new AlertDialog.Builder(IncallActivity.this).setView(content).create();
					OnClickListener l = new CallActionListener(call, dialog);
					enableView(content, R.id.transfer_existing, l, mAllowTransfers && numberOfCalls >=2);
					enableView(content, R.id.transfer_new, l, mAllowTransfers);
					enableView(content, R.id.merge_to_conference, l, showMergeToConf);
					enableView(content, R.id.terminate_call, l, true);

					String mediaEncryption = call.getCurrentParamsCopy().getMediaEncryption();
					if ("none".equals(mediaEncryption)) {
						boolean showUnencrypted = Version.hasZrtp();
						setVisibility(content, R.id.unencrypted, showUnencrypted);
					} else {
						TextView token = (TextView) content.findViewById(R.id.authentication_token);
						if ("zrtp".equals(mediaEncryption)) {
							boolean authVerified = call.isAuthenticationTokenVerified();
							String fmt = getString(authVerified ? R.string.reset_sas_fmt : R.string.validate_sas_fmt);
							token.setText(String.format(fmt, call.getAuthenticationToken()));
							enableView(content, R.id.set_auth_token_not_verified, l, authVerified);
							enableView(content, R.id.set_auth_token_verified, l, !authVerified);
							enableView(content, R.id.encrypted, l, true);
						} else {
							setVisibility(content, R.id.encrypted, true);
							token.setText(R.string.communication_encrypted);
						}
					}
					
					dialog.show();
					return true;
				}
			});
			
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					State actualState = call.getState();
					if (State.StreamsRunning == actualState) {
						lc().pauseCall(call);
					} else if (State.Paused == actualState) {
						lc().resumeCall(call);
					} else if (State.PausedByRemote == actualState) {
						Toast.makeText(IncallActivity.this, getString(R.string.cannot_resume_paused_by_remote_call), Toast.LENGTH_SHORT).show();
					}
				}
			});

			ImageView pictureView = (ImageView) v.findViewById(R.id.picture);
			if (numberOfCalls != 1) {
				// May be greatly sped up using a drawable cache
				Uri uri = LinphoneUtils.findUriPictureOfContactAndSetDisplayName(address, getContentResolver());
				LinphoneUtils.setImagePictureFromUri(IncallActivity.this, pictureView, uri, R.drawable.unknown_person);
				pictureView.setVisibility(VISIBLE);
			} else {
				pictureView.setVisibility(GONE);
			}

			
			return v;
		}
	}

	private String getStateText(State state) {
		int id;
		if (state == State.StreamsRunning) {
			id=R.string.status_active_call;
		} else if (state == State.Paused) {
			id=R.string.state_paused;
		} else if (state == State.PausedByRemote) {
			id=R.string.state_paused_by_remote;
		} else if (state == State.IncomingReceived) {
			id=R.string.state_incoming_received;
		} else if (state == State.OutgoingRinging) {
			id=R.string.state_outgoing_ringing;
		} else {
			return "";
		}
		return getString(id);
	}

	private Handler mHandler = new Handler();

	private void updateSimpleControlButtons() {
		LinphoneCall activeCall = lc().getCurrentCall();
		View control = findViewById(R.id.conf_control_buttons);
		int nonConfCallsNb = LinphoneUtils.countNonConferenceCalls(lc());

		View merge = control.findViewById(R.id.conf_simple_merge);
		boolean showMerge = nonConfCallsNb >=2
			|| (lc().getConferenceSize() > 0 && nonConfCallsNb > 0);
		merge.setVisibility(showMerge ? VISIBLE : GONE);

		View transfer = control.findViewById(R.id.conf_simple_transfer);
		boolean showTransfer = mAllowTransfers && activeCall != null;
		transfer.setVisibility(showTransfer ? VISIBLE : GONE);

		boolean showControl = showMerge || showTransfer;
		control.setVisibility(showControl ? VISIBLE : GONE);
	}

	public void onCallStateChanged(final LinphoneCall c, final State s, String m) {
		mHandler.post(new Runnable() {
			public void run() {
				mNotInConfCalls = LinphoneUtils.getLinphoneCallsNotInConf(lc());
				recreateActivity();
			}
		});
	}

	private void updateConfItem() {
		boolean confExists = lc().getConferenceSize() > 0;
		View confView = findViewById(R.id.conf_header);
		confView.setVisibility(confExists? VISIBLE : GONE);

		if (confExists) {
			if (lc().isInConference()) {
				confView.setBackgroundResource(R.drawable.conf_callee_selector_active);
			} else {
				confView.setBackgroundResource(R.drawable.conf_callee_selector_normal);
			}
		}
	}

	private void recreateActivity() {
		updateSimpleControlButtons();
		updateCalleeImage();
		updateSoundLock();
		updateAddCallButton();
		updateDtmfButton();
		updateConfItem();
		
		mListAdapter.notifyDataSetChanged();
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

	private boolean checkValidTargetUri(String uri) {
		boolean invalidUri;
		try {
			String target = lc().interpretUrl(uri).asStringUriOnly();
			invalidUri = lc().isMyself(target);
		} catch (LinphoneCoreException e) {
			invalidUri = true;
		}

		if (invalidUri) {
			String msg = String.format(getString(R.string.bad_target_uri), uri);
			Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
			return false;
		}
		return true;
	}
	
	private LinphoneCall mCallToTransfer;
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (mUnMuteOnReturnFromUriPicker) {
			lc().muteMic(false);
			((ToggleButton) findViewById(R.id.toggleMuteMic)).setChecked(false);
		}

		String uri = null;
		if (data != null) {
			uri = data.getStringExtra(UriPickerActivity.EXTRA_CALLEE_URI);
		}
		if (resultCode != RESULT_OK || TextUtils.isEmpty(uri)) {
			mCallToTransfer = null;
			Toast.makeText(this, R.string.uri_picking_canceled, Toast.LENGTH_LONG).show();
			eventuallyResumeConfOrCallOnPickerReturn(true);
			return;
		}


		if (!checkValidTargetUri(uri)) {
			eventuallyResumeConfOrCallOnPickerReturn(true);
			return;
		}

		if (lc().soundResourcesLocked()) {
			Toast.makeText(this, R.string.not_ready_to_make_new_call, Toast.LENGTH_LONG).show();
			eventuallyResumeConfOrCallOnPickerReturn(true);
			return;
		}
		
		switch (requestCode) {
		case ID_ADD_CALL:
			try {
				lc().invite(uri);
				eventuallyResumeConfOrCallOnPickerReturn(false);
			} catch (LinphoneCoreException e) {
				Log.e(e);
				Toast.makeText(this, R.string.error_adding_new_call, Toast.LENGTH_LONG).show();
			}
			break;
		case ID_TRANSFER_CALL:
			lc().transferCall(mCallToTransfer, uri);
			// don't re-enter conference if call to transfer from conference
			boolean doResume = !mCallToTransfer.isInConference();
			// don't resume call if it is the call to transfer
			doResume &= activateCallOnReturnFromUriPicker != mCallToTransfer;
			eventuallyResumeConfOrCallOnPickerReturn(doResume);
			Toast.makeText(this, R.string.transfer_started, Toast.LENGTH_LONG).show();
			break;
		default:
			throw new RuntimeException("unhandled request code " + requestCode);
		}
	}

	private void eventuallyResumeConfOrCallOnPickerReturn(boolean doCallConfResuming) {
		if (doCallConfResuming) {
			if (activateCallOnReturnFromUriPicker != null) {
				lc().resumeCall(activateCallOnReturnFromUriPicker);
			} else if (enterConferenceOnReturnFromUriPicker) {
				enterConferenceAndUpdateUI(true);
			}
		}
		activateCallOnReturnFromUriPicker = null;
		enterConferenceOnReturnFromUriPicker = false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeSoftAdjust(keyCode)) return true;
		if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onAudioStateChanged(final AudioState state) {
		mSpeakerButton.post(new Runnable() {
			@Override
			public void run() {
				switch (state) {
				case SPEAKER:
					mSpeakerButton.setChecked(true);
					break;
				case EARPIECE:
					mSpeakerButton.setChecked(false);
					break;
				default:
					throw new RuntimeException("Unknown audio state " + state);
				}
			}
		});
	}

	@Override
	public void onCallEncryptionChanged(LinphoneCall call, boolean encrypted,
			String authenticationToken) {
		mHandler.post(new Runnable() {
			public void run() {
				recreateActivity();
			}
		});
	}

}
