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
import org.linphone.LinphoneManagerWaitHelper.LinphoneManagerReadyListener;
import org.linphone.LinphoneService.LinphoneGuiListener;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.Log;
import org.linphone.core.LinphoneCall.State;
import org.linphone.ui.AddVideoButton;
import org.linphone.ui.AddressAware;
import org.linphone.ui.AddressText;
import org.linphone.ui.CallButton;
import org.linphone.ui.EraseButton;
import org.linphone.ui.HangCallButton;
import org.linphone.ui.MuteMicButton;
import org.linphone.ui.SpeakerButton;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.KeyEvent;
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
public class DialerActivity extends Activity implements LinphoneGuiListener, LinphoneManagerReadyListener, NewOutgoingCallUiListener, OnClickListener {
	
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
	private boolean useIncallActivity;
	private boolean useConferenceActivity;
	
	private static final String CURRENT_ADDRESS = "org.linphone.current-address"; 
	private static final String CURRENT_DISPLAYNAME = "org.linphone.current-displayname";

	/**
	 * @return null if not ready yet
	 */
	public static DialerActivity instance() { 
		return instance;
	}

	private LinphoneManagerWaitHelper waitHelper;
	public void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.dialer);

		useIncallActivity = getResources().getBoolean(R.bool.use_incall_activity);
		useConferenceActivity = getResources().getBoolean(R.bool.use_conference_activity);
		// Don't use Linphone Manager in the onCreate as it takes time in LinphoneService to initialize it.

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,Log.TAG+"#"+getClass().getName());


		mAddress = (AddressText) findViewById(R.id.SipUri); 
		mDisplayNameView = (TextView) findViewById(R.id.DisplayNameView);
		((EraseButton) findViewById(R.id.Erase)).setAddressWidget(mAddress);


		mCall = (CallButton) findViewById(R.id.Call);
		mCall.setAddressWidget(mAddress);


		mCallControlRow = findViewById(R.id.CallControlRow);
		mCallControlRow.findViewById(R.id.BackToConference).setOnClickListener(this);
		mAddressLayout = findViewById(R.id.Addresslayout);

		mInCallControlRow = findViewById(R.id.IncallControlRow);
		mInCallControlRow.setVisibility(View.GONE);
		mInCallAddressLayout = findViewById(R.id.IncallAddressLayout);
		mInCallAddressLayout.setVisibility(View.GONE);

		HangCallButton hang = (HangCallButton) findViewById(R.id.HangUp);
		HangCallButton decline = (HangCallButton) findViewById(R.id.Decline);
		hang.setTerminateAllCalls(true);
		decline.setTerminateAllCalls(true);

		if (useConferenceActivity || useIncallActivity) {
			mHangup = hang;
		} else {
			mMute = (MuteMicButton) findViewById(R.id.mic_mute_button);
			mSpeaker = (SpeakerButton) findViewById(R.id.speaker_button);
			mHangup = decline;
		}

		mStatus =  (TextView) findViewById(R.id.status_label);

		AddressAware numpad = (AddressAware) findViewById(R.id.Dialer);
		if (numpad != null)
			numpad.setAddressWidget(mAddress);

		// call to super must be done after all fields are initialized
		// because it may call this.enterIncallMode
		super.onCreate(savedInstanceState);
		
		checkIfOutgoingCallIntentReceived();

		waitHelper = new LinphoneManagerWaitHelper(this, this);
		waitHelper.doManagerDependentOnCreate();
		instance = this;
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onCreateWhenManagerReady() {
		LinphoneCall pendingCall = LinphoneManager.getInstance().getPendingIncomingCall();
		if (pendingCall != null) {
			LinphoneActivity.instance().startIncomingCallActivity(pendingCall);
		} else if (LinphoneManager.getLc().isIncall()) {
			enterIncallMode();
		}
	}



    private void checkIfOutgoingCallIntentReceived() {
    	if (getIntent().getData() == null) return;

    	if (!LinphoneService.isReady() || LinphoneManager.getLc().isIncall()) {
    		Log.w("Outgoing call aborted as LinphoneService"
    				+ " is not ready or we are already in call");
    		return;
    	}
    	
    	// Fix call from contact issue
    	if (getIntent().getData().getSchemeSpecificPart() != null)
    		getIntent().setAction(Intent.ACTION_CALL);
    	
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
	
	

	
	private void enterIncallMode() {
		LinphoneCore lc = LinphoneManager.getLc();
		LinphoneAddress address = lc.getRemoteAddress();
		mDisplayNameView.setText(LinphoneManager.extractADisplayName(getResources(), address));

//		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

		LinphoneManager.startProximitySensorForActivity(LinphoneActivity.instance());
		if (!mWakeLock.isHeld()) mWakeLock.acquire();
		
		if (useIncallActivity) {
			LinphoneActivity.instance().startIncallActivity(
				mDisplayNameView.getText(), mAddress.getPictureUri());
		} else if (useConferenceActivity) {
			LinphoneActivity.instance().startConferenceActivity();
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
		if (useIncallActivity || useConferenceActivity)
			throw new RuntimeException("Internal error");

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
		if (useIncallActivity || useConferenceActivity)
			throw new RuntimeException("Internal error"); // only dialer widgets are updated with this

		mMute.setChecked(LinphoneManager.getLc().isMicMuted());
		mSpeaker.setSpeakerOn(LinphoneManager.getInstance().isSpeakerOn());
	}
	
	
	private void exitCallMode() {
		if (useIncallActivity) {
			LinphoneActivity.instance().closeIncallActivity();
		} else if(useConferenceActivity) { 
			LinphoneActivity.instance().closeConferenceActivity();
		}else {
			mCallControlRow.setVisibility(View.VISIBLE);
			mInCallControlRow.setVisibility(View.GONE);
			mInCallAddressLayout.setVisibility(View.GONE);
			updateIncallVideoCallButton();
			mSpeaker.setSpeakerOn(false);
		}

		mAddressLayout.setVisibility(View.VISIBLE);

		mHangup.setEnabled(false);

		if (mWakeLock.isHeld()) mWakeLock.release();
		LinphoneManager.stopProximitySensorForActivity(LinphoneActivity.instance());
		
		setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
		mCall.setEnabled(true);
	}


	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == LinphoneManagerWaitHelper.DIALOG_ID) {
			return waitHelper.createWaitDialog();
		} else {
			return super.onCreateDialog(id);
		}
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

	
	public void setContactAddress(String aContact,String aDisplayName, Uri photo) {
		mAddress.setText(aContact);
		mAddress.setDisplayedName(aDisplayName);
		mAddress.setPictureUri(photo);
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
		Log.i("OnCallStateChanged: call=", call, ", state=", state, ", message=", message);
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc==null) {
			/* we are certainly exiting, ignore then.*/
			return;
		}
		
		if (state==State.OutgoingInit){
			enterIncallMode();
		}else if (state==State.Connected){
			if (call.getDirection() == CallDirection.Incoming) {
				enterIncallMode();
			}
		}else if (state==State.Error){
			showToast(R.string.call_error, message);
			if (lc.getCallsNb() == 0){
				if (mWakeLock.isHeld()) mWakeLock.release();
				exitCallMode();
			}
		}else if (state==State.CallEnd){
			if (lc.getCallsNb() == 0){
				exitCallMode();
			}
		}

		updateCallControlRow();
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
		if (!useIncallActivity && !useConferenceActivity) updateIncallVideoCallButton();
		else mHangup.setEnabled(!mCall.isEnabled());  

		if (getIntent().getData() != null) {
			checkIfOutgoingCallIntentReceived();
		}
	}

	public void onCallEncryptionChanged(LinphoneCall call, boolean encrypted,
			String authenticationToken) {
		if (encrypted) {
			boolean verified=call.isAuthenticationTokenVerified();
			mStatus.setText("Call encrypted ["+ authenticationToken+"] "
					+ (verified ? "verified":"unverified"));
		} else {
			mStatus.setText("Call not encrypted");
		}
	}

	@Override
	public void onResumeWhenManagerReady() {
		updateCallControlRow();

		// When coming back from a video call, if the phone orientation is different
		// Android will destroy the previous Dialer and create a new one.
		// Unfortunately the "call end" status event is received in the meanwhile
		// and set to the to be destroyed Dialer.
		// Note1: We wait as long as possible before setting the last message.
		// Note2: Linphone service is in charge of instantiating LinphoneManager
		mStatus.setText(LinphoneManager.getInstance().getLastLcStatusMessage());
		if (LinphoneManager.getLc().getCallsNb() > 0) {
			LinphoneManager.startProximitySensorForActivity(LinphoneActivity.instance());
			// removing is done directly in LinphoneActivity.onPause()
		}
	}

	@Override
	protected void onResume() {
		waitHelper.doManagerDependentOnResume();
		super.onResume();
	}


	private void updateCallControlRow() {
		if (useConferenceActivity) {
			if (LinphoneManager.isInstanciated()) {
				LinphoneCore lc = LinphoneManager.getLc();
				int calls = lc.getCallsNb();
				View backToConf = mCallControlRow.findViewById(R.id.BackToConference);
				View callButton = mCallControlRow.findViewById(R.id.Call);
				View hangButton = mCallControlRow.findViewById(R.id.Decline);
				if (calls > 0) {
					backToConf.setVisibility(View.VISIBLE);
					callButton.setVisibility(View.GONE);
					hangButton.setEnabled(true);
				} else {
					backToConf.setVisibility(View.GONE);
					callButton.setVisibility(View.VISIBLE);
					hangButton.setEnabled(false);
				}
			}
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeSoftAdjust(keyCode)) return true;
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.BackToConference:
			LinphoneActivity.instance().startConferenceActivity();
			break;
		default:
			break;
		}
		
	}
}
