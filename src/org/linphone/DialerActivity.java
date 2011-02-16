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

import org.linphone.core.AndroidCameraRecordManager;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.ui.AddressText;
import org.linphone.ui.Digit;
import org.linphone.ui.EraseButton;
import org.linphone.ui.MuteMicButton;
import org.linphone.ui.SpeakerButton;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

public class DialerActivity extends Activity implements LinphoneCoreListener {
	
	private AddressText mAddress;
	private TextView mDisplayNameView;

	private TextView mStatus;
	private ImageButton mCall;
	private View mDecline;
	private View mHangup;
	
	private MuteMicButton mMute;
	private SpeakerButton mSpeaker;
	
	private LinearLayout mCallControlRow;
	private TableRow mInCallControlRow;
	private View mAddressLayout;
	private View mInCallAddressLayout;
	
	private static DialerActivity theDialer;
	
	private PowerManager.WakeLock mWakeLock;
	private SharedPreferences mPref;
	private ImageButton mAddVideo;
	
	private static final String PREF_CHECK_CONFIG = "pref_check_config";
	public static final String PREF_FIRST_LAUNCH = "pref_first_launch";
	private static String CURRENT_ADDRESS = "org.linphone.current-address"; 
	private static String CURRENT_DISPLAYNAME = "org.linphone.current-displayname"; 
	static int VIDEO_VIEW_ACTIVITY = 100;
	
	Settings.System mSystemSettings = new Settings.System();
	MediaPlayer mRingerPlayer;

	Vibrator mVibrator;
	private static boolean accountCheckingDone;

	/**
	 * @return null if not ready yet
	 */
	public static DialerActivity getDialer() { 
		return theDialer;
	}

	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dialer);
		LinphoneManager.getInstance().setAudioManager(((AudioManager)getSystemService(Context.AUDIO_SERVICE)));
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,"Linphone");
		mPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());


		try {
			mAddress = (AddressText) findViewById(R.id.SipUri); 
			mDisplayNameView = (TextView) findViewById(R.id.DisplayNameView);
			((EraseButton) findViewById(R.id.Erase)).setAddressView(mAddress);
			

			mAddVideo = (ImageButton) findViewById(R.id.AddVideo);
			mAddVideo.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					// If no in video call; try to reinvite with video
					boolean alreadyInVideoCall = !CallManager.getInstance().reinviteWithVideo();
					if (alreadyInVideoCall) {
						// In video call; going back to video call activity
						startVideoView(VIDEO_VIEW_ACTIVITY);
					}
				}
			});

			mCall = (ImageButton) findViewById(R.id.Call);
			mCall.setOnClickListener(new OnClickListener() { 
				public void onClick(View v) {
					callOrAcceptIncommingCall();
				}
			}); 
			mDecline= findViewById(R.id.Decline);
			mDecline.setEnabled(false);

			mHangup = findViewById(R.id.HangUp); 
			

			mCallControlRow = (LinearLayout) findViewById(R.id.CallControlRow);
			mInCallControlRow = (TableRow) findViewById(R.id.IncallControlRow);
			mAddressLayout = (View) findViewById(R.id.Addresslayout);
			mInCallAddressLayout = (View) findViewById(R.id.IncallAddressLayout);
			mMute = (MuteMicButton)findViewById(R.id.mic_mute_button);
			mSpeaker = (SpeakerButton)findViewById(R.id.speaker_button);
/*			if (Hacks.isGalaxyS()) {
				// Galaxy S doesn't handle audio routing properly
				// so disabling it totally
				mSpeaker.setVisibility(View.GONE);
			}*/
			mInCallControlRow.setVisibility(View.GONE);
			mInCallAddressLayout.setVisibility(View.GONE);
			
			if (LinphoneService.isready() && getIntent().getData() != null && !LinphoneService.getLc().isIncall()) {
		    	newOutgoingCall(getIntent().getData().toString().substring("tel://".length()));
		    	getIntent().setData(null);
		    }
			
			if (LinphoneService.isready()) {
				LinphoneCore lc = LinphoneService.getLc();
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
						
						String DisplayName = lc.getRemoteAddress().getDisplayName();
						if (DisplayName!=null) {
							mDisplayNameView.setText(DisplayName);
						} else {
							mDisplayNameView.setText(lc.getRemoteAddress().getUserName());
						}
						loadMicAndSpeakerUiStateFromLibrary();
						LinphoneActivity.instance().startProxymitySensor();
						mWakeLock.acquire();
					} 
				}
			}
			

			initializeDigits();
			
			
			mStatus =  (TextView) findViewById(R.id.status_label);
			theDialer = this;
		
		} catch (Exception e) {
			Log.e(LinphoneService.TAG,"Cannot start linphone",e);
			finish();
		}


		if (!accountCheckingDone) checkAccountsSettings();
	}

	private void callOrAcceptIncommingCall() {
		LinphoneCore lLinphoneCore =  LinphoneService.instance().getLinphoneCore();
		if (lLinphoneCore.isInComingInvitePending()) {
			try {
				lLinphoneCore.acceptCall(lLinphoneCore.getCurrentCall());
			} catch (LinphoneCoreException e) {
				lLinphoneCore.terminateCall(lLinphoneCore.getCurrentCall());
				Toast toast = Toast.makeText(DialerActivity.this
						,String.format(getString(R.string.warning_wrong_destination_address),mAddress.getText().toString())
						,Toast.LENGTH_LONG);
				toast.show();
			}
			return;
		}
		if (mAddress.getText().length() >0) { 
			newOutgoingCall(mAddress.getText().toString(), mAddress.getDisplayedName());
		}
	}

	private void initializeDigits() {
		if (findViewById(R.id.Digit00) == null) return; // In landscape view, no keyboard

		((Digit) findViewById(R.id.Digit00)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.Digit1)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.Digit2)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.Digit3)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.Digit4)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.Digit5)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.Digit6)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.Digit7)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.Digit8)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.Digit9)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.DigitStar)).setAddressWidget(mAddress);
		((Digit) findViewById(R.id.DigitHash)).setAddressWidget(mAddress);
	}

	private boolean checkDefined(int ... keys) {
		for (int key : keys) {
			String conf = mPref.getString(getString(key), null);
			if (conf == null || "".equals(conf))
				return false;
		}
		return true;
	}


	private void checkAccountsSettings() {
		if (mPref.getBoolean(PREF_FIRST_LAUNCH, true)) {
			// First launch
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
					accountCheckingDone = true;
				}
			});

			builder.create().show();


		} else if (!mPref.getBoolean(PREF_CHECK_CONFIG, false)
				&& !checkDefined(R.string.pref_username_key, R.string.pref_passwd_key, R.string.pref_domain_key)) {
			// Account not set

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
					accountCheckingDone = true;
				}
			}).setNeutralButton(getString(R.string.no), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
					accountCheckingDone = true;
				}
			}).setNegativeButton(getString(R.string.never_remind), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					mPref.edit().putBoolean(PREF_CHECK_CONFIG, true).commit();
					dialog.cancel();
					accountCheckingDone = true;
				}
			});

			builder.create().show();
		} else {
			accountCheckingDone = true;
		}
	}

	private void updateIncallVideoCallButton() {
		boolean prefVideoEnabled = mPref.getBoolean(getString(R.string.pref_video_enable_key), false);
		if (prefVideoEnabled && !mCall.isEnabled()) {
			mAddVideo.setVisibility(View.VISIBLE);
			mAddVideo.setEnabled(true);
		} else {
			mAddVideo.setVisibility(View.GONE);
		}
	}
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putString(CURRENT_ADDRESS,  mAddress.getText().toString());
		if (mAddress.getDisplayedName() != null)
			savedInstanceState.putString(CURRENT_DISPLAYNAME,mAddress.getDisplayedName());
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		String lAddress = savedInstanceState.getString(CURRENT_ADDRESS);
		if (lAddress != null && mAddress != null) {
			mAddress.setText(lAddress); 
		}
		mAddress.setDisplayedName(savedInstanceState.getString(CURRENT_DISPLAYNAME));
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mWakeLock.isHeld()) mWakeLock.release();
		theDialer=null;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}

	public void authInfoRequested(LinphoneCore lc, String realm, String username) /*nop*/{}
	public void byeReceived(LinphoneCore lc, String from) {/*nop*/}
	public void displayMessage(LinphoneCore lc, String message) {/*nop*/}
	public void displayWarning(LinphoneCore lc, String message) {/*nop*/}

	public void displayStatus(LinphoneCore lc, String message) {
		mStatus.setText(message);
	}

	public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String message) {
		if (state == LinphoneCore.GlobalState.GlobalOn) {
			mCall.setEnabled(!lc.isIncall());
			mHangup.setEnabled(!mCall.isEnabled());  
			updateIncallVideoCallButton();
			try{
				LinphoneService.instance().initFromConf();
			} catch (Exception e) {
				Log.e(LinphoneService.TAG,"Cannot get initial config", e);
			}
			if (getIntent().getData() != null) {
		    	newOutgoingCall(getIntent().getData().toString().substring("tel://".length()));
		    	getIntent().setData(null);
		    }
		} 
	}


	private void startVideoView(int requestCode) {
		Intent lIntent = new Intent();
		lIntent.setClass(this, VideoCallActivity.class);
		startActivityForResult(lIntent,requestCode);
	}
	
	public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig cfg,final LinphoneCore.RegistrationState state,final String smessage) {/*nop*/};
	public void callState(final LinphoneCore lc,final LinphoneCall call, final State state, final String message) {

		if (state == LinphoneCall.State.OutgoingInit) {
			enterIncalMode(lc);
			LinphoneManager.getInstance().routeAudioToReceiver();
		} else if (state == LinphoneCall.State.IncomingReceived) { 
			resetCameraFromPreferences();
			callPending(call);
		} else if (state == LinphoneCall.State.Connected) {
			enterIncalMode(lc);
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
			if (LinphoneService.instance().getLinphoneCore().getCurrentCall().getCurrentParamsCopy().getVideoEnabled()) {
				if (!VideoCallActivity.launched) {
					startVideoView(VIDEO_VIEW_ACTIVITY);
				}
			}
		}
	}

	public void show(LinphoneCore lc) {/*nop*/}
	
	private void enterIncalMode(LinphoneCore lc) {
		mCallControlRow.setVisibility(View.GONE);
		mInCallControlRow.setVisibility(View.VISIBLE);
		mAddressLayout.setVisibility(View.GONE);
		mInCallAddressLayout.setVisibility(View.VISIBLE);
		mCall.setEnabled(false);
		updateIncallVideoCallButton();
		mHangup.setEnabled(true);
		LinphoneAddress remote=lc.getRemoteAddress();
		if (remote!=null){
			String DisplayName = remote.getDisplayName();
			if (DisplayName!=null) {
				mDisplayNameView.setText(DisplayName);
			} else  if (lc.getRemoteAddress().getUserName() != null){
				mDisplayNameView.setText(lc.getRemoteAddress().getUserName());
			} else {
				mDisplayNameView.setText(lc.getRemoteAddress().toString());
			}
		}
		loadMicAndSpeakerUiStateFromLibrary();
		
		if (mSpeaker.isSpeakerOn()) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
		} else {
			LinphoneManager.getInstance().routeAudioToReceiver();
		}
		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
		LinphoneActivity.instance().startProxymitySensor();
		if (!mWakeLock.isHeld()) mWakeLock.acquire(); 

	}
	private void loadMicAndSpeakerUiStateFromLibrary() {
		mMute.setChecked(LinphoneService.getLc().isMicMuted());
		mSpeaker.setSpeakerOn(LinphoneManager.getInstance().isSpeakerOn());
	}
	
	private void resetCameraFromPreferences() {
		boolean useFrontCam = mPref.getBoolean(getString(R.string.pref_video_use_front_camera_key), false);
		getVideoManager().setUseFrontCamera(useFrontCam);
		final int phoneOrientation = 90 * getWindowManager().getDefaultDisplay().getOrientation();
		getVideoManager().setPhoneOrientation(phoneOrientation);
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
		if (LinphoneService.instance().getLinphoneCore().isVideoEnabled()) {
			finishActivity(VIDEO_VIEW_ACTIVITY); 
		}
		if (mWakeLock.isHeld())mWakeLock.release();
		mSpeaker.setSpeakerOn(false);
		LinphoneManager.getInstance().routeAudioToReceiver();
		BandwidthManager.getInstance().setUserRestriction(false);
		resetCameraFromPreferences();
		LinphoneActivity.instance().stopProxymitySensor();
	}

	private void callPending(LinphoneCall call) {
		mDecline.setEnabled(true);
		//routeAudioToSpeaker();
		
		// Privacy setting to not share the user camera by default
		boolean prefVideoEnable = mPref.getBoolean(getString(R.string.pref_video_enable_key), false);
		boolean prefAutomaticallyShareMyCamera = mPref.getBoolean(getString(R.string.pref_video_automatically_share_my_video_key), false);
		getVideoManager().setMuted(!(prefVideoEnable && prefAutomaticallyShareMyCamera));
		call.enableCamera(prefAutomaticallyShareMyCamera);
	}
	public void newOutgoingCall(String aTo) {
		newOutgoingCall(aTo,null);
	}
	

	private synchronized void newOutgoingCall(String aTo, String displayName) {
		String lto = aTo;
		if (aTo.contains(OutgoingCallReceiver.TAG)) {
			lto = aTo.replace(OutgoingCallReceiver.TAG, "");
		}
		mAddress.setText(lto);
		mAddress.setDisplayedName(displayName);
		LinphoneCore lLinphoneCore = LinphoneService.instance().getLinphoneCore(); 
		if (lLinphoneCore.isIncall()) {
			Toast toast = Toast.makeText(DialerActivity.this, getString(R.string.warning_already_incall), Toast.LENGTH_LONG);
			toast.show();
			return;
		}
		LinphoneAddress lAddress;
		try {
			lAddress = lLinphoneCore.interpretUrl(lto);
		} catch (LinphoneCoreException e) {
			Toast toast = Toast.makeText(DialerActivity.this
					,String.format(getString(R.string.warning_wrong_destination_address),mAddress.getText().toString())
					, Toast.LENGTH_LONG);
			toast.show();
			return;
		}
		lAddress.setDisplayName(mAddress.getDisplayedName());

		try {
			boolean prefVideoEnable = mPref.getBoolean(getString(R.string.pref_video_enable_key), false);
			boolean prefInitiateWithVideo = mPref.getBoolean(getString(R.string.pref_video_initiate_call_with_video_key), false);
			resetCameraFromPreferences();
			CallManager.getInstance().inviteAddress(lAddress, prefVideoEnable && prefInitiateWithVideo);

		} catch (LinphoneCoreException e) {
			Toast toast = Toast.makeText(DialerActivity.this
					,String.format(getString(R.string.error_cannot_get_call_parameters),mAddress.getText().toString())
					,Toast.LENGTH_LONG);
			toast.show();
			return;
		}
	}
	
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf, String url) {}
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {}
	public void textReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneAddress from, String message) {}


	private AndroidCameraRecordManager getVideoManager() {
		return AndroidCameraRecordManager.getInstance();
	}
	public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status,
			int delay_ms, Object data) {}
	
	public void setContactAddress(String aContact,String aDisplayName) {
		mAddress.setText(aContact);
		mAddress.setDisplayedName(aDisplayName);
	}
}


