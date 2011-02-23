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
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.video.AndroidCameraRecordManager;
import org.linphone.ui.AddVideoButton;
import org.linphone.ui.AddressAwareWidget;
import org.linphone.ui.AddressText;
import org.linphone.ui.CallButton;
import org.linphone.ui.EraseButton;
import org.linphone.ui.MuteMicButton;
import org.linphone.ui.SpeakerButton;
import org.linphone.ui.AddVideoButton.AlreadyInVideoCallListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
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
public class DialerActivity extends Activity implements LinphoneGuiListener, AlreadyInVideoCallListener, NewOutgoingCallUiListener {
	
	private AddressText mAddress;
	private TextView mDisplayNameView;

	private TextView mStatus;
	private CallButton mCall;
	private View mDecline;
	private View mHangup;
	
	private MuteMicButton mMute;
	private SpeakerButton mSpeaker;
	
	private View mCallControlRow;
	private View mInCallControlRow;
	private View mAddressLayout;
	private View mInCallAddressLayout;
	
	private static DialerActivity instance;
	
	private PowerManager.WakeLock mWakeLock;
	private SharedPreferences mPref;
	private AddVideoButton mAddVideo;
	
	private static final String PREF_CHECK_CONFIG = "pref_check_config";
	public static final String PREF_FIRST_LAUNCH = "pref_first_launch";
	private static String CURRENT_ADDRESS = "org.linphone.current-address"; 
	private static String CURRENT_DISPLAYNAME = "org.linphone.current-displayname"; 
	static int VIDEO_VIEW_ACTIVITY = 100;
	
	private static boolean checkAccount = true;



	/**
	 * @return null if not ready yet
	 */
	public static DialerActivity instance() { 
		return instance;
	}

	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dialer);

		// Don't use Linphone Manager in the onCreate as it takes time in LinphoneService to initialize it.

		mPref = PreferenceManager.getDefaultSharedPreferences(this);
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,"Linphone");




		mAddress = (AddressText) findViewById(R.id.SipUri); 
		mDisplayNameView = (TextView) findViewById(R.id.DisplayNameView);
		((EraseButton) findViewById(R.id.Erase)).setAddressView(mAddress);


		mAddVideo = (AddVideoButton) findViewById(R.id.AddVideo);
		mAddVideo.setOnAlreadyInVideoCallListener(this);


		mCall = (CallButton) findViewById(R.id.Call);
		mCall.setAddressWidget(mAddress);


		mDecline= findViewById(R.id.Decline);
		mDecline.setEnabled(false);


		mHangup = findViewById(R.id.HangUp); 


		mCallControlRow = findViewById(R.id.CallControlRow);
		mAddressLayout = findViewById(R.id.Addresslayout);

		mInCallControlRow = findViewById(R.id.IncallControlRow);
		mInCallControlRow.setVisibility(View.GONE);
		mInCallAddressLayout = findViewById(R.id.IncallAddressLayout);
		mInCallAddressLayout.setVisibility(View.GONE);
		mMute = (MuteMicButton) findViewById(R.id.mic_mute_button);
		mSpeaker = (SpeakerButton) findViewById(R.id.speaker_button);


		try {

			outgoingCallIntentReceived();

			if (LinphoneService.isReady()) {
				LinphoneCore lc = LinphoneManager.getLc();
				if (lc.isIncall()) {
					if(lc.isInComingInvitePending()) {
						callPending(lc.getCurrentCall());
					} else {
						mCall.setEnabled(false);
						mHangup.setEnabled(!mCall.isEnabled());
						updateIncallVideoCallButton();
						mCallControlRow.setVisibility(View.GONE);
						mInCallControlRow.setVisibility(View.VISIBLE);
						mAddressLayout.setVisibility(View.GONE);
						mInCallAddressLayout.setVisibility(View.VISIBLE);
						mDisplayNameView.setText(LinphoneManager.getInstance().extractADisplayName());
						loadMicAndSpeakerUiStateFromManager();
						LinphoneActivity.instance().startProxymitySensor();
						mWakeLock.acquire();
					} 
				}
			}
			
			instance = this;
		
		} catch (Exception e) {
			Log.e(LinphoneManager.TAG,"Cannot start linphone",e);
			finish();
		}

		AddressAwareWidget numpad = (AddressAwareWidget) findViewById(R.id.Dialer);
		if (numpad != null)
			numpad.setAddressWidget(mAddress);

		mStatus =  (TextView) findViewById(R.id.status_label);
		

		if (checkAccount) {
			if (mPref.getBoolean(PREF_FIRST_LAUNCH, true)) {
				onFirstLaunch();
			} else if (!mPref.getBoolean(PREF_CHECK_CONFIG, false)
					&& !checkDefined(R.string.pref_username_key, R.string.pref_passwd_key, R.string.pref_domain_key)) {
				onBadSettings();
			} else {
				checkAccount = false;
			}
		}
	}




    private void outgoingCallIntentReceived() {
    	if (getIntent().getData() == null) return;

    	if (!LinphoneService.isReady() || LinphoneManager.getLc().isIncall()) {
    		Log.w(LinphoneManager.TAG, "Outgoing call aborted as LinphoneService"
    				+ " is not ready or we are already in call");
    		return;
    	}

    	newOutgoingCall(getIntent());
	}


	/***** Check Account *******/
	private boolean checkDefined(int ... keys) {
		for (int key : keys) {
			String conf = mPref.getString(getString(key), null);
			if (conf == null || "".equals(conf))
				return false;
		}
		return true;
	}

	private void onFirstLaunch() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		TextView lDialogTextView = new TextView(this);
		lDialogTextView.setAutoLinkMask(0x0f/*all*/);
		lDialogTextView.setPadding(10, 10, 10, 10);

		lDialogTextView.setText(Html.fromHtml(getString(R.string.first_launch_message)));

		builder.setCustomTitle(lDialogTextView)
		.setCancelable(false)
		.setPositiveButton(getString(R.string.cont), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				LinphoneActivity.instance().startprefActivity();
				checkAccount = false;
			}
		});

		builder.create().show();
	}

	private void onBadSettings() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		TextView lDialogTextView = new TextView(this);
		lDialogTextView.setAutoLinkMask(0x0f/*all*/);
		lDialogTextView.setPadding(10, 10, 10, 10);

		lDialogTextView.setText(Html.fromHtml(getString(R.string.initial_config_error)));

		builder.setCustomTitle(lDialogTextView)
		.setCancelable(false)
		.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				LinphoneActivity.instance().startprefActivity();
				checkAccount = false;
			}
		}).setNeutralButton(getString(R.string.no), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
				checkAccount = false;
			}
		}).setNegativeButton(getString(R.string.never_remind), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				mPref.edit().putBoolean(PREF_CHECK_CONFIG, true).commit();
				dialog.cancel();
				checkAccount = false;
			}
		});

		builder.create().show();
	}



	

	


	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putCharSequence(CURRENT_ADDRESS, mAddress.getText());
		if (mAddress.getDisplayedName() != null)
			savedInstanceState.putString(CURRENT_DISPLAYNAME,mAddress.getDisplayedName());
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		CharSequence lAddress = savedInstanceState.getCharSequence(CURRENT_ADDRESS);
		if (lAddress != null && mAddress != null) {
			mAddress.setText(lAddress); 
		}
		mAddress.setDisplayedName(savedInstanceState.getString(CURRENT_DISPLAYNAME));
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mWakeLock.isHeld()) mWakeLock.release();
		instance=null;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}





	

	private void startVideoView(int requestCode) {
		Intent lIntent = new Intent();
		lIntent.setClass(this, VideoCallActivity.class);
		startActivityForResult(lIntent,requestCode);
	}
	
	
	private void enterIncallMode(LinphoneCore lc) {
		LinphoneManager m = LinphoneManager.getInstance();
		mCallControlRow.setVisibility(View.GONE);
		mInCallControlRow.setVisibility(View.VISIBLE);
		mAddressLayout.setVisibility(View.GONE);
		mInCallAddressLayout.setVisibility(View.VISIBLE);
		mCall.setEnabled(false);
		updateIncallVideoCallButton();
		mHangup.setEnabled(true);
		mDisplayNameView.setText(m.extractADisplayName());

		loadMicAndSpeakerUiStateFromManager();
		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

		LinphoneActivity.instance().startProxymitySensor();
		if (!mWakeLock.isHeld()) mWakeLock.acquire(); 

	}

	
	private void updateIncallVideoCallButton() {
		boolean prefVideoEnabled = LinphoneManager.getInstance().isVideoEnabled();
		if (prefVideoEnabled && !mCall.isEnabled()) {
			mAddVideo.setVisibility(View.VISIBLE);
			mAddVideo.setEnabled(true);
		} else {
			mAddVideo.setVisibility(View.GONE);
		}
	}


	private void loadMicAndSpeakerUiStateFromManager() {
		mMute.setChecked(LinphoneManager.getLc().isMicMuted());
		mSpeaker.setSpeakerOn(LinphoneManager.getInstance().isSpeakerOn());
	}
	
	
	private void exitCallMode() {
		mCallControlRow.setVisibility(View.VISIBLE);
		mInCallControlRow.setVisibility(View.GONE);
		mAddressLayout.setVisibility(View.VISIBLE);
		mInCallAddressLayout.setVisibility(View.GONE);
		mCall.setEnabled(true);
		updateIncallVideoCallButton();
		mHangup.setEnabled(false);
		setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
		mDecline.setEnabled(false);
		if (LinphoneManager.getLc().isVideoEnabled()) {
			finishActivity(VIDEO_VIEW_ACTIVITY); 
		}
		if (mWakeLock.isHeld())mWakeLock.release();
		mSpeaker.setSpeakerOn(false);

		BandwidthManager.getInstance().setUserRestriction(false);
		LinphoneManager.getInstance().resetCameraFromPreferences();
		LinphoneActivity.instance().stopProxymitySensor();
	}

	private void callPending(LinphoneCall call) {
		mDecline.setEnabled(true);
		
		// Privacy setting to not share the user camera by default
		boolean prefVideoEnable = LinphoneManager.getInstance().isVideoEnabled();
		boolean prefAutomaticallyShareMyCamera = mPref.getBoolean(getString(R.string.pref_video_automatically_share_my_video_key), false);
		AndroidCameraRecordManager.getInstance().setMuted(!(prefVideoEnable && prefAutomaticallyShareMyCamera));
		call.enableCamera(prefAutomaticallyShareMyCamera);
	}


	
	
	
	public void newOutgoingCall(Intent intent) {
		mAddress.setText(intent.getData().toString().substring("tel:".length()));
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

	public void onAlreadyInVideoCall() {
		startVideoView(VIDEO_VIEW_ACTIVITY);		
	}

	public void onAlreadyInCall() {
		Toast toast = Toast.makeText(this,
				getString(R.string.warning_already_incall), Toast.LENGTH_LONG);
		toast.show();
	}


	public void onCannotGetCallParameters() {
		Toast toast = Toast.makeText(this
				,String.format(getString(R.string.error_cannot_get_call_parameters),mAddress.getText().toString())
				,Toast.LENGTH_LONG);
		toast.show();
	}


	public void onWrongDestinationAddress() {
		Toast toast = Toast.makeText(this
				,String.format(getResources().getString(R.string.warning_wrong_destination_address),mAddress.getText().toString())
				,Toast.LENGTH_LONG);
		toast.show();
	}


	public void onCallStateChanged(LinphoneCall call, State state, String message) {
		LinphoneCore lc = LinphoneManager.getLc();
		if (state == LinphoneCall.State.OutgoingInit) {
			enterIncallMode(lc);
			LinphoneManager.getInstance().routeAudioToReceiver();
		} else if (state == LinphoneCall.State.IncomingReceived) { 
			LinphoneManager.getInstance().resetCameraFromPreferences();
			callPending(call);
		} else if (state == LinphoneCall.State.Connected) {
			enterIncallMode(lc);
		} else if (state == LinphoneCall.State.Error) {
			if (mWakeLock.isHeld()) mWakeLock.release();
			Toast toast = Toast.makeText(this
					,String.format(getString(R.string.call_error),message)
					, Toast.LENGTH_LONG);
			toast.show();
			exitCallMode();
		} else if (state == LinphoneCall.State.CallEnd) {
			exitCallMode();
		} else if (state == LinphoneCall.State.StreamsRunning) {
			if (LinphoneManager.getLc().getCurrentCall().getCurrentParamsCopy().getVideoEnabled()) {
				if (!VideoCallActivity.launched) {
					startVideoView(VIDEO_VIEW_ACTIVITY);
				}
			}
		}		
	}


	public void onGlobalStateChangedToOn(String message) {
		mCall.setEnabled(!LinphoneManager.getLc().isIncall());
		mHangup.setEnabled(!mCall.isEnabled());  
		updateIncallVideoCallButton();

		try{
			LinphoneManager.getInstance().initFromConf(this);
		} catch (LinphoneConfigException e) {
			Log.w(LinphoneManager.TAG, "Cannot get initial config : " + e.getMessage());
		} catch (Exception e) {
			Log.e(LinphoneManager.TAG, "Cannot get initial config", e);
		}

		if (getIntent().getData() != null) {
			outgoingCallIntentReceived();
		}
	}
}
