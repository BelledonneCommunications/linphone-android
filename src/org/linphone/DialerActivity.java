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

import org.linphone.component.ToggleImageButton;
import org.linphone.component.ToggleImageButton.OnCheckedChangeListener;
import org.linphone.core.AndroidCameraRecordManager;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LinphoneCall.State;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

public class DialerActivity extends Activity implements LinphoneCoreListener {
	
	private TextView mAddress;
	private TextView mDisplayNameView;

	private TextView mStatus;
	private ImageButton mCall;
	private ImageButton mDecline;
	private ImageButton mHangup;
	private Button mErase;
	
	private Button mZero;
	private Button mOne;
	private Button mTwo;
	private Button mThree ;
	private Button mFour;
	private Button mFive;
	private Button mSix;
	private Button mSeven;
	private Button mEight;
	private Button mNine;
	private Button mStar;
	private Button mHash;
	
	private ToggleImageButton mMute;
	private ToggleImageButton mSpeaker;
	
	private LinearLayout mCallControlRow;
	private TableRow mInCallControlRow;
	private View mAddressLayout;
	private View mInCallAddressLayout;
	
	private static DialerActivity theDialer;
	
	private String mDisplayName;
	private AudioManager mAudioManager;
	private PowerManager.WakeLock mWakeLock;
	private SharedPreferences mPref;
	private ImageButton mAddVideo;
	
	private static final String PREF_CHECK_CONFIG = "pref_check_config";
	private static final String PREF_FIRST_LAUNCH = "pref_first_launch";
	private static String CURRENT_ADDRESS = "org.linphone.current-address"; 
	private static String CURRENT_DISPLAYNAME = "org.linphone.current-displayname"; 
	static int VIDEO_VIEW_ACTIVITY = 100;
	
	Settings.System mSystemSettings = new Settings.System();
	MediaPlayer mRingerPlayer;
	LinphoneCall.State mCurrentCallState;
	Vibrator mVibrator;
	/**
	 * 
	 * @return null if not ready yet
	 */
	public static DialerActivity getDialer() { 
		if (theDialer == null) {
			return null;
		} else {
			return theDialer;
		}
	}
	public void setContactAddress(String aContact,String aDisplayName) {
		mAddress.setText(aContact);
		mDisplayName = aDisplayName;
	}
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dialer);
		mAudioManager = ((AudioManager)getSystemService(Context.AUDIO_SERVICE));
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,"Linphone");
		mPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		try {

			
			mAddress = (TextView) findViewById(R.id.SipUri); 
			mDisplayNameView = (TextView) findViewById(R.id.DisplayNameView);
			mErase = (Button)findViewById(R.id.Erase); 
			
			mErase.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					mDisplayName=null;
					if (mAddress.length() >0) {
						int lBegin = mAddress.getSelectionStart();
						if (lBegin == -1) {
							lBegin = mAddress.getEditableText().length()-1;
						}
						if (lBegin >0) {
							mAddress.getEditableText().delete(lBegin-1,lBegin);
						}
					}
				}
			});
			mErase.setOnLongClickListener(new OnLongClickListener() {
				public boolean onLongClick(View arg0) {
					mAddress.getEditableText().clear();
					return true;
				}
			});

			mAddVideo = (ImageButton) findViewById(R.id.AddVideo);
			mAddVideo.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					LinphoneCore lLinphoneCore =  LinphoneService.instance().getLinphoneCore();
					LinphoneCall lCall = lLinphoneCore.getCurrentCall();
					LinphoneCallParams params = lCall.getCurrentParamsCopy();
					if (params.getVideoEnabled()) {
						// In video call; going back to video call activity
						startVideoView(VIDEO_VIEW_ACTIVITY);
					} else {
						// Not in video call; should go try to reinvite with video
						params.setVideoEnabled(true);
						getVideoManager().setMuted(false);
						lLinphoneCore.updateCall(lCall, params);
					}
				}
			});

			mCall = (ImageButton) findViewById(R.id.Call);
			mCall.setOnClickListener(new OnClickListener() { 
				public void onClick(View v) {
					LinphoneCore lLinphoneCore =  LinphoneService.instance().getLinphoneCore();
					if (lLinphoneCore.isInComingInvitePending()) {
						try {
							lLinphoneCore.acceptCall(lLinphoneCore.getCurrentCall());
						} catch (LinphoneCoreException e) {
							lLinphoneCore.terminateCall(lLinphoneCore.getCurrentCall());
							Toast toast = Toast.makeText(DialerActivity.this
									,String.format(getString(R.string.warning_wrong_destination_address),mAddress.getText().toString())
									, Toast.LENGTH_LONG);
							toast.show();
						}
						return;
					}
					if (mAddress.getText().length() >0) { 
						newOutgoingCall(mAddress.getText().toString(),mDisplayName);
					}
				}
				
			}); 
			mDecline= (ImageButton) findViewById(R.id.Decline);
			mHangup = (ImageButton) findViewById(R.id.HangUp); 
			
			OnClickListener lHangupListener = new OnClickListener() {
				
				public void onClick(View v) {
					LinphoneCore lLinphoneCore =  LinphoneService.instance().getLinphoneCore();
					lLinphoneCore.terminateCall(lLinphoneCore.getCurrentCall());
				}
				
			};
			mHangup.setOnClickListener(lHangupListener); 
			mDecline.setOnClickListener(lHangupListener);
			

			mCallControlRow = (LinearLayout) findViewById(R.id.CallControlRow);
			mInCallControlRow = (TableRow) findViewById(R.id.IncallControlRow);
			mAddressLayout = (View) findViewById(R.id.Addresslayout);
			mInCallAddressLayout = (View) findViewById(R.id.IncallAddressLayout);
			mMute = (ToggleImageButton)findViewById(R.id.mic_mute_button);
			mSpeaker = (ToggleImageButton)findViewById(R.id.speaker_button);
			
			mInCallControlRow.setVisibility(View.GONE);
			mInCallAddressLayout.setVisibility(View.GONE);
			mDecline.setEnabled(false);
			if (LinphoneService.isready() && getIntent().getData() != null && !LinphoneService.instance().getLinphoneCore().isIncall()) {
		    	newOutgoingCall(getIntent().getData().toString().substring("tel://".length()));
		    	getIntent().setData(null);
		    }
			if (LinphoneService.isready()) {
				LinphoneCore lLinphoneCore = LinphoneService.instance().getLinphoneCore();
				if (lLinphoneCore.isIncall()) {
					mCurrentCallState = lLinphoneCore.getCurrentCall().getState();
					if(lLinphoneCore.isInComingInvitePending()) {
						callPending();
					} else {
						mCall.setEnabled(false);
						mHangup.setEnabled(!mCall.isEnabled());
						updateIncallVideoCallButton();
						mCallControlRow.setVisibility(View.GONE);
						mInCallControlRow.setVisibility(View.VISIBLE);
						mAddressLayout.setVisibility(View.GONE);
						mInCallAddressLayout.setVisibility(View.VISIBLE);
						
						String DisplayName = lLinphoneCore.getRemoteAddress().getDisplayName();
						if (DisplayName!=null) {
							mDisplayNameView.setText(DisplayName);
						} else {
							mDisplayNameView.setText(lLinphoneCore.getRemoteAddress().getUserName());
						}
						configureMuteAndSpeakerButtons();
						mWakeLock.acquire();
					} 
				}
			}
			
			

			mMute.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				public void onCheckedChanged(ToggleImageButton button, boolean isChecked) {
					LinphoneCore lc = LinphoneService.instance().getLinphoneCore();
					if (isChecked) {
						lc.muteMic(true);
					} else {
						lc.muteMic(false);
					}
				}
			});
			
			
			mSpeaker.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				public void onCheckedChanged(ToggleImageButton buttonView,	boolean isChecked) {
					if (isChecked) {
						routeAudioToSpeaker();
					} else {
						routeAudioToReceiver();
					}
				}
			});
			
			mZero = (Button) findViewById(R.id.Button00) ;
			if (mZero != null) {
				setDigitListener(mZero,'0');
				mZero.setOnLongClickListener(new OnLongClickListener() {
					public boolean onLongClick(View arg0) {
						LinphoneCore lc = LinphoneService.instance().getLinphoneCore();
						lc.stopDtmf();
						int lBegin = mAddress.getSelectionStart();
						if (lBegin == -1) {
							lBegin = mAddress.getEditableText().length();
						}
						if (lBegin >=0) {
						mAddress.getEditableText().insert(lBegin,"+");
						}
						return true;
					}
					
				});
				mOne = (Button) findViewById(R.id.Button01) ;
				setDigitListener(mOne,'1');
				mTwo = (Button) findViewById(R.id.Button02);
				setDigitListener(mTwo,'2');
				mThree = (Button) findViewById(R.id.Button03);
				setDigitListener(mThree,'3');
				mFour = (Button) findViewById(R.id.Button04);
				setDigitListener(mFour,'4');
				mFive = (Button) findViewById(R.id.Button05);
				setDigitListener(mFive,'5');
				mSix = (Button) findViewById(R.id.Button06);
				setDigitListener(mSix,'6');
				mSeven = (Button) findViewById(R.id.Button07);
				setDigitListener(mSeven,'7');
				mEight = (Button) findViewById(R.id.Button08);
				setDigitListener(mEight,'8');
				mNine = (Button) findViewById(R.id.Button09);
				setDigitListener(mNine,'9');
				mStar = (Button) findViewById(R.id.ButtonStar);
				setDigitListener(mStar,'*');
				mHash = (Button) findViewById(R.id.ButtonHash);
				setDigitListener(mHash,'#');
			}

			mStatus =  (TextView) findViewById(R.id.status_label);
			theDialer = this;
		
		} catch (Exception e) {
			Log.e(LinphoneService.TAG,"Cannot start linphone",e);
			finish();
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
		if (mDisplayName != null) savedInstanceState.putString(CURRENT_DISPLAYNAME,mDisplayName);
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		String lAddress = savedInstanceState.getString(CURRENT_ADDRESS);
		if (lAddress != null && mAddress != null) {
			mAddress.setText(lAddress); 
		}
		mDisplayName = savedInstanceState.getString(CURRENT_DISPLAYNAME);
	}
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if (mWakeLock.isHeld()) mWakeLock.release();
		theDialer=null;
	}
	@Override
	protected void onResume() {
		super.onResume();
	}
	public void authInfoRequested(LinphoneCore lc, String realm, String username) {
		// TODO Auto-generated method stub
		
	}
	public void byeReceived(LinphoneCore lc, String from) {
		// TODO Auto-generated method stub
		
	}
	public void displayMessage(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}
	public void displayStatus(LinphoneCore lc, String message) {
		mStatus.setText(message);
	}
	public void displayWarning(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}
	public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String message) {

		if (state == LinphoneCore.GlobalState.GlobalOn) {
			mCall.setEnabled(!lc.isIncall());
			mHangup.setEnabled(!mCall.isEnabled());  
			updateIncallVideoCallButton();
			try{
				LinphoneService.instance().initFromConf();
			} catch (LinphoneConfigException ec) {
				if (mPref.getBoolean(PREF_FIRST_LAUNCH, true)) {
					Log.w(LinphoneService.TAG,"no valid settings found - first launch",ec);
					LinphoneActivity.instance().startprefActivity();
					mPref.edit().putBoolean(PREF_FIRST_LAUNCH, false).commit();
				} else {
					Log.w(LinphoneService.TAG,"no valid settings found", ec);
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
						}
					}).setNeutralButton(getString(R.string.no), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();

						}
					}).setNegativeButton(getString(R.string.never_remind), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							mPref.edit().putBoolean(PREF_CHECK_CONFIG, true).commit();
							dialog.cancel();
						}
					});
					if (mPref.getBoolean(PREF_CHECK_CONFIG, false) == false) {
						builder.create().show();
					}
				}
			} catch (Exception e ) {
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
		if (mCurrentCallState ==  LinphoneCall.State.IncomingReceived) { 
			//previous state was ringing, so stop ringing
			stoptRinging();
		}
		if (state == LinphoneCall.State.OutgoingInit) {
			mWakeLock.acquire();
			enterIncalMode(lc);
			routeAudioToReceiver();
		} else if (state == LinphoneCall.State.IncomingReceived) { 
			callPending();
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
		mCurrentCallState = state;
	}

	public void show(LinphoneCore lc) {
		// TODO Auto-generated method stub
		
	}
	
	private void enterIncalMode(LinphoneCore lc) {
		resetCameraFromPreferences();
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
		configureMuteAndSpeakerButtons();
		
		if (mSpeaker.isChecked()) {
			 routeAudioToSpeaker();
		} else {
			 routeAudioToReceiver();
		}
		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
	}
	private void configureMuteAndSpeakerButtons() {
		mMute.setChecked(LinphoneService.instance().getLinphoneCore().isMicMuted());
		if ((Integer.parseInt(Build.VERSION.SDK) <=4 && mAudioManager.getRouting(AudioManager.MODE_NORMAL) == AudioManager.ROUTE_SPEAKER) 
				|| Integer.parseInt(Build.VERSION.SDK) >4 &&mAudioManager.isSpeakerphoneOn()) {
			mSpeaker.setChecked(true);
		} else {
			mSpeaker.setChecked(false);
		}
	}
	
	private void resetCameraFromPreferences() {
		boolean useFrontCam = mPref.getBoolean(getString(R.string.pref_video_use_front_camera_key), false);
		AndroidCameraRecordManager.getInstance().setUseFrontCamera(useFrontCam);
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
		mSpeaker.setChecked(false);
		routeAudioToReceiver();
		BandwidthManager.getInstance().setUserRestriction(false);
		resetCameraFromPreferences();
	}
	private void routeAudioToSpeaker() {
		if (Integer.parseInt(Build.VERSION.SDK) <= 4 /*<donut*/) {
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(true); 
		}
		
	}
	private void routeAudioToReceiver() {
		if (Integer.parseInt(Build.VERSION.SDK) <=4 /*<donut*/) {
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(false); 
		}		
	}
	private void callPending() {
		mDecline.setEnabled(true);
		routeAudioToSpeaker();
		
		// Privacy setting to not share the user camera by default
		boolean prefVideoEnable = mPref.getBoolean(getString(R.string.pref_video_enable_key), false);
		boolean prefAutomaticallyShareMyCamera = mPref.getBoolean(getString(R.string.pref_video_automatically_share_my_video_key), false);
		getVideoManager().setMuted(!(prefVideoEnable && prefAutomaticallyShareMyCamera));
		startRinging();
	}
	public void newOutgoingCall(String aTo) {
		newOutgoingCall(aTo,null);
	}
	

	public synchronized void newOutgoingCall(String aTo, String displayName) {
		String lto = aTo;
		if (aTo.contains(OutgoingCallReceiver.TAG)) {
			lto = aTo.replace(OutgoingCallReceiver.TAG, "");
		}
		mAddress.setText(lto);
		mDisplayName = displayName;
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
		lAddress.setDisplayName(mDisplayName);

	try {
		LinphoneCallParams lParams = lLinphoneCore.createDefaultCallParameters();
		boolean prefVideoEnable = mPref.getBoolean(getString(R.string.pref_video_enable_key), false);
		boolean prefInitiateWithVideo = mPref.getBoolean(getString(R.string.pref_video_initiate_call_with_video_key), false);

		if (prefVideoEnable && prefInitiateWithVideo && lParams.getVideoEnabled()) {
			getVideoManager().setMuted(false);
			lParams.setVideoEnabled(true);
		} else {
			lParams.setVideoEnabled(false);
		}
		lLinphoneCore.inviteAddressWithParams(lAddress, lParams);

	} catch (LinphoneCoreException e) {
		Toast toast = Toast.makeText(DialerActivity.this
				,String.format(getString(R.string.error_cannot_get_call_parameters),mAddress.getText().toString())
				,Toast.LENGTH_LONG);
		toast.show();
		return;
	}
	}
	private void setDigitListener(Button aButton,char dtmf) {
		class DialKeyListener implements  OnClickListener ,OnTouchListener {
			final String mKeyCode;
			final TextView mAddressView;
			boolean mIsDtmfStarted=false;
			DialKeyListener(TextView anAddress, char aKeyCode) {
				mKeyCode = String.valueOf(aKeyCode);
				mAddressView = anAddress;
			}
			public void onClick(View v) {
				LinphoneCore lc = LinphoneService.instance().getLinphoneCore();
				stopDtmf(); 
				if (lc.isIncall()) {
					lc.sendDtmf(mKeyCode.charAt(0));
				} else {
					int lBegin = mAddressView.getSelectionStart();
					if (lBegin == -1) {
						lBegin = mAddressView.getEditableText().length();
					}
					if (lBegin >=0) {
						mAddressView.getEditableText().insert(lBegin,mKeyCode);
					}
					mDisplayName="";
				}
			}
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN && mIsDtmfStarted ==false) {
					LinphoneCore lc = LinphoneService.instance().getLinphoneCore();
					lc.playDtmf(mKeyCode.charAt(0), -1);
					mIsDtmfStarted=true;
				} else {
					if (event.getAction() == MotionEvent.ACTION_UP) 
						stopDtmf();
				}
				return false;
			}

			private void stopDtmf() {
				LinphoneCore lc = LinphoneService.instance().getLinphoneCore();
				lc.stopDtmf();
				mIsDtmfStarted =false;
			}
			
		};
		DialKeyListener lListener = new DialKeyListener(mAddress,dtmf);
		aButton.setOnClickListener(lListener);
		aButton.setOnTouchListener(lListener);
		
	}
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf,
			String url) {
		// TODO Auto-generated method stub
		
	}
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
		// TODO Auto-generated method stub
		
	}
	public void textReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneAddress from, String message) {
		// TODO Auto-generated method stub
		
	}
	private synchronized void startRinging()  {
		try {
			if (mAudioManager.shouldVibrate(AudioManager.VIBRATE_TYPE_RINGER) && mVibrator !=null) {
				long[] patern = {0,1000,1000};
				mVibrator.vibrate(patern, 1);
			}
			if (mRingerPlayer == null) {
				//mRingerPlayer = MediaPlayer.create(getApplicationContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
				mRingerPlayer = new MediaPlayer();
				mRingerPlayer.setAudioStreamType(AudioManager.STREAM_RING);
				mRingerPlayer.setDataSource(getApplicationContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
				mRingerPlayer.prepare();
				//mRingerPlayer.setVolume(mAudioManager.getStreamVolume(AudioManager.STREAM_RING),mAudioManager.getStreamVolume(AudioManager.STREAM_RING));
				mRingerPlayer.setLooping(true);
				mRingerPlayer.start();
			} else {
				Log.w(LinphoneService.TAG,"already ringing");
			}
		} catch (Exception e) {
			Log.e(LinphoneService.TAG, "cannot handle incoming call",e);
		}

	}
	private synchronized void stoptRinging() {
		if (mRingerPlayer !=null) {
			mRingerPlayer.stop();
			mRingerPlayer.release();
			mRingerPlayer=null;
		}
		if (mVibrator!=null) {
			mVibrator.cancel();
		}
	}

	private AndroidCameraRecordManager getVideoManager() {
		return AndroidCameraRecordManager.getInstance();
	}
}

