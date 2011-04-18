/*
DialerActivity.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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

import org.linphone.LinphoneManager.NewOutgoingCallUiListener;
import org.linphone.LinphoneService.LinphoneGuiListener;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.video.AndroidCameraRecordManager;
import org.linphone.ui.AddVideoButton;
import org.linphone.ui.AddressAware;
import org.linphone.ui.AddressText;
import org.linphone.ui.CallButton;
import org.linphone.ui.EraseButton;
import org.linphone.ui.HangCallButton;
import org.linphone.ui.MuteMicButton;
import org.linphone.ui.SpeakerButton;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * Dialer and main activity of Linphone Android.
 * 
 * Roles are:<ul>
 * <li>Display the numpad, call/accept, address buttons</li>
 * <li>Define preferences through the menu</li>
 * <li>React to bad preference / no account set</li>
 * <li>Manage first launch</li>
 * </ul>
 *
 */
public class DialerActivity extends SoftVolumeActivity implements LinphoneGuiListener, NewOutgoingCallUiListener {
	
	private TextView mStatus;
	private View mHangup;

	private View mCallControlRow;
	private TextView mDisplayNameView;
	private AddressText mAddress;
	private View mAddressLayout;
	private CallButton mCall;

	private View mInCallControlRow;
	private View mInCallAddressLayout;
	private MuteMicButton mMute;
	private SpeakerButton mSpeaker;

	private static DialerActivity instance;
	
	private PowerManager.WakeLock mWakeLock;
	private SharedPreferences mPref;
	private boolean useIncallActivity;
	private boolean useVideoActivity;
	
	private static final String CURRENT_ADDRESS = "org.linphone.current-address"; 
	private static final String CURRENT_DISPLAYNAME = "org.linphone.current-displayname";

	private static final int INCOMING_CALL_DIALOG_ID = 1;

	/**
	 * @return null if not ready yet
	 */
	public static DialerActivity instance() { 
		return instance;
	}

	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dialer);

		useIncallActivity = getResources().getBoolean(R.bool.use_incall_activity);
		useVideoActivity = getResources().getBoolean(R.bool.use_video_activity);
		// Don't use Linphone Manager in the onCreate as it takes time in LinphoneService to initialize it.

		mPref = PreferenceManager.getDefaultSharedPreferences(this);
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,"Linphone");


		mAddress = (AddressText) findViewById(R.id.SipUri); 
		mDisplayNameView = (TextView) findViewById(R.id.DisplayNameView);
		((EraseButton) findViewById(R.id.Erase)).setAddressView(mAddress);


		mCall = (CallButton) findViewById(R.id.Call);
		mCall.setAddressWidget(mAddress);


		mCallControlRow = findViewById(R.id.CallControlRow);
		mAddressLayout = findViewById(R.id.Addresslayout);

		mInCallControlRow = findViewById(R.id.IncallControlRow);
		mInCallControlRow.setVisibility(View.GONE);
		mInCallAddressLayout = findViewById(R.id.IncallAddressLayout);
		mInCallAddressLayout.setVisibility(View.GONE);

		if (useIncallActivity) {
			mHangup = findViewById(R.id.HangUp); 
		} else {
			mMute = (MuteMicButton) findViewById(R.id.mic_mute_button);
			mSpeaker = (SpeakerButton) findViewById(R.id.speaker_button);
			mHangup = findViewById(R.id.Decline); 
		}


		mStatus =  (TextView) findViewById(R.id.status_label);

		AddressAware numpad = (AddressAware) findViewById(R.id.Dialer);
		if (numpad != null)
			numpad.setAddressWidget(mAddress);

		checkIfOutgoingCallIntentReceived();

		if (LinphoneService.isReady()) {
			LinphoneCore lc = LinphoneManager.getLc();
			if (lc.isIncall()) {
				if(lc.isInComingInvitePending()) {
					callPending(lc.getCurrentCall());
				} else {
					enterIncallMode(lc);
				}
			}
		}
		instance = this;
	}




    private void checkIfOutgoingCallIntentReceived() {
    	if (getIntent().getData() == null) return;

    	if (!LinphoneService.isReady() || LinphoneManager.getLc().isIncall()) {
    		Log.w(LinphoneManager.TAG, "Outgoing call aborted as LinphoneService"
    				+ " is not ready or we are already in call");
    		return;
    	}

    	newOutgoingCall(getIntent());
	}






	

	


	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putCharSequence(CURRENT_ADDRESS, mAddress.getText());
		if (mAddress.getDisplayedName() != null)
			savedInstanceState.putString(CURRENT_DISPLAYNAME,mAddress.getDisplayedName());
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedState) {
		super.onRestoreInstanceState(savedState);
		CharSequence addr = savedState.getCharSequence(CURRENT_ADDRESS);
		if (addr != null && mAddress != null) {
			mAddress.setText(addr); 
		}
		mAddress.setDisplayedName(savedState.getString(CURRENT_DISPLAYNAME));
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mWakeLock.isHeld()) mWakeLock.release();
		instance=null;
	}
	
	

	
	private void enterIncallMode(LinphoneCore lc) {
		mDisplayNameView.setText(LinphoneManager.getInstance().extractADisplayName());

//		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

		LinphoneActivity.instance().startProxymitySensor();
		if (!mWakeLock.isHeld()) mWakeLock.acquire();
		
		if (useIncallActivity) {
			LinphoneActivity.instance().startIncallActivity(mDisplayNameView.getText());
		} else {
			loadMicAndSpeakerUiStateFromManager();
			mCallControlRow.setVisibility(View.GONE);
			mInCallControlRow.setVisibility(View.VISIBLE);
			mAddressLayout.setVisibility(View.GONE);
			mInCallAddressLayout.setVisibility(View.VISIBLE);
			mCall.setEnabled(false);
			updateIncallVideoCallButton();
			mHangup.setEnabled(true);
		}
	}

	

	private void updateIncallVideoCallButton() {
		if (useIncallActivity) throw new RuntimeException("Internal error");

		boolean prefVideoEnabled = LinphoneManager.getInstance().isVideoEnabled();
		AddVideoButton mAddVideo = (AddVideoButton) findViewById(R.id.AddVideo);

		if (prefVideoEnabled && !mCall.isEnabled()) {
			mAddVideo.setVisibility(View.VISIBLE);
			mAddVideo.setEnabled(true);
		} else {
			mAddVideo.setVisibility(View.GONE);
		}
	}


	private void loadMicAndSpeakerUiStateFromManager() {
		if (useIncallActivity) throw new RuntimeException("Internal error"); // only dialer widgets are updated with this

		mMute.setChecked(LinphoneManager.getLc().isMicMuted());
		mSpeaker.setSpeakerOn(LinphoneManager.getInstance().isSpeakerOn());
	}
	
	
	private void exitCallMode() {
		// Remove dialog if existing
		try {
			dismissDialog(INCOMING_CALL_DIALOG_ID);
		} catch (Throwable e) {/* Exception if never created */}

		if (useIncallActivity) {
			LinphoneActivity.instance().closeIncallActivity();
		} else {
			mCallControlRow.setVisibility(View.VISIBLE);
			mInCallControlRow.setVisibility(View.GONE);
			mInCallAddressLayout.setVisibility(View.GONE);
			updateIncallVideoCallButton();
			mSpeaker.setSpeakerOn(false);
		}

		mAddressLayout.setVisibility(View.VISIBLE);

		mHangup.setEnabled(false);


		if (useVideoActivity && LinphoneManager.getLc().isVideoEnabled()) {
			LinphoneActivity.instance().finishVideoActivity(); 
			BandwidthManager.getInstance().setUserRestriction(false);
			LinphoneManager.getInstance().resetCameraFromPreferences();
		}

		if (mWakeLock.isHeld()) mWakeLock.release();
		LinphoneActivity.instance().stopProxymitySensor();
		
		setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
		mCall.setEnabled(true);
	}


	private void callPending(final LinphoneCall call) {
		showDialog(INCOMING_CALL_DIALOG_ID);
	}


	@Override
	protected Dialog onCreateDialog(int id, Bundle b) {
		String from = LinphoneManager.getInstance().extractIncomingRemoteName();
		View incomingCallView = getLayoutInflater().inflate(R.layout.incoming_call, null);

		final Dialog dialog = new AlertDialog.Builder(this)
		.setMessage(String.format(getString(R.string.incoming_call_dialog_title), from))
		.setCancelable(false)
		.setView(incomingCallView).create();
		
		
		((CallButton) incomingCallView.findViewById(R.id.Call)).setExternalClickListener(new OnClickListener() {
			public void onClick(View v) {
				dialog.dismiss();
				LinphoneManager.getInstance().resetCameraFromPreferences();

				// Privacy setting to not share the user camera by default
				boolean prefVideoEnable = LinphoneManager.getInstance().isVideoEnabled();
				boolean prefAutoShareMyCamera = mPref.getBoolean(getString(R.string.pref_video_automatically_share_my_video_key), false);
				boolean videoMuted = !(prefVideoEnable && prefAutoShareMyCamera);
				AndroidCameraRecordManager.getInstance().setMuted(videoMuted);

				LinphoneManager.getLc().getCurrentCall().enableCamera(prefAutoShareMyCamera);
			}
		});
		((HangCallButton) incomingCallView.findViewById(R.id.Decline)).setExternalClickListener(new OnClickListener() {
			public void onClick(View v) {dialog.dismiss();}
		});
		
		return dialog;
	}
	
	
	
	public void newOutgoingCall(Intent intent) {
		if (Intent.ACTION_CALL.equalsIgnoreCase(intent.getAction())) {
			mAddress.setText(intent.getData().getSchemeSpecificPart());
		} else if (Intent.ACTION_SENDTO.equals(intent.getAction())) {
			mAddress.setText("sip:" + intent.getData().getLastPathSegment());
		}
		
		mAddress.clearDisplayedName();
		intent.setData(null);

		LinphoneManager.getInstance().newOutgoingCall(mAddress);
	}

	
	public void setContactAddress(String aContact,String aDisplayName) {
		mAddress.setText(aContact);
		mAddress.setDisplayedName(aDisplayName);
	}

	


	
	
	/***** GUI delegates for listener LinphoneServiceListener *************/
	public void onDisplayStatus(String message) {
		mStatus.setText(message);
	}


	public void onAlreadyInCall() {
		showToast(R.string.warning_already_incall);
	}


	public void onCannotGetCallParameters() {
		showToast(R.string.error_cannot_get_call_parameters,mAddress.getText());
	}


	public void onWrongDestinationAddress() {
		showToast(R.string.warning_wrong_destination_address, mAddress.getText());
	}


	public void onCallStateChanged(LinphoneCall call, State state, String message) {
		LinphoneCore lc = LinphoneManager.getLc();
		if (state == LinphoneCall.State.OutgoingInit) {
			enterIncallMode(lc);
		} else if (state == LinphoneCall.State.IncomingReceived) { 
			callPending(call);
		} else if (state == LinphoneCall.State.Connected) {
			if (call.getDirection() == CallDirection.Incoming) {
				enterIncallMode(lc);
			}
		} else if (state == LinphoneCall.State.Error) {
			if (mWakeLock.isHeld()) mWakeLock.release();
			showToast(R.string.call_error, message);
			exitCallMode();
		} else if (state == LinphoneCall.State.CallEnd) {
			exitCallMode();
		}
	}

	private void showToast(int id, String txt) {
		final String msg = String.format(getString(id), txt);
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	}
	private void showToast(int id, CharSequence txt) {
		showToast(id, txt.toString());
	}
	private void showToast(int id) {
		Toast.makeText(this, getString(id), Toast.LENGTH_LONG).show();
	}

	public void onGlobalStateChangedToOn(String message) {
		mCall.setEnabled(!LinphoneManager.getLc().isIncall());
		if (!useIncallActivity) updateIncallVideoCallButton();
		else mHangup.setEnabled(!mCall.isEnabled());  

		if (getIntent().getData() != null) {
			checkIfOutgoingCallIntentReceived();
		}
	}

}
