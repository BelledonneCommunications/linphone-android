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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.linphone.LinphoneSimpleListener.LinphoneOnAudioChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnCallEncryptionChangedListener;
import org.linphone.LinphoneSimpleListener.LinphoneOnCallStateChangedListener;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.Log;
import org.linphone.mediastream.Version;
import org.linphone.ui.Numpad;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Guillaume Beraudo
 */
public class IncallActivity extends AbstractCalleesActivity implements
		LinphoneOnAudioChangedListener,
		LinphoneOnCallStateChangedListener,
		LinphoneOnCallEncryptionChangedListener,
		Comparator<LinphoneCall>,
		OnLongClickListener,
		OnClickListener {

	private boolean mUnMuteOnReturnFromUriPicker;

	private static final int numpadDialogId = 1;
	private static final int addCallId = 1;
	private static final int transferCallId = 2;
	private static final int promptVideoId = 3;

	private static IncallActivity instance;
	private CountDownTimer timer;
	public static boolean active;
	@Override protected synchronized void setActive(boolean a) {active = a;}
	@Override protected synchronized boolean isActive() {return active;}

	public static boolean isReady() {
		return instance!=null;
	}
	
	static IncallActivity instance()  {
		if (isReady()) return instance;
		return null;
	}
	
	private void pauseCurrentCallOrLeaveConference() {
		LinphoneCall call = lc().getCurrentCall();
		if (call != null && !call.isInConference()) {
			lc().pauseCall(call);
		} else {
			lc().leaveConference();
			updateConfItem();
		}
	}

	private View mConferenceVirtualCallee;
	private int mMultipleCallsLimit;
	private boolean mAllowTransfers;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {	
		setActive(true);	
		
		if (finishIfAutoRestartAfterACrash(savedInstanceState)) {
			setActive(false);
			return;
		}
		
		if (!Version.isXLargeScreen(this))
		    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		setContentView(R.layout.incall_layout);
		instance = this;
		
		mAllowTransfers = getResources().getBoolean(R.bool.allow_transfers);

		findViewById(R.id.addCall).setOnClickListener(this);

		findViewById(R.id.incallNumpadShow).setOnClickListener(this);
		findViewById(R.id.conf_simple_merge).setOnClickListener(this);
		findViewById(R.id.conf_simple_pause).setOnClickListener(this);

		findViewById(R.id.incallHang).setOnClickListener(this);
		mMultipleCallsLimit = lc().getMaxCalls();

		mConferenceVirtualCallee = findViewById(R.id.conf_header);
		mConferenceVirtualCallee.setOnClickListener(this);
		mConferenceVirtualCallee.setOnLongClickListener(this);
		enableView(mConferenceVirtualCallee, R.id.conf_header_details, this, true);


		boolean mMayDoVideo = Version.isVideoCapable() && LinphoneManager.getInstance().isVideoEnabled();
		if (mMayDoVideo) {
			findViewById(R.id.conf_simple_video).setOnClickListener(this);
		} else {
			findViewById(R.id.conf_simple_video).setVisibility(View.GONE);
		}
		super.onCreate(savedInstanceState);
	}
	
	@Override
	protected void onPause() {
		setActive(false);
		instance = null;
		super.onPause();
	}

	@Override
	protected boolean shouldFinishCalleeActivity() {
		return lc().getCallsNb() == 0;
	}
	
	@Override
	protected CalleeListAdapter createCalleeListAdapter() {
		return new IncallListAdapter();
	}



	@Override
	protected List<LinphoneCall> updateSpecificCallsList() {
		return LinphoneUtils.getLinphoneCallsNotInConf(lc());
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
		findViewById(R.id.addCall).setVisibility(limitReached? View.INVISIBLE : VISIBLE);
	}

	private void updateNumpadButton() {
		LinphoneCall currentCall = lc().getCurrentCall();
		boolean enable = currentCall != null && currentCall.getState() == State.StreamsRunning;
		findViewById(R.id.incallNumpadShow).setEnabled(enable);
	}

	private void updateCalleeImage() {
		ImageView view = (ImageView) findViewById(R.id.incall_picture);
		LinphoneCall currentCall = lc().getCurrentCall();

		if (currentCall == null || lc().getCallsNb() != 1) {
			view.setVisibility(GONE);
			return;
		}

		setCalleePicture(view, currentCall.getRemoteAddress());
		view.setVisibility(VISIBLE);
	}

	private void acceptCallUpdate(boolean accept, int id) {
		 removeDialog(id);
		 timer.cancel();
		 
		 LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		 if (call == null)
			 return;
		 
		 LinphoneCallParams params = call.getCurrentParamsCopy();
		 if (accept) {
			 params.setVideoEnabled(true);
			 LinphoneManager.getLc().enableVideo(true, true);
		 }
		 
		 try {
			LinphoneManager.getLc().acceptCallUpdate(call, params);
		 } catch (LinphoneCoreException e) {
			e.printStackTrace();
		 }
		 
		 updateUI();
	}

	@Override
	protected Dialog onCreateDialog(final int id) {
		switch (id) {
		case promptVideoId:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.dynamic_video_asking);
			builder.setNegativeButton(R.string.dynamic_video_deny, new
					 DialogInterface.OnClickListener() {
						 public void onClick(DialogInterface dialog, int whichButton)
						 {
							 acceptCallUpdate(false, id);
						 }
					 });
			builder.setPositiveButton(R.string.dynamic_video_accept, new
					 DialogInterface.OnClickListener() {
						 public void onClick(DialogInterface dialog, int whichButton)
						 {
							 acceptCallUpdate(true, id);
						 }
					 });
			return builder.create();
		case numpadDialogId:
			Numpad numpad = new Numpad(this, true);
			return new AlertDialog.Builder(this).setView(numpad)
			// .setIcon(R.drawable.logo_linphone_57x57)
					// .setTitle("Send DTMFs")
					 .setPositiveButton(R.string.close_button_text, new
					 DialogInterface.OnClickListener() {
						 public void onClick(DialogInterface dialog, int whichButton)
							 {
							 	dismissDialog(id);
							 }
						 })
					.create();
		default:
			throw new IllegalArgumentException("unkown dialog id " + id);
		}
	}

	private LinphoneCall mActivateCallOnReturnFromUriPicker;
	private boolean mEnterConferenceOnReturnFromUriPicker;
	private void openUriPicker(String pickerType, int requestCode) {
		if (lc().soundResourcesLocked()) {
			Toast.makeText(this, R.string.not_ready_to_make_new_call, Toast.LENGTH_LONG).show();
			return;
		}
		mActivateCallOnReturnFromUriPicker = lc().getCurrentCall();
		mEnterConferenceOnReturnFromUriPicker = lc().isInConference();
		pauseCurrentCallOrLeaveConference();
		Intent intent = new Intent().setClass(this, UriPickerActivity.class);
		intent.putExtra(UriPickerActivity.EXTRA_PICKER_TYPE, pickerType);
		startActivityForResult(intent, requestCode);
		if (!lc().isMicMuted()) {
			mUnMuteOnReturnFromUriPicker = true;
			lc().muteMic(true);
			((Checkable) findViewById(R.id.toggleMuteMic)).setChecked(true);
		}
	}

	
	@Override
	public boolean onLongClick(View v) {
		if (v.getId() == R.id.conf_header || v.getId() == R.id.conf_header_details) {
			if (!lc().isInConference()) {
				// make sure we are in the conference
				// especially due to the difficulty to aim at the detail button.
				lc().enterConference();
			}
			LinphoneActivity.instance().startConferenceDetailsActivity();
			return true;
		}
		return false;
	}

	private void enterConferenceAndVirtualConfView(boolean enterConf) {
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

		// activity will be closed automatically by LinphoneActivity when no more calls exist
	}

	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.addCall) {
			finish();
		}
		else if (id == R.id.incallHang) {
			terminateCurrentCallOrConferenceOrAll();
		}
		else if (id == R.id.conf_header) {
			boolean enterConf = !lc().isInConference();
			enterConferenceAndVirtualConfView(enterConf);
		}
		else if (id == R.id.conf_header_details) {
			onLongClick(v);
		}
		else if (id == R.id.incallNumpadShow) {
			showDialog(numpadDialogId);
		}
		else if (id == R.id.conf_simple_merge) {
			if (!lc().soundResourcesLocked()) {
				lc().addAllToConference();
			}
		}
		else if (id == R.id.conf_simple_pause) {
			LinphoneCall call = lc().getCurrentCall();
			if (call != null) {
				lc().pauseCall(call);
			} else {
				((Checkable) v).setChecked(true);
				List<LinphoneCall> pausedCalls = LinphoneUtils.getCallsInState(lc(), Arrays.asList(State.Paused));
				if (pausedCalls.size() == 1) {
					LinphoneCall callToResume = pausedCalls.get(0);
					lc().resumeCall(callToResume);
				}
			}
		}
		else if (id == R.id.conf_simple_video) {
			LinphoneCall vCall = lc().getCurrentCall();
			if (vCall != null) {
				if (!vCall.cameraEnabled() && vCall.getCurrentParamsCopy().getVideoEnabled()) {
					// NoWebcam mode, we let it this way
					LinphoneActivity.instance().startVideoActivity(vCall, 0);
				}
				else if (!LinphoneManager.getInstance().addVideo()) {
					LinphoneActivity.instance().startVideoActivity(vCall, 0);
				}
			}
		}
		else {
			// mic, speaker
			super.onClick(v);
		}
	}

//	private void doTransfer() {
//		LinphoneCall tCall = lc().getCurrentCall();
//		if (tCall != null) {
//			prepareForTransferingExistingOrNewCall(tCall);
//		}
//	}
	
	private void prepareForTransferingExistingOrNewCall(final LinphoneCall call) {
		// Include inconf calls
		final List<LinphoneCall> existingCalls = LinphoneUtils.getLinphoneCalls(lc());
		if (existingCalls.size() == 1) {
			// Only possible choice is transfer to new call: doing it directly.
			mCallToTransfer = call;
			openUriPicker(UriPickerActivity.EXTRA_PICKER_TYPE_TRANSFER, transferCallId);
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
					openUriPicker(UriPickerActivity.EXTRA_PICKER_TYPE_TRANSFER, transferCallId);
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
		public void onClick(View v) {
			int id =v.getId();
			if (id == R.id.merge_to_conference) {
				lc().addToConference(call);
			}
			else if (id == R.id.terminate_call) {
				lc().terminateCall(call);
			}
			else if (id == R.id.transfer_existing) {
				prepareForTransferingExistingOrNewCall(call);
			}
			else if (id == R.id.transfer_new) {
				openUriPicker(UriPickerActivity.EXTRA_PICKER_TYPE_TRANSFER, transferCallId);
				mCallToTransfer = call;	
			}
			else if (id == R.id.addVideo) {
				if (!call.cameraEnabled() && call.getCurrentParamsCopy().getVideoEnabled()) {
					// NoWebcam mode, we let it this way
					LinphoneActivity.instance().startVideoActivity(call, 0);
				}
				else if (!LinphoneManager.getInstance().addVideo()) {
					LinphoneActivity.instance().startVideoActivity(call, 0);
				}
			}
			else if (id == R.id.set_auth_token_verified) {
				call.setAuthenticationTokenVerified(true);
			}
			else if (id == R.id.set_auth_token_not_verified) {
				call.setAuthenticationTokenVerified(false);
			}
			else if (id == R.id.encrypted) {
				call.setAuthenticationTokenVerified(!call.isAuthenticationTokenVerified());
			}
			else {
				throw new RuntimeException("unknown id " + v.getId());
			}
			if (dialog != null) dialog.dismiss();
		}
	}

	private class IncallListAdapter extends CalleeListAdapter {
		private boolean aConferenceIsPossible() {
			if (lc().getCallsNb() < 2) {
				return false;
			}
			int count = 0;
			boolean aConfExists = lc().getConferenceSize() > 0;
			for (LinphoneCall call : getSpecificCalls()) {
				final LinphoneCall.State state = call.getState();
				boolean connectionEstablished = state == State.StreamsRunning
						|| state == State.Paused
						|| state == State.PausedByRemote;
				if (connectionEstablished)
					count++;
				if ((aConfExists && count >= 1) || count >= 2)
					return true;
			}
			return false;
		}

		private Collection<State> mStatesToHighlight = Arrays.asList(
				State.StreamsRunning, 
				State.OutgoingRinging,
				State.OutgoingEarlyMedia,
				State.OutgoingInit,
				State.OutgoingProgress);

		public View getView(int position, View v, ViewGroup parent) {
			if (v == null) {
				if (Version.sdkAboveOrEqual(Version.API06_ECLAIR_201)) {
					v = getLayoutInflater().inflate(R.layout.conf_callee, null);
				} else {
					v = getLayoutInflater().inflate(R.layout.conf_callee_older_devices, null);
				}
			}

			final LinphoneCall call = getItem(position);
			final LinphoneCall.State state = call.getState();

			LinphoneAddress address = call.getRemoteAddress();
			TextView mainTextView = (TextView) v.findViewById(R.id.name);
			if (!TextUtils.isEmpty(address.getDisplayName())) {
				mainTextView.setText(address.getDisplayName());
			} else {
				mainTextView.setText(address.getUserName());
			}
			String statusLabel = getStateText(state);
			((TextView) v.findViewById(R.id.address)).setText(statusLabel);


			boolean highlighted = mStatesToHighlight.contains(state);

			int bgDrawableId = R.drawable.conf_callee_selector_normal;
			if (state == State.IncomingReceived) {
				bgDrawableId = R.drawable.conf_callee_selector_incoming;
			} else if (highlighted) {
				bgDrawableId = R.drawable.conf_callee_selector_active;
			}
			v.setBackgroundResource(bgDrawableId);

			final boolean connectionEstablished = state == State.StreamsRunning
					|| state == State.Paused
					|| state == State.PausedByRemote;

			boolean statusPaused = state== State.Paused || state == State.PausedByRemote;
			setVisibility(v, R.id.callee_status_paused, statusPaused);
			setVisibility(v, R.id.callee_status_qos, !statusPaused);

			final OnLongClickListener showCallActionsLongListener = new OnLongClickListener() {
				public boolean onLongClick(View v) {
					if (lc().soundResourcesLocked()) {
						return false;
					}
					View content = getLayoutInflater().inflate(R.layout.conf_choices_dialog, null);
					Dialog dialog = new AlertDialog.Builder(IncallActivity.this).setView(content).create();
					OnClickListener l = new CallActionListener(call, dialog);
					enableView(content, R.id.transfer_existing, l, mAllowTransfers && getSpecificCalls().size() >=2);
					enableView(content, R.id.transfer_new, l, mAllowTransfers);
					boolean showMergeToConf = connectionEstablished && aConferenceIsPossible();
					enableView(content, R.id.merge_to_conference, l, showMergeToConf);
					enableView(content, R.id.terminate_call, l, true);

					if (call.getCurrentParamsCopy().getMediaEncryption()==MediaEncryption.ZRTP) {
						boolean authVerified = call.isAuthenticationTokenVerified();
						String fmt = getString(authVerified ? R.string.reset_sas_fmt : R.string.verify_sas_fmt);
						TextView token = (TextView) content.findViewById(R.id.authentication_token);
						token.setText(String.format(fmt, call.getAuthenticationToken()));
						enableView(content, R.id.set_auth_token_not_verified, l, authVerified);
						enableView(content, R.id.set_auth_token_verified, l, !authVerified);
						enableView(content, R.id.encrypted, l, true);
					} else {
						setVisibility(content, R.id.encrypted, false);
					}

					dialog.show();
					return true;
				}
			};
			v.setOnLongClickListener(showCallActionsLongListener);

			OnClickListener showCallActionsSimpleListener = new OnClickListener() {
				@Override
				public void onClick(View v) {
					showCallActionsLongListener.onLongClick(v);
				}
			};

			MediaEncryption mediaEncryption = call.getCurrentParamsCopy().getMediaEncryption();
			if (MediaEncryption.None == mediaEncryption) {
				setVisibility(v, R.id.callee_status_secured, false);
				setVisibility(v, R.id.callee_status_maybe_secured, false);
				setVisibility(v, R.id.callee_status_not_secured, false);
			} else if (MediaEncryption.ZRTP == mediaEncryption ) {
				boolean reallySecured = call.isAuthenticationTokenVerified();
				enableView(v, R.id.callee_status_secured, showCallActionsSimpleListener, reallySecured);
				enableView(v, R.id.callee_status_maybe_secured, showCallActionsSimpleListener, !reallySecured);
				enableView(v, R.id.callee_status_not_secured, showCallActionsSimpleListener, false);
			} else {
				setVisibility(v, R.id.callee_status_secured, true);
				setVisibility(v, R.id.callee_status_maybe_secured, false);
				setVisibility(v, R.id.callee_status_not_secured, false);
			}

			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					State actualState = call.getState();
					if (State.StreamsRunning == actualState) {
						lc().pauseCall(call);
					} else if (State.PausedByRemote == actualState) {
						String msg = getString(R.string.cannot_resume_paused_by_remote_call);
						Toast.makeText(IncallActivity.this, msg, Toast.LENGTH_SHORT).show();
					} else if (lc().soundResourcesLocked()) {
						return;
					} else if (State.Paused == actualState) {
						if (call != null && call.cameraEnabled() && call.getCurrentParamsCopy().getVideoEnabled())
						{
							finish();
							LinphoneActivity.instance().startVideoActivity(call, 0);
						}
						lc().resumeCall(call);
					}
				}
			});

			ImageView pictureView = (ImageView) v.findViewById(R.id.picture);
			if (lc().getCallsNb() != 1) {
				// May be greatly sped up using a drawable cache
				setCalleePicture(pictureView, address);
			} else {
				pictureView.setVisibility(GONE);
			}

			registerCallDurationTimer(v, call);
			registerCallQualityListener(v, call);

			enableView(v, R.id.callee_status_details, showCallActionsSimpleListener, true);

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
		} else if (state == State.OutgoingProgress) {
			id=R.string.state_outgoing_progress;
		} else {
			return "";
		}
		return getString(id);
	}

	private void updatePauseMergeButtons() {
		View controls = findViewById(R.id.incall_controls_layout);

		int nbCalls = lc().getCallsNb();
		View pauseView = controls.findViewById(R.id.conf_simple_pause);
		View mergeView = controls.findViewById(R.id.conf_simple_merge);

		if (nbCalls <= 1) {
			((Checkable) pauseView).setChecked(lc().getCurrentCall() == null);
			mergeView.setVisibility(GONE);
			pauseView.setVisibility(VISIBLE);
			
		} else {
			int nonConfCallsNb = LinphoneUtils.countNonConferenceCalls(lc());
			boolean enableMerge = nonConfCallsNb >=2;
			enableMerge |= nonConfCallsNb >=1 && lc().getConferenceSize() > 0;
			mergeView.setEnabled(enableMerge);
			pauseView.setVisibility(GONE);
			mergeView.setVisibility(VISIBLE);
		}
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) 
	{
		if (!Version.isXLargeScreen(this))
		{
			// Do nothing to not recreate the activity on smartphone if screen is rotated
			super.onConfigurationChanged(null);
			return;
		}
		super.onConfigurationChanged(newConfig);
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

	protected void updateUI() {
		updatePauseMergeButtons();
		updateCalleeImage();
		updateSoundLock();
		updateAddCallButton();
		updateNumpadButton();
		updateConfItem();

		super.updateUI();
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
			((Checkable) findViewById(R.id.toggleMuteMic)).setChecked(false);
		}

		String uri = null;
		if (data != null) {
			uri = data.getStringExtra(UriPickerActivity.EXTRA_CALLEE_URI);
		}
		if (resultCode != RESULT_OK || TextUtils.isEmpty(uri)) {
			mCallToTransfer = null;
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
		case addCallId:
			try {
				lc().invite(uri);
				eventuallyResumeConfOrCallOnPickerReturn(false);
			} catch (LinphoneCoreException e) {
				Log.e(e);
				Toast.makeText(this, R.string.error_adding_new_call, Toast.LENGTH_LONG).show();
			}
			break;
		case transferCallId:
			lc().transferCall(mCallToTransfer, uri);
			// don't re-enter conference if call to transfer from conference
			boolean doResume = !mCallToTransfer.isInConference();
			// don't resume call if it is the call to transfer
			doResume &= mActivateCallOnReturnFromUriPicker != mCallToTransfer;
			eventuallyResumeConfOrCallOnPickerReturn(doResume);
			Toast.makeText(this, R.string.transfer_started, Toast.LENGTH_LONG).show();
			break;
		default:
			throw new RuntimeException("unhandled request code " + requestCode);
		}
	}

	private void eventuallyResumeConfOrCallOnPickerReturn(boolean doCallConfResuming) {
		if (doCallConfResuming) {
			if (mActivateCallOnReturnFromUriPicker != null) {
				lc().resumeCall(mActivateCallOnReturnFromUriPicker);
			} else if (mEnterConferenceOnReturnFromUriPicker) {
				enterConferenceAndVirtualConfView(true);
			}
		}
		mActivateCallOnReturnFromUriPicker = null;
		mEnterConferenceOnReturnFromUriPicker = false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeSoftAdjust(keyCode)) return true;
		if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
		return super.onKeyDown(keyCode, event);
	}


	private Handler mHandler = new Handler();
	@Override
	public void onCallEncryptionChanged(LinphoneCall call, boolean encrypted,
			String authenticationToken) {
		mHandler.post(new Runnable() {
			public void run() {
				updateUI();
			}
		});
	}

	@Override
	public void onCallStateChanged(LinphoneCall call, final State state, String message) {
		if (state == State.CallUpdatedByRemote) {
			// If the correspondent proposes video while audio call
			boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
			boolean localVideo = call.getCurrentParamsCopy().getVideoEnabled();
			boolean autoAcceptCameraPolicy = LinphoneManager.getInstance().isAutoAcceptCamera();
			if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
				mHandler.post(new Runnable() {
					public void run() {
						showDialog(promptVideoId);
						// We let 30 secs for the user to decide
						timer = new CountDownTimer(30000, 1000) {
							public void onTick(long millisUntilFinished) { }

							public void onFinish() {
								acceptCallUpdate(false, promptVideoId);
					    	}
						}.start();
					}
				});
			}
		}
		super.onCallStateChanged(call, state, message);
	}
}
